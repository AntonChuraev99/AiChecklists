package com.antonchuraev.homesearchchecklist.voicecall

import android.content.Context
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.voice.api.CallConfig
import com.antonchuraev.homesearchchecklist.core.voice.api.SpeechLocale
import com.antonchuraev.homesearchchecklist.core.voice.api.SpeechRate
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceCallEngine
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceCallEntry
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceSessionGateway
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceTurnGateway
import com.antonchuraev.homesearchchecklist.core.voice.impl.di.voiceModule
import com.antonchuraev.homesearchchecklist.feature.aichat.api.dispatcher.ToolCallDispatcher
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.di.aiChatFeatureModule
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import com.antonchuraev.homesearchchecklist.feature.user.domain.repository.UserDataRepository
import com.antonchuraev.homesearchchecklist.feature.voicecall.di.VoiceCallLanguage
import com.antonchuraev.homesearchchecklist.feature.voicecall.di.voiceCallFeatureModule
import com.antonchuraev.homesearchchecklist.feature.voicecall.presentation.VoiceCallViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.parameter.parametersOf
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame

/**
 * The voice call's Koin wiring resolves with the REAL modules (`voiceModule` Android bindings,
 * `aiChatFeatureModule`'s per-call `VoiceTurnGateway`, `voiceCallFeatureModule`): the engine factory
 * takes the two gateways as parameters by type, and every call gets fresh gateways. Only the app-level
 * dependencies the graph reaches into (repositories, dispatcher, logger, analytics) are stand-ins.
 *
 * Run: ./gradlew :composeApp:testAndroidHostTest --tests "*VoiceCallKoinGraphTest*"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VoiceCallKoinGraphTest {

    private inline fun <reified T : Any> unused(): T = Proxy.newProxyInstance(
        T::class.java.classLoader,
        arrayOf(T::class.java),
    ) { _, method, _ -> error("${T::class.simpleName}.${method.name} is not part of the graph check") } as T

    private object NoLog : AppLogger {
        override fun debug(tag: String, message: String) = Unit
        override fun info(tag: String, message: String) = Unit
        override fun warning(tag: String, message: String) = Unit
        override fun error(tag: String, message: String, throwable: Throwable?) = Unit
    }

    private fun graph() = koinApplication {
        modules(
            voiceModule,
            aiChatFeatureModule,
            voiceCallFeatureModule,
            module {
                single<Context> { RuntimeEnvironment.getApplication() }
                single<AppLogger> { NoLog }
                single<AnalyticsTracker> { unused() }
                single<UserDataRepository> { unused() }
                single<ChecklistRepository> { unused() }
                single<ToolCallDispatcher> { unused() }
                single<VoiceCallLanguage> { VoiceCallLanguage { "en" } }
            },
        )
    }.koin

    @Test
    fun engineFactory_resolvesWithTheFeatureGateways_andCreatesAnEngine() {
        val koin = graph()
        val turns = koin.get<VoiceTurnGateway>()
        val sessions = koin.get<VoiceSessionGateway>()
        assertEquals("ChatAgentVoiceTurnGateway", turns::class.simpleName)
        assertEquals("VoiceSessionGatewayImpl", sessions::class.simpleName)

        val factory = koin.get<VoiceCallEngine.Factory> { parametersOf(turns, sessions) }
        val scope = CoroutineScope(SupervisorJob())
        try {
            val engine = factory.create(
                scope,
                CallConfig(
                    entry = VoiceCallEntry("test"),
                    listenLocale = SpeechLocale("en-US"),
                    uiLocale = "en",
                    manualEndOfTurn = false,
                    bargeInSpeakerEnabled = true,
                    streamEnabled = false,
                    rate = SpeechRate.Normal,
                    softLimitSec = null,
                    backgroundEndSec = 120,
                ),
            )
            assertNotNull(engine.state.value)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun everyCall_getsFreshGateways() {
        val koin = graph()
        assertNotSame(koin.get<VoiceTurnGateway>(), koin.get<VoiceTurnGateway>())
        assertNotSame(koin.get<VoiceSessionGateway>(), koin.get<VoiceSessionGateway>())
    }

    @Test
    fun viewModel_resolves() {
        assertNotNull(graph().get<VoiceCallViewModel>())
    }
}
