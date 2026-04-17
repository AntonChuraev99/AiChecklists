package com.antonchuraev.homesearchchecklist.feature.updatefeed.domain.repository

import com.antonchuraev.homesearchchecklist.feature.updatefeed.domain.model.UpdatePost

interface UpdateFeedRepository {
    suspend fun getPosts(): List<UpdatePost>
}
