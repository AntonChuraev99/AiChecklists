package com.antonchuraev.homesearchchecklist.desingsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens

/**
 * [EmptyState] whose action slot is the standard placeholder CTA — a full-width [AppButton].
 *
 * ## Why this overload exists
 * Four placeholders across three screens want the same thing: "the one thing to do next, inside the
 * placeholder". Written out at each site it is the identical five-line block — a nullable
 * `@Composable` lambda wrapping an `AppButton(text, onClick, Modifier.fillMaxWidth())` — and each copy
 * grew its own paragraph re-arguing `fillMaxWidth`. That is the shape the repo's own rule is about: a
 * block copy-pasted into N sites drifts, and fixing one leaves the others wrong. One overload means
 * the next tweak to the CTA's width, spacing or type lands everywhere at once.
 *
 * `fillMaxWidth` and not hug-content: [EmptyState] already caps its own width with
 * `ScreenPaddingHorizontal`, and a hug-content button centred under a full-width centred paragraph
 * reads as a footnote rather than as the thing to press.
 *
 * @param actionLabel the button's text. Always resolved, even when [onAction] is null — a `when` that
 *   only reads the string in one branch is how a missing translation goes unnoticed.
 * @param onAction `null` renders NO action at all, i.e. exactly the plain [EmptyState]. That is the
 *   whole point of taking a nullable callback rather than a boolean: a caller that has no action to
 *   offer says so by passing null, and cannot accidentally draw a dead button.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    actionLabel: String,
    onAction: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    EmptyState(
        icon = icon,
        title = title,
        description = description,
        modifier = modifier,
        action = onAction?.let { click ->
            {
                AppButton(
                    text = actionLabel,
                    onClick = click,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = AppDimens.ScreenPaddingHorizontal),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Icon with circular background
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(44.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(AppDimens.SpacingXl))

        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(AppDimens.SpacingSm))

        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = AppDimens.SpacingLg)
        )

        if (action != null) {
            Spacer(modifier = Modifier.height(AppDimens.SpacingXl))
            action()
        }
    }
}
