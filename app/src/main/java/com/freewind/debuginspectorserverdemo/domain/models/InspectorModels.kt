package com.freewind.debuginspectorserverdemo.domain.models

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import java.util.Locale

// 表示一个组件的矩形边界。
data class InspectorBounds(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

// 表示一个组件的基础信息。
data class InspectorNode(
    val id: String,
    val type: String,
    val text: String?,
    val role: String?,
    val backgroundColor: String?,
    val contentColor: String?,
    val visible: Boolean,
    val enabled: Boolean,
    val clickable: Boolean,
    val value: String?,
    val extra: Map<String, String>,
    val bounds: InspectorBounds?,
)

// 表示当前页面快照。
data class InspectorSnapshot(
    val appName: String,
    val screenName: String,
    val componentCount: Int,
    val serverHost: String,
    val serverPort: Int,
    val updatedAtEpochMs: Long,
    val nodes: List<InspectorNode>,
)

// 表示动作请求。
data class InspectorActionRequest(
    val action: String,
    val targetId: String?,
    val text: String?,
    val dx: Float?,
    val dy: Float?,
)

// 表示动作执行结果。
data class InspectorActionResult(
    val ok: Boolean,
    val message: String,
)

// 用于注册组件的结构化数据。
data class InspectorNodeDraft(
    val id: String,
    val type: String,
    val text: String? = null,
    val role: String? = null,
    val backgroundColor: Color? = null,
    val contentColor: Color? = null,
    val visible: Boolean = true,
    val enabled: Boolean = true,
    val clickable: Boolean = false,
    val value: String? = null,
    val extra: Map<String, String> = emptyMap(),
    val bounds: Rect? = null,
)

// 把 Compose Rect 转为可序列化结构。
fun Rect.toInspectorBounds(): InspectorBounds {
    return InspectorBounds(
        left = left,
        top = top,
        width = width,
        height = height,
    )
}

// 统一色值输出格式。
fun Color.toHexString(): String {
    return String.format(
        Locale.US,
        "#%08X",
        toArgbCompat(),
    )
}

// 避免额外依赖，自行把 Color 转 ARGB Int。
fun Color.toArgbCompat(): Int {
    val alphaInt = (alpha * 255f).toInt().coerceIn(0, 255)
    val redInt = (red * 255f).toInt().coerceIn(0, 255)
    val greenInt = (green * 255f).toInt().coerceIn(0, 255)
    val blueInt = (blue * 255f).toInt().coerceIn(0, 255)
    return (alphaInt shl 24) or (redInt shl 16) or (greenInt shl 8) or blueInt
}
