package com.freewind.debuginspectorserverdemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.freewind.debuginspectorserverdemo.domain.handler.DebugInspectorHandler
import com.freewind.debuginspectorserverdemo.domain.models.InspectorActionResult
import com.freewind.debuginspectorserverdemo.domain.models.InspectorNode
import com.freewind.debuginspectorserverdemo.domain.models.InspectorNodeDraft
import com.freewind.debuginspectorserverdemo.domain.models.toHexString
import com.freewind.debuginspectorserverdemo.domain.models.toInspectorBounds
import com.freewind.debuginspectorserverdemo.domain.store.DebugInspectorStore
import com.freewind.debuginspectorserverdemo.infra.persistence.DebugInspectorHttpApi
import com.freewind.debuginspectorserverdemo.infra.system.DebugInspectorActionBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val store = DebugInspectorStore(
        appName = "Debug Inspector Server Demo",
        host = BuildConfig.INSPECTOR_HOST,
        port = BuildConfig.INSPECTOR_PORT,
    )
    private val actionBus = DebugInspectorActionBus()
    private val handler = DebugInspectorHandler(
        store = store,
        actionBus = actionBus,
    )
    private val httpApi = DebugInspectorHttpApi(
        host = BuildConfig.INSPECTOR_HOST,
        port = BuildConfig.INSPECTOR_PORT,
        store = store,
        handler = handler,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        httpApi.start()
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFFF3F6FB),
                ) {
                    DebugInspectorDemoApp(
                        handler = handler,
                        actionBus = actionBus,
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        httpApi.stop()
        super.onDestroy()
    }
}

