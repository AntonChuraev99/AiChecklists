package com.antonchuraev.homesearchchecklist.feature.analyze.presentation

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import org.junit.rules.ExternalResource
import org.koin.core.context.GlobalContext
import org.koin.core.context.loadKoinModules
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.context.unloadKoinModules
import org.koin.dsl.module

/**
 * Supplies the [AppLogger] that the Analyze voice section resolves through `koinInject()`.
 *
 * Koin is JVM-global and Gradle reuses one JVM per test task, so other classes may leave a context
 * running (and rely on it). This rule stops only a context it started, never shadows a logger
 * someone else registered, and unloads only the module it loaded.
 */
class NoOpLoggerKoinRule : ExternalResource() {
    private val loggerModule = module { single<AppLogger> { NoOpLogger } }
    private var startedKoin = false
    private var loadedModule = false

    override fun before() {
        val running = GlobalContext.getOrNull()
        when {
            running == null -> {
                startKoin { modules(loggerModule) }
                startedKoin = true
            }
            running.getOrNull<AppLogger>() == null -> {
                loadKoinModules(loggerModule)
                loadedModule = true
            }
        }
    }

    override fun after() {
        if (startedKoin) stopKoin()
        if (loadedModule) unloadKoinModules(loggerModule)
    }

    private object NoOpLogger : AppLogger {
        override fun debug(tag: String, message: String) {}
        override fun info(tag: String, message: String) {}
        override fun warning(tag: String, message: String) {}
        override fun error(tag: String, message: String, throwable: Throwable?) {}
    }
}
