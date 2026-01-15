package com.antonchuraev.homesearchchecklist.feature.user.data.device

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings

actual class DeviceIdProvider(
    private val context: Context
) {
    @SuppressLint("HardwareIds")
    actual fun getDeviceId(): String {
        return Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "unknown_android_device"
    }
}
