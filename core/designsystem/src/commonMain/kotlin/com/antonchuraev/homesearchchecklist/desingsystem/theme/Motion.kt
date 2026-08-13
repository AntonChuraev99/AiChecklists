package com.antonchuraev.homesearchchecklist.desingsystem.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The app's motion language: **six spring tokens, one language on both targets**.
 *
 * Material 3 froze the "easing + duration" system in favour of a spring-physics one, and Compose
 * ships it as `MotionScheme` (wired into the theme in [AppTheme]). These tokens are the app-level
 * half of the same idea: the specs product code reaches for directly.
 *
 * **No Android/web fork.** M3 tells the *web* platform to convert springs back into cubic-bezier,
 * but that guidance is about CSS/DOM, where no spring primitive exists. Compose-on-wasm runs the very
 * same Kotlin [SpringSpec] over `Animatable` — a spring here is arithmetic, not a platform animator —
 * so a fork would only create drift. The cubic-bezier equivalents below exist for the two cases where
 * a spring is genuinely wrong: a CSS surface (the landing page) and layout animations where an
 * unsettled spring re-measures every frame (`expandVertically`, `animateContentSize` over tall
 * content) — see [spatialFastTween].
 *
 * The real wasm constraint is the frame budget, and it produces exactly one platform rule:
 * **[spatialSlow] drives a single large surface, never N list rows at once** — a soft spring taking a
 * long time to settle visibly judders on a loaded Skiko canvas.
 *
 * ## Invariant: effects never bounce
 * The three `effects*` tokens are critically damped — `dampingRatio` is exactly `1.0`. Alpha and
 * color overshooting means a value that goes past its target and comes back, i.e. a color flickering
 * to and fro, which reads as a render bug rather than as motion. Only `spatial*` may overshoot
 * (~1–2%), where it reads as weight.
 *
 * | Token | damping | stiffness | ≈settle | Drives |
 * |---|---|---|---|---|
 * | [spatialFast] | 0.90 | 1400 | ~180 ms | moves ≤48dp: a chip appearing, drag scale |
 * | [spatialDefault] | 0.85 | 700 | ~320 ms | row insert/remove, reorder, sheet, tab content |
 * | [spatialSlow] | 0.90 | 300 | ~520 ms | one large surface: list → detail |
 * | [effectsFast] | 1.00 | 1800 | ~140 ms | color/alpha on press |
 * | [effectsDefault] | 1.00 | 1000 | ~220 ms | fade in/out, chip color change |
 * | [effectsSlow] | 1.00 | 500 | ~400 ms | scrim, highlight, shimmer |
 *
 * ## Which accessor to use
 * The `val`s are typed `SpringSpec<Float>` — alpha, scale, progress, a float offset. Anything else
 * Compose animates (`Color`, `Dp`, `IntSize`, `IntOffset`) needs the same physics at a different type,
 * which a `Float`-typed constant cannot satisfy; use the `…As()` factory for those:
 *
 * ```kotlin
 * val alpha by animateFloatAsState(target, AppMotion.effectsDefault)
 * val color by animateColorAsState(target, AppMotion.effectsDefaultAs())
 * Modifier.animateContentSize(AppMotion.spatialFastAs())
 * ```
 */
object AppMotion {

    // ── Spring physics (single source of truth for both the specs and the factories) ──
    private const val SPATIAL_FAST_DAMPING = 0.90f
    private const val SPATIAL_FAST_STIFFNESS = 1400f
    private const val SPATIAL_DEFAULT_DAMPING = 0.85f
    private const val SPATIAL_DEFAULT_STIFFNESS = 700f
    private const val SPATIAL_SLOW_DAMPING = 0.90f
    private const val SPATIAL_SLOW_STIFFNESS = 300f

    /** Critically damped — the [AppMotion] invariant for every `effects*` token. */
    private const val EFFECTS_DAMPING = 1.00f
    private const val EFFECTS_FAST_STIFFNESS = 1800f
    private const val EFFECTS_DEFAULT_STIFFNESS = 1000f
    private const val EFFECTS_SLOW_STIFFNESS = 500f

    // ── Float specs — the common case ──

    /** Moves of 48dp or less: a chip appearing, the scale bump while dragging. */
    val spatialFast: SpringSpec<Float> = spring(SPATIAL_FAST_DAMPING, SPATIAL_FAST_STIFFNESS)

    /** Row insert/remove, reorder, sheet, tab content. The workhorse. */
    val spatialDefault: SpringSpec<Float> = spring(SPATIAL_DEFAULT_DAMPING, SPATIAL_DEFAULT_STIFFNESS)

    /** One large surface at a time (list → detail). ⛔ Never N list rows simultaneously on wasm. */
    val spatialSlow: SpringSpec<Float> = spring(SPATIAL_SLOW_DAMPING, SPATIAL_SLOW_STIFFNESS)

    /** Color/alpha on press. */
    val effectsFast: SpringSpec<Float> = spring(EFFECTS_DAMPING, EFFECTS_FAST_STIFFNESS)

    /** Fade in/out, chip color change. */
    val effectsDefault: SpringSpec<Float> = spring(EFFECTS_DAMPING, EFFECTS_DEFAULT_STIFFNESS)

