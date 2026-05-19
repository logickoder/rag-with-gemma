package dev.logickoder.ragwithgemma.domain.consultant

import kotlinx.coroutines.flow.Flow

enum class AIProcessorMode { SUMMARIZE, EXPAND }

data class ConsultantRequest(
    val context: String,
    val mode: AIProcessorMode,
    val population: String,
    val drug: String,
    val history: List<HistoryTurn> = emptyList(),
)

data class HistoryTurn(val user: String, val assistant: String)

sealed interface ConsultantEvent {
    data class Delta(val text: String) : ConsultantEvent
    data class Final(val text: String) : ConsultantEvent
}

interface Consultant {
    fun process(request: ConsultantRequest): Flow<ConsultantEvent>
    suspend fun warmup() {}
    fun close() {}
}
