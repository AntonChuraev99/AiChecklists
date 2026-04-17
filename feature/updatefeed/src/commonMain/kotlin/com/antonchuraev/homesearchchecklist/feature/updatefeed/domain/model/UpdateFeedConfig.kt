package com.antonchuraev.homesearchchecklist.feature.updatefeed.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class UpdateFeedConfig(val posts: List<UpdatePost> = emptyList())
