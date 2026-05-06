package com.antonchuraev.homesearchchecklist.feature.updatefeed.data.repository

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.feature.updatefeed.data.UpdateFeedContent
import com.antonchuraev.homesearchchecklist.feature.updatefeed.domain.model.UpdateFeedConfig
import com.antonchuraev.homesearchchecklist.feature.updatefeed.domain.model.VersionReleaseGroup
import com.antonchuraev.homesearchchecklist.feature.updatefeed.domain.repository.UpdateFeedRepository
import kotlinx.serialization.json.Json

class UpdateFeedRepositoryImpl(
    private val logger: AppLogger,
    private val jsonSource: String = UpdateFeedContent.JSON
) : UpdateFeedRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override suspend fun getReleases(): List<VersionReleaseGroup> {
        return try {
            val config = json.decodeFromString<UpdateFeedConfig>(jsonSource)

            // Build the union of all known versions from posts + releaseNotes.
            val allVersions: Set<String> =
                config.posts.map { it.version }.toSet() + config.releaseNotes.keys

            // For each version, group its posts, pick storeDescription, derive publishedAtMillis.
            allVersions
                .map { version ->
                    val sortedPosts = config.posts
                        .filter { it.version == version }
                        .sortedByDescending { it.publishedAtMillis }
                    val storeDescription = config.releaseNotes[version]?.notes
                    // Use max post timestamp when available; fall back to releaseNotes timestamp.
                    val publishedAtMillis = sortedPosts.maxOfOrNull { it.publishedAtMillis }
                        ?: config.releaseNotes[version]!!.publishedAtMillis
                    VersionReleaseGroup(
                        version = version,
                        publishedAtMillis = publishedAtMillis,
                        storeDescription = storeDescription,
                        posts = sortedPosts
                    )
                }
                .filter { it.posts.isNotEmpty() || it.storeDescription != null }
                .sortedByDescending { it.publishedAtMillis }
        } catch (e: Exception) {
            logger.error("UpdateFeedRepository", "Failed to load update feed: ${e.message}")
            emptyList()
        }
    }
}
