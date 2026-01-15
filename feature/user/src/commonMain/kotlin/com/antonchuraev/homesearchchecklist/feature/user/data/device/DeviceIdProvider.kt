package com.antonchuraev.homesearchchecklist.feature.user.data.device

/**
 * Provides a unique device identifier for user registration.
 * This ID is used to prevent abuse by reinstalling the app.
 */
expect class DeviceIdProvider {
    /**
     * Returns a unique device identifier.
     * - On Android: Uses Settings.Secure.ANDROID_ID
     * - On iOS: Uses UIDevice.identifierForVendor
     */
    fun getDeviceId(): String
}
