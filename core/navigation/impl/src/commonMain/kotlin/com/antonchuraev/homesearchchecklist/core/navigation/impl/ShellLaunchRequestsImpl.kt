package com.antonchuraev.homesearchchecklist.core.navigation.impl

import com.antonchuraev.homesearchchecklist.core.navigation.api.ShellLaunch
import com.antonchuraev.homesearchchecklist.core.navigation.api.ShellLaunchRequests
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory one-slot mailbox (see [ShellLaunchRequests]). A Koin `single`, so the feature that
 * requests and the App shell that consumes see the same slot.
 */
class ShellLaunchRequestsImpl : ShellLaunchRequests {
    private val _pending = MutableStateFlow<ShellLaunch?>(null)
    override val pending: StateFlow<ShellLaunch?> = _pending.asStateFlow()

    override fun request(launch: ShellLaunch) {
        _pending.value = launch
    }

    override fun consume(launch: ShellLaunch) {
        // CAS: a newer request that landed while the shell was acting on [launch] must survive.
        _pending.compareAndSet(launch, null)
    }
}
