package com.antonchuraev.homesearchchecklist.core.auth.api

import kotlinx.serialization.Serializable

@Serializable
data class GoogleUser(
    val firebaseUid: String,
    val email: String,
    val displayName: String,
    val photoUrl: String? = null,
)
