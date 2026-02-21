package ai.openclaw.node.capabilities

import android.content.Context
import android.os.Environment
import android.util.Base64
import ai.openclaw.node.gateway.NodeCommand
import ai.openclaw.node.gateway.NodeResponse
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class FileCapability(private val context: Context) : Capability {

    override val supportedActions = listOf("file_read", "file_write", "file_list")

    // Sandbox to Downloads and app-specific dirs for safety
    private val allowedRoots = listOf(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        context.filesDir,
        context.cacheDir
    )

    private fun isAllowed(path: String): Boolean {
        val canonical = File(path).canonicalPath
        return allowedRoots.any { canonical.startsWith(it.canonicalPath) }
    }

    override fun execute(cmd: NodeCommand): NodeResponse {
        return when (cmd.action) {
            "file_list" -> listFiles(cmd)
            "file_read" -> readFile(cmd)
            "file_write" -> writeFile(cmd)
            else -> NodeResponse(id = cmd.id, status = "error", error = "Unknown")
        }
    }

    private fun listFiles(cmd: NodeCommand): NodeResponse {
        val path = cmd.params.optString("path",
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).path)

        if (!isAllowed(path)) return NodeResponse(id = cmd.id, status = "error", error = "Path not allowed")

        val dir = File(path)
        if (!dir.isDirectory) return NodeResponse(id = cmd.id, status = "error", error = "Not a directory")

        val files = JSONArray()
        dir.listFiles()?.sortedBy { it.name }?.forEach { f ->
            files.put(JSONObject().apply {
                put("name", f.name)
                put("size", f.length())
                put("isDir", f.isDirectory)
                put("modified", f.lastModified())
            })
        }

        return NodeResponse(
            id = cmd.id,
            status = "ok",
            data = JSONObject().apply {
                put("path", path)
                put("files", files)
                put("count", files.length())
            }
        )
    }

    private fun readFile(cmd: NodeCommand): NodeResponse {
        val path = cmd.params.getString("path")
        if (!isAllowed(path)) return NodeResponse(id = cmd.id, status = "error", error = "Path not allowed")

        val file = File(path)
        if (!file.exists()) return NodeResponse(id = cmd.id, status = "error", error = "File not found")

        val maxSize = cmd.params.optLong("maxSize", 5_000_000) // 5MB default
        if (file.length() > maxSize) return NodeResponse(id = cmd.id, status = "error", error = "File too large")

        val bytes = file.readBytes()
        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)

        return NodeResponse(
            id = cmd.id,
            status = "ok",
            data = JSONObject().apply {
                put("path", path)
                put("size", bytes.size)
                put("mimeType", guessMime(file.name))
            },
            attachment = b64
        )
    }

    private fun writeFile(cmd: NodeCommand): NodeResponse {
        val path = cmd.params.getString("path")
        if (!isAllowed(path)) return NodeResponse(id = cmd.id, status = "error", error = "Path not allowed")

        val content = cmd.params.optString("content", "")
        val b64 = cmd.params.optString("attachment", "")

        val file = File(path)
        file.parentFile?.mkdirs()

        if (b64.isNotEmpty()) {
            file.writeBytes(Base64.decode(b64, Base64.NO_WRAP))
        } else {
            file.writeText(content)
        }

        return NodeResponse(
            id = cmd.id,
            status = "ok",
            data = JSONObject().apply {
                put("path", path)
                put("size", file.length())
            }
        )
    }

    private fun guessMime(name: String): String = when {
        name.endsWith(".txt") -> "text/plain"
        name.endsWith(".json") -> "application/json"
        name.endsWith(".jpg") || name.endsWith(".jpeg") -> "image/jpeg"
        name.endsWith(".png") -> "image/png"
        name.endsWith(".pdf") -> "application/pdf"
        else -> "application/octet-stream"
    }
}
