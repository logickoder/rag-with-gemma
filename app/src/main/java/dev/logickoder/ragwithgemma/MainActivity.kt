package dev.logickoder.ragwithgemma

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import dev.logickoder.ragwithgemma.domain.UiState


class MainActivity : ComponentActivity() {
    private val viewModel by lazy {
        ViewModelProvider(this)[MedicalRagViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = when {
                    isSystemInDarkTheme() -> darkColorScheme()
                    else -> lightColorScheme()
                },
                content = ::Screen
            )
        }
    }

    @Composable
    private fun Screen() {
        val state by viewModel.uiState.collectAsState()
        val response by viewModel.response.collectAsState()
        var text by remember { mutableStateOf("") }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            content = { scaffoldPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(scaffoldPadding)
                        .padding(16.dp),
                    content = {
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = state == UiState.Idle || state is UiState.Err
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                viewModel.query(text)
                            },
                            enabled = text.isNotBlank() && (state == UiState.Idle || state is UiState.Err),
                            modifier = Modifier.fillMaxWidth(),
                            content = {
                                Text(
                                    when (state) {
                                        UiState.Init -> "Mapping..."
                                        UiState.Ingest -> "Indexing..."
                                        UiState.Gen -> "Thinking..."
                                        else -> "Ask"
                                    }
                                )
                            }
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            response,
                            Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                        )
                    }
                )
            }
        )
    }
}