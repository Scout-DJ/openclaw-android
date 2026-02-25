package ai.openclaw.node.gateway

import org.json.JSONArray
import org.json.JSONObject

/**
 * OpenClaw Gateway WebSocket Protocol v3
 *
 * Frame types:
 *   req  → { type:"req", id, method, params }
 *   res  → { type:"res", id, ok, payload|error }
 *   event → { type:"event", event, payload }
 */

// --- Inbound (from gateway) ---

sealed class GatewayMessage {
    data class Challenge(val nonce: String, val ts: Long) : GatewayMessage()
    data class Response(val id: String, val ok: Boolean, val payload: JSONObject?, val error: JSONObject?) : GatewayMessage()
    data class Event(val event: String, val payload: JSONObject) : GatewayMessage()
    data class Request(val id: String, val method: String, val params: JSONObject) : GatewayMessage()

    companion object {
        fun parse(json: String): GatewayMessage {
            val obj = JSONObject(json)
            return when (obj.getString("type")) {
                "event" -> {
                    val event = obj.getString("event")
                    val payload = obj.optJSONObject("payload") ?: JSONObject()
                    if (event == "connect.challenge") {
                        Challenge(
                            nonce = payload.getString("nonce"),
                            ts = payload.getLong("ts")
                        )
                    } else {
                        Event(event, payload)
                    }
                }
                "res" -> Response(
                    id = obj.getString("id"),
                    ok = obj.getBoolean("ok"),
                    payload = obj.optJSONObject("payload"),
                    error = obj.optJSONObject("error")
                )
                "req" -> Request(
                    id = obj.getString("id"),
                    method = obj.getString("method"),
                    params = obj.optJSONObject("params") ?: JSONObject()
                )
                else -> throw IllegalArgumentException("Unknown frame type: ${obj.optString("type")}")
            }
        }
    }
}

// --- Outbound (to gateway) ---

object ProtocolFrames {

    private var reqCounter = 0L

    fun nextId(): String = "android-${++reqCounter}"

    /**
     * Build the connect request frame for a node role.
     */
    fun connectRequest(
        id: String,
        authToken: String,
        nodeName: String,
        deviceId: String,
        caps: List<String>,
        commands: List<String>,
        permissions: Map<String, Boolean> = emptyMap()
    ): String = JSONObject().apply {
        put("type", "req")
        put("id", id)
        put("method", "connect")
        put("params", JSONObject().apply {
            put("minProtocol", 3)
            put("maxProtocol", 3)
            put("client", JSONObject().apply {
                put("id", "openclaw-android-node")
                put("version", "0.1.0")
                put("platform", "android")
                put("mode", "node")
            })
            put("role", "node")
            put("scopes", JSONArray())
            put("caps", JSONArray(caps))
            put("commands", JSONArray(commands))
            put("permissions", JSONObject().apply {
                permissions.forEach { (k, v) -> put(k, v) }
            })
            put("auth", JSONObject().apply {
                put("token", authToken)
            })
            put("locale", java.util.Locale.getDefault().toLanguageTag())
            put("userAgent", "openclaw-android/0.1.0")
            put("device", JSONObject().apply {
                put("id", deviceId)
            })
        })
    }.toString()

    /**
     * Build a node.pair.request frame.
     */
    fun pairRequest(id: String, nodeName: String, deviceId: String): String =
        JSONObject().apply {
            put("type", "req")
            put("id", id)
            put("method", "node.pair.request")
            put("params", JSONObject().apply {
                put("name", nodeName)
                put("deviceId", deviceId)
            })
        }.toString()

    /**
     * Build a response frame (for answering gateway invoke commands).
     */
    fun response(id: String, ok: Boolean, payload: JSONObject? = null, error: String? = null): String =
        JSONObject().apply {
            put("type", "res")
            put("id", id)
            put("ok", ok)
            if (ok && payload != null) put("payload", payload)
            if (!ok && error != null) put("error", JSONObject().apply {
                put("message", error)
            })
        }.toString()
}

// --- Capability response (used by capability handlers) ---

data class NodeResponse(
    val id: String,
    val status: String,  // "ok" or "error"
    val data: JSONObject = JSONObject(),
    val error: String? = null,
    val attachment: String? = null  // base64
)

// --- Legacy compat wrappers ---

/** Represents an inbound command from the gateway (invoke on this node). */
data class NodeCommand(
    val id: String,
    val action: String,
    val params: JSONObject = JSONObject()
) {
    companion object {
        /** Parse from a gateway "req" frame targeting this node. */
        fun fromGatewayRequest(msg: GatewayMessage.Request): NodeCommand {
            return NodeCommand(
                id = msg.id,
                action = msg.method,
                params = msg.params
            )
        }
    }
}
