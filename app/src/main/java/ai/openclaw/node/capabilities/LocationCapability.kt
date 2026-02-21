package ai.openclaw.node.capabilities

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.*
import ai.openclaw.node.gateway.NodeCommand
import ai.openclaw.node.gateway.NodeResponse
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class LocationCapability(private val context: Context) : Capability {

    override val supportedActions = listOf("location_get")

    @SuppressLint("MissingPermission")
    override fun execute(cmd: NodeCommand): NodeResponse {
        val accuracy = cmd.params.optString("desiredAccuracy", "balanced")
        val timeoutMs = cmd.params.optLong("timeoutMs", 10000)

        val priority = when (accuracy) {
            "precise" -> Priority.PRIORITY_HIGH_ACCURACY
            "coarse" -> Priority.PRIORITY_LOW_POWER
            else -> Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }

        val client = LocationServices.getFusedLocationProviderClient(context)
        val latch = CountDownLatch(1)
        var result: Location? = null

        val request = CurrentLocationRequest.Builder()
            .setPriority(priority)
            .setMaxUpdateAgeMillis(cmd.params.optLong("maxAgeMs", 60000))
            .build()

        client.getCurrentLocation(request, null)
            .addOnSuccessListener { loc ->
                result = loc
                latch.countDown()
            }
            .addOnFailureListener {
                latch.countDown()
            }

        latch.await(timeoutMs, TimeUnit.MILLISECONDS)

        val loc = result ?: return NodeResponse(
            id = cmd.id,
            status = "error",
            error = "Location unavailable"
        )

        return NodeResponse(
            id = cmd.id,
            status = "ok",
            data = JSONObject().apply {
                put("latitude", loc.latitude)
                put("longitude", loc.longitude)
                put("altitude", loc.altitude)
                put("accuracy", loc.accuracy)
                put("speed", loc.speed)
                put("bearing", loc.bearing)
                put("time", loc.time)
                put("provider", loc.provider)
            }
        )
    }
}
