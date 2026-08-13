package com.antonchuraev.homesearchchecklist.desingsystem.components

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.source_a11y_ai
import aichecklists.core.designsystem.generated.resources.source_a11y_email
import aichecklists.core.designsystem.generated.resources.source_a11y_messenger
import aichecklists.core.designsystem.generated.resources.source_a11y_webhook
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.theme.GistiSource
import com.antonchuraev.homesearchchecklist.desingsystem.theme.GistiSourceKind
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Sizing for [AppSourceIcon]. */
object AppSourceIconDefaults {

    /**
     * Default glyph size in a meta row.
     *
     * The same 14dp as [AppItemMetaChipDefaults.IconSize] so the source glyph optically lines up
     * with the icon inside the due chip next to it.
     */
    val Size: Dp = 14.dp
}

/**
 * Where a task came from, as a bare glyph in the meta row.
 *
 * ## A glyph, not a chip
 * A filled chip would roughly double the meta row's width and push the due chip off a 60dp task row.
 * The source is context, not a headline — it earns 14dp, not a container.
 *
 * ## Manual draws nothing
 * [GistiSourceKind.Manual] is the default and the overwhelming majority of tasks. A glyph on every
 * ordinary row is noise that makes the genuinely interesting sources *harder* to spot, so this
 * composable emits nothing at all for it — not a transparent icon, not a spacer.
 *
 * A task that arrived from a connected source otherwise looks exactly like any other task. That is
 * deliberate: a task rendered as foreign teaches the user not to trust it, and we created it on
 * their own instructions.
 *
 * ## Accessibility
 * With no label to lean on, the `contentDescription` carries the entire meaning, so it is a real
 * string resource rather than a decorative `null`. It is also the reason the strings live with the
 * component rather than with the [GistiSource] colour tokens — user-facing copy belongs where it can
 * be localized.
 *
 * @param kind Which source produced this task.
 * @param modifier Optional external modifier.
 * @param size Glyph size. Grows for the source cards on the connected-sources screen, where the
 *   icon is the card's identity rather than a footnote.
 */
@Composable
fun AppSourceIcon(
    kind: GistiSourceKind,
    modifier: Modifier = Modifier,
    size: Dp = AppSourceIconDefaults.Size,
) {
    val glyph = kind.glyph() ?: return
    val tint = GistiSource.tint(kind) ?: return

    Icon(
        imageVector = glyph.icon,
        contentDescription = stringResource(glyph.description),
        modifier = modifier.size(size),
        tint = tint,
    )
}

/** The icon and its spoken description, paired so the two can never be resolved for different kinds. */
@Immutable
private data class SourceGlyph(val icon: ImageVector, val description: StringResource)

private fun GistiSourceKind.glyph(): SourceGlyph? = when (this) {
    GistiSourceKind.Manual -> null
    GistiSourceKind.Ai -> SourceGlyph(Icons.Outlined.AutoAwesome, Res.string.source_a11y_ai)
    GistiSourceKind.Email -> SourceGlyph(Icons.Outlined.MailOutline, Res.string.source_a11y_email)
    GistiSourceKind.Webhook -> SourceGlyph(Icons.Outlined.Bolt, Res.string.source_a11y_webhook)
    GistiSourceKind.Messenger ->
        SourceGlyph(Icons.Outlined.ChatBubbleOutline, Res.string.source_a11y_messenger)
}
