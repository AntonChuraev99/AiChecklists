package com.antonchuraev.homesearchchecklist.desingsystem.components.gisti

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppChatColors
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppShapeTokens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppSurface
import com.antonchuraev.homesearchchecklist.desingsystem.theme.LocalIsDarkTheme

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
 *  - leading: a 48dp "+" target (36dp recessed circle) when [onAttachClick] is set, otherwise the
 *    36dp [SparkleTile]
 *  - label `bodyLarge` `onSurfaceVariant`, ONE line + ellipsis
 *  - trailing: mic (48dp target, neutral `onSurfaceVariant` glyph), then the call button — a 36dp
 *    `primary` circle (the same [AskPillDefaults.DiscSize] as the "+") with an `onPrimary`
 *    sound-wave glyph in a 48dp target
 *
 * @param placeholder Text shown when [value] is null.
 * @param onClick Invoked when the pill body is tapped (opens chat).
 * @param onMicClick Invoked when the mic icon is tapped (starts voice input); null hides the mic.
 * @param value If non-null, shown in place of the placeholder with full `onSurface` color.
 * @param micContentDescription Accessibility label for the mic button — pass the existing chat mic
 *   string; required whenever [onMicClick] is non-null.
 * @param style [AskGistiBarStyle.Card] or [AskGistiBarStyle.ChromePill].
 * @param onAttachClick ChromePill only: the leading "+" (attach a file). Null keeps the sparkle —
 *   never a "+" that does nothing. Ignored by [AskGistiBarStyle.Card].
 * @param attachContentDescription Accessibility label for "+"; required when [onAttachClick] is set.
 * @param onCallClick ChromePill only: the trailing filled call button. Null draws no call button.
 *   Ignored by [AskGistiBarStyle.Card].
 * @param callContentDescription Accessibility label for the call button; required when
 *   [onCallClick] is set.
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
    onAttachClick: (() -> Unit)? = null,
    attachContentDescription: String = "",
    onCallClick: (() -> Unit)? = null,
    callContentDescription: String = "",
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
            onAttachClick = onAttachClick,
            attachContentDescription = attachContentDescription,
            onCallClick = onCallClick,
            callContentDescription = callContentDescription,
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
    onAttachClick: (() -> Unit)?,
    attachContentDescription: String,
    onCallClick: (() -> Unit)?,
    callContentDescription: String,
) {
    val hasTrailingAction = onMicClick != null || onCallClick != null
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
            modifier = Modifier
                .fillMaxWidth()
                // The pill area is ONE button; "+", mic and call are sibling nodes with their own
                // role, so TalkBack reads "Attach a file, button", "Ask Gisti or add a task, button",
                // "Voice input, button", "Voice call with Gisti, button".
                .clickable(onClick = onClick, role = Role.Button)
                .padding(
                    // A 48dp "+" target already insets its 36dp circle by 6dp — the sparkle's inset.
                    start = if (onAttachClick != null) 0.dp else 6.dp,
                    end = if (hasTrailingAction) 0.dp else 16.dp,
                ),
        ) {
            if (onAttachClick != null) {
                GistiAttachButton(
                    onClick = onAttachClick,
                    contentDescription = attachContentDescription,
                )
            } else {
                // Control path (no attach entry wired): keep the brand mark so the leading edge
                // never goes empty. Part of the pill's own tap target.
                SparkleTile(size = AskPillDefaults.DiscSize, cornerRadius = AskPillDefaults.DiscSize / 2)
            }

            Text(
                text = value ?: placeholder,
                style = MaterialTheme.typography.bodyLarge,
                color = if (value != null) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                // ONE row, owner decision 2026-11-04 ("ограничение на 1 ряд высоту"). The pill is
                // an entry point, not a reading surface: at 320dp × large font RU the label ends in
                // an ellipsis instead of growing the bottom plate.
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp, vertical = 12.dp),
            )

            if (onMicClick != null) {
                PillIconButton(onClick = onMicClick, contentDescription = micContentDescription) {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = null, // carried by the button node
                        // Neutral, like the reference: the filled call circle is the one accent.
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(AppDimens.IconSizeMd),
                    )
                }
            }

            if (onCallClick != null) {
                GistiCallButton(onClick = onCallClick, contentDescription = callContentDescription)
            }
        }
    }
}

