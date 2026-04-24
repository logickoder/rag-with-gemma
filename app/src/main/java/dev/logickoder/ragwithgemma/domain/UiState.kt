package dev.logickoder.ragwithgemma.domain

sealed interface UiState {
    data object Init : UiState
    data object Ingest : UiState
    data object Idle : UiState
    data object Gen : UiState
    data class Err(val msg: String) : UiState
}