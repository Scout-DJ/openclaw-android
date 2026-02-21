package ai.openclaw.node.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Starts the node service on device boot.
 * Reads saved credentials from DataStore.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // TODO: Read saved gateway URL/token from DataStore
            // and start NodeService if auto-connect is enabled
        }
    }
}
