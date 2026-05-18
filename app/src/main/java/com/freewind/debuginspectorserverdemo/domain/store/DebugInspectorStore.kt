package com.freewind.debuginspectorserverdemo.domain.store

import com.freewind.debuginspectorserverdemo.domain.models.InspectorActionResult
import com.freewind.debuginspectorserverdemo.domain.models.InspectorNode
import com.freewind.debuginspectorserverdemo.domain.models.InspectorSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// 只管理内存态，不处理 IO。
class DebugInspectorStore(
    private val appName: String,
    private val host: String,
    private val port: Int,
) {
    private val snapshotState = MutableStateFlow(
        InspectorSnapshot(
            appName = appName,
            screenName = "Booting",
            componentCount = 0,
            serverHost = host,
            serverPort = port,
            updatedAtEpochMs = System.currentTimeMillis(),
            nodes = emptyList(),
        ),
    )

    private val actionLogState = MutableStateFlow<List<String>>(emptyList())

    fun snapshot(): StateFlow<InspectorSnapshot> = snapshotState.asStateFlow()

    fun actionLog(): StateFlow<List<String>> = actionLogState.asStateFlow()

    fun updateSnapshot(
        screenName: String,
        nodes: List<InspectorNode>,
    ) {
        snapshotState.value = InspectorSnapshot(
            appName = appName,
            screenName = screenName,
            componentCount = nodes.size,
            serverHost = host,
            serverPort = port,
            updatedAtEpochMs = System.currentTimeMillis(),
            nodes = nodes.sortedBy { it.id },
        )
    }

    fun appendActionLog(
        requestSummary: String,
        result: InspectorActionResult,
    ) {
        val line = "${System.currentTimeMillis()} | $requestSummary | ${result.message}"
        actionLogState.value = (listOf(line) + actionLogState.value).take(30)
    }
}
