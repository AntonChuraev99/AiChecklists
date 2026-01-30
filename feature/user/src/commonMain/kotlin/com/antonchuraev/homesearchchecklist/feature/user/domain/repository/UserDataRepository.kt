package com.antonchuraev.homesearchchecklist.feature.user.domain.repository

import com.antonchuraev.homesearchchecklist.feature.user.domain.model.RegistrationData
import com.antonchuraev.homesearchchecklist.feature.user.domain.model.UserData
import kotlinx.coroutines.flow.Flow

interface UserDataRepository {

    fun getUserDataFlow(): Flow<UserData>

    suspend fun getUserData(): UserData

    suspend fun update(userData: UserData)

    /**
     * Ensures the user is registered with the server.
     * If the user is already registered locally, syncs with server.
     * If not, registers with the server using device ID and saves the result.
     *
     * @return Result with RegistrationData (includes isNewUser flag) or error
     */
    suspend fun ensureUserRegistered(): Result<RegistrationData>

    /**
     * Syncs user data with the server.
     * Always calls the server to get fresh data (credits, premium status).
     * Updates local cache with server data.
     *
     * @return Result with RegistrationData or error
     */
    suspend fun syncWithServer(): Result<RegistrationData>

    /**
     * Checks if the user has been linked with RevenueCat.
     */
    suspend fun isPaywallLinked(): Boolean

    /**
     * Marks the user as linked with RevenueCat.
     */
    suspend fun setPaywallLinked(linked: Boolean)
}