// 汇总页面 UI，并把节点信息推给 store。
@Composable
private fun DebugInspectorDemoApp(
    handler: DebugInspectorHandler,
    actionBus: DebugInspectorActionBus,
) {
    val registry = remember { DebugNodeRegistry() }
    var message by remember { mutableStateOf("ready") }
    var counter by remember { mutableIntStateOf(0) }
    var sliderValue by remember { mutableStateOf(0.35f) }
    var textValue by remember { mutableStateOf("debug") }
    var switchChecked by remember { mutableStateOf(true) }
    val feedItems = remember {
        mutableStateListOf(
            "server up",
            "compose tree tracked",
            "action bus ready",
        )
    }

    LaunchedEffect(registry.version, message, counter, sliderValue, textValue, switchChecked, feedItems.size) {
        handler.publishSnapshot(
            screenName = "InspectorHome",
            nodes = registry.toSnapshotNodes(),
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        InspectableCard(
            registry = registry,
            id = "hero_card",
            type = "Card",
            role = "banner",
            backgroundColor = Color(0xFF11213C),
            contentColor = Color.White,
            extra = mapOf("shape" to "rounded-xl"),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Android,
                    contentDescription = "android icon",
                    tint = Color(0xFF80ED99),
                    modifier = Modifier.size(42.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Compose Inspector Server",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = "实时导出 UI 节点、颜色、位置；HTTP 可回放动作",
                        color = Color(0xFFD8E0F0),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        InspectableButton(
            registry = registry,
            actionBus = actionBus,
            id = "primary_button",
            text = "触发主操作",
            backgroundColor = Color(0xFF1F6FEB),
            contentColor = Color.White,
            onClick = {
                counter += 1
                message = "primary button clicked $counter"
                feedItems.add(0, "primary click #$counter")
            },
        )

        InspectableCounterCard(
            registry = registry,
            actionBus = actionBus,
            id = "counter_card",
            counter = counter,
            onClick = {
                counter += 1
                message = "counter card click $counter"
                feedItems.add(0, "counter card +1")
            },
            onDoubleClick = {
                counter += 2
                message = "counter card double click $counter"
                feedItems.add(0, "counter card +2")
            },
        )

        InspectableSliderCard(
            registry = registry,
            actionBus = actionBus,
            id = "volume_slider",
            value = sliderValue,
            onValueChange = { nextValue ->
                sliderValue = nextValue.coerceIn(0f, 1f)
                message = "slider moved ${(sliderValue * 100).toInt()}%"
            },
        )

        InspectableTextFieldCard(
            registry = registry,
            actionBus = actionBus,
            id = "input_field",
            value = textValue,
            onValueChange = { nextValue ->
                textValue = nextValue
                message = "input updated ${nextValue.length} chars"
            },
        )

        InspectableSwitchRow(
            registry = registry,
            actionBus = actionBus,
            id = "toggle_switch",
            checked = switchChecked,
            onCheckedChange = { nextChecked ->
                switchChecked = nextChecked
                message = "switch is $nextChecked"
            },
        )

        InspectableFeedCard(
            registry = registry,
            id = "event_feed",
            message = message,
            items = feedItems.toList(),
        )
    }
}

// 用于聚合当前页面全部组件快照。
private class DebugNodeRegistry {
    private val nodes = linkedMapOf<String, InspectorNodeDraft>()
    var version by mutableIntStateOf(0)
        private set

    fun upsert(draft: InspectorNodeDraft) {
        val previous = nodes[draft.id]
        if (previous != draft) {
            nodes[draft.id] = draft
            version += 1
        }
    }

    fun remove(id: String) {
        if (nodes.remove(id) != null) {
            version += 1
        }
    }

    fun toSnapshotNodes(): List<InspectorNode> {
        return nodes.values.map { draft ->
            InspectorNode(
                id = draft.id,
                type = draft.type,
                text = draft.text,
                role = draft.role,
                backgroundColor = draft.backgroundColor?.toHexString(),
                contentColor = draft.contentColor?.toHexString(),
                visible = draft.visible,
                enabled = draft.enabled,
                clickable = draft.clickable,
                value = draft.value,
                extra = draft.extra,
                bounds = draft.bounds?.toInspectorBounds(),
            )
        }
    }
}

// 统一记录组件布局与样式。
@Composable
private fun rememberInspectableModifier(
    registry: DebugNodeRegistry,
    id: String,
    type: String,
    text: String? = null,
    role: String? = null,
    backgroundColor: Color? = null,
    contentColor: Color? = null,
    visible: Boolean = true,
    enabled: Boolean = true,
    clickable: Boolean = false,
    value: String? = null,
    extra: Map<String, String> = emptyMap(),
): Modifier {
    var bounds by remember(id) { mutableStateOf<Rect?>(null) }

    SideEffect {
        registry.upsert(
            InspectorNodeDraft(
                id = id,
                type = type,
                text = text,
                role = role,
                backgroundColor = backgroundColor,
                contentColor = contentColor,
                visible = visible,
                enabled = enabled,
                clickable = clickable,
                value = value,
                extra = extra,
                bounds = bounds,
            ),
        )
    }

    DisposableEffect(id) {
        onDispose {
            registry.remove(id)
        }
    }

    return Modifier.onGloballyPositioned { coordinates ->
        bounds = coordinates.boundsInWindow()
    }
}

// 包一层通用卡片。
@Composable
private fun InspectableCard(
    registry: DebugNodeRegistry,
    id: String,
    type: String,
    role: String,
    backgroundColor: Color,
    contentColor: Color,
    extra: Map<String, String> = emptyMap(),
    content: @Composable () -> Unit,
) {
    Card(
        modifier = rememberInspectableModifier(
            registry = registry,
            id = id,
            type = type,
            role = role,
            backgroundColor = backgroundColor,
            contentColor = contentColor,
            extra = extra,
        ),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

// 演示按钮节点与 click action。
@Composable
private fun InspectableButton(
    registry: DebugNodeRegistry,
    actionBus: DebugInspectorActionBus,
    id: String,
    text: String,
    backgroundColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
) {
    DisposableEffect(id, text) {
        actionBus.registerAction(id) { request ->
            if (request.action != "click") {
                return@registerAction InspectorActionResult(false, "unsupported action for button")
            }
            withContext(Dispatchers.Main.immediate) {
                onClick()
            }
            InspectorActionResult(true, "button clicked")
        }
        onDispose {
            actionBus.unregisterAction(id)
        }
    }

    Button(
        onClick = onClick,
        modifier = rememberInspectableModifier(
            registry = registry,
            id = id,
            type = "Button",
            text = text,
            role = "button",
            backgroundColor = backgroundColor,
            contentColor = contentColor,
            clickable = true,
        ).fillMaxWidth(),
    ) {
        Icon(
            imageVector = Icons.Rounded.BugReport,
            contentDescription = null,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text)
    }
}

// 演示 click/doubleClick。
@Composable
private fun InspectableCounterCard(
    registry: DebugNodeRegistry,
    actionBus: DebugInspectorActionBus,
    id: String,
    counter: Int,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit,
) {
    DisposableEffect(id, counter) {
        actionBus.registerAction(id) { request ->
            when (request.action) {
                "click" -> {
                    withContext(Dispatchers.Main.immediate) {
                        onClick()
                    }
                    InspectorActionResult(true, "counter card clicked")
                }
                "doubleClick" -> {
                    withContext(Dispatchers.Main.immediate) {
                        onDoubleClick()
                    }
                    InspectorActionResult(true, "counter card double clicked")
                }
                else -> InspectorActionResult(false, "unsupported action for counter card")
            }
        }
        onDispose {
            actionBus.unregisterAction(id)
        }
    }

    Card(
        modifier = rememberInspectableModifier(
            registry = registry,
            id = id,
            type = "Card",
            text = "Counter $counter",
            role = "button",
            backgroundColor = Color(0xFFFFFBEB),
            contentColor = Color(0xFF7C5E10),
            clickable = true,
            value = counter.toString(),
            extra = mapOf("gesture" to "click,doubleClick"),
        )
            .fillMaxWidth()
            .pointerInput(counter) {
                detectTapGestures(
                    onTap = { onClick() },
                    onDoubleTap = { onDoubleClick() },
                )
            },
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "Counter Card",
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFF7C5E10),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "当前计数：$counter",
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFF7C5E10),
            )
        }
    }
}

// 演示 slider 与 swipe action。
@Composable
private fun InspectableSliderCard(
    registry: DebugNodeRegistry,
    actionBus: DebugInspectorActionBus,
    id: String,
    value: Float,
    onValueChange: (Float) -> Unit,
) {
    DisposableEffect(id, value) {
        actionBus.registerAction(id) { request ->
            when (request.action) {
                "swipe" -> {
                    val delta = request.dx ?: 0f
                    withContext(Dispatchers.Main.immediate) {
                        onValueChange(value + delta)
                    }
                    InspectorActionResult(true, "slider moved by $delta")
                }
                "setValue" -> {
                    val next = request.text?.toFloatOrNull()
                    if (next == null) {
                        InspectorActionResult(false, "missing numeric text")
                    } else {
                        withContext(Dispatchers.Main.immediate) {
                            onValueChange(next)
                        }
                        InspectorActionResult(true, "slider set to $next")
                    }
                }
                else -> InspectorActionResult(false, "unsupported action for slider")
            }
        }
        onDispose {
            actionBus.unregisterAction(id)
        }
    }

    Card(
        modifier = rememberInspectableModifier(
            registry = registry,
            id = id,
            type = "Slider",
            text = "Volume",
            role = "slider",
            backgroundColor = Color.White,
            contentColor = Color(0xFF004C97),
            clickable = true,
            value = "%.2f".format(value),
            extra = mapOf("min" to "0", "max" to "1"),
        ).fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "Volume Slider ${(value * 100).toInt()}%",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Slider(
                value = value,
                onValueChange = onValueChange,
            )
        }
    }
}

