package com.antonchuraev.homesearchchecklist.core.remoteconfig.impl

import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigProvider

/**
 * Factory function to create platform-specific RemoteConfigProvider.
 */
expect fun createRemoteConfigProvider(): RemoteConfigProvider
