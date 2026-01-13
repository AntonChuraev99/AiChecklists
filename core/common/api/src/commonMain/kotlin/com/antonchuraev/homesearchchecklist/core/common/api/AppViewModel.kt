package com.antonchuraev.homesearchchecklist.core.common.api

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * todo refactofing
 * todo add side effect
 */
abstract class AppViewModel<S : State, I : Intent, SE : SideEffect> : ViewModel() {

    abstract val screenState: StateFlow<S>

    private val _intent: MutableSharedFlow<I> = MutableSharedFlow(extraBufferCapacity = 64)

    init {
        viewModelScope.launch {
            _intent.collect {
                onIntent(it)
            }
        }
    }

    abstract fun onIntent(intent: I)

    final fun sendIntent(intent: I) {
        _intent.tryEmit(intent)
    }

    fun Flow<S>.defaultStateIn(initial: S): StateFlow<S> = stateIn(
        viewModelScope,
        kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
        initial
    )
}

interface State

interface Intent

interface SideEffect