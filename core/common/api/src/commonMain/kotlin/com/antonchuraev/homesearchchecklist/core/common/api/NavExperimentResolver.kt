package com.antonchuraev.homesearchchecklist.core.common.api

/**
 * The navigation A/B arm this install belongs to.
 *
 * [CONTROL] is the current shell (drawer + bottom chat dock) and MUST stay byte-identical in
 * behaviour — it is the experiment's baseline, so any un-gated v2 behaviour leaking into it makes
 * the whole comparison unreadable. [V2] is the Todoist-style 4-tab shell.
 *
 * CONTROL is also the fail-safe: every failure mode (RC not fetched, DataStore unreadable,
 * unknown console value) resolves here, so a broken experiment degrades to today's product.
 */
enum class NavVariant { CONTROL, V2 }

/**
 * App-scoped, sticky holder for the navigation A/B arm (`RemoteConfigKeys.NAV_V2_ARM` on the
 * remoteconfig side).
 *
 * ## Why the arm is PERSISTED, unlike the push-timing arm
 * The push-timing arm is read live on every scheduling pass because a flip only changes when a
 * future push is delivered. This arm gates the ENTIRE navigation shell on every launch, including
 * launches that never await a Remote Config activation: `SplashViewModel` only awaits
 * `fetchAndActivate()` for users who have not passed onboarding, so an existing user routinely
 * reaches the shell with RC still un-activated. A live read would therefore render CONTROL on the
 * first launch after the update and V2 on the second — the user would see their navigation change
 * by itself, and the analysis would see one install in both arms. Persisting the first *assigned*
 * arm makes the assignment stable for the install's lifetime.
 *
 * ## Why "not assigned yet" is not the same as CONTROL
 * An empty RC value means the fetch has not landed, not that the user is in control. Persisting
 * that fallback would pin the whole installed base to control forever (the experiment would read
 * 100/0). So an unassigned read returns CONTROL but is neither cached, persisted nor mirrored to
 * the `nav_arm` user property, and [ensureResolved] re-attempts on the next call.
 *
 * All members are best-effort and never throw: navigation must render even if analytics or
 * DataStore are broken.
 */
interface NavExperimentResolver {
    /**
     * Arm cached for this process. Non-suspending so it is safe to read during composition
     * (the shell needs a value on the very first frame, before any coroutine can run).
     *
     * Returns [NavVariant.CONTROL] until [ensureResolved] has succeeded with a non-empty source —
     * i.e. the safe arm is what renders while resolution is still in flight.
     */
    fun currentArm(): NavVariant

    /**
     * Resolves the arm (per-process cache -> persisted value -> Remote Config), caches it,
     * persists it and mirrors it once into the sticky `nav_arm` user property.
     *
     * Idempotent and never throws on failure (coroutine cancellation still propagates, as it must).
     * Short-circuits as soon as an arm is known, so callers may invoke it on every navigation
     * change to re-attempt while RC has not yet returned an arm.
     */
    suspend fun ensureResolved(): NavVariant

    /**
     * True once an arm has actually been **assigned** — restored from the persisted value or returned
     * by Remote Config. False while resolution has not landed, i.e. when [currentArm] /
     * [ensureResolved] are handing out the [NavVariant.CONTROL] fail-safe rather than a real arm.
     *
     * Exists because [NavVariant] alone cannot express the difference, and two callers genuinely need
     * it:
     *  * Anything DESTRUCTIVE gated on "the user is in control" — de-flagging a system Inbox, say.
     *    Acting on the fallback would mutate the data of a v2 user whose Remote Config simply had not
     *    activated on that launch.
     *  * Arm-exposure analytics, which must not count the not-yet-assigned population inside the
     *    control baseline: they carry no `nav_arm` user property by design and are not in the
     *    experiment.
     *
     * Call [ensureResolved] first — this is a non-suspending read of the same per-process state, so
     * on its own it only reports what resolution has already established.
     */
    fun isArmAssigned(): Boolean
}
