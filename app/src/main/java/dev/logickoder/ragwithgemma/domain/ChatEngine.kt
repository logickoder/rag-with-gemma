package dev.logickoder.ragwithgemma.domain

import dev.logickoder.ragwithgemma.data.model.ChatMessage
import dev.logickoder.ragwithgemma.data.model.ChatRole
import dev.logickoder.ragwithgemma.domain.consultant.ConsultantEvent
import dev.logickoder.ragwithgemma.domain.consultant.HistoryTurn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatEngine(
    private val repo: DrugRepository,
    private val scope: CoroutineScope,
) {
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isThinking = MutableStateFlow(false)
    val isThinking: StateFlow<Boolean> = _isThinking.asStateFlow()

    private var lastDrug: String? = null

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        appendUser(trimmed)
        _isThinking.value = true

        scope.launch {
            try {
                val extracted = repo.extractDrugNames(trimmed)
                val drug = extracted.firstOrNull() ?: lastDrug
                if (drug == null) {
                    appendAssistantFinal("Please specify a drug name to view its clinical profile.")
                    return@launch
                }
                lastDrug = drug

                val isJustDrugName = trimmed.lowercase() == drug.lowercase()
                val history = buildHistory()
                val flow: Flow<ConsultantEvent> = if (isJustDrugName) {
                    repo.getSatisfactorySummary(drug)
                } else {
                    repo.getSemanticAnswer(trimmed, drug, history)
                }

                val assistantId = appendAssistantPlaceholder()
                flow.collect { event ->
                    when (event) {
                        is ConsultantEvent.Delta -> mutateAssistant(assistantId) { it + event.text }
                        is ConsultantEvent.Final -> mutateAssistant(assistantId) { event.text }
                    }
                }
            } catch (t: Throwable) {
                appendAssistantFinal("Error: ${t.message ?: "unknown failure"}")
            } finally {
                _isThinking.value = false
            }
        }
    }

    fun reset() {
        _messages.value = emptyList()
        lastDrug = null
    }

    private fun appendUser(text: String) {
        _messages.update { it + ChatMessage(role = ChatRole.USER, content = text) }
    }

    private fun appendAssistantFinal(text: String) {
        _messages.update { it + ChatMessage(role = ChatRole.ASSISTANT, content = text) }
    }

    private fun appendAssistantPlaceholder(): String {
        val msg = ChatMessage(role = ChatRole.ASSISTANT, content = "")
        _messages.update { it + msg }
        return msg.id
    }

    private fun mutateAssistant(id: String, transform: (String) -> String) {
        _messages.update { list ->
            list.map { m ->
                if (m.id == id) m.copy(content = transform(m.content)) else m
            }
        }
    }

    private fun buildHistory(): List<HistoryTurn> {
        val list = _messages.value
        val pairs = mutableListOf<HistoryTurn>()
        var pendingUser: String? = null
        for (m in list.dropLast(1)) {
            when (m.role) {
                ChatRole.USER -> pendingUser = m.content
                ChatRole.ASSISTANT -> {
                    val user = pendingUser
                    if (user != null && m.content.isNotBlank()) {
                        pairs += HistoryTurn(user, m.content)
                    }
                    pendingUser = null
                }
            }
        }
        return pairs.takeLast(HISTORY_WINDOW)
    }

    companion object {
        private const val HISTORY_WINDOW = 6
    }
}
