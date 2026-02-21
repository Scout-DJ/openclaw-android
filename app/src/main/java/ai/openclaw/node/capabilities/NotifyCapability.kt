package ai.openclaw.node.capabilities

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import ai.openclaw.node.App
import ai.openclaw.node.gateway.NodeCommand
import ai.openclaw.node.gateway.NodeResponse
import org.json.JSONObject

class NotifyCapability(private val context: Context) : Capability {

    override val supportedActions = listOf("notify", "tts_speak", "vibrate", "flashlight")
    private var notifyId = 1000

    override fun execute(cmd: NodeCommand): NodeResponse {
        return when (cmd.action) {
            "notify" -> sendNotification(cmd)
            "tts_speak" -> speak(cmd)
            "vibrate" -> vibrate(cmd)
            "flashlight" -> flashlight(cmd)
            else -> NodeResponse(id = cmd.id, status = "error", error = "Unknown action")
        }
    }

    private fun sendNotification(cmd: NodeCommand): NodeResponse {
        val title = cmd.params.optString("title", "OpenClaw")
        val body = cmd.params.optString("body", "")
        val priority = cmd.params.optString("priority", "active")

        val importance = when (priority) {
            "timeSensitive" -> NotificationCompat.PRIORITY_MAX
            "active" -> NotificationCompat.PRIORITY_HIGH
            else -> NotificationCompat.PRIORITY_DEFAULT
        }

        val notification = NotificationCompat.Builder(context, App.CHANNEL_AGENT)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(importance)
            .setAutoCancel(true)
            .build()

        val nm = context.getSystemService(NotificationManager::class.java)
        val id = notifyId++
        nm.notify(id, notification)

        return NodeResponse(
            id = cmd.id,
            status = "ok",
            data = JSONObject().apply { put("notificationId", id) }
        )
    }

    @Suppress("DEPRECATION")
    private fun vibrate(cmd: NodeCommand): NodeResponse {
        val ms = cmd.params.optLong("durationMs", 500)
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
        vibrator.vibrate(ms)
        return NodeResponse(id = cmd.id, status = "ok")
    }

    private fun speak(cmd: NodeCommand): NodeResponse {
        val text = cmd.params.optString("text", "")
        val tts = android.speech.tts.TextToSpeech(context) { status ->
            if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                // Ready
            }
        }
        // Small delay to let TTS init
        Thread.sleep(500)
        tts.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, cmd.id)
        return NodeResponse(id = cmd.id, status = "ok")
    }

    private fun flashlight(cmd: NodeCommand): NodeResponse {
        val on = cmd.params.optBoolean("on", true)
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
        val cameraId = manager.cameraIdList[0]
        manager.setTorchMode(cameraId, on)
        return NodeResponse(id = cmd.id, status = "ok", data = JSONObject().apply { put("on", on) })
    }
}
