package com.antonchuraev.homesearchchecklist.core.auth.impl

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.antonchuraev.homesearchchecklist.core.auth.api.GoogleAuthState
import com.antonchuraev.homesearchchecklist.core.auth.api.GoogleUser
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider as FirebaseGoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import java.lang.ref.WeakReference
import java.security.MessageDigest
import java.security.SecureRandom

internal class AndroidGoogleAuthProvider(
    private val webClientId: String,
) : AuthProvider {

    private val _authState = MutableStateFlow<GoogleAuthState>(GoogleAuthState.NotAuthenticated)
    override val authState: StateFlow<GoogleAuthState> = _authState.asStateFlow()

    private var activityRef: WeakReference<Activity>? = null
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()

    override fun setActivityContext(context: Any) {
        (context as? Activity)?.let { activityRef = WeakReference(it) }
    }

    override suspend fun restoreSession() {
        val firebaseUser = firebaseAuth.currentUser ?: return
        _authState.value = GoogleAuthState.Authenticated(
            GoogleUser(
                firebaseUid = firebaseUser.uid,
                email = firebaseUser.email ?: "",
                displayName = firebaseUser.displayName ?: "",
                photoUrl = firebaseUser.photoUrl?.toString(),
            )
        )
    }

    override suspend fun signIn(): Result<GoogleUser> {
        val activity = activityRef?.get()
            ?: return Result.failure(IllegalStateException("Activity not available"))

        _authState.value = GoogleAuthState.Loading

        return try {
            val credentialManager = CredentialManager.create(activity)

            // Explicit sign-in BUTTON flow: GetSignInWithGoogleOption always enumerates ALL device
            // Google accounts (no filterByAuthorizedAccounts / autoSelect), so users with no prior
            // authorization still get the account picker instead of TYPE_NO_CREDENTIAL.
            val signInOption = GetSignInWithGoogleOption.Builder(webClientId)
                .setNonce(generateNonce())
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInOption)
                .build()

            val credResult = credentialManager.getCredential(
                request = request,
                context = activity,
            )

            val credential = credResult.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleCred = GoogleIdTokenCredential.createFrom(credential.data)
                val firebaseCred = FirebaseGoogleAuthProvider.getCredential(googleCred.idToken, null)
                val authResult = firebaseAuth.signInWithCredential(firebaseCred).await()
                val user = authResult.user
                    ?: return Result.failure(IllegalStateException("Firebase user is null"))

                val googleUser = GoogleUser(
                    firebaseUid = user.uid,
                    email = user.email ?: "",
                    displayName = user.displayName ?: "",
                    photoUrl = user.photoUrl?.toString(),
                )
                _authState.value = GoogleAuthState.Authenticated(googleUser)
                Result.success(googleUser)
            } else {
                val err = "Unexpected credential type: ${credential.type}"
                _authState.value = GoogleAuthState.Error(err)
                Result.failure(IllegalStateException(err))
            }
        } catch (e: NoCredentialException) {
            // No Google account on the device at all: send the user to Add-Account so they can add
            // one, then surface a distinct failure. Keep the "TYPE_NO_CREDENTIAL" token so the
            // ViewModel maps it to the right snackbar.
            _authState.value = GoogleAuthState.NotAuthenticated
            try {
                activity.startActivity(
                    Intent(Settings.ACTION_ADD_ACCOUNT)
                        .putExtra(Settings.EXTRA_ACCOUNT_TYPES, arrayOf("com.google")),
                )
            } catch (_: ActivityNotFoundException) {
                // No add-account UI available; still return the distinct failure below.
            }
            Result.failure(Exception("TYPE_NO_CREDENTIAL: no google account on device", e))
        } catch (e: GetCredentialCancellationException) {
            // User dismissed the picker.
            _authState.value = GoogleAuthState.NotAuthenticated
            Result.failure(Exception("TYPE_USER_CANCELED: cancelled", e))
        } catch (e: GetCredentialException) {
            // Carry the STABLE Credential Manager type (e.type is a constant string id, NOT
            // obfuscated by R8) + the human message up to the ViewModel, so login_failed analytics
            // are diagnosable on a release/Play build. A Play-signed binary whose SHA-1 isn't a
            // registered OAuth client typically surfaces here as a no-credential / provider-config type.
            _authState.value = GoogleAuthState.NotAuthenticated
            Result.failure(Exception("${e.type}: ${e.errorMessage ?: e.message ?: "no message"}", e))
        } catch (e: Exception) {
            _authState.value = GoogleAuthState.Error(e.message ?: "Sign-in failed")
            Result.failure(e)
        }
    }

    /** Cryptographically secure per-request nonce (SHA-256 hex of 32 random bytes). */
    private fun generateNonce(): String {
        val raw = ByteArray(NONCE_BYTES).also { SecureRandom().nextBytes(it) }
        val digest = MessageDigest.getInstance("SHA-256").digest(raw)
        return digest.joinToString("") { "%02x".format(it) }
    }

    override suspend fun signOut() {
        firebaseAuth.signOut()
        activityRef?.get()?.let { activity ->
            try {
                CredentialManager.create(activity)
                    .clearCredentialState(ClearCredentialStateRequest())
            } catch (_: Exception) {}
        }
        _authState.value = GoogleAuthState.NotAuthenticated
    }

    override suspend fun getIdToken(): String? {
        return try {
            firebaseAuth.currentUser?.getIdToken(false)?.await()?.token
        } catch (_: Exception) {
            null
        }
    }

    private companion object {
        private const val NONCE_BYTES = 32
    }
}
