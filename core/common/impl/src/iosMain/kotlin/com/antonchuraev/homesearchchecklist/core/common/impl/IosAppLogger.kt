package com.antonchuraev.homesearchchecklist.core.common.impl

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import platform.Foundation.NSLog

class IosAppLogger : AppLogger {
    override fun debug(tag: String, message: String) { NSLog("[$tag] DEBUG: $message") }
    override fun info(tag: String, message: String) { NSLog("[$tag] INFO: $message") }
    override fun warning(tag: String, message: String) { NSLog("[$tag] WARNING: $message") }
    override fun error(tag: String, message: String, throwable: Throwable?) {
        NSLog("[$tag] ERROR: $message${throwable?.let { "\n${it.stackTraceToString()}" } ?: ""}")
    }
}
