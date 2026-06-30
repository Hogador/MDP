package com.mdaopay.app.core.blockchain

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.resolverStore by preferencesDataStore(name = "nickname_resolver")

@Singleton
class NicknameResolver @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json { ignoreUnknownKeys = true }

    private object Keys {
        val MAPPINGS = stringPreferencesKey("nickname_to_address")
    }

    suspend fun resolve(nickname: String): String? {
        val mappings = getAll()
        return mappings[nickname.lowercase()]
    }

    suspend fun register(nickname: String, address: String) {
        val key = nickname.lowercase()
        context.resolverStore.edit { prefs ->
            val raw = prefs[Keys.MAPPINGS] ?: "{}"
            val mappings = json.decodeFromString<MutableMap<String, String>>(raw)
            mappings[key] = address
            prefs[Keys.MAPPINGS] = json.encodeToString(mappings)
        }
    }

    private suspend fun getAll(): Map<String, String> {
        return context.resolverStore.data.first()[Keys.MAPPINGS]?.let {
            json.decodeFromString<Map<String, String>>(it)
        } ?: emptyMap()
    }
}
