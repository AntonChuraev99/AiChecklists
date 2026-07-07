package com.antonchuraev.homesearchchecklist.core.remoteconfig.impl

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigProvider

/**
 * Factory function to create platform-specific RemoteConfigProvider.
 *
 * [logger] is used by the Android provider to report its Firebase Installations (FIS) warm-up
 * outcome; other platforms currently ignore it.
 */
expect fun createRemoteConfigProvider(logger: AppLogger): RemoteConfigProvider
