package ai.openclaw.node.gateway

import android.util.Log
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * WebSocket client implementing the OpenClaw Gateway Protocol v3.
 *
 * Flow:
 *   1. Connect to ws://<host>:<port>  (plain WebSocket, no path)
 *   2. Receive connect.challenge event from gateway
 *   3. Send connect request with role=node, capabilities, auth token, device identity
 *   4. Receive hello-ok (paired) or pairing-required error
 *   5. If pairing required: send node.pair.request, wait for approval, reconnect
 *   6. Once connected: receive invoke commands, send responses
 */
class GatewayClient(
    private val gatewayUrl: String,       // e.g. "ws://5.78.90.129:18789"
    private val authToken: String,
    private val nodeName: String,
    private val deviceId: String,
    private val onCommand: (NodeCommand) -> Unit,
    private val onConnectionChange: (ConnectionState) -> Unit
) {
    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        AWAITING_CHALLENGE,
        HANDSHAKING,
        PAIRING_PENDING,
        CONNECTED
    }

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    private var ws: WebSocket? = null
    private var state = ConnectionState.DISCONNECTED
    private var shouldReconnect = true
    private var deviceToken: String? = null  // Persisted after successful pairing

    // Capabilities this node offers
    private val caps = listOf("camera", "location", "screen", "notify", "run", "sensor", "file", "tts")
    private val commands = listOf(
        "camera.snap", "camera.clip",
        "location.get",
        "screen.record",
        "notify",
        "run",
        "sensor.read",
        "file.read", "file.write", "file.list",
        "clipboard.read", "clipboard.write",
        "tts.speak",
        "vibrate",
        "flashlight",
        "sms.send", "sms.read",
        "call",
        "contacts.read",
        "calendar.read",
        "media.play",
        "a11y.tap", "a11y.swipe", "a11y.type", "a11y.screenshot"
    )

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
        setState(ConnectionState.DISCONNECTED)
    }

    /** Send a response to a gateway command. */
    fun sendResponse(id: String, ok: Boolean, payload: JSONObject? = null, error: String? = null) {
        ws?.send(ProtocolFrames.response(id, ok, payload, error))
    }

    /** Set a previously-saved device token for reconnection. */
    fun setDeviceToken(token: String?) {
        deviceToken = token
    }

    /** Get the current device token (save this for persistence). */
    fun getDeviceToken(): String? = deviceToken

    private fun setState(newState: ConnectionState) {
        state = newState
        onConnectionChange(newState)
    }

    private fun doConnect() {
        setState(ConnectionState.CONNECTING)

        // Connect to the base gateway URL (no special path)
        val request = Request.Builder().url(gatewayUrl).build()

        ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "WebSocket opened, waiting for challenge...")
                setState(ConnectionState.AWAITING_CHALLENGE)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    handleMessage(webSocket, text)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to handle message: $text", e)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "Connection failed: ${t.message}")
                setState(ConnectionState.DISCONNECTED)
                scheduleReconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "Connection closed: $code $reason")
                setState(ConnectionState.DISCONNECTED)
                if (shouldReconnect) scheduleReconnect()
            }
        })
    }

    private fun handleMessage(webSocket: WebSocket, text: String) {
        val msg = GatewayMessage.parse(text)

        when (msg) {
            is GatewayMessage.Challenge -> handleChallenge(webSocket, msg)
            is GatewayMessage.Response -> handleResponse(webSocket, msg)
            is GatewayMessage.Event -> handleEvent(webSocket, msg)
            is GatewayMessage.Request -> handleRequest(webSocket, msg)
        }
    }

    private fun handleChallenge(webSocket: WebSocket, challenge: GatewayMessage.Challenge) {
        Log.i(TAG, "Received challenge, sending connect request...")
        setState(ConnectionState.HANDSHAKING)

        val connectId = ProtocolFrames.nextId()

        // Use device token if we have one from a previous pairing, otherwise use gateway token
        val token = deviceToken ?: authToken

        val frame = ProtocolFrames.connectRequest(
            id = connectId,
            authToken = token,
            nodeName = nodeName,
            deviceId = deviceId,
            caps = caps,
            commands = commands
        )
        webSocket.send(frame)
    }

    private fun handleResponse(webSocket: WebSocket, res: GatewayMessage.Response) {
        if (res.ok) {
            val payload = res.payload
            val type = payload?.optString("type")

            if (type == "hello-ok") {
                Log.i(TAG, "Connected and authenticated! Protocol: ${payload.optInt("protocol")}")
                reconnectDelay = RECONNECT_DELAY_MS
                setState(ConnectionState.CONNECTED)

                // Save device token if issued
                val auth = payload.optJSONObject("auth")
                auth?.optString("deviceToken")?.takeIf { it.isNotEmpty() }?.let {
                    deviceToken = it
                    Log.i(TAG, "Device token issued — save for reconnection")
                }
            } else {
                Log.d(TAG, "Response OK: ${res.id} -> $type")
            }
        } else {
            val error = res.error
            val message = error?.optString("message") ?: "unknown"
            Log.w(TAG, "Response error: ${res.id} -> $message")

            // If pairing required, request pairing
            if (message.contains("pairing", ignoreCase = true)) {
                Log.i(TAG, "Pairing required — sending pair request...")
                setState(ConnectionState.PAIRING_PENDING)
                val pairId = ProtocolFrames.nextId()
                webSocket.send(ProtocolFrames.pairRequest(pairId, nodeName, deviceId))
            }
        }
    }

    private fun handleEvent(webSocket: WebSocket, event: GatewayMessage.Event) {
        Log.d(TAG, "Event: ${event.event}")

        when (event.event) {
            "node.pair.resolved" -> {
                val approved = event.payload.optBoolean("approved", false)
                if (approved) {
                    Log.i(TAG, "Pairing approved! Reconnecting...")
                    // Token will be in the payload
                    event.payload.optString("token").takeIf { it.isNotEmpty() }?.let {
                        deviceToken = it
                    }
                    // Reconnect with the new device token
                    ws?.close(1000, "Reconnecting after pairing")
                    doConnect()
                } else {
                    Log.w(TAG, "Pairing rejected")
                    setState(ConnectionState.DISCONNECTED)
                }
            }
            // Other events we might care about
            "tick" -> { /* keepalive tick, ignore */ }
            else -> Log.d(TAG, "Unhandled event: ${event.event}")
        }
    }

    private fun handleRequest(webSocket: WebSocket, req: GatewayMessage.Request) {
        // Gateway is invoking a command on this node
        Log.d(TAG, "Invoke: ${req.method}")
        val cmd = NodeCommand.fromGatewayRequest(req)
        onCommand(cmd)
    }

    private fun scheduleReconnect() {
        if (!shouldReconnect) return
        Log.i(TAG, "Reconnecting in ${reconnectDelay}ms...")
        Thread {
            Thread.sleep(reconnectDelay)
            reconnectDelay = (reconnectDelay * 2).coerceAtMost(MAX_RECONNECT_DELAY_MS)
            if (shouldReconnect) doConnect()
        }.start()
    }

    val isConnected get() = state == ConnectionState.CONNECTED
    val currentState get() = state
}
