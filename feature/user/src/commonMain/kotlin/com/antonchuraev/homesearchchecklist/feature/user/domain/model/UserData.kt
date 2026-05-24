package com.antonchuraev.homesearchchecklist.feature.user.domain.model

import kotlinx.serialization.Serializable



@Serializable
data class UserData(
    val userId: String = "",
    val isOnboardingPassed: Boolean = false,
    val isPremium: Boolean = false,
    val aiCredits: Int = 0,
    val googleEmail: String? = null,
    val googleDisplayName: String? = null,
    val googlePhotoUrl: String? = null,
    val isGoogleLinked: Boolean = false,
)

