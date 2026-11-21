@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.antonchuraev.homesearchchecklist.feature.analyze.data.job

import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AppForegroundMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Web [AppForegroundMonitor]: the tab is "in the foreground" while `document.visibilityState` is
 * `visible`. Driven by `visibilitychange` (fires on tab switch, window minimise, mobile app switch,
 * screen lock) plus `pagehide`/`pageshow`, which cover a bfcache freeze where `visibilitychange` may
 * not be the last event the page sees.
 *
 * Koin `single`: one listener for the life of the page, never removed (the page itself is the scope).
 * Where `document` is unavailable the bridge reports visible and registers nothing, i.e. the old
 * always-foreground behaviour.
 */
class VisibilityForegroundMonitor : AppForegroundMonitor {

    private val state = MutableStateFlow(jsIsDocumentVisible())

    override val isInForeground: StateFlow<Boolean> = state.asStateFlow()

    init {
        jsObserveVisibility { visible -> state.value = visible }
    }
}

@JsFun(
    """() => {
        try { return document.visibilityState !== 'hidden'; } catch (e) { return true; }
    }"""
)
private external fun jsIsDocumentVisible(): Boolean

@JsFun(
    """(onChange) => {
        try {
            const emit = (visible) => { try { onChange(visible); } catch (e) { console.error('[AnalyzeForeground] callback failed:', e); } };
            document.addEventListener('visibilitychange', () => emit(document.visibilityState !== 'hidden'));
            globalThis.addEventListener('pagehide', () => emit(false));
            globalThis.addEventListener('pageshow', () => emit(document.visibilityState !== 'hidden'));
        } catch (e) {
            console.warn('[AnalyzeForeground] visibility listener unavailable:', e);
        }
    }"""
)
private external fun jsObserveVisibility(onChange: (Boolean) -> Unit)
