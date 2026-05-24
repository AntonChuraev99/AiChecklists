@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.antonchuraev.homesearchchecklist.core.auth.impl

import com.antonchuraev.homesearchchecklist.core.auth.api.GoogleAuthState
import com.antonchuraev.homesearchchecklist.core.auth.api.GoogleUser
import kotlin.js.Promise
import kotlinx.coroutines.await
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class JsSignInResult(
    val ok: Boolean = false,
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val photoURL: String = "",
    val idToken: String = "",
    val error: String? = null,
)

private fun readGoogleSignIn(): Promise<JsAny?> =
    js("globalThis.__googleSignIn()")

private fun readCurrentUser(): Promise<JsAny?> =
    js("globalThis.__getCurrentFirebaseUser()")

private fun callSignOut(): Promise<JsAny?> =
    js("globalThis.__firebaseSignOut()")

private fun readAuthReady(): Promise<JsAny?> =
    js("globalThis.__authReadyPromise")

private val json = Json { ignoreUnknownKeys = true }

internal class WasmGoogleAuthProvider : AuthProvider {

    private val _authState = MutableStateFlow<GoogleAuthState>(GoogleAuthState.NotAuthenticated)
    override val authState: StateFlow<GoogleAuthState> = _authState.asStateFlow()

    private var cachedIdToken: String? = null

    override suspend fun restoreSession() {
        val result = readAuthReady().await<JsAny?>() ?: return
        val resultStr = result.toString()
        if (resultStr.isNotBlank() && resultStr != "null") {
            val parsed = json.decodeFromString<JsSignInResult>(resultStr)
            cachedIdToken = parsed.idToken
            _authState.value = GoogleAuthState.Authenticated(parsed.toGoogleUser())
        }
    }

    override suspend fun signIn(): Result<GoogleUser> {
        _authState.value = GoogleAuthState.Loading
        return try {
            val result = readGoogleSignIn().await<JsAny?>()
                ?: return Result.failure(Exception("Google Sign-In returned null"))
            val parsed = json.decodeFromString<JsSignInResult>(result.toString())

            if (parsed.ok) {
                cachedIdToken = parsed.idToken
                val user = parsed.toGoogleUser()
                _authState.value = GoogleAuthState.Authenticated(user)
                Result.success(user)
            } else {
                _authState.value = GoogleAuthState.NotAuthenticated
                Result.failure(Exception(parsed.error ?: "Sign-in failed"))
            }
        } catch (e: Exception) {
            _authState.value = GoogleAuthState.Error(e.message ?: "Sign-in error")
            Result.failure(e)
        }
    }

    override suspend fun signOut() {
        callSignOut().await<JsAny?>()
        cachedIdToken = null
        _authState.value = GoogleAuthState.NotAuthenticated
    }

    override suspend fun getIdToken(): String? {
        return try {
            val result = readCurrentUser().await<JsAny?>() ?: return cachedIdToken
            val parsed = json.decodeFromString<JsSignInResult>(result.toString())
            cachedIdToken = parsed.idToken
            parsed.idToken
        } catch (_: Exception) {
            cachedIdToken
        }
    }
}

private fun JsSignInResult.toGoogleUser() = GoogleUser(
    firebaseUid = uid,
    email = email,
    displayName = displayName,
    photoUrl = photoURL.ifBlank { null },
)