// 演示文本输入 action。
@Composable
private fun InspectableTextFieldCard(
    registry: DebugNodeRegistry,
    actionBus: DebugInspectorActionBus,
    id: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    DisposableEffect(id, value) {
        actionBus.registerAction(id) { request ->
            when (request.action) {
                "input" -> {
                    withContext(Dispatchers.Main.immediate) {
                        onValueChange(request.text.orEmpty())
                    }
                    InspectorActionResult(true, "input updated")
                }
                else -> InspectorActionResult(false, "unsupported action for input")
            }
        }
        onDispose {
            actionBus.unregisterAction(id)
        }
    }

    Card(
        modifier = rememberInspectableModifier(
            registry = registry,
            id = id,
            type = "TextField",
            text = "Input",
            role = "input",
            backgroundColor = Color.White,
            contentColor = Color(0xFF1E293B),
            clickable = true,
            value = value,
            extra = mapOf("keyboardType" to "text"),
        ).fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Command input") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            )
        }
    }
}

// 演示 switch click action。
@Composable
private fun InspectableSwitchRow(
    registry: DebugNodeRegistry,
    actionBus: DebugInspectorActionBus,
    id: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    DisposableEffect(id, checked) {
        actionBus.registerAction(id) { request ->
            when (request.action) {
                "click", "toggle" -> {
                    withContext(Dispatchers.Main.immediate) {
                        onCheckedChange(!checked)
                    }
                    InspectorActionResult(true, "switch toggled")
                }
                else -> InspectorActionResult(false, "unsupported action for switch")
            }
        }
        onDispose {
            actionBus.unregisterAction(id)
        }
    }

    Card(
        modifier = rememberInspectableModifier(
            registry = registry,
            id = id,
            type = "Switch",
            text = "Remote toggle",
            role = "switch",
            backgroundColor = Color.White,
            contentColor = Color(0xFF0F172A),
            clickable = true,
            value = checked.toString(),
        ).fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Remote Toggle",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "状态：$checked",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        }
    }
}

// 演示日志列表，只读导出。
@Composable
private fun InspectableFeedCard(
    registry: DebugNodeRegistry,
    id: String,
    message: String,
    items: List<String>,
) {
    Card(
        modifier = rememberInspectableModifier(
            registry = registry,
            id = id,
            type = "Column",
            text = message,
            role = "feed",
            backgroundColor = Color.White,
            contentColor = Color(0xFF334155),
            extra = mapOf("items" to items.size.toString()),
        ).fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "Event Feed",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "最新状态：$message",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(12.dp))
            items.take(6).forEachIndexed { index, item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                color = if (index == 0) Color(0xFF1D4ED8) else Color(0xFFCBD5E1),
                                shape = RoundedCornerShape(999.dp),
                            ),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = item,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
