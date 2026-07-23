package com.antonchuraev.homesearchchecklist.core.auth.impl

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.NoCredentialException
import com.antonchuraev.homesearchchecklist.core.auth.api.GoogleAuthState
import com.antonchuraev.homesearchchecklist.core.auth.api.GoogleUser
import com.antonchuraev.homesearchchecklist.core.auth.api.SignInError
import com.antonchuraev.homesearchchecklist.core.auth.api.SignInException
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider as FirebaseGoogleAuthProvider
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import java.lang.ref.WeakReference
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Mature Google sign-in for Android, ported from the swapfaceandroid reference
 * (`FirebaseSignInLauncher.kt`, solution doc `google-signin-credentialmanager-fallback-2026-05-04`).
 *
 * Flow:
 *  1. Credential Manager, two-phase [GetGoogleIdOption] (filterByAuthorizedAccounts true -> false):
 *     phase 1 is the 1-tap bottom sheet; phase 2 (on [NoCredentialException]) is the full account
 *     picker so a fresh user with no authorized account is NOT dead-ended on `TYPE_NO_CREDENTIAL`.
 *  2. Retry x3 with [1.5s, 3s] backoff on interrupted / generic Credential Manager errors.
 *  3. Legacy [GoogleSignIn] fallback on any Credential Manager failure except Cancelled — covers
 *     emulators / devices where Identity Services is missing (Credential Manager can't find a
 *     provider but the AccountManager-backed GMS Auth stack still works).
 *
 * Every catch logs via [AppLogger] (error -> Crashlytics recordException; warning for retries) — the
 * provider is never silent. Typed [SignInError] rides up through `Result.failure(SignInException)`
 * so the ViewModel attaches a stable, R8-safe analytics `error_code` and the message keeps the
 * Credential Manager `TYPE_` prefix the snackbar mapper matches on.
 */
