package dev.logickoder.ragwithgemma.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.logickoder.ragwithgemma.data.prefs.ConsultantMode
import dev.logickoder.ragwithgemma.data.prefs.UserPrefs
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    prefs: UserPrefs,
    onComplete: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    fun choose(mode: ConsultantMode) {
        scope.launch {
            prefs.setMode(mode)
            prefs.completeOnboarding()
            onComplete()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            "Choose your assistant",
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            "You can change this anytime in Settings.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(12.dp))

        ModeCard(
            title = "AI Mode (Gemma)",
            subtitle = "Highest quality answers. Requires the ~2.6 GB Gemma model to be sideloaded onto the device via scripts/setup.sh.",
            onClick = { choose(ConsultantMode.LLM) },
        )
        ModeCard(
            title = "Semantic Mode",
            subtitle = "Lightweight. No model needed. Extractive summaries from Medscape data using on-device sentence embeddings.",
            onClick = { choose(ConsultantMode.SEMANTIC) },
        )
    }
}

@Composable
private fun ModeCard(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
