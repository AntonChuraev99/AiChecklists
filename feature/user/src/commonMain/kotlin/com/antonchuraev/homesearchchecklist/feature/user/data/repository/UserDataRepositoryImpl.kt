package com.antonchuraev.homesearchchecklist.feature.user.data.repository

import com.antonchuraev.homesearchchecklist.feature.user.domain.model.UserData
import com.antonchuraev.homesearchchecklist.feature.user.domain.repository.UserDataRepository
import kotlinx.coroutines.flow.Flow

class UserDataRepositoryImpl : UserDataRepository {

    override fun getUserDataFlow(): Flow<UserData> {
        TODO("Not yet implemented")
    }

    override suspend fun getUserData(): UserData {
        TODO("Not yet implemented")
    }

    override suspend fun update(userData: UserData) {
        TODO("Not yet implemented")
    }
}

