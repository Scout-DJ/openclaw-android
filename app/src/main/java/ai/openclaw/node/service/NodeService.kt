package ai.openclaw.node.service

import android.app.*
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import ai.openclaw.node.App
import ai.openclaw.node.MainActivity
import ai.openclaw.node.gateway.GatewayClient
import ai.openclaw.node.gateway.NodeCommand
import ai.openclaw.node.gateway.NodeResponse
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
        const val EXTRA_GATEWAY_URL = "gateway_url"
        const val EXTRA_AUTH_TOKEN = "auth_token"
        const val EXTRA_NODE_NAME = "node_name"
    }

    override fun onCreate() {
        super.onCreate()
        // Register capabilities
        registerCapability(CameraCapability(this))
        registerCapability(LocationCapability(this))
        registerCapability(NotifyCapability(this))
        registerCapability(SensorCapability(this))
        registerCapability(FileCapability(this))
        // More capabilities registered as implemented
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

        startForeground(NOTIFICATION_ID, buildNotification("Connecting..."))

        gateway = GatewayClient(
            gatewayUrl = url,
            authToken = token,
            nodeName = name,
            onCommand = ::handleCommand,
            onConnectionChange = { connected ->
                val status = if (connected) "Connected to $url" else "Disconnected — reconnecting..."
                updateNotification(status)
            }
        )
        gateway?.connect()

        return START_STICKY
    }

    private fun handleCommand(cmd: NodeCommand) {
        val cap = capabilities[cmd.action]
        if (cap == null) {
            gateway?.send(NodeResponse(
                id = cmd.id,
                status = "error",
                error = "Unknown action: ${cmd.action}"
            ))
            return
        }

        // Execute async
        Thread {
            try {
                val response = cap.execute(cmd)
                gateway?.send(response)
            } catch (e: Exception) {
                Log.e(TAG, "Command failed: ${cmd.action}", e)
                gateway?.send(NodeResponse(
                    id = cmd.id,
                    status = "error",
                    error = e.message ?: "Unknown error"
                ))
            }
        }.start()
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
