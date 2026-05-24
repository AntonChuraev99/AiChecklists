package com.antonchuraev.homesearchchecklist.core.auth.impl

import com.antonchuraev.homesearchchecklist.core.auth.api.GoogleAuthState
import com.antonchuraev.homesearchchecklist.core.auth.api.GoogleUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.js.Promise

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

private fun readGoogleSignIn(): JsAny? = js("globalThis.__googleSignIn()")
private fun readCurrentUser(): JsAny? = js("globalThis.__getCurrentFirebaseUser()")
private fun callSignOut(): JsAny? = js("globalThis.__firebaseSignOut()")
private fun readAuthReady(): JsAny? = js("globalThis.__authReadyPromise")

private val json = Json { ignoreUnknownKeys = true }

internal class WasmGoogleAuthProvider : AuthProvider {

    private val _authState = MutableStateFlow<GoogleAuthState>(GoogleAuthState.NotAuthenticated)
    override val authState: StateFlow<GoogleAuthState> = _authState.asStateFlow()

    private var cachedIdToken: String? = null

    override suspend fun restoreSession() {
        val promise = readAuthReady() ?: return
        @Suppress("UNCHECKED_CAST")
        val resultJson = (promise as Promise<JsString?>).await<JsString?>()
        if (resultJson != null) {
            val result = json.decodeFromString<JsSignInResult>(resultJson.toString())
            cachedIdToken = result.idToken
            _authState.value = GoogleAuthState.Authenticated(result.toGoogleUser())
        }
    }

    override suspend fun signIn(): Result<GoogleUser> {
        _authState.value = GoogleAuthState.Loading
        val promise = readGoogleSignIn() ?: return Result.failure(Exception("Google Sign-In bridge not available"))

        @Suppress("UNCHECKED_CAST")
        val resultJson = (promise as Promise<JsString>).await<JsString>()
        val result = json.decodeFromString<JsSignInResult>(resultJson.toString())

        return if (result.ok) {
            cachedIdToken = result.idToken
            val user = result.toGoogleUser()
            _authState.value = GoogleAuthState.Authenticated(user)
            Result.success(user)
        } else {
            _authState.value = GoogleAuthState.NotAuthenticated
            Result.failure(Exception(result.error ?: "Sign-in failed"))
        }
    }

    override suspend fun signOut() {
        callSignOut()
        cachedIdToken = null
        _authState.value = GoogleAuthState.NotAuthenticated
    }

    override suspend fun getIdToken(): String? {
        val promise = readCurrentUser() ?: return cachedIdToken
        @Suppress("UNCHECKED_CAST")
        val resultJson = (promise as Promise<JsString?>).await<JsString?>() ?: return null
        val result = json.decodeFromString<JsSignInResult>(resultJson.toString())
        cachedIdToken = result.idToken
        return result.idToken
    }
}

private fun JsSignInResult.toGoogleUser() = GoogleUser(
    firebaseUid = uid,
    email = email,
    displayName = displayName,
    photoUrl = photoURL.ifBlank { null },
)
