package com.antonchuraev.homesearchchecklist.feature.user.domain.repository

import com.antonchuraev.homesearchchecklist.feature.user.domain.model.RegistrationData
import com.antonchuraev.homesearchchecklist.feature.user.domain.model.UserData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface UserDataRepository {

    fun getUserDataFlow(): StateFlow<UserData>

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

    /**
     * Restore credits after premium purchase.
     *
     * Calls the server to instantly restore credits for the user
     * after successful subscription purchase or restore.
     * Updates local cache with new credits and premium status.
     *
     * @return Result with restored credits info or error
     */
    suspend fun restoreCreditsAfterPurchase(): Result<Int>

    /**
     * Returns the timestamp (millis) of the user's first app launch.
     * Returns 0 if not yet recorded.
     */
    suspend fun getFirstLaunchAtMillis(): Long

    /**
     * Link a Google account with the server and update local state.
     * On web, this grants the starter credits pack (same as mobile install).
     *
     * @param idToken Firebase ID token from Google Sign-In
     * @param platform "android", "ios", or "web"
     * @return Result with link outcome including bonus credits
     */
    suspend fun linkGoogleAccount(
        idToken: String,
        platform: String,
    ): Result<LinkGoogleAccountResult> = Result.failure(UnsupportedOperationException("Not implemented"))

    /**
     * Clears all Google account data from local DataStore.
     * Called on sign-out so that the app returns to a non-linked state
     * without requiring a full app restart.
     */
    suspend fun clearGoogleAccountData() {}

    /**
     * Updates the locally cached AI credits / premium status from a server-side
     * snapshot (real-time Firestore listener on `users/{userId}`). Lets server-side
     * credit deductions made on OTHER devices propagate to this device's balance, so
     * the AI-credit pool is actually shared across web/Android for one account.
     */
    suspend fun updateCachedCredits(aiCredits: Int, isPremium: Boolean) {}

    /**
     * Self-healing convergence: re-point the locally stored USER_ID_KEY (the credit-doc id)
     * at [canonicalUserId] — the canonical Google-linked doc resolved by `google_uid`.
     *
     * Heals legacy devices that linked Google before USER_ID_KEY switching shipped: they
     * still point at their own device-id credit doc (no `google_uid`, never shared). After
     * convergence the credit listener re-attaches to the shared doc, so the AI-credit pool
     * is finally common across web/Android. No-op when blank or already equal. Default no-op
     * keeps test fakes compiling.
     */
    suspend fun convergeUserIdToCanonical(canonicalUserId: String) {}
}

data class LinkGoogleAccountResult(
    val googleEmail: String,
    val aiCredits: Int,
    val isPremium: Boolean,
    val bonusCreditsGranted: Int,
    val isExistingAccount: Boolean,
)
