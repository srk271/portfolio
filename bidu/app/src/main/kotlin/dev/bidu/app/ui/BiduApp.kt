package dev.bidu.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.bidu.app.R
import dev.bidu.app.ui.main.MainScreen
import dev.bidu.app.ui.main.MainViewModel
import dev.bidu.app.ui.notes.NotesScreen
import dev.bidu.app.ui.notes.NotesViewModel
import dev.bidu.app.ui.onboarding.OnboardingScreen
import dev.bidu.app.ui.onboarding.OnboardingViewModel
import dev.bidu.app.ui.settings.SettingsScreen
import dev.bidu.app.ui.settings.SettingsViewModel

private enum class Screen(val title: Int) {
    SETUP(R.string.ui_title_setup),
    HOME(R.string.app_name),
    SETTINGS(R.string.ui_title_settings),
    NOTES(R.string.ui_title_notes),
}

/** Default Material 3 with the device's dynamic colours; nothing custom. */
@Composable
fun BiduTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colors = if (isSystemInDarkTheme()) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    MaterialTheme(colorScheme = colors, content = content)
}

/** Four screens switched by state; system back returns to Home (or to where Setup was opened from). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BiduApp(factory: ViewModelProvider.Factory) {
    val main = viewModel<MainViewModel>(factory = factory)
    val settings by main.settings.collectAsStateWithLifecycle()
    val ui by main.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    CollectMessages(main.messages, snackbar)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { main.refreshPermissions() }

    // Null means "the start screen": Setup until onboarding is complete, Home afterwards.
    var chosen by rememberSaveable { mutableStateOf<Screen?>(null) }
    var setupReturn by rememberSaveable { mutableStateOf<Screen?>(null) }
    var hiddenConfirmation by remember { mutableStateOf<String?>(null) }
    val screen = chosen ?: if (settings.onboardingComplete) Screen.HOME else Screen.SETUP

    val openSetup: (Screen) -> Unit = { from ->
        setupReturn = from
        chosen = Screen.SETUP
    }
    val leaveSetup: () -> Unit = {
        chosen = setupReturn ?: Screen.HOME
        setupReturn = null
    }
    val canGoBack = when (screen) {
        Screen.HOME -> false
        Screen.SETUP -> setupReturn != null
        Screen.SETTINGS, Screen.NOTES -> true
    }
    val goBack: () -> Unit = {
        if (screen == Screen.SETUP) {
            leaveSetup()
        } else {
            chosen = Screen.HOME
        }
    }
    BackHandler(enabled = canGoBack, onBack = goBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(screen.title)) },
                navigationIcon = {
                    if (canGoBack) {
                        TextButton(onClick = goBack) { Text(stringResource(R.string.ui_back)) }
                    }
                },
                actions = {
                    if (screen == Screen.HOME) {
                        TextButton(onClick = { chosen = Screen.NOTES }) { Text(stringResource(R.string.ui_nav_notes)) }
                        TextButton(onClick = { chosen = Screen.SETTINGS }) { Text(stringResource(R.string.ui_nav_settings)) }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (screen) {
                Screen.SETUP -> OnboardingScreen(
                    viewModel = viewModel<OnboardingViewModel>(factory = factory),
                    snackbar = snackbar,
                    onStarted = {
                        setupReturn = null
                        chosen = Screen.HOME
                    },
                    onLeave = leaveSetup,
                )
                Screen.HOME -> MainScreen(main, onOpenSetup = { openSetup(Screen.HOME) })
                Screen.SETTINGS -> SettingsScreen(
                    viewModel = viewModel<SettingsViewModel>(factory = factory),
                    snackbar = snackbar,
                    onOpenSetup = { openSetup(Screen.SETTINGS) },
                )
                Screen.NOTES -> NotesScreen(viewModel<NotesViewModel>(factory = factory), snackbar)
            }
        }
    }

    // On Home the confirmation is a card; elsewhere it must still reach the owner.
    val pending = ui.pendingConfirmation
    if (pending != null && screen != Screen.HOME && hiddenConfirmation != pending.id) {
        AlertDialog(
            onDismissRequest = { hiddenConfirmation = pending.id },
            title = { Text(stringResource(R.string.ui_confirm_title)) },
            text = { Text(pending.prompt) },
            confirmButton = {
                TextButton(onClick = { main.answer(pending.id, true) }) { Text(stringResource(R.string.ui_confirm_yes)) }
            },
            dismissButton = {
                TextButton(onClick = { main.answer(pending.id, false) }) { Text(stringResource(R.string.ui_confirm_no)) }
            },
        )
    }
}
