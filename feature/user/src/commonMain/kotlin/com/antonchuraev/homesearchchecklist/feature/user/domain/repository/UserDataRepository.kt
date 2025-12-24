package com.antonchuraev.homesearchchecklist.feature.user.domain.repository

import com.antonchuraev.homesearchchecklist.feature.user.domain.model.UserData
import kotlinx.coroutines.flow.Flow

interface UserDataRepository {

    fun getUserDataFlow(): Flow<UserData>

    suspend fun getUserData(): UserData

    suspend fun update(userData: UserData)
}

