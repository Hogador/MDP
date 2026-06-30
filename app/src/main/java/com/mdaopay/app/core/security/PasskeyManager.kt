package com.mdaopay.app.core.security

import kotlin.ExperimentalStdlibApi
import android.content.Context
import android.util.Base64
import androidx.credentials.CreatePublicKeyCredentialRequest
import com.mdaopay.app.BuildConfig
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.publickeycredential.CreatePublicKeyCredentialException
import androidx.credentials.exceptions.publickeycredential.GetPublicKeyCredentialException
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

private val Context.passkeyStore by preferencesDataStore(name = "passkey")

@OptIn(ExperimentalStdlibApi::class)
@Singleton
class PasskeyManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val credentialManager = CredentialManager.create(context)
    private val secureRandom = SecureRandom()
    // F-063: RP ID from BuildConfig — overridable per flavor
    private val rpId: String get() = BuildConfig.PASSKEY_RP_ID
    private val rpName = "MDAOPay"

    private fun getHkdfSalt(): ByteArray {
        val cached = runBlocking { context.passkeyStore.data.first()[Keys.HKDF_SALT] }
        if (cached != null) return cached.hexToByteArray()
        val salt = ByteArray(32).also { secureRandom.nextBytes(it) }
        runBlocking {
            context.passkeyStore.edit { prefs ->
                prefs[Keys.HKDF_SALT] = salt.joinToString("") { "%02x".format(it) }
            }
        }
        return salt
    }

    private object Keys {
        val HKDF_SALT = stringPreferencesKey("hkdf_salt")
    }

    suspend fun createRecoveryPasskey(userId: String): Result<PasskeyData> {
        val evalInput = ByteArray(32).also { secureRandom.nextBytes(it) }
        val challenge = ByteArray(32).also { secureRandom.nextBytes(it) }
        val userIdBytes = userId.encodeToByteArray()

        // Try with PRF extension first
        val prfResult = try {
            val createJson = buildCreateJson(challenge, userIdBytes, evalInput, withPrf = true)
            val request = CreatePublicKeyCredentialRequest(createJson)
            val response: CreatePublicKeyCredentialResponse =
                withContext(Dispatchers.Main) {
                    credentialManager.createCredential(context, request) as CreatePublicKeyCredentialResponse
                }
            val registrationJson = response.registrationResponseJson
            val credentialId = extractCredentialId(registrationJson)
            val prfOutput = withContext(Dispatchers.IO) {
                extractPrfFromRegistration(registrationJson, evalInput)
            }
            Result.success(PasskeyData(credentialId, prfOutput, registrationJson))
        } catch (e: CreatePublicKeyCredentialException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }

        if (prfResult.isSuccess) return prfResult

        // Fallback: create passkey WITHOUT PRF, use PIN-based recovery
        return try {
            val createJson = buildCreateJson(challenge, userIdBytes, evalInput, withPrf = false)
            val request = CreatePublicKeyCredentialRequest(createJson)
            val response: CreatePublicKeyCredentialResponse =
                withContext(Dispatchers.Main) {
                    credentialManager.createCredential(context, request) as CreatePublicKeyCredentialResponse
                }
            val registrationJson = response.registrationResponseJson
            val credentialId = extractCredentialId(registrationJson)
            Result.success(PasskeyData(credentialId, ByteArray(0), registrationJson))
        } catch (e: CreateCredentialCancellationException) {
            Result.failure(e)
        } catch (e: CreatePublicKeyCredentialException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun authenticateWithPasskey(evalInput: ByteArray): Result<PasskeyAuthResult> {
        val challenge = ByteArray(32).also { secureRandom.nextBytes(it) }

        // Try with PRF first
        val prfResult = try {
            val authJson = buildAuthJson(challenge, evalInput, withPrf = true)
            val request = GetCredentialRequest(listOf(GetPublicKeyCredentialOption(authJson)))
            val response: GetCredentialResponse =
                withContext(Dispatchers.Main) { credentialManager.getCredential(context, request) }
            val credential = response.credential as PublicKeyCredential
            val authenticationJson = credential.authenticationResponseJson
            val prfOutput = withContext(Dispatchers.IO) {
                extractPrfFromAuthentication(authenticationJson, evalInput)
            }
            val credentialId = extractCredentialId(authenticationJson)
            Result.success(PasskeyAuthResult(credentialId, prfOutput, authenticationJson))
        } catch (e: GetPublicKeyCredentialException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }

        if (prfResult.isSuccess) return prfResult

        // Fallback: authenticate without PRF
        return try {
            val authJson = buildAuthJson(challenge, evalInput, withPrf = false)
            val request = GetCredentialRequest(listOf(GetPublicKeyCredentialOption(authJson)))
            val response: GetCredentialResponse =
                withContext(Dispatchers.Main) { credentialManager.getCredential(context, request) }
            val credential = response.credential as PublicKeyCredential
            val authenticationJson = credential.authenticationResponseJson
            val credentialId = extractCredentialId(authenticationJson)
            Result.success(PasskeyAuthResult(credentialId, ByteArray(0), authenticationJson))
        } catch (e: GetCredentialCancellationException) {
            Result.failure(e)
        } catch (e: GetPublicKeyCredentialException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun deriveKeyFromPrf(prfOutput: ByteArray, info: String): ByteArray {
        val prk = hkdfExtract(getHkdfSalt(), prfOutput)
        return hkdfExpand(prk, info.encodeToByteArray(), 32)
    }

    fun generateEvalInput(): ByteArray {
        return ByteArray(32).also { secureRandom.nextBytes(it) }
    }

    private fun hkdfExtract(salt: ByteArray, ikm: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(salt, "HmacSHA256"))
        return mac.doFinal(ikm)
    }

    private fun hkdfExpand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        var result = ByteArray(0)
        var t = ByteArray(0)
        var i = 1
        while (result.size < length) {
            mac.update(t)
            mac.update(info)
            mac.update(byteArrayOf(i.toByte()))
            t = mac.doFinal()
            result += t
            i++
        }
        return result.copyOf(length)
    }

    private fun extractPrfFromRegistration(registrationJson: String, evalInput: ByteArray): ByteArray {
        val json = JSONObject(registrationJson)
        val response = json.getJSONObject("response")
        val attestationB64 = response.getString("attestationObject")
        val attestationBytes = Base64.decode(attestationB64, Base64.URL_SAFE)

        val prfBytes = CborDecoder.findBytesFromAttestation(attestationBytes, "prf", "results", "first")
            ?: throw IllegalStateException("PRF result not found in registration response. Device may not support PRF extension.")

        return prfBytes
    }

    private fun extractPrfFromAuthentication(authenticationJson: String, evalInput: ByteArray): ByteArray {
        val json = JSONObject(authenticationJson)
        val response = json.getJSONObject("response")
        val authDataB64 = response.getString("authenticatorData")
        val authDataBytes = Base64.decode(authDataB64, Base64.URL_SAFE)

        val prfBytes = CborDecoder.findBytesFromAuthData(authDataBytes, "prf", "results", "first")
            ?: throw IllegalStateException("PRF result not found in auth response. Device may not support PRF extension.")

        return prfBytes
    }

    private fun buildCreateJson(challenge: ByteArray, userId: ByteArray, evalInput: ByteArray, withPrf: Boolean): String {
        val challengeB64 = Base64.encodeToString(challenge, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        val userIdB64 = Base64.encodeToString(userId, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        val evalInputB64 = Base64.encodeToString(evalInput, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

        val extensions = if (withPrf) {
            """"extensions": {
    "prf": {
      "eval": {
        "first": "$evalInputB64"
      }
    }
  },"""
        } else {
            ""
        }

        return """{
  "challenge": "$challengeB64",
  "rp": {"id": "$rpId", "name": "$rpName"},
  "user": {
    "id": "$userIdB64",
    "name": "$rpId",
    "displayName": "MDAOPay Recovery"
  },
  "pubKeyCredParams": [{"type": "public-key", "alg": -7}],
  "authenticatorSelection": {
    "authenticatorAttachment": "platform",
    "residentKey": "required",
    "userVerification": "required"
  },
  "attestation": "none",
  $extensions
}"""
    }

    private fun buildAuthJson(challenge: ByteArray, evalInput: ByteArray, withPrf: Boolean): String {
        val challengeB64 = Base64.encodeToString(challenge, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        val evalInputB64 = Base64.encodeToString(evalInput, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

        val extensions = if (withPrf) {
            """"extensions": {
    "prf": {
      "eval": {
        "first": "$evalInputB64"
      }
    }
  },"""
        } else {
            ""
        }

        return """{
  "challenge": "$challengeB64",
  "rpId": "$rpId",
  "allowCredentials": [{"type": "public-key"}],
  "userVerification": "required",
  $extensions
}"""
    }

    private fun extractCredentialId(jsonStr: String): String {
        return try {
            JSONObject(jsonStr).getString("id")
        } catch (_: Exception) { "" }
    }
}

data class PasskeyData(
    val credentialId: String,
    val prfOutput: ByteArray,
    val registrationJson: String
)

data class PasskeyAuthResult(
    val credentialId: String,
    val prfOutput: ByteArray,
    val authenticationJson: String
)
