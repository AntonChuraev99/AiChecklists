package com.antonchuraev.homesearchchecklist.feature.user.domain.model

/**
 * Result of user registration containing user data and whether this is a new user.
 * Used to trigger different behavior for new vs returning users (e.g., auto-restore purchases).
 */
data class RegistrationData(
    val userData: UserData,
    val isNewUser: Boolean
)
