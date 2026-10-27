package dev.bidu.app.ui

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.bidu.app.biduContainer
import dev.bidu.app.ui.main.MainViewModel
import dev.bidu.app.ui.notes.NotesViewModel
import dev.bidu.app.ui.onboarding.OnboardingViewModel
import dev.bidu.app.ui.settings.SettingsViewModel

/** Builds every UI view model from the app's dependency graph; graph members are touched only when needed. */
fun biduViewModelFactory(context: Context): ViewModelProvider.Factory {
    val container = context.biduContainer
    return viewModelFactory {
        initializer {
            MainViewModel(
                assistant = container.assistant,
                history = container.history,
                settingsRepository = container.settings,
                permissions = container.permissions,
            )
        }
        initializer {
            OnboardingViewModel(
                permissions = container.permissions,
                settingsRepository = container.settings,
                models = container.voskModels,
                assistant = container.assistant,
                appScope = container.appScope,
            )
        }
        initializer {
            SettingsViewModel(
                settingsRepository = container.settings,
                secrets = container.secrets,
                speaker = container.speaker,
                assistant = container.assistant,
                history = container.history,
                actionLog = container.actionLog,
                permissions = container.permissions,
            )
        }
        initializer {
            NotesViewModel(
                noteRepository = container.notes,
                memory = container.memory,
                reminderRepository = container.reminders,
                actionLogRepository = container.actionLog,
            )
        }
    }
}
