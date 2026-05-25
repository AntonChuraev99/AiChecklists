package com.antonchuraev.homesearchchecklist.core.auth.api

sealed interface GoogleAuthState {
    data object NotAuthenticated : GoogleAuthState
    data object Loading : GoogleAuthState
    data class Authenticated(val user: GoogleUser) : GoogleAuthState
    data class Error(val message: String) : GoogleAuthState
}
