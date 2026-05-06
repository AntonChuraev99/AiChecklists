package com.antonchuraev.homesearchchecklist.feature.updatefeed.presentation

import com.antonchuraev.homesearchchecklist.core.common.api.Intent
import com.antonchuraev.homesearchchecklist.core.common.api.State
import com.antonchuraev.homesearchchecklist.feature.updatefeed.domain.model.UpdatePostAction
import com.antonchuraev.homesearchchecklist.feature.updatefeed.domain.model.VersionReleaseGroup

sealed interface UpdateFeedScreenState : State {
    data object Loading : UpdateFeedScreenState
    data class Success(
        val releases: List<VersionReleaseGroup>,
        val isPremium: Boolean,
        val formattedExpirationDate: String?,
        val lockedActionDeepLinks: Set<String> = emptySet()
    ) : UpdateFeedScreenState
    data object Empty : UpdateFeedScreenState
}

sealed interface UpdateFeedScreenIntent : Intent {
    data object OnLoad : UpdateFeedScreenIntent
    data object OnBackClick : UpdateFeedScreenIntent
    data class OnActionClick(
        val postId: String,
        val action: UpdatePostAction
    ) : UpdateFeedScreenIntent
    data object OnPremiumBannerClick : UpdateFeedScreenIntent
}
