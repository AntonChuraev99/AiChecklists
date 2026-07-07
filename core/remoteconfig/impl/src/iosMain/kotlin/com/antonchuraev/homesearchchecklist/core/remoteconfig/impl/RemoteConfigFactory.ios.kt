package com.antonchuraev.homesearchchecklist.core.remoteconfig.impl

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigProvider

// logger is unused on iOS (stub provider); accepted to satisfy the common expect signature.
actual fun createRemoteConfigProvider(logger: AppLogger): RemoteConfigProvider = StubRemoteConfigProvider()