    /** Scrim, highlight, shimmer. */
    val effectsSlow: SpringSpec<Float> = spring(EFFECTS_DAMPING, EFFECTS_SLOW_STIFFNESS)

    // ── Same physics at a non-Float type: Color, Dp, IntSize, IntOffset ──

    /** [spatialFast] for a non-`Float` animatable. */
    fun <T> spatialFastAs(): SpringSpec<T> = spring(SPATIAL_FAST_DAMPING, SPATIAL_FAST_STIFFNESS)

    /** [spatialDefault] for a non-`Float` animatable. */
    fun <T> spatialDefaultAs(): SpringSpec<T> = spring(SPATIAL_DEFAULT_DAMPING, SPATIAL_DEFAULT_STIFFNESS)

    /** [spatialSlow] for a non-`Float` animatable. */
    fun <T> spatialSlowAs(): SpringSpec<T> = spring(SPATIAL_SLOW_DAMPING, SPATIAL_SLOW_STIFFNESS)

    /** [effectsFast] for a non-`Float` animatable. */
    fun <T> effectsFastAs(): SpringSpec<T> = spring(EFFECTS_DAMPING, EFFECTS_FAST_STIFFNESS)

    /** [effectsDefault] for a non-`Float` animatable. */
    fun <T> effectsDefaultAs(): SpringSpec<T> = spring(EFFECTS_DAMPING, EFFECTS_DEFAULT_STIFFNESS)

    /** [effectsSlow] for a non-`Float` animatable. */
    fun <T> effectsSlowAs(): SpringSpec<T> = spring(EFFECTS_DAMPING, EFFECTS_SLOW_STIFFNESS)

    // ── Cubic-bezier equivalents ──

    /** Easing matching [spatialFast] / [spatialDefault]: decelerate, no overshoot. */
    val SpatialEasing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Easing matching [spatialSlow]: a long, soft settle for one large surface. */
    val SpatialSlowEasing: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /**
     * Easing for every `effects*` equivalent.
     *
     * Linear on purpose: it is the curve that carries no overshoot at all, which is the same
     * invariant the `dampingRatio = 1.0` springs express.
     */
    val EffectsEasing: Easing = LinearEasing

    const val SpatialFastMillis: Int = 180
    const val SpatialDefaultMillis: Int = 320
    const val SpatialSlowMillis: Int = 500
    const val EffectsFastMillis: Int = 140
    const val EffectsDefaultMillis: Int = 220
    const val EffectsSlowMillis: Int = 400

    /** Duration/easing equivalent of [spatialFast], for CSS parity or to avoid measure thrash. */
    fun <T> spatialFastTween(): TweenSpec<T> = tween(SpatialFastMillis, easing = SpatialEasing)

    /** Duration/easing equivalent of [spatialDefault]. */
    fun <T> spatialDefaultTween(): TweenSpec<T> = tween(SpatialDefaultMillis, easing = SpatialEasing)

    /** Duration/easing equivalent of [spatialSlow]. */
    fun <T> spatialSlowTween(): TweenSpec<T> = tween(SpatialSlowMillis, easing = SpatialSlowEasing)

    /** Duration/easing equivalent of [effectsFast]. */
    fun <T> effectsFastTween(): TweenSpec<T> = tween(EffectsFastMillis, easing = EffectsEasing)

    /** Duration/easing equivalent of [effectsDefault]. */
    fun <T> effectsDefaultTween(): TweenSpec<T> = tween(EffectsDefaultMillis, easing = EffectsEasing)

    /** Duration/easing equivalent of [effectsSlow]. */
    fun <T> effectsSlowTween(): TweenSpec<T> = tween(EffectsSlowMillis, easing = EffectsEasing)

    /** Duration every `effects*` token collapses to when [LocalReducedMotion] is on. */
    const val ReducedMotionMillis: Int = 120
}

/**
 * Whether the user asked the platform to reduce motion.
 *
 * ## Contract when `true`
 * - every `spatial*` spec becomes `snap()` — position changes land instantly;
 * - every `effects*` spec becomes `tween(`[AppMotion.ReducedMotionMillis]`)`. Fades are **shortened,
 *   not removed**: dropping them entirely makes a state change imperceptible, which is a regression
 *   in its own right, not an accessibility win;
 * - no `rememberInfiniteTransition` is **created at all**. The gate goes *before* the call, not
 *   inside the animation: a transition that exists still drives frames even if its output is ignored.
 *
 * Sources are platform-specific — Android `Settings.Global.ANIMATOR_DURATION_SCALE == 0f`, wasmJs
 * `window.matchMedia("(prefers-reduced-motion: reduce)")`.
 *
 * TODO(ux-overhaul): wire this to the platform. `expect fun isReducedMotionEnabled(): Boolean` is
 *  owned by @kmp-expert, its `actual`s by @android-platform-expert and @wasmjs-expert; [AppTheme]
 *  provides `false` until that lands, which keeps today's behaviour byte-for-byte.
 */
val LocalReducedMotion = staticCompositionLocalOf { false }
