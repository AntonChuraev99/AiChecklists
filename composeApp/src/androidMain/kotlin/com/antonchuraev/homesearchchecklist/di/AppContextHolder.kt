package com.antonchuraev.homesearchchecklist.di

import android.content.Context

object AppContextHolder {
    private var value: Context? = null

    fun init(context: Context) {
        if (value == null) {
            value = context.applicationContext
        }
    }

    val context: Context
        get() = requireNotNull(value) {
            "Application context is not initialized"
        }
}


