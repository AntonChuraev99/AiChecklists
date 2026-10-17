package com.antonchuraev.homesearchchecklist.desingsystem.components.gisti

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppChatColors
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppShapeTokens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppSurface

/** Visual variant of [AskGistiBar]. */
enum class AskGistiBarStyle {
    /** The original home-screen card bar: 56dp, 16dp corners, white card + 1.5dp hairline. */
    Card,

    /**
     * Redesign B "Ask pill" inside the v2 bottom plate: min 48dp, full pill, raised on the bottom
     * chrome ([AppSurface.bottomChromeRaised]) with a 1dp [AppChatColors.controlOutline] ring,
     * 36dp circular sparkle, 48dp mic target.
     */
    ChromePill,
}

/**
 * Persistent AI command bar.
 *
 * The entire bar (excluding the mic button) is one tap target → [onClick].
 * The mic icon is a separate touch target → [onMicClick]; pass `null` to render no mic at all (a mic
 * that does nothing is a dead affordance).
 *
 * [AskGistiBarStyle.Card] (default) — visual spec from gisti-extra.jsx AskGistiBar:
 *  - Height: 56dp, corner radius: 16dp
 *  - Background: `surfaceContainerLowest` (card)
 *  - Border: 1.5dp `outlineVariant`, no shadow
 *  - Left: [SparkleTile] 28dp
 *  - Center: placeholder in `onSurfaceVariant`, or value in `onSurface`
 *  - Right: [IconButton] 40dp with Mic icon in `onSurfaceVariant`
 *
 * [AskGistiBarStyle.ChromePill] — redesign B (the v2 shell's Ask pill):
 *  - min height 48dp, shape [AppShapeTokens.Pill]
 *  - container [AppSurface.bottomChromeRaised], 1dp [AppChatColors.controlOutline]
 *  - [SparkleTile] 36dp circle, label `bodyLarge` `onSurfaceVariant`, up to 2 lines + ellipsis
 *  - mic: 48dp target, `primary` glyph (the mock's blue mic)
 *
 * @param placeholder Text shown when [value] is null.
 * @param onClick Invoked when the non-mic area is tapped (opens chat).
 * @param onMicClick Invoked when the mic icon is tapped (starts voice input); null hides the mic.
 * @param value If non-null, shown in place of the placeholder with full `onSurface` color.
 * @param micContentDescription Accessibility label for the mic button — pass the existing chat mic
 *   string; required whenever [onMicClick] is non-null.
 * @param style [AskGistiBarStyle.Card] or [AskGistiBarStyle.ChromePill].
 */
@Composable
fun AskGistiBar(
    placeholder: String,
    onClick: () -> Unit,
    onMicClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    micContentDescription: String = "",
    value: String? = null,
    style: AskGistiBarStyle = AskGistiBarStyle.Card,
) {
    when (style) {
        AskGistiBarStyle.Card -> AskGistiCardBar(
            placeholder = placeholder,
            onClick = onClick,
            onMicClick = onMicClick,
            modifier = modifier,
            micContentDescription = micContentDescription,
            value = value,
        )
        AskGistiBarStyle.ChromePill -> AskGistiChromePill(
            placeholder = placeholder,
            onClick = onClick,
            onMicClick = onMicClick,
            modifier = modifier,
            micContentDescription = micContentDescription,
            value = value,
        )
    }
}

@Composable
private fun AskGistiCardBar(
    placeholder: String,
    onClick: () -> Unit,
    onMicClick: (() -> Unit)?,
    modifier: Modifier,
    micContentDescription: String,
    value: String?,
) {
    val shape = RoundedCornerShape(16.dp)

    // Flat input — no shadow (user request 2026-06-02). The 1.5dp outlineVariant
    // border alone separates the bar from the background, keeping the home screen calm.
    Surface(
        modifier = modifier
            .fillMaxWidth()
            // heightIn, never height — see AppButton's KDoc. The label here is a `bodyLarge`
            // placeholder or the user's own text, which at fontScale 1.5 does not fit 56dp.
            .heightIn(min = 56.dp),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd),
            modifier = Modifier
                // The clickable fills the row minus the mic button end zone.
                // We apply clickable on the Row and let the IconButton consume its own events.
                .fillMaxWidth()
                .clickable(
                    onClick = onClick,
                    role = Role.Button,
                )
                .padding(start = 14.dp, end = if (onMicClick != null) 4.dp else 14.dp),
        ) {
            SparkleTile(size = 28.dp)

            Text(
                text = value ?: placeholder,
                style = MaterialTheme.typography.bodyLarge,
                color = if (value != null) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )

            if (onMicClick != null) {
                IconButton(
                    onClick = onMicClick,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = micContentDescription,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(AppDimens.IconSizeMd),
                    )
                }
            }
        }
    }
}

@Composable
private fun AskGistiChromePill(
    placeholder: String,
    onClick: () -> Unit,
    onMicClick: (() -> Unit)?,
    modifier: Modifier,
    micContentDescription: String,
    value: String?,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        shape = AppShapeTokens.Pill,
        color = AppSurface.bottomChromeRaised(),
        border = BorderStroke(1.dp, AppChatColors.controlOutline()),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                // The pill area is ONE button; the mic below is a sibling node with its own role, so
                // TalkBack reads "Ask Gisti or add a task, button" then "Voice input, button".
                .clickable(onClick = onClick, role = Role.Button)
                .padding(start = 6.dp, end = if (onMicClick != null) 0.dp else 16.dp),
        ) {
            SparkleTile(size = 36.dp, cornerRadius = 18.dp)

            Text(
                text = value ?: placeholder,
                style = MaterialTheme.typography.bodyLarge,
                color = if (value != null) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                // Two lines, not one: measured on the 320dp × 1.5 RU golden, one line cut the label
                // to "Спросите Gi…" — the brand and the whole promise gone. The pill grows (heightIn)
                // and the shell plate measures itself, so a second line costs height, not meaning.
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 12.dp),
            )

            if (onMicClick != null) {
                IconButton(
                    onClick = onMicClick,
                    modifier = Modifier
                        .size(48.dp)
                        .semantics {
                            role = Role.Button
                            contentDescription = micContentDescription
                        },
                ) {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = null, // carried by the button node above
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(AppDimens.IconSizeMd),
                    )
                }
            }
        }
    }
}
