package com.antonchuraev.homesearchchecklist.feature.updatefeed.domain.repository

import com.antonchuraev.homesearchchecklist.feature.updatefeed.domain.model.VersionReleaseGroup

interface UpdateFeedRepository {
    /**
     * Returns posts grouped by app version, sorted descending (newest release first).
     * Within each group posts are sorted descending by [UpdatePost.publishedAtMillis].
     */
    suspend fun getReleases(): List<VersionReleaseGroup>
}
