package com.mdaopay.app.core.security

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.mdaopay.app.core.common.AppError
import com.mdaopay.app.core.common.Result
import com.mdaopay.app.core.datastore.UserPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class SocialUser(
    val provider: String,
    val email: String,
    val name: String,
    val id: String
)

@Singleton
class SocialAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferences: UserPreferences
) {
    private val credentialManager = CredentialManager.create(context)

    suspend fun signInWithGoogle(activity: Activity): Result<SocialUser> = withContext(Dispatchers.Main) {
        try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(true)
                .setServerClientId(WEB_CLIENT_ID)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val response = credentialManager.getCredential(activity, request)
            val token = GoogleIdTokenCredential.createFrom(response.credential.data)
            val claims = decodeIdToken(token.idToken)

            val user = SocialUser(
                provider = "google",
                email = claims.email,
                name = token.displayName ?: token.givenName ?: claims.name,
                id = claims.sub
            )
            userPreferences.saveSocialAuth(user.provider, user.email, user.name, user.id)
            com.mdaopay.app.core.common.Result.Success(user)
        } catch (e: GetCredentialCancellationException) {
            com.mdaopay.app.core.common.Result.Error(AppError.Unknown(e))
        } catch (e: GetCredentialException) {
            Log.e(TAG, "Google sign-in error: ${e.message}", e)
            com.mdaopay.app.core.common.Result.Error(AppError.Unknown(e))
        } catch (e: Exception) {
            Log.e(TAG, "Google sign-in error: ${e.message}", e)
            com.mdaopay.app.core.common.Result.Error(AppError.Unknown(e))
        }
    }

    suspend fun signInWithApple(activity: Activity): Result<SocialUser> = withContext(Dispatchers.Main) {
        try {
            val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
            val provider = com.google.firebase.auth.OAuthProvider.newBuilder("apple.com")
                .addCustomParameter("locale", "ru")
                .setScopes(listOf("email", "name"))
                .build()

            val pendingResult = auth.startActivityForSignInWithProvider(activity, provider)
            val authResult = kotlinx.coroutines.suspendCancellableCoroutine<com.google.firebase.auth.AuthResult> { cont ->
                pendingResult.addOnSuccessListener { result -> cont.resumeWith(kotlin.Result.success(result)) }
                    .addOnFailureListener { e -> cont.resumeWith(kotlin.Result.failure(e)) }
            }

            val firebaseUser = authResult.user ?: throw Exception("No user from Apple Sign-In")
            val email = firebaseUser.email ?: ""
            val displayName = firebaseUser.displayName ?: ""
            val uid = firebaseUser.uid

            val user = SocialUser(
                provider = "apple",
                email = email,
                name = displayName.ifBlank { email.substringBefore("@") },
                id = uid
            )
            userPreferences.saveSocialAuth(user.provider, user.email, user.name, user.id)
            com.mdaopay.app.core.common.Result.Success(user)
        } catch (e: GetCredentialCancellationException) {
            com.mdaopay.app.core.common.Result.Error(AppError.Unknown(e))
        } catch (e: Exception) {
            Log.e(TAG, "Apple sign-in error: ${e.message}", e)
            com.mdaopay.app.core.common.Result.Error(AppError.Unknown(e))
        }
    }

    suspend fun isSignedIn(): Boolean =
        userPreferences.getSocialProvider() != null

    suspend fun getCurrentUser(): SocialUser? {
        val provider = userPreferences.getSocialProvider() ?: return null
        val email = userPreferences.getSocialEmail() ?: return null
        val name = userPreferences.getSocialName() ?: return null
        val id = userPreferences.getSocialId() ?: return null
        return SocialUser(provider, email, name, id)
    }

    suspend fun signOut() {
        userPreferences.clearSocialAuth()
    }

    private fun decodeIdToken(idToken: String): IdTokenClaims {
        val parts = idToken.split(".")
        if (parts.size != 3) return IdTokenClaims("", "", "")
        val payload = String(
            android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE),
            Charsets.UTF_8
        )
        val json = JSONObject(payload)
        return IdTokenClaims(
            sub = json.optString("sub", ""),
            email = json.optString("email", ""),
            name = json.optString("name", "")
        )
    }

    private fun generateNonce(): String {
        val random = java.security.SecureRandom()
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val TAG = "SocialAuth"
        // ponytail: extract to BuildConfig per-flavor (dev/staging/prod — different OAuth consent screens)
        const val WEB_CLIENT_ID = "925151210559-01ge4gml47c0u6pnpu88ebf6hbfntmu2.apps.googleusercontent.com"
    }
}

private data class IdTokenClaims(
    val sub: String,
    val email: String,
    val name: String
)
