package ai.openclaw.node.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import ai.openclaw.node.App
import ai.openclaw.node.MainActivity
import ai.openclaw.node.gateway.GatewayClient
import ai.openclaw.node.gateway.GatewayClient.ConnectionState
import ai.openclaw.node.gateway.NodeCommand
import ai.openclaw.node.capabilities.*
import org.json.JSONObject

/**
 * Foreground service that maintains the gateway connection
 * and dispatches commands to capability handlers.
 */
class NodeService : Service() {

    private var gateway: GatewayClient? = null
    private val capabilities = mutableMapOf<String, Capability>()

    companion object {
        private const val TAG = "NodeService"
        private const val NOTIFICATION_ID = 1
        private const val PREFS_NAME = "openclaw_node"
        private const val KEY_DEVICE_TOKEN = "device_token"
        const val EXTRA_GATEWAY_URL = "gateway_url"
        const val EXTRA_AUTH_TOKEN = "auth_token"
        const val EXTRA_NODE_NAME = "node_name"
    }

    override fun onCreate() {
        super.onCreate()
        registerCapability(CameraCapability(this))
        registerCapability(LocationCapability(this))
        registerCapability(NotifyCapability(this))
        registerCapability(SensorCapability(this))
        registerCapability(FileCapability(this))
    }

    private fun registerCapability(cap: Capability) {
        for (action in cap.supportedActions) {
            capabilities[action] = cap
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra(EXTRA_GATEWAY_URL) ?: return START_NOT_STICKY
        val token = intent.getStringExtra(EXTRA_AUTH_TOKEN) ?: return START_NOT_STICKY
        val name = intent.getStringExtra(EXTRA_NODE_NAME) ?: "android"

        // Generate a stable device ID from the app installation
        val deviceId = getOrCreateDeviceId()

        startForeground(NOTIFICATION_ID, buildNotification("Connecting..."))

        gateway = GatewayClient(
            gatewayUrl = url,
            authToken = token,
            nodeName = name,
            deviceId = deviceId,
            onCommand = ::handleCommand,
            onConnectionChange = { state ->
                val status = when (state) {
                    ConnectionState.DISCONNECTED -> "Disconnected — reconnecting..."
                    ConnectionState.CONNECTING -> "Connecting..."
                    ConnectionState.AWAITING_CHALLENGE -> "Authenticating..."
                    ConnectionState.HANDSHAKING -> "Handshaking..."
                    ConnectionState.PAIRING_PENDING -> "Pairing pending — approve on gateway"
                    ConnectionState.CONNECTED -> "Connected to $url"
                }
                updateNotification(status)

                // Persist device token when connected
                if (state == ConnectionState.CONNECTED) {
                    gateway?.getDeviceToken()?.let { saveDeviceToken(it) }
                }
            }
        )

        // Restore saved device token
        loadDeviceToken()?.let { gateway?.setDeviceToken(it) }

        gateway?.connect()

        return START_STICKY
    }

    private fun handleCommand(cmd: NodeCommand) {
        val cap = capabilities[cmd.action]
        if (cap == null) {
            gateway?.sendResponse(
                id = cmd.id,
                ok = false,
                error = "Unknown action: ${cmd.action}"
            )
            return
        }

        Thread {
            try {
                val result = cap.execute(cmd)
                if (result.status == "ok") {
                    val payload = JSONObject(result.data.toString())
                    result.attachment?.let { payload.put("attachment", it) }
                    gateway?.sendResponse(id = cmd.id, ok = true, payload = payload)
                } else {
                    gateway?.sendResponse(id = cmd.id, ok = false, error = result.error)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Command failed: ${cmd.action}", e)
                gateway?.sendResponse(id = cmd.id, ok = false, error = e.message ?: "Unknown error")
            }
        }.start()
    }

    private fun getOrCreateDeviceId(): String {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var id = prefs.getString("device_id", null)
        if (id == null) {
            id = "android-${java.util.UUID.randomUUID()}"
            prefs.edit().putString("device_id", id).apply()
        }
        return id
    }

    private fun saveDeviceToken(token: String) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_DEVICE_TOKEN, token).apply()
    }

    private fun loadDeviceToken(): String? {
        return getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_DEVICE_TOKEN, null)
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, App.CHANNEL_SERVICE)
            .setContentTitle("OpenClaw Node")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    override fun onDestroy() {
        gateway?.disconnect()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
