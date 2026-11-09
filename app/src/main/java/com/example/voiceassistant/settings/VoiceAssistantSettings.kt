package com.example.voiceassistant.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.getStringPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.rx.preferences
import androidx.datastore.rx.RxDataStore
import androidx.datastore.rx.RxDataStoreBuilder
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single

/**
 * Settings manager using DataStore for persistent preferences.
 * Handles: overlay, hotkey, TTS/STT engine selection, etc.
 */
class VoiceAssistantSettings private constructor(
    private val dataStore: RxDataStore<Preferences>
) {
    
    // Keys
    private val OVERLAY_ENABLED = booleanPreferencesKey("overlay_enabled")
    private val EDGE_GLOW_ENABLED = booleanPreferencesKey("edge_glow_enabled")
    private val HOTKEY_ENABLED = booleanPreferencesKey("hotkey_enabled")
    private val HOTKEY_COMBINATION = stringPreferencesKey("hotkey_combination")
    private val USE_WHISPER = booleanPreferencesKey("use_whisper")
    private val USE_PIPER = booleanPreferencesKey("use_piper")
    private val WHISPER_MODEL = stringPreferencesKey("whisper_model")
    private val PIPER_LANGUAGE = stringPreferencesKey("piper_language")
    private val PIPER_VARIANT = stringPreferencesKey("piper_variant")
    private val FIRST_RUN = booleanPreferencesKey("first_run")
    
    // Defaults
    private val DEFAULT_HOTKEY = "meta+space"
    private val DEFAULT_WHISPER_MODEL = "base"
    private val DEFAULT_PIPER_LANG = "ru_RU"
    private val DEFAULT_PIPER_VARIANT = "medium"

    companion object {
        private var INSTANCE: VoiceAssistantSettings? = null
        
        fun getInstance(context: Context): VoiceAssistantSettings {
            if (INSTANCE == null) {
                val dataStore = RxDataStoreBuilder(context, "voice_assistant_settings").build()
                INSTANCE = VoiceAssistantSettings(dataStore)
            }
            return INSTANCE!!
        }
    }

    // Overlay
    val overlayEnabled: Single<Boolean> = dataStore.data.map { it[OVERLAY_ENABLED] ?: false }
    
    fun setOverlayEnabled(enabled: Boolean): Completable {
        return dataStore.updateDataAsync { it.edit { it[OVERLAY_ENABLED] = enabled } }.ignoreElement()
    }

    // Edge glow
    val edgeGlowEnabled: Single<Boolean> = dataStore.data.map { it[EDGE_GLOW_ENABLED] ?: true }
    
    fun setEdgeGlowEnabled(enabled: Boolean): Completable {
        return dataStore.updateDataAsync { it.edit { it[EDGE_GLOW_ENABLED] = enabled } }.ignoreElement()
    }

    // Hotkey
    val hotkeyEnabled: Single<Boolean> = dataStore.data.map { it[HOTKEY_ENABLED] ?: false }
    
    fun setHotkeyEnabled(enabled: Boolean): Completable {
        return dataStore.updateDataAsync { it.edit { it[HOTKEY_ENABLED] = enabled } }.ignoreElement()
    }
    
    val hotkeyCombination: Single<String> = dataStore.data.map { it[HOTKEY_COMBINATION] ?: DEFAULT_HOTKEY }
    
    fun setHotkeyCombination(combination: String): Completable {
        return dataStore.updateDataAsync { it.edit { it[HOTKEY_COMBINATION] = combination } }.ignoreElement()
    }

    // STT Engine
    val useWhisper: Single<Boolean> = dataStore.data.map { it[USE_WHISPER] ?: false }
    
    fun setUseWhisper(use: Boolean): Completable {
        return dataStore.updateDataAsync { it.edit { it[USE_WHISPER] = use } }.ignoreElement()
    }
    
    val whisperModel: Single<String> = dataStore.data.map { it[WHISPER_MODEL] ?: DEFAULT_WHISPER_MODEL }
    
    fun setWhisperModel(model: String): Completable {
        return dataStore.updateDataAsync { it.edit { it[WHISPER_MODEL] = model } }.ignoreElement()
    }

    // TTS Engine
    val usePiper: Single<Boolean> = dataStore.data.map { it[USE_PIPER] ?: false }
    
    fun setUsePiper(use: Boolean): Completable {
        return dataStore.updateDataAsync { it.edit { it[USE_PIPER] = use } }.ignoreElement()
    }
    
    val piperLanguage: Single<String> = dataStore.data.map { it[PIPER_LANGUAGE] ?: DEFAULT_PIPER_LANG }
    val piperVariant: Single<String> = dataStore.data.map { it[PIPER_VARIANT] ?: DEFAULT_PIPER_VARIANT }
    
    fun setPiperLanguage(lang: String): Completable {
        return dataStore.updateDataAsync { it.edit { it[PIPER_LANGUAGE] = lang } }.ignoreElement()
    }
    
    fun setPiperVariant(variant: String): Completable {
        return dataStore.updateDataAsync { it.edit { it[PIPER_VARIANT] = variant } }.ignoreElement()
    }

    // First run
    val isFirstRun: Single<Boolean> = dataStore.data.map { it[FIRST_RUN] ?: true }
    
    fun setFirstRunComplete(): Completable {
        return dataStore.updateDataAsync { it.edit { it[FIRST_RUN] = false } }.ignoreElement()
    }
}