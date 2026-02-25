package ai.openclaw.node.capabilities

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.BatteryManager
import ai.openclaw.node.gateway.NodeCommand
import ai.openclaw.node.gateway.NodeResponse
import org.json.JSONObject

class SensorCapability(private val context: Context) : Capability {

    override val supportedActions = listOf("sensor_read")

    override fun execute(cmd: NodeCommand): NodeResponse {
        val sensor = cmd.params.optString("sensor", "all")

        val data = JSONObject()

        if (sensor == "all" || sensor == "battery") {
            data.put("battery", getBattery())
        }
        if (sensor == "all" || sensor == "wifi") {
            data.put("wifi", getWifi())
        }
        if (sensor == "all" || sensor == "storage") {
            data.put("storage", getStorage())
        }
        if (sensor == "all" || sensor == "memory") {
            data.put("memory", getMemory())
        }

        return NodeResponse(id = cmd.id, status = "ok", data = data)
    }

    private fun getBattery(): JSONObject {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val temp = (intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10.0

        return JSONObject().apply {
            put("percent", (level * 100) / scale)
            put("charging", status == BatteryManager.BATTERY_STATUS_CHARGING || plugged != 0)
            put("temperature", temp)
        }
    }

    @Suppress("DEPRECATION")
    private fun getWifi(): JSONObject {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val info = wm.connectionInfo
        return JSONObject().apply {
            put("ssid", info.ssid?.replace("\"", "") ?: "unknown")
            put("rssi", info.rssi)
            put("linkSpeed", info.linkSpeed)
            put("ip", intToIp(info.ipAddress))
        }
    }

    private fun getStorage(): JSONObject {
        val stat = android.os.StatFs(android.os.Environment.getDataDirectory().path)
        val total = stat.totalBytes
        val free = stat.freeBytes
        return JSONObject().apply {
            put("totalGB", total / 1_073_741_824.0)
            put("freeGB", free / 1_073_741_824.0)
            put("usedPercent", ((total - free) * 100.0 / total))
        }
    }

    private fun getMemory(): JSONObject {
        val rt = Runtime.getRuntime()
        return JSONObject().apply {
            put("totalMB", rt.totalMemory() / 1_048_576)
            put("freeMB", rt.freeMemory() / 1_048_576)
            put("maxMB", rt.maxMemory() / 1_048_576)
        }
    }

    private fun intToIp(ip: Int): String =
        "${ip and 0xFF}.${ip shr 8 and 0xFF}.${ip shr 16 and 0xFF}.${ip shr 24 and 0xFF}"
}