/**
 * The voice-call button of the Ask pill: a 36dp `primary` disc with an `onPrimary` sound-wave glyph
 * inside a 48dp target, announced as its own button.
 *
 * Public so every call entry draws the SAME control (owner, layout-debug 2026-11-16: «звонок можно
 * начать с главного экрана или в чате») — the Ask pill on the home plate and the chat composer
 * (`ChatInputRow`) both call this rather than each drawing its own disc.
 *
 * @param enabled false swaps the disc to the M3 disabled filled pair (`onSurface` 12% / 38%) and
 *   drops the click; the disc stays, so the composer does not change shape mid-recording.
 */
@Composable
fun GistiCallButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    PillIconButton(
        onClick = onClick,
        contentDescription = contentDescription,
        modifier = modifier,
        enabled = enabled,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                // The SAME disc as the "+" at the other end of the pill (owner,
                // layout-debug 2026-11-16: "эти 2 кнопки должны быть одинаковы" — the call
                // disc was 40dp against the 36dp "+" and read as sitting higher).
                .size(AskPillDefaults.DiscSize)
                // primary / onPrimary: the M3 pair built for ≥3:1 in both themes
                // (light #1565C0 / white, dark Blue80 / Blue10).
                .background(
                    if (enabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = DisabledContainerAlpha)
                    },
                    CircleShape,
                ),
        ) {
            Icon(
                imageVector = Icons.Filled.GraphicEq,
                contentDescription = null, // carried by the button node
                tint = if (enabled) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = DisabledGlyphAlpha)
                },
                modifier = Modifier.size(AskPillDefaults.CallGlyphSize),
            )
        }
    }
}

/** Sizing shared by the Ask pill's round controls and every "+" attach button that copies it. */
object AskPillDefaults {
    /** Touch target of every round control in the pill (and of [GistiAttachButton]). */
    val TouchTarget: Dp = 48.dp

    /** Visible disc of the "+", the call button and the control-path sparkle — ONE size for all. */
    val DiscSize: Dp = 36.dp

    /** The call glyph inside [DiscSize]; the "+" uses the standard 24dp icon. */
    val CallGlyphSize: Dp = 20.dp
}

/**
 * The "+" attach button of the Ask pill: a 36dp recessed disc with a "+" glyph inside a 48dp target,
 * announced as its own button.
 *
 * Public so every attach entry draws the SAME control (owner, layout-debug 2026-11-16: "кнопки
 * вложений должны везде совпадать") — the Ask pill on the home plate and the chat composer
 * (`ChatInputRow`) both call this rather than each drawing its own glyph.
 *
 * @param enabled false greys the glyph to the M3 disabled alpha and drops the click; the disc stays,
 *   so the composer does not change shape while a recording is in flight.
 */
@Composable
fun GistiAttachButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    PillIconButton(
        onClick = onClick,
        contentDescription = contentDescription,
        modifier = modifier,
        enabled = enabled,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(AskPillDefaults.DiscSize)
                // A step DOWN from the cream pill, carried by the fill alone. In dark `recessed()`
                // equals `bottomChromeRaised()` (the pill), so the top of the container ramp is the
                // only rung that still reads.
                .background(attachFill(), CircleShape),
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = null, // carried by the button node
                tint = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = DisabledGlyphAlpha)
                },
                modifier = Modifier.size(AppDimens.IconSizeMd),
            )
        }
    }
}

/** M3 disabled-content alpha. */
private const val DisabledGlyphAlpha = 0.38f

/** M3 disabled filled-container alpha. */
private const val DisabledContainerAlpha = 0.12f

/** 48dp touch target inside the pill, announced as its own button. */
@Composable
private fun PillIconButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .size(AskPillDefaults.TouchTarget)
            .semantics {
                role = Role.Button
                this.contentDescription = contentDescription
            },
        content = content,
    )
}

@Composable
@ReadOnlyComposable
private fun attachFill(): Color = if (LocalIsDarkTheme.current) {
    MaterialTheme.colorScheme.surfaceContainerHighest
} else {
    AppSurface.recessed()
}
