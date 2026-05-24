@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.antonchuraev.homesearchchecklist.core.auth.impl

import com.antonchuraev.homesearchchecklist.core.auth.api.GoogleAuthState
import com.antonchuraev.homesearchchecklist.core.auth.api.GoogleUser
import kotlin.js.Promise
import kotlinx.coroutines.await
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@JsFun("() => globalThis.__googleSignIn()")
private external fun signInWithGooglePromise(): Promise<JsAny>

@JsFun("() => globalThis.__getCurrentFirebaseUser()")
private external fun getCurrentUserPromise(): Promise<JsAny?>

@JsFun("() => globalThis.__firebaseSignOut()")
private external fun firebaseSignOutPromise(): Promise<JsAny?>

@JsFun("() => globalThis.__authReadyPromise")
private external fun authReadyPromise(): Promise<JsAny?>

@JsFun("(r) => !!(r && r.ok)")
private external fun getResultOk(r: JsAny): Boolean

@JsFun("(r) => (r && r.uid) ? r.uid : null")
private external fun getResultUid(r: JsAny): String?

@JsFun("(r) => (r && r.email) ? r.email : ''")
private external fun getResultEmail(r: JsAny): String

@JsFun("(r) => (r && r.displayName) ? r.displayName : ''")
private external fun getResultDisplayName(r: JsAny): String

@JsFun("(r) => (r && r.photoURL) ? r.photoURL : null")
private external fun getResultPhotoURL(r: JsAny): String?

@JsFun("(r) => (r && r.idToken) ? r.idToken : null")
private external fun getResultIdToken(r: JsAny): String?

@JsFun("(r) => (r && r.error) ? r.error : null")
private external fun getResultError(r: JsAny): String?

internal class WasmGoogleAuthProvider : AuthProvider {

    private val _authState = MutableStateFlow<GoogleAuthState>(GoogleAuthState.NotAuthenticated)
    override val authState: StateFlow<GoogleAuthState> = _authState.asStateFlow()

    private var cachedIdToken: String? = null

    override suspend fun restoreSession() {
        val result: JsAny? = authReadyPromise().await()
        if (result == null) return
        val uid = getResultUid(result) ?: return
        cachedIdToken = getResultIdToken(result)
        _authState.value = GoogleAuthState.Authenticated(
            GoogleUser(
                firebaseUid = uid,
                email = getResultEmail(result),
                displayName = getResultDisplayName(result),
                photoUrl = getResultPhotoURL(result),
            )
        )
    }

    override suspend fun signIn(): Result<GoogleUser> {
        _authState.value = GoogleAuthState.Loading
        return runCatching {
            val result: JsAny = signInWithGooglePromise().await()

            if (getResultOk(result)) {
                val uid = getResultUid(result)
                    ?: throw IllegalStateException("Sign-in returned null uid")
                cachedIdToken = getResultIdToken(result)
                val user = GoogleUser(
                    firebaseUid = uid,
                    email = getResultEmail(result),
                    displayName = getResultDisplayName(result),
                    photoUrl = getResultPhotoURL(result),
                )
                _authState.value = GoogleAuthState.Authenticated(user)
                user
            } else {
                val error = getResultError(result) ?: "Sign-in failed"
                _authState.value = GoogleAuthState.NotAuthenticated
                throw Exception(error)
            }
        }.onFailure { e ->
            if (_authState.value is GoogleAuthState.Loading) {
                _authState.value = GoogleAuthState.Error(e.message ?: "Sign-in error")
            }
        }
    }

    override suspend fun signOut() {
        firebaseSignOutPromise().await<JsAny?>()
        cachedIdToken = null
        _authState.value = GoogleAuthState.NotAuthenticated
    }

    override suspend fun getIdToken(): String? {
        return runCatching {
            val result: JsAny? = getCurrentUserPromise().await()
            if (result != null) {
                val token = getResultIdToken(result)
                if (token != null) cachedIdToken = token
                token
            } else {
                cachedIdToken
            }
        }.getOrElse { cachedIdToken }
    }
}
