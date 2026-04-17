package com.antonchuraev.homesearchchecklist.feature.updatefeed.data.repository

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigDefaults
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigKeys
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigProvider
import com.antonchuraev.homesearchchecklist.feature.updatefeed.domain.model.UpdateFeedConfig
import com.antonchuraev.homesearchchecklist.feature.updatefeed.domain.model.UpdatePost
import com.antonchuraev.homesearchchecklist.feature.updatefeed.domain.repository.UpdateFeedRepository
import kotlinx.serialization.json.Json

class UpdateFeedRepositoryImpl(
    private val remoteConfigProvider: RemoteConfigProvider,
    private val logger: AppLogger
) : UpdateFeedRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override suspend fun getPosts(): List<UpdatePost> {
        return try {
            val jsonString = remoteConfigProvider.getString(
                key = RemoteConfigKeys.UPDATE_FEED_JSON,
                defaultValue = RemoteConfigDefaults.UPDATE_FEED_JSON
            )
            val config = json.decodeFromString<UpdateFeedConfig>(jsonString)
            config.posts.sortedByDescending { it.publishedAtMillis }
        } catch (e: Exception) {
            logger.error("UpdateFeedRepository", "Failed to load update feed: ${e.message}")
            emptyList()
        }
    }
}
