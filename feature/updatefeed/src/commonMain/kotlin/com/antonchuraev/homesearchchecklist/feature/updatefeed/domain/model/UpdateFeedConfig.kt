package com.antonchuraev.homesearchchecklist.feature.updatefeed.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ReleaseNoteEntry(
    val notes: String,
    val publishedAtMillis: Long
)

@Serializable
data class UpdateFeedConfig(
    val posts: List<UpdatePost> = emptyList(),
    val releaseNotes: Map<String, ReleaseNoteEntry> = emptyMap()
)
