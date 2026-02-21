package ai.openclaw.node.gateway

import org.json.JSONArray
import org.json.JSONObject

/**
 * OpenClaw Node Protocol — command/response between gateway and device.
 */

data class NodeCommand(
    val id: String,
    val action: String,
    val params: JSONObject = JSONObject()
) {
    companion object {
        fun fromJson(json: String): NodeCommand {
            val obj = JSONObject(json)
            return NodeCommand(
                id = obj.getString("id"),
                action = obj.getString("action"),
                params = obj.optJSONObject("params") ?: JSONObject()
            )
        }
    }
}

data class NodeResponse(
    val id: String,
    val status: String,  // "ok" or "error"
    val data: JSONObject = JSONObject(),
    val error: String? = null,
    val attachment: String? = null  // base64
) {
    fun toJson(): String = JSONObject().apply {
        put("id", id)
        put("status", status)
        put("data", data)
        error?.let { put("error", it) }
        attachment?.let { put("attachment", it) }
    }.toString()
}

data class NodeCapabilities(
    val name: String,
    val platform: String = "android",
    val capabilities: List<String>
) {
    fun toJson(): String = JSONObject().apply {
        put("type", "node_announce")
        put("name", name)
        put("platform", platform)
        put("capabilities", JSONArray(capabilities))
    }.toString()
}
