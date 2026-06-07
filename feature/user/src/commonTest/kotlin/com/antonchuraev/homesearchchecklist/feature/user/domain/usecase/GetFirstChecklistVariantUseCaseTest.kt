package com.antonchuraev.homesearchchecklist.feature.user.domain.usecase

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigDefaults
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigKeys
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigProvider
import com.antonchuraev.homesearchchecklist.feature.user.domain.usecase.GetFirstChecklistVariantUseCase.FirstChecklistVariant
import kotlin.test.Test
import kotlin.test.assertEquals

class GetFirstChecklistVariantUseCaseTest {

    private fun createUseCase(rcValue: String): GetFirstChecklistVariantUseCase {
        return GetFirstChecklistVariantUseCase(
            remoteConfigProvider = FakeRemoteConfigProvider(rcValue = rcValue),
            logger = NoOpLogger(),
        )
    }

    @Test
    fun invoke_autoCreate_returnsAutoCreate() {
        val result = createUseCase("auto_create")()

        assertEquals(FirstChecklistVariant.AUTO_CREATE, result)
    }

    @Test
    fun invoke_current_returnsCurrent() {
        val result = createUseCase("current")()

        assertEquals(FirstChecklistVariant.CURRENT, result)
    }

    @Test
    fun invoke_emptyString_returnsCurrent() {
        val result = createUseCase("")()

        assertEquals(FirstChecklistVariant.CURRENT, result)
    }

    @Test
    fun invoke_unknownValue_returnsCurrent() {
        val result = createUseCase("garbage_value")()

        assertEquals(FirstChecklistVariant.CURRENT, result)
    }

    /**
     * Guards the A/B distribution: the empty client default must resolve to CURRENT
     * (control), so a stale/empty Remote Config never silently funnels every user into
     * the auto_create treatment. If someone flips the default to "auto_create", this fails.
     */
    @Test
    fun invoke_clientDefaultIsEmpty_resolvesToCurrent() {
        val useCase = GetFirstChecklistVariantUseCase(
            remoteConfigProvider = PassThroughDefaultProvider(),
            logger = NoOpLogger(),
        )

        val result = useCase()

        assertEquals(FirstChecklistVariant.CURRENT, result)
        assertEquals(
            "",
            RemoteConfigDefaults.FIRST_CHECKLIST_VARIANT,
            "Client default for FIRST_CHECKLIST_VARIANT must stay empty to keep A/B distribution honest"
        )
    }

    // --- Test doubles ---

    private class FakeRemoteConfigProvider(
        private val rcValue: String
    ) : RemoteConfigProvider {
        override suspend fun fetchAndActivate(): Boolean = true
        override fun getBoolean(key: String, defaultValue: Boolean): Boolean = defaultValue
        override fun getLong(key: String, defaultValue: Long): Long = defaultValue
        override fun getString(key: String, defaultValue: String): String {
            return if (key == RemoteConfigKeys.FIRST_CHECKLIST_VARIANT) rcValue else defaultValue
        }
    }

    private class PassThroughDefaultProvider : RemoteConfigProvider {
        override suspend fun fetchAndActivate(): Boolean = true
        override fun getBoolean(key: String, defaultValue: Boolean): Boolean = defaultValue
        override fun getLong(key: String, defaultValue: Long): Long = defaultValue
        override fun getString(key: String, defaultValue: String): String = defaultValue
    }

    private class NoOpLogger : AppLogger {
        override fun debug(tag: String, message: String) {}
        override fun info(tag: String, message: String) {}
        override fun warning(tag: String, message: String) {}
        override fun error(tag: String, message: String, throwable: Throwable?) {}
    }
}
