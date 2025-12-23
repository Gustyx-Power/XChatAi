package id.xms.xcai.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import id.xms.xcai.data.model.ResponseMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class UserPreferences(private val context: Context) {

    private val dataStore = context.dataStore

    companion object {
        private val SELECTED_MODEL_KEY = stringPreferencesKey("selected_model")
        private val DARK_MODE_KEY = booleanPreferencesKey("dark_mode")
        private val DYNAMIC_COLOR_KEY = booleanPreferencesKey("dynamic_color")
        private val RESPONSE_MODE_KEY = stringPreferencesKey("response_mode")
        private val ONBOARDING_COMPLETED_KEY = booleanPreferencesKey("onboarding_completed")
        private val CUSTOM_PROMPT_KEY = stringPreferencesKey("custom_prompt")
    }

    val onboardingCompleted: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[ONBOARDING_COMPLETED_KEY] ?: false
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { preferences ->
            preferences[ONBOARDING_COMPLETED_KEY] = completed
        }
    }

    val selectedModelId: Flow<String> = dataStore.data.map { preferences ->
        preferences[SELECTED_MODEL_KEY] ?: "moonshotai/kimi-k2-instruct-0905"
    }

    val isDarkMode: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[DARK_MODE_KEY] ?: false
    }

    val isDynamicColorEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[DYNAMIC_COLOR_KEY] ?: true
    }

    val responseMode: Flow<ResponseMode> = dataStore.data.map { preferences ->
        val modeName = preferences[RESPONSE_MODE_KEY] ?: ResponseMode.CHAT.name
        ResponseMode.fromString(modeName)
    }

    val customPrompt: Flow<String> = dataStore.data.map { preferences ->
        preferences[CUSTOM_PROMPT_KEY] ?: ""
    }

    suspend fun setSelectedModel(modelId: String) {
        dataStore.edit { preferences ->
            preferences[SELECTED_MODEL_KEY] = modelId
        }
    }

    suspend fun setDarkMode(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[DARK_MODE_KEY] = enabled
        }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[DYNAMIC_COLOR_KEY] = enabled
        }
    }

    suspend fun setResponseMode(mode: ResponseMode) {
        dataStore.edit { preferences ->
            preferences[RESPONSE_MODE_KEY] = mode.name
        }
    }

    suspend fun setCustomPrompt(prompt: String) {
        dataStore.edit { preferences ->
            preferences[CUSTOM_PROMPT_KEY] = prompt
        }
    }
}
