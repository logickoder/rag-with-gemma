package dev.logickoder.ragwithgemma.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ConsultantMode(val storageKey: String) {
    LLM("llm"),
    SEMANTIC("semantic");

    companion object {
        fun fromKey(key: String?): ConsultantMode =
            entries.firstOrNull { it.storageKey == key } ?: SEMANTIC
    }
}

private val Context.userPrefsDataStore by preferencesDataStore(name = "user_prefs")

class UserPrefs(context: Context) {
    private val store = context.applicationContext.userPrefsDataStore

    val modeFlow: Flow<ConsultantMode> = store.data.map { p ->
        ConsultantMode.fromKey(p[KEY_MODE])
    }

    val onboardingCompleteFlow: Flow<Boolean> = store.data.map { p ->
        p[KEY_ONBOARDING] ?: false
    }

    val ingestionCompleteFlow: Flow<Boolean> = store.data.map { p ->
        p[KEY_INGESTION_DONE] ?: false
    }

    suspend fun setMode(mode: ConsultantMode) {
        store.edit { it[KEY_MODE] = mode.storageKey }
    }

    suspend fun completeOnboarding() {
        store.edit { it[KEY_ONBOARDING] = true }
    }

    suspend fun markIngestionComplete() {
        store.edit { it[KEY_INGESTION_DONE] = true }
    }

    suspend fun resetIngestion() {
        store.edit { it[KEY_INGESTION_DONE] = false }
    }

    companion object {
        private val KEY_MODE = stringPreferencesKey("consultant_mode")
        private val KEY_ONBOARDING = booleanPreferencesKey("onboarding_complete")
        private val KEY_INGESTION_DONE = booleanPreferencesKey("ingestion_complete")
    }
}
