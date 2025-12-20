package com.antonchuraev.homesearchchecklist.core.common.impl

import android.util.Log
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger

class AndroidAppLogger : AppLogger {
    override fun debug(tag: String, message: String) { Log.d(tag, message) }
    override fun info(tag: String, message: String) { Log.i(tag, message) }
    override fun warning(tag: String, message: String) { Log.w(tag, message) }
    override fun error(tag: String, message: String, throwable: Throwable?) {
        if (throwable != null) Log.e(tag, message, throwable) else Log.e(tag, message)
    }
}
