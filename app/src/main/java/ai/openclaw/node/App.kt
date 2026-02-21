package ai.openclaw.node

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class App : Application() {
    companion object {
        const val CHANNEL_SERVICE = "openclaw_service"
        const val CHANNEL_AGENT = "openclaw_agent"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val serviceChannel = NotificationChannel(
            CHANNEL_SERVICE,
            "Node Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps OpenClaw node connection alive"
            setShowBadge(false)
        }

        val agentChannel = NotificationChannel(
            CHANNEL_AGENT,
            "Agent Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications from your agents"
        }

        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(serviceChannel)
        nm.createNotificationChannel(agentChannel)
    }
}
