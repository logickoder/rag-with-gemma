package dev.logickoder.ragwithgemma

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.logickoder.ragwithgemma.domain.MedicalRagEngine
import dev.logickoder.ragwithgemma.domain.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MedicalRagViewModel(app: Application) : AndroidViewModel(app) {
    private val engine = MedicalRagEngine(app)

    private val _uiState = MutableStateFlow<UiState>(UiState.Init)
    val uiState = _uiState.asStateFlow()

    private val _response = MutableStateFlow("")
    val response = _response.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                engine.initialize()
                _uiState.update { UiState.Ingest }
                engine.ingestFdaDataIfEmpty()
                _uiState.update { UiState.Idle }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { UiState.Err(e.message ?: "Init failed") }
            }
        }
    }

    fun query(text: String) = viewModelScope.launch {
        if (text.isBlank()) return@launch
        _uiState.value = UiState.Gen
        _response.update { "" }
        try {
            engine.askMedicalQuestion(text).collectLatest { _response.value += it }
            _uiState.value = UiState.Idle
        } catch (e: Exception) {
            Log.e("MedicalRagEngine", "Query failed", e)
            _uiState.value = UiState.Err(e.message ?: "Gen failed")
        }
    }

    override fun onCleared() = engine.close()
}