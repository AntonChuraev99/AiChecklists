package com.antonchuraev.homesearchchecklist.core.auth.impl

import com.antonchuraev.homesearchchecklist.core.auth.api.GoogleAuthState
import com.antonchuraev.homesearchchecklist.core.auth.api.GoogleUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal class IosGoogleAuthProvider : AuthProvider {
    override val authState: StateFlow<GoogleAuthState> =
        MutableStateFlow(GoogleAuthState.NotAuthenticated)

    override suspend fun signIn(): Result<GoogleUser> =
        Result.failure(UnsupportedOperationException("iOS Google Auth not implemented"))

    override suspend fun signOut() {}
    override suspend fun getIdToken(): String? = null
    override suspend fun restoreSession() {}
}
