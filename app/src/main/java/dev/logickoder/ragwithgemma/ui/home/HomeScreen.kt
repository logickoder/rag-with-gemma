package dev.logickoder.ragwithgemma.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.logickoder.ragwithgemma.data.ingestion.IngestProgress
import dev.logickoder.ragwithgemma.data.prefs.ConsultantMode
import dev.logickoder.ragwithgemma.domain.InteractionRepository
import dev.logickoder.ragwithgemma.ui.BootstrapState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    bootstrapState: StateFlow<BootstrapState>,
    interactionRepo: InteractionRepository,
    onChat: () -> Unit,
    onSettings: () -> Unit,
) {
    val state by bootstrapState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var drugA by remember { mutableStateOf("") }
    var drugB by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Medical RAG") },
                actions = {
                    IconButton(onClick = onChat) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = "Chat")
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BootstrapBanner(state)
            Spacer(Modifier.height(8.dp))
            Text("Drug interaction finder", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = drugA,
                onValueChange = { drugA = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Drug A") },
            )
            OutlinedTextField(
                value = drugB,
                onValueChange = { drugB = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Drug B") },
            )
            Button(
                onClick = {
                    scope.launch {
                        val result = interactionRepo.findInteractions(drugA, drugB)
                        val msg = result.exceptionOrNull()?.message ?: "Done."
                        snackbar.showSnackbar(msg)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = drugA.isNotBlank() && drugB.isNotBlank(),
            ) {
                Text("Analyze Interaction")
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Or tap the sparkles icon above to open the AI chat consultant.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun BootstrapBanner(state: BootstrapState) {
    val (label, sub) = when (state) {
        BootstrapState.Idle -> "Starting…" to null
        is BootstrapState.ResolvingAssets -> "Preparing" to state.message
        is BootstrapState.Ingesting -> {
            val p: IngestProgress = state.progress
            "Indexing drugs" to "${p.processed}/${p.total} (${p.chunksInserted} chunks)"
        }
        is BootstrapState.LoadingConsultant -> {
            val name = if (state.mode == ConsultantMode.LLM) "AI Mode" else "Semantic Mode"
            "Loading $name" to null
        }
        is BootstrapState.Ready -> {
            val name = if (state.activeMode == ConsultantMode.LLM) "AI Mode" else "Semantic Mode"
            val suffix = if (state.fellBackToSemantic) " (model not found, fell back)" else ""
            "Ready · $name$suffix" to null
        }
        is BootstrapState.Error -> "Error" to state.message
    }
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall)
    }
}
