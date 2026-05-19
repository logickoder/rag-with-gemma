package dev.logickoder.ragwithgemma.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.logickoder.ragwithgemma.app.AppContainer
import dev.logickoder.ragwithgemma.app.RagApplication
import dev.logickoder.ragwithgemma.data.ingestion.MedscapeIngestor
import dev.logickoder.ragwithgemma.data.prefs.ConsultantMode
import dev.logickoder.ragwithgemma.domain.ChatEngine
import dev.logickoder.ragwithgemma.domain.DrugRepository
import dev.logickoder.ragwithgemma.domain.consultant.Consultant
import dev.logickoder.ragwithgemma.domain.consultant.ConsultantFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class BootstrapViewModel(
    private val container: AppContainer,
) : ViewModel() {

    private val _state = MutableStateFlow<BootstrapState>(BootstrapState.Idle)
    val state: StateFlow<BootstrapState> = _state.asStateFlow()

    val onboardingCompleteFlow get() = container.prefs.onboardingCompleteFlow
    val modeFlow get() = container.prefs.modeFlow

    @Volatile
    private var repository: DrugRepository? = null

    @Volatile
    private var chatEngine: ChatEngine? = null

    private val rebuildMutex = Mutex()
    private var modeWatcher: Job? = null

    fun start() {
        if (modeWatcher != null) return
        viewModelScope.launch {
            try {
                ensureAssetsAndIngest()
                modeWatcher = launch {
                    container.prefs.modeFlow.distinctUntilChanged().collect { mode ->
                        rebuildConsultant(mode)
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Bootstrap failed", t)
                _state.value = BootstrapState.Error(t.message ?: "Initialization failed")
            }
        }
    }

    private suspend fun ensureAssetsAndIngest() {
        _state.value = BootstrapState.ResolvingAssets("Locating embedder…")
        val embedder = container.ensureEmbedder()

        _state.value = BootstrapState.ResolvingAssets("Preparing drug database…")
        container.db.createVecTables()

        val needsIngest = container.dao.getDrugCount() == 0 ||
            !container.prefs.ingestionCompleteFlow.first()
        if (needsIngest) {
            _state.value = BootstrapState.ResolvingAssets("Locating drug content…")
            val drugDir = container.bootstrap.resolveDrugJsons { downloaded, total ->
                Log.d(TAG, "Downloaded $downloaded / $total")
            }
            val ingestor = MedscapeIngestor(container.dao, embedder)
            withContext(Dispatchers.Default) {
                ingestor.ingestAll(drugDir).collect { progress ->
                    _state.value = BootstrapState.Ingesting(progress)
                }
            }
            container.prefs.markIngestionComplete()
        }
    }

    private suspend fun rebuildConsultant(mode: ConsultantMode) = rebuildMutex.withLock {
        _state.value = BootstrapState.LoadingConsultant(mode)
        val embedder = container.ensureEmbedder()
        val result = ConsultantFactory.create(
            context = container.appContext,
            bootstrap = container.bootstrap,
            embedder = embedder,
            requested = mode,
        )
        val previousConsultant: Consultant? = repository?.consultant
        val repo = repository
        if (repo == null) {
            val newRepo = DrugRepository(container.dao, embedder, result.consultant)
            newRepo.refreshDrugCache()
            repository = newRepo
            chatEngine = ChatEngine(newRepo, viewModelScope)
        } else {
            repo.consultant = result.consultant
        }
        runCatching { previousConsultant?.close() }
        _state.value = BootstrapState.Ready(result.effectiveMode, result.fellBackToSemantic)
    }

    fun requireChatEngine(): ChatEngine =
        chatEngine ?: error("ChatEngine not ready — bootstrap state: ${_state.value}")

    override fun onCleared() {
        repository?.consultant?.close()
    }

    companion object {
        private const val TAG = "BootstrapViewModel"

        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as RagApplication
                BootstrapViewModel(app.container)
            }
        }
    }
}
