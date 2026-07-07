package com.antonchuraev.homesearchchecklist.core.remoteconfig.impl

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigProvider

actual fun createRemoteConfigProvider(logger: AppLogger): RemoteConfigProvider =
    FirebaseRemoteConfigProvider(logger)
