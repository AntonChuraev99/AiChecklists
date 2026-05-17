package com.antonchuraev.homesearchchecklist.desingsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.credits_display
import aichecklists.core.designsystem.generated.resources.credits_get_more
import aichecklists.core.designsystem.generated.resources.credits_premium_unlimited
import org.jetbrains.compose.resources.stringResource
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens

/**
 * Shared credit-balance pill chip used across screens that display AI credits.
 *
 * ## Behaviour matrix
 *
 * | credits | isPremium | onClick | Visual |
 * |---------|-----------|---------|--------|
 * | > 0     | false     | any     | AutoAwesome + count (primaryContainer bg) |
 * | > 0     | true      | any     | AutoAwesome + ∞ (primaryContainer bg) |
 * | ≤ 0     | false     | non-null| "Get More" label (primary bg, onPrimary text) — clickable CTA |
 * | ≤ 0     | false     | null    | Hidden — caller is responsible for not rendering |
 * | ≤ 0     | true      | any     | AutoAwesome + ∞ (premium users never run out on a free tier) |
 *
 * ## Usage
 *
 * Interactive (MainScreen top bar, ChatHeader):
 * ```kotlin
 * AppCreditsChip(
 *     credits = state.aiCredits,
 *     isPremium = state.isPremium,
 *     onClick = { navigate(Paywall) },
 * )
 * ```
 *
 * Read-only (places where you only want the number shown, no navigate):
 * ```kotlin
 * AppCreditsChip(credits = balance, onClick = null)
 * ```
 *
 * ## Token mapping (MD3 Expressive)
 * - Normal: container = `primaryContainer`, content = `onPrimaryContainer` / icon = `primary`
 * - CTA "Get More": container = `primary`, content = `onPrimary`
 *
 * @param credits   Current AI credit balance. Negative values are treated as 0.
 * @param isPremium When true the chip always shows ∞ regardless of [credits].
 *                  Premium users have daily auto-refill — showing 0 would be misleading.
 * @param onClick   Navigation/action callback. `null` = chip has no ripple and is not announced
 *                  as a button by TalkBack (read-only display). Pass a lambda to make it tappable.
 * @param modifier  Standard Compose modifier.
 */
@Composable
fun AppCreditsChip(
    credits: Int,
    isPremium: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    // Premium users always show ∞; free users show numeric or "Get More" CTA
    val showCta = !isPremium && credits <= 0

    // Token selection — M3 tonal pairing rules:
    //   CTA state: primary / onPrimary (high-emphasis, draws the eye to the upsell)
    //   Normal state: primaryContainer / onPrimaryContainer (lower-emphasis badge)
    val containerColor = if (showCta) {
        MaterialTheme.colorScheme.primary           // md.sys.color.primary
    } else {
        MaterialTheme.colorScheme.primaryContainer  // md.sys.color.primary-container
    }

    val contentColor = if (showCta) {
        MaterialTheme.colorScheme.onPrimary         // md.sys.color.on-primary
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer // md.sys.color.on-primary-container
    }

    val iconTint = if (showCta) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.primary           // AutoAwesome uses primary on container bg
    }

    // Visual label — terse: bare digit or ∞ symbol. The chip's icon already
    // signals "AI credits"; repeating the word in text is redundant.
    val visualLabel = when {
        isPremium -> "∞"
        showCta   -> stringResource(Res.string.credits_get_more)
        else      -> credits.toString()
    }

    // Full accessibility label for TalkBack — keeps the noun ("5 credits" /
    // "Unlimited credits") so screen readers announce meaning, not bare digits.
    val a11yLabel = when {
        isPremium -> stringResource(Res.string.credits_premium_unlimited)
        showCta   -> stringResource(Res.string.credits_get_more)
        else      -> stringResource(Res.string.credits_display, credits)
    }

    val chipModifier = modifier
        .clip(RoundedCornerShape(16.dp))            // full-pill shape — MD3 shape.full
        .background(containerColor)
        .then(
            if (onClick != null) {
                Modifier
                    .clickable(onClick = onClick)
                    .semantics(mergeDescendants = true) {
                        role = Role.Button
                        contentDescription = a11yLabel
                    }
            } else {
                Modifier.semantics(mergeDescendants = true) {
                    contentDescription = a11yLabel
                }
            }
        )
        .padding(horizontal = AppDimens.SpacingMd, vertical = 6.dp)

    Row(
        modifier = chipModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingXs),
    ) {
        Icon(
            imageVector = Icons.Outlined.AutoAwesome,
            contentDescription = null, // decorative — label is the semantic content
            modifier = Modifier.size(AppDimens.IconSizeSm),
            tint = iconTint,
        )
        Text(
            text = visualLabel,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
        )
    }
}
