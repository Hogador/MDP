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
data class TransactionRecord(
    val id: String,
    val nickname: String,
    val address: String,
    val amountUsdt: String,
    val timestamp: Long,
    val status: String
)

private val Context.dataStore by preferencesDataStore(name = "transactions")

@Singleton
class TransactionHistory @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json { ignoreUnknownKeys = true }

    private object Keys {
        val TRANSACTIONS = stringPreferencesKey("transactions")
    }

    val transactionsFlow: Flow<List<TransactionRecord>> = context.dataStore.data.map { prefs ->
        val raw = prefs[Keys.TRANSACTIONS] ?: return@map emptyList()
        json.decodeFromString<List<TransactionRecord>>(raw)
    }

    suspend fun getTransactions(): List<TransactionRecord> = transactionsFlow.first()

    suspend fun addTransaction(tx: TransactionRecord) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.TRANSACTIONS] ?: "[]"
            val list = json.decodeFromString<List<TransactionRecord>>(current).toMutableList()
            list.add(0, tx)
            prefs[Keys.TRANSACTIONS] = json.encodeToString(list)
        }
    }

    suspend fun replaceId(oldId: String, newId: String, newStatus: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.TRANSACTIONS] ?: "[]"
            val list = json.decodeFromString<List<TransactionRecord>>(current).toMutableList()
            val updated = list.map { tx ->
                if (tx.id == oldId) tx.copy(id = newId, status = newStatus) else tx
            }
            prefs[Keys.TRANSACTIONS] = json.encodeToString(updated)
        }
    }

    suspend fun updateStatus(id: String, newStatus: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.TRANSACTIONS] ?: "[]"
            val list = json.decodeFromString<List<TransactionRecord>>(current).toMutableList()
            val updated = list.map { tx ->
                if (tx.id == id) tx.copy(status = newStatus) else tx
            }
            prefs[Keys.TRANSACTIONS] = json.encodeToString(updated)
        }
    }
}
