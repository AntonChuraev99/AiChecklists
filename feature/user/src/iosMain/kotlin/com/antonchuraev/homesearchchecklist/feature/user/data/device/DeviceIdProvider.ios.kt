package com.antonchuraev.homesearchchecklist.feature.user.data.device

import platform.Foundation.NSUUID

/**
 * iOS implementation of UUID generation using NSUUID().
 */
internal actual fun uuidString(): String = NSUUID().UUIDString
