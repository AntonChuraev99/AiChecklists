package com.antonchuraev.homesearchchecklist.feature.user.data.device

import java.util.UUID

/**
 * Android implementation of UUID generation using java.util.UUID.
 */
internal actual fun uuidString(): String = UUID.randomUUID().toString()
