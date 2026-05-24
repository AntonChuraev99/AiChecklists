package com.antonchuraev.homesearchchecklist.core.auth.api

import kotlinx.coroutines.flow.StateFlow

interface GoogleAuthRepository {

    val authState: StateFlow<GoogleAuthState>

    val isAuthenticated: Boolean
        get() = authState.value is GoogleAuthState.Authenticated

    val currentUser: GoogleUser?
        get() = (authState.value as? GoogleAuthState.Authenticated)?.user

    suspend fun signInWithGoogle(): Result<GoogleUser>

    suspend fun signOut()

    suspend fun getIdToken(): String?

    suspend fun restoreSession()

    /**
     * Android-only: pass the current Activity so Credential Manager can
     * show its bottom sheet. Call from a Composable LaunchedEffect that
     * observes LocalActivity. No-op on wasmJs and iOS.
     */
    fun setActivityContext(context: Any) {}
}
