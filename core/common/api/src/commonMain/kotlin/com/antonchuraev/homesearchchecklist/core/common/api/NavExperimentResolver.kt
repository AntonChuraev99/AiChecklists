package com.antonchuraev.homesearchchecklist.core.common.api

/**
 * Which navigation shell renders.
 *
 * [V2] is the Todoist-style 4-tab shell (Inbox · Calendar · Projects · Overview) and is **the
 * product's navigation** since 2026-08-03. [CONTROL] is the previous shell (drawer + bottom chat
 * dock), kept as a user-selectable fallback while v2 is still unverified on web, tablet and
 * desktop.
 *
 * The names are historical — these used to be A/B arms. See
 * `docs/decisions/2026-08-03-shift-from-ai-first-to-checklist-first.md`.
 */
enum class NavVariant { CONTROL, V2 }

/**
 * App-scoped holder for the navigation choice, backed by a persisted user setting.
 *
 * ## Why it is resolved once per process rather than observed
 * The shell is gated on this value on every launch and on every navigation change. Observing it as
 * a flow would let a late write swap the whole navigation out from under a live screen — the user's
 * app would restructure itself mid-tap. Instead the value is read once, cached for the process, and
 * changed only through [setVariant] / [clearVariant], so every writer goes through one place and
 * the cache can never disagree with what is stored.
 *
 * ## What "not resolved yet" means
 * [currentArm] is non-suspending because the shell needs a value on the very first frame, before
 * any coroutine can run. Until [ensureResolved] lands it returns the DEFAULT (v2) — not v1 —
 * because falling back to the old shell would flash it on every cold start for every user who never
 * opened Settings.
 *
 * All members are best-effort and never throw: navigation must render even if DataStore or
 * analytics are broken.
 */
interface NavExperimentResolver {
    /**
     * Variant cached for this process. Non-suspending so it is safe to read during composition.
     *
     * Returns the default (v2) until [ensureResolved] has run.
     */
    fun currentArm(): NavVariant

    /**
     * Reads the stored choice (per-process cache -> DataStore), caches it and mirrors it once into
     * the sticky `nav_arm` user property.
     *
     * Idempotent and never throws on failure (coroutine cancellation still propagates, as it must).
     */
    suspend fun ensureResolved(): NavVariant

    /**
     * Persists a choice made in Settings and — only once the write has landed — updates the
     * per-process cache, so the shell switches on the next recomposition rather than on the next
     * launch.
     *
     * ## Failure contract: a write that does not land is DROPPED, not applied session-only
     * Like every member this never throws, so the outcome is reported the only other way there is:
     * [currentArm] does NOT advance. Callers learn whether the choice stuck by reading it back.
     *
     * ```kotlin
     * resolver.setVariant(requested)
     * val applied = resolver.currentArm() == requested   // false -> the write did not land
     * ```
     *
     * Every implementation and every test fake MUST honour this. Advancing the cache on a failed
     * write makes the failure invisible: the shell switches, the host re-roots its back stack for
     * it, and the next launch silently restores the old shell. Both live readers hang off the
     * read-back — the Settings switch (which reverts and explains itself) and App.kt's re-root.
     */
    suspend fun setVariant(variant: NavVariant)

    /**
     * Forgets the stored choice, so the install resolves to the default (v2) again.
     *
     * DEBUG/QA ONLY — there is no user-facing "reset layout" action. It exists because the debug
     * screen must be able to remove a forced variant, and clearing it by writing storage directly
     * would leave this resolver's cache pointing at the value it just deleted: two writers
     * disagreeing inside one process, with the Settings switch reading the stale one.
     *
     * Same failure contract as [setVariant]: a write that does not land leaves [currentArm]
     * unchanged.
     */
    suspend fun clearVariant()

    /**
     * True once the stored preference has been read this process — i.e. [currentArm] is reporting a
     * real choice rather than the pre-resolution default.
     *
     * Callers that must not act on the default (analytics attribution, anything destructive gated
     * on the variant) check this first. Call [ensureResolved] before relying on it: this is a
     * non-suspending read of the same per-process state.
     */
    fun isArmAssigned(): Boolean
}
