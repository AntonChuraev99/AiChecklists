package com.antonchuraev.homesearchchecklist.desingsystem.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(AppDimens.CardPadding),
    content: @Composable () -> Unit
) {
    val shape = MaterialTheme.shapes.medium
    val containerModifier = modifier.fillMaxWidth()

    // Material 3 "filled + hairline" card style — flat tonal container, 1dp outline, no shadow in any
    // interaction state. All five tokens come from [AppCardDefaults] so this is the single source of
    // truth shared with every feature card that can't delegate to AppCard. See AppCardDefaults for the
    // rationale behind dropping the old elevated look.
    val colors = AppCardDefaults.colors()
    val border = AppCardDefaults.border()
    val elevation = AppCardDefaults.flatElevation()

    // Native Card.onClick is used only for tap-only cards: it carries correct button semantics and
    // its ripple is already clipped to [shape] by the Card. But Card.onClick can't carry a
    // long-press — so when [onLongClick] is supplied we drop the native onClick and apply
    // combinedClickable to the INNER content box (above contentPadding). The Card clips its content
    // slot to [shape], so the tap ripple stays inside the rounded form while the whole surface
    // (padding included) stays tappable. Putting clickable on the outer [modifier] would draw the
    // ripple OUTSIDE the clip, bleeding a rectangle past the rounded corners.
    val cardClick: (() -> Unit)? = if (onLongClick == null) onClick else null
    val contentBox: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .then(
                    if (cardClick == null && onClick != null) {
                        Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                    } else {
                        Modifier
                    }
                )
                .padding(contentPadding)
        ) {
            content()
        }
    }

    if (cardClick != null) {
        Card(
            onClick = cardClick,
            modifier = containerModifier,
            shape = shape,
            colors = colors,
            elevation = elevation,
            border = border,
        ) { contentBox() }
    } else {
        Card(
            modifier = containerModifier,
            shape = shape,
            colors = colors,
            elevation = elevation,
            border = border,
        ) { contentBox() }
    }
}