internal class AndroidGoogleAuthProvider(
    private val webClientId: String,
    private val logger: AppLogger,
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

        val credentialManager = CredentialManager.create(activity)
        val tokenResult = getGoogleIdTokenWithRetry(credentialManager, activity)

        val idToken: String = when (tokenResult) {
            is GoogleIdTokenResult.Success -> tokenResult.idToken
            is GoogleIdTokenResult.Failure -> when (tokenResult.error) {
                SignInError.Cancelled -> {
                    // User dismissed the picker — no fallback, don't nag with a second one.
                    logger.info(TAG, "Sign-in cancelled by user; skipping legacy fallback")
                    _authState.value = GoogleAuthState.NotAuthenticated
                    return Result.failure(tokenResult.toException())
                }
                SignInError.NoCredentials -> {
                    // No Google account on the device at all — offer add-account, surface distinct fail.
                    offerAddAccount(activity)
                    _authState.value = GoogleAuthState.NotAuthenticated
                    return Result.failure(tokenResult.toException())
                }
                else -> {
                    logger.warning(
                        TAG,
                        "Credential Manager failed (${tokenResult.error.code}); trying legacy GoogleSignInClient",
                    )
                    val legacyToken = tryLegacyGoogleSignIn(activity)
                    if (legacyToken != null) {
                        logger.info(TAG, "Legacy GoogleSignInClient succeeded")
                        legacyToken
                    } else {
                        logger.warning(TAG, "Legacy fallback failed; returning original Credential Manager error")
                        _authState.value = GoogleAuthState.NotAuthenticated
                        return Result.failure(tokenResult.toException())
                    }
                }
            }
        }

        return signInToFirebaseWithGoogleIdToken(idToken)
    }

    /**
     * Exchanges a Google id token for a Firebase session. Shared by the Credential Manager and the
     * legacy fallback paths so both converge on identical [GoogleUser] mapping / state updates.
     */
    private suspend fun signInToFirebaseWithGoogleIdToken(idToken: String): Result<GoogleUser> {
        val firebaseCred = FirebaseGoogleAuthProvider.getCredential(idToken, null)
        val result = runCatching {
            val authResult = firebaseAuth.signInWithCredential(firebaseCred).await()
            val user = authResult.user ?: error("Firebase user is null after signInWithCredential")
            GoogleUser(
                firebaseUid = user.uid,
                email = user.email ?: "",
                displayName = user.displayName ?: "",
                photoUrl = user.photoUrl?.toString(),
            )
        }
        // Never swallow coroutine cancellation captured by runCatching.
        result.exceptionOrNull()?.let { if (it is CancellationException) throw it }

        return result
            .onSuccess { googleUser -> _authState.value = GoogleAuthState.Authenticated(googleUser) }
            .recoverCatching { e ->
                logger.error(TAG, "FirebaseAuth.signInWithCredential failed: ${e.message}", e)
                _authState.value = GoogleAuthState.NotAuthenticated
                val (error, message) = if (e is FirebaseNetworkException) {
                    SignInError.NetworkOrTimeout to "network: firebase sign-in failed ${e.message}"
                } else {
                    SignInError.Generic to "SignInWithIdp: firebase sign-in failed ${e.message}"
                }
                throw SignInException(error, message, e)
            }
    }

    /**
     * Legacy [GoogleSignIn] fallback via the Activity Result API. The provider isn't a Composable,
     * so instead of `rememberLauncherForActivityResult` it registers directly on the host Activity's
     * [ComponentActivity.getActivityResultRegistry] (the no-LifecycleOwner overload — callable
     * outside a lifecycle scope), bridging the callback to suspend code with [CompletableDeferred].
     * The launcher is always unregistered after the round-trip.
     *
     * `signOut()` before launch forces the account picker — the client caches the last sign-in and
     * would otherwise silently return it without any UI.
     *
     * @return idToken on success, null on any failure (cancellation is rethrown, not swallowed).
     */
    private suspend fun tryLegacyGoogleSignIn(activity: Activity): String? {
        val componentActivity = activity as? ComponentActivity
        if (componentActivity == null) {
            logger.warning(TAG, "Host is not a ComponentActivity; legacy sign-in fallback unavailable")
            return null
        }

        val client = runCatching {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(webClientId)
                .requestEmail()
                .build()
            GoogleSignIn.getClient(activity, gso)
        }.getOrElse { e ->
            logger.error(TAG, "Legacy GoogleSignInClient init failed: ${e.message}", e)
            return null
        }

        // Force the account picker (the client caches sign-in state and would return it silently).
        runCatching { client.signOut().await() }
            .onFailure { if (it is CancellationException) throw it }

        val deferred = CompletableDeferred<String?>()
        val launcher = componentActivity.activityResultRegistry.register(
            "$LEGACY_SIGN_IN_KEY${System.nanoTime()}",
            ActivityResultContracts.StartActivityForResult(),
        ) { activityResult ->
            val token = runCatching {
                GoogleSignIn.getSignedInAccountFromIntent(activityResult.data)
                    .getResult(ApiException::class.java)
                    .idToken
            }.onFailure { e ->
                if (e is ApiException) {
                    logger.warning(TAG, "Legacy sign-in ApiException: code=${e.statusCode}")
                } else {
                    logger.error(TAG, "Legacy sign-in result parse failed: ${e.message}", e)
                }
            }.getOrNull()
            deferred.complete(token)
        }

        val outcome = runCatching {
            launcher.launch(client.signInIntent)
            // Bound the wait: a config-change (rotation) mid-picker destroys the registry that owns
            // this launcher, so the callback can never fire and deferred.await() would hang forever
            // (authState stuck in Loading, no terminal). Time out -> null -> caller resets state and
            // returns the original Credential Manager error.
            withTimeout(LEGACY_SIGN_IN_TIMEOUT_MS) { deferred.await() }
        }
        launcher.unregister() // exactly once, on every path (success / timeout / cancel / error)
        return outcome.getOrElse { e ->
            when (e) {
                // TimeoutCancellationException extends CancellationException — must be matched FIRST,
                // otherwise it would be rethrown as a normal coroutine cancellation below.
                is TimeoutCancellationException -> {
                    logger.warning(TAG, "legacy sign-in timed out")
                    null
                }
                is CancellationException -> throw e
                else -> {
                    logger.error(TAG, "Legacy sign-in launch failed: ${e.message}", e)
                    null
                }
            }
        }
    }

    /**
     * Retry loop for the Credential Manager id token. Cancellation / no-credential are terminal (no
     * retry); interrupted / generic Credential Manager errors retry with [RETRY_DELAYS] backoff.
     * After exhausting attempts -> [SignInError.NetworkOrTimeout] (the fallback trigger).
     */
    private suspend fun getGoogleIdTokenWithRetry(
        credentialManager: CredentialManager,
        activity: Activity,
    ): GoogleIdTokenResult {
        val maxAttempts = RETRY_DELAYS.size + 1
        var lastError: GetCredentialException? = null

        repeat(maxAttempts) { attempt ->
            val attemptNumber = attempt + 1
            val outcome = runCatching { getGoogleIdToken(credentialManager, activity) }
            val token = outcome.getOrNull()
            if (token != null) {
                if (attempt > 0) logger.info(TAG, "Sign-in succeeded on attempt $attemptNumber")
                return GoogleIdTokenResult.Success(token)
            }
            when (val e = outcome.exceptionOrNull()) {
                is CancellationException -> throw e
                is GetCredentialCancellationException -> {
                    logger.info(TAG, "Sign-in cancelled by user (attempt $attemptNumber)")
                    return GoogleIdTokenResult.Failure(
                        SignInError.Cancelled,
                        "${e.type}: ${e.errorMessage ?: "cancelled"}",
                        e,
                    )
                }
                is NoCredentialException -> {
                    logger.info(TAG, "No Google credentials on device (attempt $attemptNumber): ${e.message}")
                    return GoogleIdTokenResult.Failure(
                        SignInError.NoCredentials,
                        "${e.type}: no google account on device",
                        e,
                    )
                }
                is GetCredentialInterruptedException -> {
                    lastError = e
                    logger.warning(TAG, "Sign-in interrupted (attempt $attemptNumber), will retry: ${e.message}")
                }
                is GetCredentialException -> {
                    lastError = e
                    logger.warning(TAG, "Sign-in failed (attempt $attemptNumber): type=${e.type}, msg=${e.message}")
                }
                else -> {
                    logger.error(TAG, "Sign-in unexpected error (attempt $attemptNumber): ${e?.message}", e)
                    return GoogleIdTokenResult.Failure(
                        SignInError.Generic,
                        "SignInWithIdp: ${e?.message ?: "unknown error"}",
                        e,
                    )
                }
            }

            if (attempt < RETRY_DELAYS.size) delay(RETRY_DELAYS[attempt])
        }

        val finalError = lastError
        logger.error(TAG, "Sign-in failed after $maxAttempts attempts: type=${finalError?.type}", finalError)
        // Prefix "network:" so signInErrorSnackbarKey maps to sign_in_network, aligned with NETWORK code.
        val message = "network: ${finalError?.type ?: "interrupted"} ${finalError?.errorMessage ?: ""}".trim()
        return GoogleIdTokenResult.Failure(SignInError.NetworkOrTimeout, message, finalError)
    }

    /**
     * Two-phase request: phase 1 auto-selects an authorized account (1-tap, no dialog); phase 2 on
     * [NoCredentialException] shows the full account picker so a fresh user isn't dead-ended.
     */
    private suspend fun getGoogleIdToken(
        credentialManager: CredentialManager,
        activity: Activity,
    ): String = runCatching {
        requestGoogleCredential(credentialManager, activity, filterByAuthorizedAccounts = true)
    }.recoverCatching { error ->
        if (error is NoCredentialException) {
            requestGoogleCredential(credentialManager, activity, filterByAuthorizedAccounts = false)
        } else {
            throw error
        }
    }.getOrThrow()

    private suspend fun requestGoogleCredential(
        credentialManager: CredentialManager,
        activity: Activity,
        filterByAuthorizedAccounts: Boolean,
    ): String {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(filterByAuthorizedAccounts)
            .setServerClientId(webClientId)
            .setAutoSelectEnabled(true)
            .setNonce(generateNonce())
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val response = credentialManager.getCredential(context = activity, request = request)
        val credential = response.credential
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            return GoogleIdTokenCredential.createFrom(credential.data).idToken
        }
        error("Unexpected credential type: ${credential.type}")
    }

    /** Best-effort: send the user to the system Add-Account screen for a com.google account. */
    private fun offerAddAccount(activity: Activity) {
        runCatching {
            activity.startActivity(
                Intent(Settings.ACTION_ADD_ACCOUNT)
                    .putExtra(Settings.EXTRA_ACCOUNT_TYPES, arrayOf("com.google")),
            )
        }.onFailure { e ->
            logger.warning(TAG, "No add-account UI available: ${e.message}")
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
            runCatching {
                CredentialManager.create(activity)
                    .clearCredentialState(ClearCredentialStateRequest())
            }.onFailure { e ->
                if (e is CancellationException) throw e
                logger.warning(TAG, "clearCredentialState failed on sign-out: ${e.message}")
            }
        }
        _authState.value = GoogleAuthState.NotAuthenticated
    }

    override suspend fun getIdToken(): String? =
        runCatching { firebaseAuth.currentUser?.getIdToken(false)?.await()?.token }
            .onFailure { e ->
                if (e is CancellationException) throw e
                logger.warning(TAG, "getIdToken failed: ${e.message}")
            }
            .getOrNull()

    private sealed interface GoogleIdTokenResult {
        data class Success(val idToken: String) : GoogleIdTokenResult
        data class Failure(
            val error: SignInError,
            val message: String,
            val cause: Throwable?,
        ) : GoogleIdTokenResult {
            fun toException(): SignInException = SignInException(error, message, cause)
        }
    }

    private companion object {
        private const val TAG = "GoogleAuth"
        private const val NONCE_BYTES = 32
        private const val LEGACY_SIGN_IN_KEY = "google_legacy_signin_"
        private const val LEGACY_SIGN_IN_TIMEOUT_MS = 120_000L
        private val RETRY_DELAYS = listOf(1500.milliseconds, 3000.milliseconds)
    }
}
