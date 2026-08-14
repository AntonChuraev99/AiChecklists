package com.antonchuraev.homesearchchecklist.desingsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppShapeTokens

/**
 * The primary, filled action.
 *
 * ## Why the label is never scaled down, and the button grows instead
 * This button used to pin itself to `Modifier.height(ButtonHeight)` and hand its label
 * `maxLines = 1` plus `TextAutoSize.StepBased(labelLarge.fontSize / 2, …)`. Those three constraints
 * cannot all hold at once, so the text lost: `Modifier.height` fixes min AND max, so the box could
 * not grow; `maxLines = 1` forbade a second line; and the only remaining degree of freedom was the
 * font, with a floor at **half** the type ramp — about 7sp on a 14sp `labelLarge`. Long labels
 * therefore shipped at an unreadable size on every sticky CTA in the app, which all route through
 * here (share, template preview, analyze preview, paywall). RU and HI at `fontScale 1.5` hit it
 * hardest because their labels are longest exactly where the user asked for bigger text.
 *
 * The fix is the one the repo already documents for chips — see `GistiPromptChips` ("heightIn, never
 * height … the chip is allowed to grow") and `SourceRow` ("MinTouchTarget as a MINIMUM, never a fixed
 * height"). [AppDimens.ButtonHeight] becomes a floor, the label keeps the full `labelLarge` ramp, and
 * a label too long for one line WRAPS to a second, centred.
 *
 * Two lines is the cap, not one: two lines fit a CTA, three mean the string is wrong. Ellipsis is the
 * overflow of last resort and is only reachable past two lines at extreme scale — a truncated CTA is
 * better than one that pushes the sheet off screen, but it should not be reachable on ordinary text.
 */
@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    shape: Shape = AppShapeTokens.Button,
    loading: Boolean = false,
) {
    Button(
        // While loading the button keeps its primary color (not greyed) so the spinner
        // reads as "processing", but taps are swallowed.
        onClick = { if (!loading) onClick() },
        modifier = modifier.heightIn(min = AppDimens.ButtonHeight),
        enabled = enabled,
        shape = shape,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(ButtonDefaults.IconSize),
                strokeWidth = 2.dp,
                color = LocalContentColor.current,
            )
        } else {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
                Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * The outlined, second-rank action.
 *
 * ## [accentColor] — why an outlined button needs to know what it is standing on
 * By default this button belongs to no single palette: its outline is `colorScheme.primary` (blue)
 * while M3 paints its label `onSurfaceVariant` (neutral grey). On the app's own page background
 * that reads fine, because grey-on-cream is the page's own text colour and the blue outline is the
 * app accent. Drop the very same button onto a **tonal container** and both halves become foreign
 * at once — on the Inbox error state's `errorContainer` it rendered a `#1565C0` outline and a
 * `#49454F` label on `#FFDAD6` pink, i.e. three unrelated palettes inside one 100dp control.
 *
 * [accentColor] is the fix, and it is opt-in rather than a new default on purpose: defaulting the
 * label to `primary` would repaint it on all eight existing call sites, none of which this change
 * has looked at. [Color.Unspecified] therefore means "leave today's rendering exactly as it is",
 * and a caller sitting on a tonal surface passes that surface's paired `on*Container` role so the
 * outline and the label both come from the block they are inside.
 *
 * ⛔ Not read from `LocalContentColor` automatically: [Surface] sets that to the container's
 * content colour, which sounds like the right source until a button placed on the plain page
 * background silently turns `onSurface` — i.e. every existing call site would change. The colour
 * is passed because the decision belongs to the call site, which is the thing that knows it is
 * inside a coloured block.
 *
 * @param accentColor outline AND label colour. [Color.Unspecified] keeps the default
 *   primary-outline / neutral-label pairing.
 */
@Composable
fun AppButtonSecondary(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    shape: Shape = AppShapeTokens.Button,
    accentColor: Color = Color.Unspecified,
) {
    OutlinedButton(
        onClick = onClick,
        // heightIn, never height — see [AppButton]'s KDoc. This one carries the app's longest CTA
        // strings (TemplatesScreen swaps "Create weekly" for "Unlock more with Premium" in the same
        // slot), so a fixed box clipped or shrank the label on the widest-label surface there is.
        modifier = modifier.heightIn(min = AppDimens.ButtonHeight),
        enabled = enabled,
        shape = shape,
        colors = if (accentColor.isSpecified) {
            ButtonDefaults.outlinedButtonColors(contentColor = accentColor)
        } else {
            ButtonDefaults.outlinedButtonColors()
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (accentColor.isSpecified) accentColor else MaterialTheme.colorScheme.primary,
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize)
            )
            Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun AppButtonText(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
fun AppButtonDestructive(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = AppShapeTokens.Button,
) {
    Button(
        onClick = onClick,
        // heightIn, never height — see [AppButton]'s KDoc.
        modifier = modifier.heightIn(min = AppDimens.ButtonHeight),
        enabled = enabled,
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
