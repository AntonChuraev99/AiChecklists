package com.antonchuraev.homesearchchecklist.desingsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.TextAutoSize
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
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppShapeTokens

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
        modifier = modifier.height(AppDimens.ButtonHeight),
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
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(
                    MaterialTheme.typography.labelLarge.fontSize / 2,
                    MaterialTheme.typography.labelLarge.fontSize
                )
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
        modifier = modifier.height(AppDimens.ButtonHeight),
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
            style = MaterialTheme.typography.labelLarge
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
        modifier = modifier.height(AppDimens.ButtonHeight),
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
            style = MaterialTheme.typography.labelLarge
        )
    }
}
