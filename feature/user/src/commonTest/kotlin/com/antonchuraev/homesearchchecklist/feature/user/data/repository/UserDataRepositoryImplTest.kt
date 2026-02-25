package com.antonchuraev.homesearchchecklist.feature.user.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.datastore.api.AppDatastore
import com.antonchuraev.homesearchchecklist.feature.user.data.device.DeviceIdProvider
import com.antonchuraev.homesearchchecklist.feature.user.data.remote.RegisterUserResult
import com.antonchuraev.homesearchchecklist.feature.user.data.remote.RestoreCreditsResult
import com.antonchuraev.homesearchchecklist.feature.user.data.remote.UserApiService
import com.antonchuraev.homesearchchecklist.feature.user.domain.model.UserData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for UserDataRepositoryImpl.
 *
 * Tests the getUserData() optimization that uses StateFlow fast path
 * with fallback for cold start scenarios.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UserDataRepositoryImplTest {

    private val testDispatcher = StandardTestDispatcher()

    init {
        Dispatchers.setMain(testDispatcher)
    }

    // ============================================
    // getUserData() tests
    // ============================================

    @Test
    fun testGetUserDataReturnsCachedDataWhenAvailable() = runTest {
        // Arrange: DataStore has user data pre-populated
        val datastore = createStubDatastore(
            strings = mapOf("user_id" to "test-user-123"),
            booleans = mapOf("is_onboarding_passed" to true, "is_premium" to false),
            ints = mapOf("ai_credits" to 50)
        )
        val repo = createRepository(datastore)

        // Give StateFlow time to collect from DataStore
        testDispatcher.scheduler.advanceUntilIdle()

        // Act
        val userData = repo.getUserData()

        // Assert: should return data from StateFlow fast path
        assertEquals("test-user-123", userData.userId)
        assertTrue(userData.isOnboardingPassed)
        assertEquals(false, userData.isPremium)
        assertEquals(50, userData.aiCredits)
    }

    @Test
    fun testGetUserDataReturnsDefaultForNewUser() = runTest {
        // Arrange: DataStore is empty (new user, first launch)
        val datastore = createStubDatastore()
        val repo = createRepository(datastore)

        testDispatcher.scheduler.advanceUntilIdle()

        // Act
        val userData = repo.getUserData()

        // Assert: should return default data (empty userId)
        assertEquals("", userData.userId)
        assertEquals(false, userData.isOnboardingPassed)
        assertEquals(false, userData.isPremium)
        assertEquals(0, userData.aiCredits)
    }

    @Test
    fun testGetUserDataFlowReflectsUpdates() = runTest {
        // Arrange
        val datastore = createStubDatastore(
            strings = mapOf("user_id" to "user-abc"),
            booleans = mapOf("is_onboarding_passed" to false, "is_premium" to false),
            ints = mapOf("ai_credits" to 10)
        )
        val repo = createRepository(datastore)
        testDispatcher.scheduler.advanceUntilIdle()

        // Act: update user data
        repo.update(UserData(
            userId = "user-abc",
            isOnboardingPassed = true,
            isPremium = true,
            aiCredits = 300
        ))
        testDispatcher.scheduler.advanceUntilIdle()

        // Assert: getUserData() should reflect the update
        val updated = repo.getUserData()
        assertEquals("user-abc", updated.userId)
        assertTrue(updated.isOnboardingPassed)
        assertTrue(updated.isPremium)
        assertEquals(300, updated.aiCredits)
    }

    @Test
    fun testGetUserDataFlowValueMatchesDirectRead() = runTest {
        // Arrange
        val datastore = createStubDatastore(
            strings = mapOf("user_id" to "user-xyz"),
            booleans = mapOf("is_onboarding_passed" to true, "is_premium" to true),
            ints = mapOf("ai_credits" to 200)
        )
        val repo = createRepository(datastore)
        testDispatcher.scheduler.advanceUntilIdle()

        // Act
        val fromGetData = repo.getUserData()
        val fromFlow = repo.getUserDataFlow().value

        // Assert: both paths should return same data
        assertEquals(fromFlow.userId, fromGetData.userId)
        assertEquals(fromFlow.isOnboardingPassed, fromGetData.isOnboardingPassed)
        assertEquals(fromFlow.isPremium, fromGetData.isPremium)
        assertEquals(fromFlow.aiCredits, fromGetData.aiCredits)
    }

    @Test
    fun testGetUserDataMultipleCallsAreIdempotent() = runTest {
        // Arrange
        val datastore = createStubDatastore(
            strings = mapOf("user_id" to "user-stable"),
            booleans = mapOf("is_onboarding_passed" to true, "is_premium" to false),
            ints = mapOf("ai_credits" to 75)
        )
        val repo = createRepository(datastore)
        testDispatcher.scheduler.advanceUntilIdle()

        // Act: call multiple times
        val first = repo.getUserData()
        val second = repo.getUserData()
        val third = repo.getUserData()

        // Assert: all calls return same data
        assertEquals(first, second)
        assertEquals(second, third)
    }

    // ============================================
    // Log masking verification (structural test)
    // ============================================

    @Test
    fun testLogMaskingDoesNotExposeFullIds() = runTest {
        // Arrange: capture log messages
        val logMessages = mutableListOf<String>()
        val logger = object : AppLogger {
            override fun debug(tag: String, message: String) { logMessages.add(message) }
            override fun info(tag: String, message: String) { logMessages.add(message) }
            override fun warning(tag: String, message: String) { logMessages.add(message) }
            override fun error(tag: String, message: String, throwable: Throwable?) { logMessages.add(message) }
        }

        val fullDeviceId = "550e8400-e29b-41d4-a716-446655440000"
        val fullUserId = "a1b2c3d4-e5f6-4789-abcd-ef0123456789"

        val datastore = createStubDatastore()
        val stubApiService = object : UserApiService {
            override suspend fun registerUser(
                deviceId: String, appVersion: String?, platform: String?
            ): Result<RegisterUserResult> = Result.success(
                RegisterUserResult(
                    userId = fullUserId,
                    isNewUser = true,
                    isPremium = false,
                    aiCredits = 100,
                    createdAt = "2026-02-22"
                )
            )
            override suspend fun restoreCreditsAfterPurchase(userId: String) =
                Result.success(RestoreCreditsResult(100, true, "ok"))
        }

        val stubDeviceIdProvider = DeviceIdProvider(
            createStubDatastore(strings = mapOf("device_id" to fullDeviceId))
        )

        val repo = UserDataRepositoryImpl(
            appScope = backgroundScope,
            deviceIdProvider = stubDeviceIdProvider,
            userApiService = stubApiService,
            logger = logger,
            appDatastore = datastore,
            analyticsTracker = NoOpAnalyticsTracker()
        )
        testDispatcher.scheduler.advanceUntilIdle()

        // Act: trigger registration flow which logs deviceId and userId
        repo.ensureUserRegistered()

        // Assert: no log message should contain full device ID or user ID
        for (msg in logMessages) {
            assertTrue(
                !msg.contains(fullDeviceId),
                "Log should not contain full deviceId: $msg"
            )
            assertTrue(
                !msg.contains(fullUserId),
                "Log should not contain full userId: $msg"
            )
        }

        // Assert: logs should contain masked prefix (first 8 chars)
        val devicePrefix = fullDeviceId.take(8) // "550e8400"
        val userPrefix = fullUserId.take(8) // "a1b2c3d4"
        val hasDevicePrefix = logMessages.any { it.contains(devicePrefix) }
        val hasUserPrefix = logMessages.any { it.contains(userPrefix) }
        assertTrue(hasDevicePrefix, "Logs should contain masked deviceId prefix")
        assertTrue(hasUserPrefix, "Logs should contain masked userId prefix")
    }

    // ============================================
    // restoreCreditsAfterPurchase() tests
    // ============================================

    @Test
    fun restoreCreditsAfterPurchase_successOnFirstAttempt_returnsCreditsAndTracksAnalytics() = runTest {
        val analytics = RecordingAnalyticsTracker()
        val apiService = FakeUserApiService(
            restoreResults = listOf(
                Result.success(RestoreCreditsResult(aiCredits = 300, isPremium = true, message = "ok"))
            )
        )
        val datastore = createStubDatastore(
            strings = mapOf("user_id" to "user-premium-123"),
            booleans = mapOf("is_premium" to false),
            ints = mapOf("ai_credits" to 0)
        )
        val repo = createRepositoryWith(datastore, apiService, analytics)
        testDispatcher.scheduler.advanceUntilIdle()

        val result = repo.restoreCreditsAfterPurchase()
        testDispatcher.scheduler.advanceUntilIdle()

        // Assert: returns 300 credits
        assertTrue(result.isSuccess)
        assertEquals(300, result.getOrNull())

        // Assert: DataStore updated
        val userData = repo.getUserData()
        assertEquals(300, userData.aiCredits)
        assertTrue(userData.isPremium)

        // Assert: analytics tracked
        assertTrue(analytics.hasEvent("credits_restore_started"))
        assertTrue(analytics.hasEvent("credits_restore_success"))
        assertEquals(300, analytics.getEventParam("credits_restore_success", "credits"))
        assertEquals(1, analytics.getEventParam("credits_restore_success", "attempt"))

        // Assert: no retry or failure events
        assertTrue(!analytics.hasEvent("credits_restore_retry"))
        assertTrue(!analytics.hasEvent("credits_restore_failed"))
    }

    @Test
    fun restoreCreditsAfterPurchase_failsThenSucceeds_retriesAndReturnsCredits() = runTest {
        val analytics = RecordingAnalyticsTracker()
        val apiService = FakeUserApiService(
            restoreResults = listOf(
                Result.failure(Exception("timeout")),
                Result.success(RestoreCreditsResult(aiCredits = 300, isPremium = true, message = "ok"))
            )
        )
        val datastore = createStubDatastore(
            strings = mapOf("user_id" to "user-retry-456"),
            booleans = mapOf("is_premium" to false),
            ints = mapOf("ai_credits" to 0)
        )
        val repo = createRepositoryWith(datastore, apiService, analytics)
        testDispatcher.scheduler.advanceUntilIdle()

        val result = repo.restoreCreditsAfterPurchase()
        testDispatcher.scheduler.advanceUntilIdle()

        // Assert: eventually succeeds
        assertTrue(result.isSuccess)
        assertEquals(300, result.getOrNull())

        // Assert: retry event tracked
        assertTrue(analytics.hasEvent("credits_restore_retry"))
        assertEquals(1, analytics.getEventParam("credits_restore_retry", "attempt"))
        assertEquals("timeout", analytics.getEventParam("credits_restore_retry", "error"))

        // Assert: success on attempt 2
        assertEquals(2, analytics.getEventParam("credits_restore_success", "attempt"))
    }

    @Test
    fun restoreCreditsAfterPurchase_allRetriesFail_returnsFailureAndTracksAnalytics() = runTest {
        val analytics = RecordingAnalyticsTracker()
        val apiService = FakeUserApiService(
            restoreResults = listOf(
                Result.failure(Exception("timeout 1")),
                Result.failure(Exception("timeout 2")),
                Result.failure(Exception("timeout 3"))
            )
        )
        val datastore = createStubDatastore(
            strings = mapOf("user_id" to "user-fail-789"),
            booleans = mapOf("is_premium" to false),
            ints = mapOf("ai_credits" to 0)
        )
        val repo = createRepositoryWith(datastore, apiService, analytics)
        testDispatcher.scheduler.advanceUntilIdle()

        val result = repo.restoreCreditsAfterPurchase()
        testDispatcher.scheduler.advanceUntilIdle()

        // Assert: returns failure
        assertTrue(result.isFailure)

        // Assert: DataStore NOT updated (still 0 credits)
        val userData = repo.getUserData()
        assertEquals(0, userData.aiCredits)
        assertEquals(false, userData.isPremium)

        // Assert: analytics tracked
        assertTrue(analytics.hasEvent("credits_restore_started"))
        assertTrue(analytics.hasEvent("credits_restore_failed"))
        assertEquals(3, analytics.getEventParam("credits_restore_failed", "attempts"))

        // Assert: 2 retry events (after attempt 1 and 2, not after final attempt 3)
        assertEquals(2, analytics.countEvents("credits_restore_retry"))

        // Assert: no success event
        assertTrue(!analytics.hasEvent("credits_restore_success"))
    }

    @Test
    fun restoreCreditsAfterPurchase_noUserId_returnsFailureImmediately() = runTest {
        val analytics = RecordingAnalyticsTracker()
        val apiService = FakeUserApiService(restoreResults = emptyList())
        val datastore = createStubDatastore() // no user_id
        val repo = createRepositoryWith(datastore, apiService, analytics)
        testDispatcher.scheduler.advanceUntilIdle()

        val result = repo.restoreCreditsAfterPurchase()

        // Assert: fails with "User not registered"
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("User not registered") == true)

        // Assert: analytics tracked with no_user_id error
        assertTrue(analytics.hasEvent("credits_restore_failed"))
        assertEquals("no_user_id", analytics.getEventParam("credits_restore_failed", "error"))

        // Assert: API was never called
        assertEquals(0, apiService.restoreCallCount)
    }

    @Test
    fun restoreCreditsAfterPurchase_apiCalledWithCorrectUserId() = runTest {
        val apiService = FakeUserApiService(
            restoreResults = listOf(
                Result.success(RestoreCreditsResult(aiCredits = 300, isPremium = true, message = "ok"))
            )
        )
        val datastore = createStubDatastore(
            strings = mapOf("user_id" to "expected-user-id-here")
        )
        val repo = createRepositoryWith(datastore, apiService, RecordingAnalyticsTracker())
        testDispatcher.scheduler.advanceUntilIdle()

        repo.restoreCreditsAfterPurchase()

        // Assert: API called with correct userId
        assertEquals(1, apiService.restoreCallCount)
        assertEquals("expected-user-id-here", apiService.lastRestoreUserId)
    }

    // ============================================
    // Helper Functions
    // ============================================

    private fun TestScope.createRepository(datastore: AppDatastore): UserDataRepositoryImpl {
        return UserDataRepositoryImpl(
            appScope = backgroundScope,
            deviceIdProvider = DeviceIdProvider(createStubDatastore()),
            userApiService = NoOpUserApiService(),
            logger = NoOpLogger(),
            appDatastore = datastore,
            analyticsTracker = NoOpAnalyticsTracker()
        )
    }

    private fun TestScope.createRepositoryWith(
        datastore: AppDatastore,
        apiService: UserApiService,
        analyticsTracker: AnalyticsTracker
    ): UserDataRepositoryImpl {
        return UserDataRepositoryImpl(
            appScope = backgroundScope,
            deviceIdProvider = DeviceIdProvider(createStubDatastore()),
            userApiService = apiService,
            logger = NoOpLogger(),
            appDatastore = datastore,
            analyticsTracker = analyticsTracker
        )
    }

    private fun createStubDatastore(
        strings: Map<String, String> = emptyMap(),
        booleans: Map<String, Boolean> = emptyMap(),
        ints: Map<String, Int> = emptyMap()
    ): AppDatastore {
        val stringMap = strings.toMutableMap()
        val booleanMap = booleans.toMutableMap()
        val intMap = ints.toMutableMap()

        fun buildPreferences(): Preferences {
            val pairs = mutableListOf<Preferences.Pair<*>>()
            stringMap.forEach { (k, v) -> pairs.add(stringPreferencesKey(k) to v) }
            booleanMap.forEach { (k, v) -> pairs.add(booleanPreferencesKey(k) to v) }
            intMap.forEach { (k, v) -> pairs.add(intPreferencesKey(k) to v) }
            return mutablePreferencesOf(*pairs.toTypedArray())
        }

        val prefsFlow = MutableStateFlow(buildPreferences())

        val stubDataStore = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = prefsFlow

            override suspend fun updateData(
                transform: suspend (Preferences) -> Preferences
            ): Preferences {
                val current = buildPreferences()
                val newPrefs = transform(current)

                // Update internal maps from the transformed preferences
                newPrefs.asMap().forEach { (key, value) ->
                    when (value) {
                        is String -> stringMap[key.name] = value
                        is Boolean -> booleanMap[key.name] = value
                        is Int -> intMap[key.name] = value
                    }
                }

                val updated = buildPreferences()
                prefsFlow.value = updated
                return updated
            }
        }

        return AppDatastore(stubDataStore, Dispatchers.Unconfined)
    }

    private class NoOpLogger : AppLogger {
        override fun debug(tag: String, message: String) {}
        override fun info(tag: String, message: String) {}
        override fun warning(tag: String, message: String) {}
        override fun error(tag: String, message: String, throwable: Throwable?) {}
    }

    private class NoOpAnalyticsTracker : AnalyticsTracker {
        override fun setUserId(userId: String) {}
        override fun screenView(name: String) {}
        override fun event(name: String, params: Map<String, Any>) {}
    }

    /**
     * Records all analytics events for assertion in tests.
     */
    private class RecordingAnalyticsTracker : AnalyticsTracker {
        private val events = mutableListOf<Pair<String, Map<String, Any>>>()

        override fun setUserId(userId: String) {}
        override fun screenView(name: String) {}
        override fun event(name: String, params: Map<String, Any>) {
            events.add(name to params)
        }

        fun hasEvent(name: String): Boolean = events.any { it.first == name }
        fun countEvents(name: String): Int = events.count { it.first == name }
        fun getEventParam(name: String, param: String): Any? =
            events.firstOrNull { it.first == name }?.second?.get(param)
    }

    /**
     * Configurable fake API service that returns sequential results for restoreCreditsAfterPurchase.
     */
    private class FakeUserApiService(
        private val restoreResults: List<Result<RestoreCreditsResult>>
    ) : UserApiService {
        var restoreCallCount = 0
            private set
        var lastRestoreUserId: String? = null
            private set

        override suspend fun registerUser(
            deviceId: String, appVersion: String?, platform: String?
        ): Result<RegisterUserResult> =
            Result.failure(Exception("Not implemented in test"))

        override suspend fun restoreCreditsAfterPurchase(userId: String): Result<RestoreCreditsResult> {
            lastRestoreUserId = userId
            val index = restoreCallCount++
            return if (index < restoreResults.size) {
                restoreResults[index]
            } else {
                Result.failure(Exception("No more stub results (call #$index)"))
            }
        }
    }

    private class NoOpUserApiService : UserApiService {
        override suspend fun registerUser(
            deviceId: String, appVersion: String?, platform: String?
        ): Result<RegisterUserResult> =
            Result.failure(Exception("Not implemented in test"))

        override suspend fun restoreCreditsAfterPurchase(userId: String): Result<RestoreCreditsResult> =
            Result.failure(Exception("Not implemented in test"))
    }
}
