package com.mdaopay.app.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "user_preferences")

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val NICKNAME = stringPreferencesKey("nickname")
        val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val APP_THEME = stringPreferencesKey("app_theme")
        val RECOVERY_SCENARIO = stringPreferencesKey("recovery_scenario")
        val COLD_DEVICE_PIN_HASH = stringPreferencesKey("cold_device_pin_hash")
        val COLD_DEVICE_PIN_SALT = stringPreferencesKey("cold_device_pin_salt")
        val SOCIAL_PROVIDER = stringPreferencesKey("social_provider")
        val SOCIAL_EMAIL = stringPreferencesKey("social_email")
        val SOCIAL_NAME = stringPreferencesKey("social_name")
        val SOCIAL_ID = stringPreferencesKey("social_id")
    }

    val recoveryScenarioFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.RECOVERY_SCENARIO] ?: "standard"
    }

    suspend fun getRecoveryScenario(): String = recoveryScenarioFlow.first()

    suspend fun setRecoveryScenario(scenario: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.RECOVERY_SCENARIO] = scenario
        }
    }

    val coldDevicePinHashFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.COLD_DEVICE_PIN_HASH]
    }

    suspend fun getColdDevicePinHash(): String? = coldDevicePinHashFlow.first()

    suspend fun setColdDevicePinHash(hash: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.COLD_DEVICE_PIN_HASH] = hash
        }
    }

    val coldDevicePinSaltFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.COLD_DEVICE_PIN_SALT]
    }

    suspend fun getColdDevicePinSalt(): String? = coldDevicePinSaltFlow.first()

    suspend fun setColdDevicePinSalt(salt: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.COLD_DEVICE_PIN_SALT] = salt
        }
    }

    val nicknameFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.NICKNAME]
    }

    suspend fun getNickname(): String? = nicknameFlow.first()

    suspend fun setNickname(nickname: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.NICKNAME] = nickname
        }
    }

    val appLockEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.APP_LOCK_ENABLED] ?: true
    }

    suspend fun isAppLockEnabled(): Boolean = appLockEnabledFlow.first()

    suspend fun setAppLockEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.APP_LOCK_ENABLED] = enabled
        }
    }

    val onboardingCompleteFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.ONBOARDING_COMPLETE] ?: false
    }

    suspend fun isOnboardingComplete(): Boolean = onboardingCompleteFlow.first()

    suspend fun setOnboardingComplete() {
        context.dataStore.edit { prefs ->
            prefs[Keys.ONBOARDING_COMPLETE] = true
        }
    }

    suspend fun resetOnboarding() {
        context.dataStore.edit { prefs ->
            prefs[Keys.ONBOARDING_COMPLETE] = false
            prefs.remove(Keys.NICKNAME)
        }
    }

    val appThemeFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.APP_THEME] ?: "dark"
    }

    suspend fun getAppTheme(): String = appThemeFlow.first()

    suspend fun setAppTheme(theme: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.APP_THEME] = theme
        }
    }

    // ─── Social Auth ────────────────────────────────

    val socialProviderFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.SOCIAL_PROVIDER]
    }

    val socialEmailFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.SOCIAL_EMAIL]
    }

    val socialNameFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.SOCIAL_NAME]
    }

    val socialIdFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.SOCIAL_ID]
    }

    suspend fun getSocialProvider(): String? = socialProviderFlow.first()
    suspend fun getSocialEmail(): String? = socialEmailFlow.first()
    suspend fun getSocialName(): String? = socialNameFlow.first()
    suspend fun getSocialId(): String? = socialIdFlow.first()

    suspend fun saveSocialAuth(provider: String, email: String, name: String, id: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SOCIAL_PROVIDER] = provider
            prefs[Keys.SOCIAL_EMAIL] = email
            prefs[Keys.SOCIAL_NAME] = name
            prefs[Keys.SOCIAL_ID] = id
        }
    }

    suspend fun clearSocialAuth() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.SOCIAL_PROVIDER)
            prefs.remove(Keys.SOCIAL_EMAIL)
            prefs.remove(Keys.SOCIAL_NAME)
            prefs.remove(Keys.SOCIAL_ID)
        }
    }
}
