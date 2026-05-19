package dev.logickoder.ragwithgemma.ui

import dev.logickoder.ragwithgemma.data.ingestion.IngestProgress
import dev.logickoder.ragwithgemma.data.prefs.ConsultantMode

sealed interface BootstrapState {
    data object Idle : BootstrapState
    data class ResolvingAssets(val message: String) : BootstrapState
    data class Ingesting(val progress: IngestProgress) : BootstrapState
    data class LoadingConsultant(val mode: ConsultantMode) : BootstrapState
    data class Ready(
        val activeMode: ConsultantMode,
        val fellBackToSemantic: Boolean,
    ) : BootstrapState
    data class Error(val message: String) : BootstrapState
}
