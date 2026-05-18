package com.freewind.debuginspectorserverdemo.infra.persistence

import android.util.Log
import com.freewind.debuginspectorserverdemo.domain.handler.DebugInspectorHandler
import com.freewind.debuginspectorserverdemo.domain.models.InspectorActionRequest
import com.freewind.debuginspectorserverdemo.domain.models.InspectorNode
import com.freewind.debuginspectorserverdemo.domain.models.InspectorSnapshot
import com.freewind.debuginspectorserverdemo.domain.store.DebugInspectorStore
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

// 用最小依赖起本地 HTTP server。
class DebugInspectorHttpApi(
    private val host: String,
    private val port: Int,
    private val store: DebugInspectorStore,
    private val handler: DebugInspectorHandler,
) {
    private val scope = CoroutineScope(Job() + Dispatchers.IO)
    private var serverJob: Job? = null
    private var serverSocket: ServerSocket? = null

    fun start() {
        if (serverJob != null) {
            return
        }
        serverJob = scope.launch {
            try {
                serverSocket = ServerSocket(port, 50, InetAddress.getByName(host))
                while (true) {
                    val socket = serverSocket?.accept() ?: break
                    launch {
                        socket.use(::handleSocket)
                    }
                }
            } catch (throwable: Throwable) {
                Log.e("InspectorHttpApi", "server stopped", throwable)
            }
        }
    }

    fun stop() {
        serverSocket?.close()
        serverSocket = null
        serverJob?.cancel()
        serverJob = null
        scope.cancel()
    }

    private fun handleSocket(socket: Socket) {
        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
        val firstLine = reader.readLine() ?: return
        val headers = mutableMapOf<String, String>()
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isBlank()) {
                break
            }
            val separatorIndex = line.indexOf(':')
            if (separatorIndex > 0) {
                val key = line.substring(0, separatorIndex).trim().lowercase()
                val value = line.substring(separatorIndex + 1).trim()
                headers[key] = value
            }
        }

        val parts = firstLine.split(" ")
        val method = parts.getOrNull(0).orEmpty()
        val path = parts.getOrNull(1).orEmpty()
        val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
        val body = if (contentLength > 0) {
            CharArray(contentLength).also { reader.read(it) }.concatToString()
        } else {
            ""
        }

        when {
            method == "GET" && path == "/" -> {
                writeHtml(socket.getOutputStream(), buildIndexHtml(store.snapshot(), store.actionLog()))
            }
            method == "GET" && path == "/snapshot" -> {
                writeJson(socket.getOutputStream(), store.snapshot().value.toJson())
            }
            method == "GET" && path == "/logs" -> {
                writeJson(socket.getOutputStream(), actionLogToJson(store.actionLog().value))
            }
            method == "POST" && path == "/action" -> {
                val request = parseActionRequest(body)
                val result = runBlocking {
                    handler.performAction(request)
                }
                writeJson(socket.getOutputStream(), result.toJson())
            }
            else -> {
                writeText(socket.getOutputStream(), 404, "not found")
            }
        }
    }

    private fun writeHtml(
        outputStream: OutputStream,
        body: String,
    ) {
        writeResponse(
            outputStream = outputStream,
            contentType = "text/html; charset=utf-8",
            statusCode = 200,
            body = body,
        )
    }

    private fun writeJson(
        outputStream: OutputStream,
        body: String,
    ) {
        writeResponse(
            outputStream = outputStream,
            contentType = "application/json; charset=utf-8",
            statusCode = 200,
            body = body,
        )
    }

    private fun writeText(
        outputStream: OutputStream,
        statusCode: Int,
        body: String,
    ) {
        writeResponse(
            outputStream = outputStream,
            contentType = "text/plain; charset=utf-8",
            statusCode = statusCode,
            body = body,
        )
    }

    private fun writeResponse(
        outputStream: OutputStream,
        contentType: String,
        statusCode: Int,
        body: String,
    ) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        outputStream.write(
            buildString {
                append("HTTP/1.1 ")
                append(statusCode)
                append(
                    when (statusCode) {
                        200 -> " OK"
                        404 -> " Not Found"
                        else -> ""
                    },
                )
                append("\r\n")
                append("Content-Type: ")
                append(contentType)
                append("\r\n")
                append("Content-Length: ")
                append(bytes.size)
                append("\r\n")
                append("Connection: close\r\n")
                append("Access-Control-Allow-Origin: *\r\n")
                append("\r\n")
            }.toByteArray(Charsets.UTF_8),
        )
        outputStream.write(bytes)
        outputStream.flush()
    }

    private fun buildIndexHtml(
        snapshotFlow: StateFlow<InspectorSnapshot>,
        actionLogFlow: StateFlow<List<String>>,
    ): String {
        val snapshot = snapshotFlow.value
        val logs = actionLogFlow.value
        val snapshotJson = snapshot.toJson()
        val logsJson = actionLogToJson(logs)
        return """
            <!doctype html>
            <html lang="en">
            <head>
              <meta charset="utf-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1" />
              <title>Debug Inspector Demo</title>
              <style>
                body { font-family: sans-serif; padding: 16px; background: #f6f6f6; color: #1f1f1f; }
                textarea { width: 100%; min-height: 120px; }
                pre { white-space: pre-wrap; word-break: break-word; background: #fff; padding: 12px; border-radius: 8px; }
                button { margin-right: 8px; margin-top: 8px; }
              </style>
            </head>
            <body>
              <h1>Debug Inspector Demo</h1>
              <p>GET /snapshot 返回当前组件树。POST /action 可触发组件动作。</p>
              <p>默认 adb 转发：<code>adb forward tcp:${snapshot.serverPort} tcp:${snapshot.serverPort}</code></p>
              <button onclick="refreshSnapshot()">Refresh Snapshot</button>
              <button onclick="clickTarget('primary_button')">Click Primary Button</button>
              <button onclick="postAction({ action: 'doubleClick', targetId: 'counter_card' })">Double Click Counter</button>
              <button onclick="postAction({ action: 'swipe', targetId: 'volume_slider', dx: 0.2, dy: 0 })">Swipe Slider +20%</button>
              <h2>Snapshot</h2>
              <pre id="snapshot">${escapeHtml(snapshotJson)}</pre>
              <h2>Action Logs</h2>
              <pre id="logs">${escapeHtml(logsJson)}</pre>
              <script>
                async function refreshSnapshot() {
                  const res = await fetch('/snapshot');
                  document.getElementById('snapshot').textContent = JSON.stringify(await res.json(), null, 2);
                  const logRes = await fetch('/logs');
                  document.getElementById('logs').textContent = JSON.stringify(await logRes.json(), null, 2);
                }
                async function clickTarget(targetId) {
                  await postAction({ action: 'click', targetId });
                }
                async function postAction(payload) {
                  const res = await fetch('/action', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(payload),
                  });
                  await res.json();
                  await refreshSnapshot();
                }
              </script>
            </body>
            </html>
        """.trimIndent()
    }

    private fun parseActionRequest(body: String): InspectorActionRequest {
        fun readString(key: String): String? {
            val pattern = Regex("\"$key\"\\s*:\\s*\"([^\"]*)\"")
            return pattern.find(body)?.groupValues?.get(1)
        }

        fun readFloat(key: String): Float? {
            val pattern = Regex("\"$key\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)")
            return pattern.find(body)?.groupValues?.get(1)?.toFloatOrNull()
        }

        return InspectorActionRequest(
            action = readString("action").orEmpty(),
            targetId = readString("targetId"),
            text = readString("text"),
            dx = readFloat("dx"),
            dy = readFloat("dy"),
        )
    }

    private fun actionLogToJson(lines: List<String>): String {
        return buildString {
            append("{\"items\":[")
            lines.forEachIndexed { index, line ->
                if (index > 0) {
                    append(",")
                }
                append("\"")
                append(line.escapeJson())
                append("\"")
            }
            append("]}")
        }
    }
}

