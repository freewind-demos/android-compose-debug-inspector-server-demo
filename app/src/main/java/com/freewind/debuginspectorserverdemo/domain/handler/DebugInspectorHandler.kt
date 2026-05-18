package com.freewind.debuginspectorserverdemo.domain.handler

import com.freewind.debuginspectorserverdemo.domain.models.InspectorActionRequest
import com.freewind.debuginspectorserverdemo.domain.models.InspectorActionResult
import com.freewind.debuginspectorserverdemo.domain.models.InspectorNode
import com.freewind.debuginspectorserverdemo.domain.store.DebugInspectorStore
import com.freewind.debuginspectorserverdemo.infra.system.DebugInspectorActionBus

// 编排 UI 快照与动作执行。
class DebugInspectorHandler(
    private val store: DebugInspectorStore,
    private val actionBus: DebugInspectorActionBus,
) {
    fun publishSnapshot(
        screenName: String,
        nodes: List<InspectorNode>,
    ) {
        store.updateSnapshot(
            screenName = screenName,
            nodes = nodes,
        )
    }

    suspend fun performAction(request: InspectorActionRequest): InspectorActionResult {
        val result = actionBus.dispatch(request)
        store.appendActionLog(
            requestSummary = buildString {
                append(request.action)
                append(" target=")
                append(request.targetId)
                request.text?.let {
                    append(" text=")
                    append(it)
                }
                if (request.dx != null || request.dy != null) {
                    append(" delta=")
                    append(request.dx ?: 0f)
                    append(",")
                    append(request.dy ?: 0f)
                }
            },
            result = result,
        )
        return result
    }
}
