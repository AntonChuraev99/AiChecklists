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
     * Locks in the baseline: the client default resolves to AUTO_CREATE, so a brand-new user
     * gets the starter checklist even before the first Remote Config fetch (or when a fetch
     * fails). If someone reverts the default to "" / "current", this fails on purpose — change
     * it only with an explicit product decision and update the Console parameter default too.
     */
    @Test
    fun invoke_clientDefaultIsAutoCreate_resolvesToAutoCreate() {
        val useCase = GetFirstChecklistVariantUseCase(
            remoteConfigProvider = PassThroughDefaultProvider(),
            logger = NoOpLogger(),
        )

        val result = useCase()

        assertEquals(FirstChecklistVariant.AUTO_CREATE, result)
        assertEquals(
            "auto_create",
            RemoteConfigDefaults.FIRST_CHECKLIST_VARIANT,
            "Client default for FIRST_CHECKLIST_VARIANT must stay 'auto_create' so new users get the starter checklist by default"
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
