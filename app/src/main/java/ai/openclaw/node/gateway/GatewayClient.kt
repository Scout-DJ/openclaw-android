package ai.openclaw.node.gateway

import android.util.Log
import okhttp3.*
import java.util.concurrent.TimeUnit

/**
 * WebSocket client that connects to the OpenClaw gateway.
 * Handles reconnection, heartbeat, and command dispatch.
 */
class GatewayClient(
    private val gatewayUrl: String,
    private val authToken: String,
    private val nodeName: String,
    private val onCommand: (NodeCommand) -> Unit,
    private val onConnectionChange: (Boolean) -> Unit
) {
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)  // No timeout for WebSocket
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    private var ws: WebSocket? = null
    private var connected = false
    private var shouldReconnect = true

    companion object {
        private const val TAG = "GatewayClient"
        private const val RECONNECT_DELAY_MS = 5000L
        private const val MAX_RECONNECT_DELAY_MS = 60000L
    }

    private var reconnectDelay = RECONNECT_DELAY_MS

    fun connect() {
        shouldReconnect = true
        doConnect()
    }

    fun disconnect() {
        shouldReconnect = false
        ws?.close(1000, "Client disconnect")
        ws = null
        connected = false
        onConnectionChange(false)
    }

    fun send(response: NodeResponse) {
        ws?.send(response.toJson())
    }

    private fun doConnect() {
        val url = "$gatewayUrl/ws/node?token=$authToken&name=$nodeName"
        val request = Request.Builder().url(url).build()

        ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "Connected to gateway")
                connected = true
                reconnectDelay = RECONNECT_DELAY_MS
                onConnectionChange(true)

                // Announce capabilities
                val caps = NodeCapabilities(
                    name = nodeName,
                    capabilities = listOf(
                        "camera_snap", "camera_clip",
                        "location_get",
                        "screen_record",
                        "notify",
                        "run",
                        "sensor_read",
                        "file_read", "file_write", "file_list",
                        "clipboard_read", "clipboard_write",
                        "tts_speak",
                        "vibrate",
                        "flashlight",
                        "sms_send", "sms_read",
                        "call",
                        "contacts_read",
                        "calendar_read",
                        "media_play",
                        "a11y_tap", "a11y_swipe", "a11y_type", "a11y_screenshot"
                    )
                )
                webSocket.send(caps.toJson())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val cmd = NodeCommand.fromJson(text)
                    Log.d(TAG, "Command: ${cmd.action}")
                    onCommand(cmd)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse command: $text", e)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "Connection failed: ${t.message}")
                connected = false
                onConnectionChange(false)
                scheduleReconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "Connection closed: $code $reason")
                connected = false
                onConnectionChange(false)
                if (shouldReconnect) scheduleReconnect()
            }
        })
    }

    private fun scheduleReconnect() {
        if (!shouldReconnect) return
        Log.i(TAG, "Reconnecting in ${reconnectDelay}ms...")
        Thread.sleep(reconnectDelay)
        reconnectDelay = (reconnectDelay * 2).coerceAtMost(MAX_RECONNECT_DELAY_MS)
        if (shouldReconnect) doConnect()
    }

    val isConnected get() = connected
}
