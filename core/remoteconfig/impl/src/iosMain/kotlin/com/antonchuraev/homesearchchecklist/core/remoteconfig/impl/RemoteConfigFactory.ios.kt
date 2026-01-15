package com.antonchuraev.homesearchchecklist.core.remoteconfig.impl

import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigProvider

actual fun createRemoteConfigProvider(): RemoteConfigProvider = StubRemoteConfigProvider()
