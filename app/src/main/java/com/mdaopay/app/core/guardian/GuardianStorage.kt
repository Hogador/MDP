package com.mdaopay.app.core.guardian

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

private val Context.guardianStore by preferencesDataStore(name = "guardians")

@Singleton
class GuardianStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json { ignoreUnknownKeys = true }

    private object Keys {
        val MY_GUARDIANS = stringPreferencesKey("my_guardians")
        val MY_INVITES = stringPreferencesKey("my_invites")
        val PENDING_RECOVERIES = stringPreferencesKey("pending_recoveries")
        val IDENTITY_SALT = stringPreferencesKey("identity_salt")
    }

    private val secureRandom = SecureRandom()

    fun getIdentitySalt(): ByteArray {
        val prefs = context.getSharedPreferences("mdaopay_identity_salt", Context.MODE_PRIVATE)
        val cached = prefs.getString(Keys.IDENTITY_SALT.name, null)
        if (cached != null) return cached.hexToByteArray()
        val salt = ByteArray(32).also { secureRandom.nextBytes(it) }
        prefs.edit().putString(Keys.IDENTITY_SALT.name, salt.joinToString("") { "%02x".format(it) }).commit()
        return salt
    }

    val myGuardiansFlow: Flow<List<GuardianInfo>> = context.guardianStore.data.map { prefs ->
        prefs[Keys.MY_GUARDIANS]?.let {
            json.decodeFromString<List<GuardianInfo>>(it)
        } ?: emptyList()
    }

    val myInvitesFlow: Flow<List<GuardianInvite>> = context.guardianStore.data.map { prefs ->
        prefs[Keys.MY_INVITES]?.let {
            json.decodeFromString<List<GuardianInvite>>(it)
        } ?: emptyList()
    }

    val pendingRecoveriesFlow: Flow<List<PendingRecovery>> = context.guardianStore.data.map { prefs ->
        prefs[Keys.PENDING_RECOVERIES]?.let {
            json.decodeFromString<List<PendingRecovery>>(it)
        } ?: emptyList()
    }

    suspend fun getMyGuardians(): List<GuardianInfo> = myGuardiansFlow.first()

    suspend fun addGuardian(guardian: GuardianInfo) {
        context.guardianStore.edit { prefs ->
            val list = getMyGuardians().toMutableList()
            list.add(guardian)
            prefs[Keys.MY_GUARDIANS] = json.encodeToString(list)
        }
    }

    suspend fun removeGuardian(identityHash: String) {
        context.guardianStore.edit { prefs ->
            val list = getMyGuardians().toMutableList()
            list.removeAll { it.identityHash == identityHash }
            prefs[Keys.MY_GUARDIANS] = json.encodeToString(list)
        }
    }

    suspend fun getMyInvites(): List<GuardianInvite> = myInvitesFlow.first()

    suspend fun addInvite(invite: GuardianInvite) {
        context.guardianStore.edit { prefs ->
            val list = getMyInvites().toMutableList()
            list.add(invite)
            prefs[Keys.MY_INVITES] = json.encodeToString(list)
        }
    }

    suspend fun updateInviteStatus(inviteId: String, status: InviteStatus) {
        context.guardianStore.edit { prefs ->
            val list = getMyInvites().toMutableList()
            list.replaceAll { if (it.inviteId == inviteId) it.copy(status = status) else it }
            prefs[Keys.MY_INVITES] = json.encodeToString(list)
        }
    }

    suspend fun getPendingRecoveries(): List<PendingRecovery> = pendingRecoveriesFlow.first()

    suspend fun setPendingRecoveries(recoveries: List<PendingRecovery>) {
        context.guardianStore.edit { prefs ->
            prefs[Keys.PENDING_RECOVERIES] = json.encodeToString(recoveries)
        }
    }
}

private fun String.hexToByteArray(): ByteArray {
    val len = length / 2
    return ByteArray(len) { substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}
