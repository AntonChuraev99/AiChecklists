package com.antonchuraev.homesearchchecklist.core.auth.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LinkGoogleResult(
    @SerialName("user_id") val userId: String,
    @SerialName("google_email") val googleEmail: String,
    @SerialName("is_existing_account") val isExistingAccount: Boolean,
    @SerialName("ai_credits") val aiCredits: Int,
    @SerialName("is_premium") val isPremium: Boolean,
    @SerialName("bonus_credits_granted") val bonusCreditsGranted: Int,
)
