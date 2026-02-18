package com.antonchuraev.homesearchchecklist.csat

import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Decorator that wraps [AnalyticsTracker] and broadcasts every [event] call
 * on a [SharedFlow]. Allows zero-coupling observation of analytics events
 * (e.g. for CSAT trigger logic) without modifying the original interface.
 */
class ObservableAnalyticsTracker(
    private val delegate: AnalyticsTracker
) : AnalyticsTracker by delegate {

    private val _events = MutableSharedFlow<AnalyticsEvent>(
        extraBufferCapacity = 64 // tryEmit never suspends; drops if buffer overflows
    )
    val events: SharedFlow<AnalyticsEvent> = _events.asSharedFlow()

    override fun event(name: String, params: Map<String, Any>) {
        delegate.event(name, params)
        _events.tryEmit(AnalyticsEvent(name, params))
    }
}

data class AnalyticsEvent(
    val name: String,
    val params: Map<String, Any> = emptyMap(),
)
