package com.antonchuraev.homesearchchecklist.core.common.api

/**
 * Minimal analytics interface for cross-module access.
 * Android implementation uses Firebase Analytics.
 * iOS is a no-op stub.
 */
interface AnalyticsTracker {
    fun setUserId(userId: String)
    fun setUserProperties(properties: Map<String, Any>)

    /**
     * Publishes properties whose FIRST value must survive every later write
     * (Amplitude `Identify.setOnce`, Firebase has no equivalent — plain `setUserProperty` there).
     *
     * Needed for install-scoped facts such as `install_date`: a plain `set` from a later app start
     * silently rewrites the cohort a user belongs to, and nothing in the resulting report reveals
     * that the date moved.
     *
     * SEAM ONLY — declared so the once-semantics is expressible; the default keeps every existing
     * implementation compiling and at least publishing the value. Platform implementations that
     * have a real set-once primitive MUST override it (see the handoff contract).
     */
    fun setUserPropertiesOnce(properties: Map<String, Any>) = setUserProperties(properties)

    fun screenView(name: String)
    fun event(name: String, params: Map<String, Any> = emptyMap())

    /**
     * Emits [event] WITHOUT opening or extending an analytics session.
     *
     * Use this — and not [event] — from any code path that can execute in a process with **no
     * Activity**: FCM handlers, AlarmManager receivers, WorkManager jobs and the app-start
     * telemetry in `Application.onCreate` (which runs on every background process wake exactly as
     * it does on a launcher tap).
     *
     * Why it exists: a plain [event] arriving with no session open makes the analytics SDK mint one,
     * so every background wake stamped an empty `session_start`. That inflated `session_start` to
     * far above the users who actually reached a screen, and made every metric with
     * sessions in the denominator (activation, retention, feature penetration) undiagnosable.
     *
     * The decision is STRUCTURAL, not a runtime probe: pick by asking "can this line run without an
     * Activity?". Runtime background-detection was tried and disproven twice — see
     * `docs/todos/2026-07-29-amplitude-no-init-in-background-processes.md`.
     *
     * SEAM ONLY — the default keeps every implementation and test fake compiling and still
     * delivering the event. Platforms that HAVE background process wakeups (Android) MUST override
     * it; platforms that cannot be woken without a user present (web) correctly keep the default.
     */
    fun eventOutOfSession(name: String, params: Map<String, Any> = emptyMap()) = event(name, params)

    /**
     * [setUserProperties] without opening or extending a session — same rule and same reason as
     * [eventOutOfSession]. A user-property write is an `$identify` event on the wire, so it mints a
     * phantom session just as readily as a named event does.
     */
    fun setUserPropertiesOutOfSession(properties: Map<String, Any>) = setUserProperties(properties)
}
