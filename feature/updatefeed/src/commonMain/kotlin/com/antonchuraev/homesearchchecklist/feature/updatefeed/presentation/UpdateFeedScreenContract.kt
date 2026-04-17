package com.antonchuraev.homesearchchecklist.feature.updatefeed.presentation

import com.antonchuraev.homesearchchecklist.core.common.api.Intent
import com.antonchuraev.homesearchchecklist.core.common.api.State
import com.antonchuraev.homesearchchecklist.feature.updatefeed.domain.model.UpdatePost
import com.antonchuraev.homesearchchecklist.feature.updatefeed.domain.model.UpdatePostAction

sealed interface UpdateFeedScreenState : State {
    data object Loading : UpdateFeedScreenState
    data class Success(val posts: List<UpdatePost>) : UpdateFeedScreenState
    data object Empty : UpdateFeedScreenState
}

sealed interface UpdateFeedScreenIntent : Intent {
    data object OnLoad : UpdateFeedScreenIntent
    data object OnBackClick : UpdateFeedScreenIntent
    data class OnActionClick(
        val postId: String,
        val action: UpdatePostAction
    ) : UpdateFeedScreenIntent
}
