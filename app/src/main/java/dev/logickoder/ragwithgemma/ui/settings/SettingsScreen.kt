package dev.logickoder.ragwithgemma.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.logickoder.ragwithgemma.data.prefs.ConsultantMode
import dev.logickoder.ragwithgemma.data.prefs.UserPrefs
import dev.logickoder.ragwithgemma.data.source.AssetBootstrap
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    prefs: UserPrefs,
    bootstrap: AssetBootstrap,
    onBack: () -> Unit,
) {
    val mode by prefs.modeFlow.collectAsState(initial = ConsultantMode.SEMANTIC)
    val scope = rememberCoroutineScope()
    var pendingLlm by remember { mutableStateOf(false) }

    fun applyMode(newMode: ConsultantMode) {
        if (newMode == ConsultantMode.LLM && !bootstrap.gemmaModelExists()) {
            pendingLlm = true
            return
        }
        scope.launch { prefs.setMode(newMode) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Assistant mode", style = MaterialTheme.typography.titleMedium)
            ModeRow(
                label = "AI Mode (Gemma)",
                selected = mode == ConsultantMode.LLM,
                onClick = { applyMode(ConsultantMode.LLM) },
            )
            ModeRow(
                label = "Semantic Mode",
                selected = mode == ConsultantMode.SEMANTIC,
                onClick = { applyMode(ConsultantMode.SEMANTIC) },
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "AI Mode requires the Gemma .litertlm model at /data/local/tmp/ or in the app's files directory. Semantic Mode runs on-device with MobileBERT only.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    if (pendingLlm) {
        AlertDialog(
            onDismissRequest = { pendingLlm = false },
            title = { Text("Gemma model not found") },
            text = {
                Text(
                    "We can't find ${AssetBootstrap.GEMMA_FILENAME} on this device. " +
                        "Sideload it via scripts/setup.sh first, or stay on Semantic Mode."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingLlm = false
                    scope.launch { prefs.setMode(ConsultantMode.LLM) }
                }) {
                    Text("Switch anyway")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingLlm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ModeRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
