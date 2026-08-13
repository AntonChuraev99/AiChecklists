package com.antonchuraev.homesearchchecklist.desingsystem.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * The app's corner scale, recut one step rounder than the M3 default (4/8/12/16/24).
 *
 * The scale had drifted apart from the product: a 12dp card next to an 8dp text field reads as two
 * components from different kits, and several sites had already hardcoded their way around it. Every
 * step moves up together so the relationships stay intact.
 *
 * | Token | Was | Now | Applies to |
 * |---|---|---|---|
 * | `extraSmall` | 4 | **6** | meta chips, skeleton lines |
 * | `small` | 8 | **12** | `AppTextField`, `DropdownMenu`, inline rows |
 * | `medium` | 12 | **16** | cards, list rows — the product's main shape |
 * | `large` | 16 | **20** | FAB, AI proposal card |
 * | `extraLarge` | 24 | **28** | sheet top, dialog, focus card |
 *
 * ⚠️ `small` is the step that reaches furthest: it is the default shape of every `OutlinedTextField`
 * and `DropdownMenu` in the app, not just of sites that name it. Buttons deliberately do **not** ride
 * this token — see [AppShapeTokens.Button].
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * Shapes that carry a specific meaning and therefore do not belong on the generic [AppShapes] ladder.
 *
 * A token lands here when its value has to stay put while the ladder moves — otherwise a later recut
 * of `AppShapes` silently re-shapes it.
 */
object AppShapeTokens {

    /** Fully rounded: preset chips, the credits chip, badges. Percentage-based, so height-independent. */
    val Pill: CornerBasedShape = RoundedCornerShape(percent = 50)

    /**
     * Every `AppButton*`. **Not** `AppShapes.small` and **not** `full`.
     *
     * At the app's 48dp button height a fully rounded corner resolves to 24dp, and a full-width CTA
     * then reads as a web banner arguing with the 16dp cards around it. 14dp sits deliberately one
     * notch off the ladder — close enough to belong, distinct enough to read as a control.
     */
    val Button: CornerBasedShape = RoundedCornerShape(14.dp)

    /**
     * Top corners of anything that rises from the bottom edge — the capture dock, the inline chat
     * panel, `ModalBottomSheet`.
     *
     * One token because these surfaces are seen in succession and previously disagreed (the dock was
     * 20dp while the panel was 28dp), which read as two different bottom sheets.
     */
    val SheetTop: CornerBasedShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
}
