package com.antonchuraev.homesearchchecklist.core.common.impl

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger

private class ConsoleAppLogger : AppLogger {
    override fun debug(tag: String, message: String) = consoleLog("[D] $tag: $message")
    override fun info(tag: String, message: String) = consoleLog("[I] $tag: $message")
    override fun warning(tag: String, message: String) = consoleWarn("[W] $tag: $message")
    override fun error(tag: String, message: String, throwable: Throwable?) {
        // Mirrors Android's Log.e(tag, msg, throwable): type + stack + causal chain.
        // Printing only throwable.message used to reduce a prod NPE to "| null" — see
        // describeForLog().
        consoleError("[E] $tag: $message${throwable?.let { "\n${it.describeForLog()}" } ?: ""}")
    }
}

actual fun createLogger(): AppLogger = ConsoleAppLogger()

@JsFun("(msg) => console.log(msg)")
private external fun consoleLog(msg: String)

@JsFun("(msg) => console.warn(msg)")
private external fun consoleWarn(msg: String)

@JsFun("(msg) => console.error(msg)")
private external fun consoleError(msg: String)
