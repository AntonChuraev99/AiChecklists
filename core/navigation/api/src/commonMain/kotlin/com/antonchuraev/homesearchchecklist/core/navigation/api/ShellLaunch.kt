package com.antonchuraev.homesearchchecklist.core.navigation.api

import kotlinx.coroutines.flow.StateFlow

/**
 * Something the app shell must do once it is on screen, requested by a feature that cannot reach the
 * shell itself — the chat dock and the voice call live in App.kt, not behind a route.
 *
 * First requester: the onboarding's "Chat" and "Call" exits, which navigate to the main screen and
 * then need the dock opened (with an unsent prefill) or the call started on top of it.
 */
sealed interface ShellLaunch {
    /**
     * Open the AI chat. Null [prefill] opens the chat's normal empty state.
     *
     * [autoSend] = false: [prefill] goes into the composer and is NOT sent (the user confirms it there).
     * [autoSend] = true: [prefill] is sent at once through the chat's regular send path (credits, the
     * out-of-credits reply, analytics) — for a requester whose own screen already had a Send the user
     * pressed (onboarding Talk, owner decision 2026-11-17), where a second confirm would be the defect.
     * Ignored when [prefill] is null.
     */
    data class OpenChat(val prefill: String? = null, val autoSend: Boolean = false) : ShellLaunch

    /** Start the voice call through the same entry as the Ask-pill call button. */
    data object StartVoiceCall : ShellLaunch
}

/**
 * A sticky one-slot mailbox between a feature and the app shell.
 *
 * Sticky (a StateFlow), not a replay-0 event like [AppNavEvent]: the request is made in the same
 * frame as the navigation that mounts the shell, so a one-shot event would be emitted before the
 * shell's collector exists and be lost. The shell consumes the value once it has acted on it.
 *
 * A separate interface rather than more [AppNavigator] members so the ~27 hand-written navigator
 * fakes in feature tests stay untouched. In-memory only: after process death nothing is pending,
 * and the user simply lands on the main screen (onboarding is already complete by then).
 */
interface ShellLaunchRequests {
    val pending: StateFlow<ShellLaunch?>

    /** Replaces any pending request (last writer wins — only one exit can happen). */
    fun request(launch: ShellLaunch)

    /** Clears [launch] if it is still the pending one; a newer request is left in place. */
    fun consume(launch: ShellLaunch)
}
