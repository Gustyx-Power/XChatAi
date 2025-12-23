package id.xms.xcai.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import id.xms.xcai.data.model.ResponseMode
import id.xms.xcai.data.preferences.UserPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val preferencesManager = UserPreferences(application)

    val selectedModelId: StateFlow<String> = preferencesManager.selectedModelId
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "moonshotai/kimi-k2-instruct-0905"
        )

    val isDarkMode: StateFlow<Boolean> = preferencesManager.isDarkMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val isDynamicColorEnabled: StateFlow<Boolean> = preferencesManager.isDynamicColorEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    // NEW: Response Mode StateFlow
    val responseMode: StateFlow<ResponseMode> = preferencesManager.responseMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ResponseMode.CHAT
        )
    
    // Custom prompt for CUSTOM response mode
    val customPrompt: StateFlow<String> = preferencesManager.customPrompt
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ""
        )

    fun setSelectedModel(modelId: String) {
        viewModelScope.launch {
            preferencesManager.setSelectedModel(modelId)
        }
    }

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setDarkMode(enabled)
        }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setDynamicColor(enabled)
        }
    }
    fun setResponseMode(mode: ResponseMode) {
        viewModelScope.launch {
            preferencesManager.setResponseMode(mode)
        }
    }
    
    fun setCustomPrompt(prompt: String) {
        viewModelScope.launch {
            preferencesManager.setCustomPrompt(prompt)
        }
    }
}