private fun InspectorSnapshot.toJson(): String {
    return buildString {
        append("{")
        append("\"appName\":\"")
        append(appName.escapeJson())
        append("\",")
        append("\"screenName\":\"")
        append(screenName.escapeJson())
        append("\",")
        append("\"componentCount\":")
        append(componentCount)
        append(",")
        append("\"serverHost\":\"")
        append(serverHost.escapeJson())
        append("\",")
        append("\"serverPort\":")
        append(serverPort)
        append(",")
        append("\"updatedAtEpochMs\":")
        append(updatedAtEpochMs)
        append(",")
        append("\"nodes\":[")
        nodes.forEachIndexed { index, node ->
            if (index > 0) {
                append(",")
            }
            append(node.toJson())
        }
        append("]}")
    }
}

private fun InspectorNode.toJson(): String {
    return buildString {
        append("{")
        append("\"id\":\"")
        append(id.escapeJson())
        append("\",")
        append("\"type\":\"")
        append(type.escapeJson())
        append("\",")
        append("\"text\":")
        appendNullableString(text)
        append(",")
        append("\"role\":")
        appendNullableString(role)
        append(",")
        append("\"backgroundColor\":")
        appendNullableString(backgroundColor)
        append(",")
        append("\"contentColor\":")
        appendNullableString(contentColor)
        append(",")
        append("\"visible\":")
        append(visible)
        append(",")
        append("\"enabled\":")
        append(enabled)
        append(",")
        append("\"clickable\":")
        append(clickable)
        append(",")
        append("\"value\":")
        appendNullableString(value)
        append(",")
        append("\"extra\":{")
        extra.entries.sortedBy { it.key }.forEachIndexed { index, entry ->
            if (index > 0) {
                append(",")
            }
            append("\"")
            append(entry.key.escapeJson())
            append("\":\"")
            append(entry.value.escapeJson())
            append("\"")
        }
        append("},")
        append("\"bounds\":")
        if (bounds == null) {
            append("null")
        } else {
            append("{")
            append("\"left\":")
            append(bounds.left)
            append(",")
            append("\"top\":")
            append(bounds.top)
            append(",")
            append("\"width\":")
            append(bounds.width)
            append(",")
            append("\"height\":")
            append(bounds.height)
            append("}")
        }
        append("}")
    }
}

private fun com.freewind.debuginspectorserverdemo.domain.models.InspectorActionResult.toJson(): String {
    return """{"ok":$ok,"message":"${message.escapeJson()}"}"""
}

private fun String.escapeJson(): String {
    return replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
}

private fun escapeHtml(value: String): String {
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
}

private fun StringBuilder.appendNullableString(value: String?) {
    if (value == null) {
        append("null")
    } else {
        append("\"")
        append(value.escapeJson())
        append("\"")
    }
}
