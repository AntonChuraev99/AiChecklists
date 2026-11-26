package com.antonchuraev.homesearchchecklist.feature.user.data.repository

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.feature.user.data.remote.LinkGoogleAccountApiResult
import com.antonchuraev.homesearchchecklist.feature.user.data.remote.RegisterUserResult
import com.antonchuraev.homesearchchecklist.feature.user.data.remote.RestoreCreditsResult
import com.antonchuraev.homesearchchecklist.feature.user.data.remote.UserApiService
import com.antonchuraev.homesearchchecklist.feature.user.data.remote.decodeCreditsConfig
import com.antonchuraev.homesearchchecklist.feature.user.domain.model.RegistrationData
import com.antonchuraev.homesearchchecklist.feature.user.domain.model.UserData
import com.antonchuraev.homesearchchecklist.feature.user.domain.repository.CreditsConfig
import com.antonchuraev.homesearchchecklist.feature.user.domain.repository.UserDataRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `get_credits_info` parsing and the shared cache (FEATURE_SPEC 2026-11-26 §3): the number the
 * out-of-credits banner promises is `premium_daily_credits_cap` from the server, never a client
 * default; a failure keeps the last good value.
 */
class CreditsConfigProviderImplTest {

    @Test
    fun decode_readsBothNumbersFromTheServerConfig() {
        // Both values deliberately differ from the served ones (CLAUDE.md: pin two different
        // config values), so a hardcoded cost or cap in the decoder fails this test.
        val raw = """{"success":true,"config":{"action_cost":37,"premium_daily_credits_cap":777,""" +
            """"refill_time":"12:00 CET"},"user_credits":40}"""

        assertEquals(CreditsConfig(actionCost = 37, premiumDailyCreditsCap = 777), decodeCreditsConfig(raw))
    }

    @Test
    fun decode_missingCap_isAFailure_notADefault() {
        // An older server without the cap must not turn into a made-up number.
        val raw = """{"success":true,"config":{"action_cost":20}}"""

        assertFailsWith<IllegalStateException> { decodeCreditsConfig(raw) }
    }

    @Test
    fun decode_successFalse_isAFailure() {
        assertFailsWith<IllegalStateException> {
            decodeCreditsConfig("""{"success":false,"error":"boom","config":{"action_cost":20,"premium_daily_credits_cap":300}}""")
        }
    }

    @Test
    fun refresh_success_publishesTheConfig() = runTest {
        val api = FakeApi(Result.success(CreditsConfig(20, 777)))
        val provider = CreditsConfigProviderImpl(api, FakeUsers("u1"), SilentLogger())

        provider.refresh()

        assertEquals(CreditsConfig(20, 777), provider.config.value)
        assertEquals("u1", api.lastUserId)
    }

    @Test
    fun refresh_failure_keepsTheLastGoodValue_andWarns() = runTest {
        val api = FakeApi(Result.success(CreditsConfig(20, 777)))
        val logger = SilentLogger()
        val provider = CreditsConfigProviderImpl(api, FakeUsers("u1"), logger)
        provider.refresh()

        api.next = Result.failure(Exception("offline"))
        val result = provider.refresh()

        assertTrue(result.isFailure)
        assertEquals(CreditsConfig(20, 777), provider.config.value)
        assertEquals(1, logger.warnings.size)
    }

    @Test
    fun refresh_unregisteredUser_isAFailure_withoutCallingTheServer() = runTest {
        val api = FakeApi(Result.success(CreditsConfig(20, 777)))
        val logger = SilentLogger()
        val provider = CreditsConfigProviderImpl(api, FakeUsers(""), logger)

        val result = provider.refresh()

        assertTrue(result.isFailure)
        assertNull(provider.config.value)
        assertNull(api.lastUserId)
        assertEquals(1, logger.warnings.size, "a skipped refresh is logged, never silent")
    }

    private class FakeApi(var next: Result<CreditsConfig>) : UserApiService {
        var lastUserId: String? = null
        override suspend fun getCreditsInfo(userId: String): Result<CreditsConfig> {
            lastUserId = userId
            return next
        }

        override suspend fun registerUser(deviceId: String, appVersion: String?, platform: String?): Result<RegisterUserResult> =
            error("not used")

        override suspend fun restoreCreditsAfterPurchase(userId: String): Result<RestoreCreditsResult> = error("not used")

        override suspend fun linkGoogleAccount(userId: String, idToken: String, platform: String): Result<LinkGoogleAccountApiResult> =
            error("not used")
    }

    private class FakeUsers(userId: String) : UserDataRepository {
        private val flow = MutableStateFlow(UserData(userId = userId))
        override fun getUserDataFlow(): StateFlow<UserData> = flow
        override suspend fun getUserData(): UserData = flow.value
        override suspend fun update(userData: UserData) { flow.value = userData }
        override suspend fun ensureUserRegistered(): Result<RegistrationData> = error("not used")
        override suspend fun syncWithServer(): Result<RegistrationData> = error("not used")
        override suspend fun isPaywallLinked(): Boolean = false
        override suspend fun setPaywallLinked(linked: Boolean) = Unit
        override suspend fun restoreCreditsAfterPurchase(): Result<Int> = error("not used")
        override suspend fun getFirstLaunchAtMillis(): Long = 0L
    }

    private class SilentLogger : AppLogger {
        val warnings = mutableListOf<String>()
        override fun debug(tag: String, message: String) {}
        override fun info(tag: String, message: String) {}
        override fun warning(tag: String, message: String) { warnings += message }
        override fun error(tag: String, message: String, throwable: Throwable?) {}
    }
}
