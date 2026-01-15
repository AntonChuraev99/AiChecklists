package com.antonchuraev.homesearchchecklist.feature.user.data.device

import platform.UIKit.UIDevice

actual class DeviceIdProvider {
    actual fun getDeviceId(): String {
        return UIDevice.currentDevice.identifierForVendor?.UUIDString
            ?: "unknown_ios_device"
    }
}
