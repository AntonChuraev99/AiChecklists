package com.antonchuraev.homesearchchecklist.feature.user.domain.model

import kotlinx.serialization.Serializable



@Serializable
data class UserData(
    val userId: String = "",
    val isOnboardingPassed: Boolean = false,
    val isPremium: Boolean = false
)

