package ai.openclaw.node.capabilities

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Base64
import android.util.Log
import ai.openclaw.node.gateway.NodeCommand
import ai.openclaw.node.gateway.NodeResponse
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class CameraCapability(private val context: Context) : Capability {

    override val supportedActions = listOf("camera_snap")

    companion object {
        private const val TAG = "CameraCap"
    }

    override fun execute(cmd: NodeCommand): NodeResponse {
        val facing = cmd.params.optString("facing", "back")
        val quality = cmd.params.optInt("quality", 80)
        val maxWidth = cmd.params.optInt("maxWidth", 1920)

        val lensFacing = when (facing) {
            "front" -> CameraCharacteristics.LENS_FACING_FRONT
            else -> CameraCharacteristics.LENS_FACING_BACK
        }

        return try {
            val jpeg = captureImage(lensFacing, quality, maxWidth)
            val b64 = Base64.encodeToString(jpeg, Base64.NO_WRAP)

            NodeResponse(
                id = cmd.id,
                status = "ok",
                data = JSONObject().apply {
                    put("size", jpeg.size)
                    put("facing", facing)
                    put("mimeType", "image/jpeg")
                },
                attachment = b64
            )
        } catch (e: Exception) {
            Log.e(TAG, "Camera capture failed", e)
            NodeResponse(id = cmd.id, status = "error", error = e.message)
        }
    }

    private fun captureImage(lensFacing: Int, quality: Int, maxWidth: Int): ByteArray {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

        // Find camera
        val cameraId = manager.cameraIdList.firstOrNull { id ->
            val chars = manager.getCameraCharacteristics(id)
            chars.get(CameraCharacteristics.LENS_FACING) == lensFacing
        } ?: throw Exception("No camera found for requested facing")

        val chars = manager.getCameraCharacteristics(cameraId)
        val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
        val sizes = map.getOutputSizes(ImageFormat.JPEG)
        val size = sizes.firstOrNull { it.width <= maxWidth } ?: sizes.last()

        val thread = HandlerThread("camera").apply { start() }
        val handler = Handler(thread.looper)
        val latch = CountDownLatch(1)
        var result: ByteArray? = null

        val reader = ImageReader.newInstance(size.width, size.height, ImageFormat.JPEG, 1)
        reader.setOnImageAvailableListener({ r ->
            val image = r.acquireLatestImage()
            if (image != null) {
                val buffer = image.planes[0].buffer
                result = ByteArray(buffer.remaining())
                buffer.get(result!!)
                image.close()
            }
            latch.countDown()
        }, handler)

        // Open camera and capture
        val openLatch = CountDownLatch(1)
        var device: CameraDevice? = null

        manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                device = camera
                openLatch.countDown()
            }
            override fun onDisconnected(camera: CameraDevice) { camera.close() }
            override fun onError(camera: CameraDevice, error: Int) {
                camera.close()
                openLatch.countDown()
            }
        }, handler)

        if (!openLatch.await(5, TimeUnit.SECONDS)) throw Exception("Camera open timeout")
        val cam = device ?: throw Exception("Camera open failed")

        val captureRequest = cam.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
            addTarget(reader.surface)
            set(CaptureRequest.JPEG_QUALITY, quality.toByte())
            set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
        }

        val captureLatch = CountDownLatch(1)
        cam.createCaptureSession(listOf(reader.surface), object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                session.capture(captureRequest.build(), object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureCompleted(s: CameraCaptureSession, r: CaptureRequest, tr: TotalCaptureResult) {
                        captureLatch.countDown()
                    }
                }, handler)
            }
            override fun onConfigureFailed(session: CameraCaptureSession) {
                captureLatch.countDown()
            }
        }, handler)

        captureLatch.await(5, TimeUnit.SECONDS)
        latch.await(5, TimeUnit.SECONDS)

        cam.close()
        reader.close()
        thread.quitSafely()

        return result ?: throw Exception("No image captured")
    }
}
