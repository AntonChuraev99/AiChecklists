package com.antonchuraev.homesearchchecklist.desingsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * Where a task came from.
 *
 * [Manual] is the default and the overwhelming majority, so it renders **nothing**: a glyph on every
 * ordinary task is noise that makes the genuinely interesting sources harder to spot. Its accessor
 * returns `null` rather than a transparent color, so a call site that forgets to handle it fails to
 * compile instead of drawing an invisible icon that still occupies width.
 */
enum class GistiSourceKind {
    /** Typed by the user. Draws no indicator. */
    Manual,

    /** Created by Gisti from a prompt, a file, or a conversation. */
    Ai,

    /** Arrived through the personal intake email address. */
    Email,

    /** Arrived through the incoming webhook. */
    Webhook,

    /** Arrived from a connected messenger. */
    Messenger,
}

/**
 * Source semantics — the color half of it.
 *
 * **Zero new hex:** every source maps onto an existing Material role, so the palette does not grow
 * and dark mode is handled by the scheme swap rather than by a second literal.
 *
 * | Source | Role | Light | Dark |
 * |---|---|---|---|
 * | [GistiSourceKind.Ai] | `primary` | `#1565C0` | `#90CAF9` |
 * | [GistiSourceKind.Email] | `secondary` | `#4A6572` | `#B0C8D4` |
 * | [GistiSourceKind.Webhook] | `tertiary` | `#006874` | `#4DD8E8` |
 * | [GistiSourceKind.Messenger] | `secondary` | `#4A6572` | `#B0C8D4` |
 *
 * ## Why the AI glyph is `primary`, not [GistiColors.aiStart]
 * The project's convention is vivid `#2196F3` for AI *surfaces* (gradients, sparkle tiles, the FAB)
 * and the darker `#1565C0` for chrome and text. A 14dp glyph in a meta row is chrome. It is also the
 * accessible choice: `#2196F3` on white is 3.12:1, right on the threshold, while `#1565C0` is 5.76:1.
 *
 * ## Rendering contract
 * A bare 14dp icon in the meta row, never a filled chip — a filled chip would roughly double the
 * meta row's width and push the due chip off a 60dp row. The icon and its `contentDescription` live
 * with the component, because the description is user-facing copy and has to come from a string
 * resource; this object stays purely about colour.
 *
 * Unlike [GistiSchedule] this object does not read [LocalIsDarkTheme]: with no literals of its own
 * there is nothing to switch, and `MaterialTheme.colorScheme` is already resolved from the same
 * theme flag by [AppTheme].
 */
object GistiSource {

    /**
     * Tint for the source glyph, or `null` for [GistiSourceKind.Manual], which draws nothing.
     */
    @Composable
    @ReadOnlyComposable
    fun tint(kind: GistiSourceKind): Color? = when (kind) {
        GistiSourceKind.Manual -> null
        GistiSourceKind.Ai -> MaterialTheme.colorScheme.primary
        GistiSourceKind.Email -> MaterialTheme.colorScheme.secondary
        GistiSourceKind.Webhook -> MaterialTheme.colorScheme.tertiary
        GistiSourceKind.Messenger -> MaterialTheme.colorScheme.secondary
    }
}
