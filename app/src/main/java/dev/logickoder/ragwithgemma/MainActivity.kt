package dev.logickoder.ragwithgemma

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.logickoder.ragwithgemma.app.RagApplication
import dev.logickoder.ragwithgemma.ui.BootstrapState
import dev.logickoder.ragwithgemma.ui.BootstrapViewModel
import dev.logickoder.ragwithgemma.ui.chat.ChatScreen
import dev.logickoder.ragwithgemma.ui.home.HomeScreen
import dev.logickoder.ragwithgemma.ui.onboarding.OnboardingScreen
import dev.logickoder.ragwithgemma.ui.settings.SettingsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot()
                }
            }
        }
    }
}

@Composable
private fun AppRoot() {
    val app = androidx.compose.ui.platform.LocalContext.current
        .applicationContext as RagApplication
    val container = app.container
    val vm: BootstrapViewModel = viewModel(factory = BootstrapViewModel.Factory)
    val nav = rememberNavController()

    LaunchedEffect(Unit) { vm.start() }

    val onboardingComplete by vm.onboardingCompleteFlow.collectAsState(initial = null)

    val start = when (onboardingComplete) {
        null -> Routes.LOADING
        false -> Routes.ONBOARDING
        true -> Routes.HOME
    }

    NavHost(navController = nav, startDestination = start) {
        composable(Routes.LOADING) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text("Starting…")
            }
        }
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                prefs = container.prefs,
                onComplete = {
                    nav.navigate(Routes.HOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.HOME) {
            HomeScreen(
                bootstrapState = vm.state,
                interactionRepo = container.interactionRepo,
                onChat = { nav.navigate(Routes.CHAT) },
                onSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.CHAT) {
            val engine = vm.chatEngineOrNull()
            val state by vm.state.collectAsState()
            if (engine != null) {
                ChatScreen(engine = engine, onBack = { nav.popBackStack() })
            } else {
                ChatNotReady(state = state, onBack = { nav.popBackStack() })
            }
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                prefs = container.prefs,
                bootstrap = container.bootstrap,
                onBack = { nav.popBackStack() },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatNotReady(state: BootstrapState, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Consultant") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text(
                text = when (state) {
                    is BootstrapState.Ingesting ->
                        "Indexing drugs: ${state.progress.processed} / ${state.progress.total}"
                    is BootstrapState.ResolvingAssets -> state.message
                    is BootstrapState.LoadingConsultant -> "Loading consultant…"
                    is BootstrapState.Error -> "Error: ${state.message}"
                    else -> "Preparing chat…"
                },
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

private object Routes {
    const val LOADING = "loading"
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val CHAT = "chat"
    const val SETTINGS = "settings"
}

@Composable
private fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
        content = content,
    )
}
