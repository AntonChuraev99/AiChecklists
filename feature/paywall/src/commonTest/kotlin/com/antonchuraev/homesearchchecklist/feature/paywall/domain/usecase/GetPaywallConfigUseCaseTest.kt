package com.antonchuraev.homesearchchecklist.feature.paywall.domain.usecase

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigKeys
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigProvider
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.PaywallRemoteConfig
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Green coverage for [GetPaywallConfigUseCase]: the `paywall_config` JSON resolver must
 * always return a non-blank currentOffer, falling back to [PaywallRemoteConfig.DEFAULT_OFFER]
 * on empty / blank / invalid / partial config, and must never throw.
 */
class GetPaywallConfigUseCaseTest {

    private fun createUseCase(paywallConfigValue: String): GetPaywallConfigUseCase {
        return GetPaywallConfigUseCase(
            remoteConfigProvider = FakeRemoteConfigProvider(
                paywallConfigValue = paywallConfigValue,
            ),
            logger = NoOpLogger(),
        )
    }

    @Test
    fun invoke_validJson_returnsCurrentOffer() {
        val useCase = createUseCase("""{"currentOffer":"someOffer"}""")

        val result = useCase()

        assertEquals("someOffer", result.currentOffer)
    }

    @Test
    fun invoke_emptyValue_returnsDefaultOffer() {
        val useCase = createUseCase("")

        val result = useCase()

        assertEquals(PaywallRemoteConfig.DEFAULT_OFFER, result.currentOffer)
    }

    @Test
    fun invoke_blankValue_returnsDefaultOffer() {
        val useCase = createUseCase("   ")

        val result = useCase()

        assertEquals(PaywallRemoteConfig.DEFAULT_OFFER, result.currentOffer)
    }

    @Test
    fun invoke_invalidJson_returnsDefaultOfferWithoutThrowing() {
        val useCase = createUseCase("{not json")

        val result = useCase()

        assertEquals(PaywallRemoteConfig.DEFAULT_OFFER, result.currentOffer)
    }

    @Test
    fun invoke_jsonMissingCurrentOffer_returnsDefaultOffer() {
        val useCase = createUseCase("{}")

        val result = useCase()

        assertEquals(PaywallRemoteConfig.DEFAULT_OFFER, result.currentOffer)
    }

    @Test
    fun invoke_jsonBlankCurrentOffer_returnsDefaultOffer() {
        val useCase = createUseCase("""{"currentOffer":""}""")

        val result = useCase()

        assertEquals(PaywallRemoteConfig.DEFAULT_OFFER, result.currentOffer)
    }

    @Test
    fun invoke_jsonWithUnknownField_ignoresUnknownAndReturnsCurrentOffer() {
        val useCase = createUseCase("""{"currentOffer":"x","futureField":42}""")

        val result = useCase()

        assertEquals("x", result.currentOffer)
    }

    // --- Test doubles ---

    private class FakeRemoteConfigProvider(
        private val paywallConfigValue: String,
    ) : RemoteConfigProvider {
        override suspend fun fetchAndActivate(): Boolean = true
        override fun getBoolean(key: String, defaultValue: Boolean): Boolean = defaultValue
        override fun getLong(key: String, defaultValue: Long): Long = defaultValue
        override fun getString(key: String, defaultValue: String): String {
            return if (key == RemoteConfigKeys.PAYWALL_CONFIG) paywallConfigValue else defaultValue
        }
    }

    private class NoOpLogger : AppLogger {
        override fun debug(tag: String, message: String) {}
        override fun info(tag: String, message: String) {}
        override fun warning(tag: String, message: String) {}
        override fun error(tag: String, message: String, throwable: Throwable?) {}
    }
}
