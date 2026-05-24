package com.antonchuraev.homesearchchecklist.core.auth.impl

import com.antonchuraev.homesearchchecklist.core.auth.api.GoogleAuthState
import com.antonchuraev.homesearchchecklist.core.auth.api.GoogleUser
import kotlinx.coroutines.flow.StateFlow

internal interface AuthProvider {
    val authState: StateFlow<GoogleAuthState>
    suspend fun signIn(): Result<GoogleUser>
    suspend fun signOut()
    suspend fun getIdToken(): String?
    suspend fun restoreSession()
    fun setActivityContext(context: Any) {}
}
