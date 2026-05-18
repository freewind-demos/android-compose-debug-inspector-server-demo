package com.freewind.debuginspectorserverdemo.infra.system

import com.freewind.debuginspectorserverdemo.domain.models.InspectorActionRequest
import com.freewind.debuginspectorserverdemo.domain.models.InspectorActionResult
import java.util.concurrent.ConcurrentHashMap

// 收口动作分发，不让 HTTP 层直碰 UI 细节。
class DebugInspectorActionBus {
    private val actions = ConcurrentHashMap<String, suspend (InspectorActionRequest) -> InspectorActionResult>()

    fun registerAction(
        targetId: String,
        action: suspend (InspectorActionRequest) -> InspectorActionResult,
    ) {
        actions[targetId] = action
    }

    fun unregisterAction(targetId: String) {
        actions.remove(targetId)
    }

    suspend fun dispatch(request: InspectorActionRequest): InspectorActionResult {
        val targetId = request.targetId ?: return InspectorActionResult(
            ok = false,
            message = "missing targetId",
        )
        val action = actions[targetId] ?: return InspectorActionResult(
            ok = false,
            message = "target not found: $targetId",
        )
        return action(request)
    }
}
