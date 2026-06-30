package com.mdaopay.app.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class Contact(
    val id: String,
    val nickname: String,
    val address: String,
    val avatarUrl: String = "",
    val addedAt: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false
)

private val Context.contactsStore by preferencesDataStore(name = "contacts")

@Singleton
class ContactsStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json { ignoreUnknownKeys = true }

    private object Keys {
        val CONTACTS = stringPreferencesKey("contacts")
    }

    val contactsFlow: Flow<List<Contact>> = context.contactsStore.data.map { prefs ->
        val raw = prefs[Keys.CONTACTS] ?: return@map emptyList()
        json.decodeFromString<List<Contact>>(raw)
    }

    suspend fun getContacts(): List<Contact> = contactsFlow.first()

    suspend fun addContact(contact: Contact) {
        context.contactsStore.edit { prefs ->
            val current = prefs[Keys.CONTACTS] ?: "[]"
            val list = json.decodeFromString<List<Contact>>(current).toMutableList()
            list.add(0, contact)
            prefs[Keys.CONTACTS] = json.encodeToString(list)
        }
    }

    suspend fun removeContact(id: String) {
        context.contactsStore.edit { prefs ->
            val current = prefs[Keys.CONTACTS] ?: "[]"
            val list = json.decodeFromString<List<Contact>>(current).toMutableList()
            list.removeAll { it.id == id }
            prefs[Keys.CONTACTS] = json.encodeToString(list)
        }
    }

    suspend fun hasContact(nickname: String): Boolean {
        return getContacts().any { it.nickname.equals(nickname, ignoreCase = true) }
    }

    suspend fun toggleFavorite(id: String) {
        context.contactsStore.edit { prefs ->
            val current = prefs[Keys.CONTACTS] ?: "[]"
            val list = json.decodeFromString<List<Contact>>(current).toMutableList()
            val idx = list.indexOfFirst { it.id == id }
            if (idx >= 0) {
                list[idx] = list[idx].copy(isFavorite = !list[idx].isFavorite)
                prefs[Keys.CONTACTS] = json.encodeToString(list)
            }
        }
    }
}
