package com.antonchuraev.homesearchchecklist.feature.user.data.device

/** Generates a UUID v4 string using the browser's crypto.randomUUID() API. */
@OptIn(ExperimentalStdlibApi::class)
internal actual fun uuidString(): String = randomUuidJs()

@JsFun("() => crypto.randomUUID()")
private external fun randomUuidJs(): String
