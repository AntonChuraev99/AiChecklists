package com.antonchuraev.homesearchchecklist.core.auth.impl

import com.antonchuraev.homesearchchecklist.core.auth.api.GoogleAuthRepository
import com.antonchuraev.homesearchchecklist.core.auth.api.GoogleAuthState
import com.antonchuraev.homesearchchecklist.core.auth.api.GoogleUser
import kotlinx.coroutines.flow.StateFlow

internal class GoogleAuthRepositoryImpl(
    private val provider: AuthProvider,
) : GoogleAuthRepository {

    override val authState: StateFlow<GoogleAuthState> = provider.authState

    override suspend fun signInWithGoogle(): Result<GoogleUser> = provider.signIn()

    override suspend fun signOut() = provider.signOut()

    override suspend fun getIdToken(): String? = provider.getIdToken()

    override suspend fun restoreSession() = provider.restoreSession()

    override fun setActivityContext(context: Any) = provider.setActivityContext(context)
}
