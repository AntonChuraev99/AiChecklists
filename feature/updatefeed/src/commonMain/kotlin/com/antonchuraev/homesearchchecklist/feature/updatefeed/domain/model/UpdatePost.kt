package com.antonchuraev.homesearchchecklist.feature.updatefeed.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class UpdatePost(
    val id: String,
    val title: String,
    val description: String,
    val publishedAtMillis: Long,
    val iconName: String? = null,
    val actions: List<UpdatePostAction> = emptyList()
)

@Serializable
data class UpdatePostAction(
    val label: String,
    val deepLink: String
)
