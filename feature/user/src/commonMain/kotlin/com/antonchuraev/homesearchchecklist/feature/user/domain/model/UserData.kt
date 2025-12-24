package com.antonchuraev.homesearchchecklist.feature.user.domain.model

import kotlinx.serialization.Serializable



@Serializable
data class UserData(
    val isOnboardingPassed: Boolean
)

