package com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.welcome.components

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.back
import aichecklists.core.designsystem.generated.resources.onboarding_progress_a11y
import aichecklists.core.designsystem.generated.resources.onboarding_skip
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppShapeTokens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppShapes
import org.jetbrains.compose.resources.stringResource

/**
 * Shared chrome of both onboarding steps (DESIGN_SPEC §1.1): ← (step 2 only) · 2-segment progress ·
 * Skip. The progress sits in a WEIGHTED centre slot, not absolutely centred: at RU × fontScale 2.0
 * "Пропустить" is wide enough that an absolutely centred bar would touch it.
 */
@Composable
internal fun OnboardingTopBar(
    stepIndex: Int,
    stepCount: Int,
    showBack: Boolean,
    enabled: Boolean,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = AppDimens.MinTouchTarget)
            .padding(horizontal = AppDimens.SpacingXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showBack) {
            IconButton(onClick = onBack, enabled = enabled) {
                // Explicit tint: the onboarding root is a plain Box, not a Surface, so the inherited
                // content colour would be black on the dark page.
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(Res.string.back),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else DISABLED_ALPHA),
                )
            }
        } else {
            Spacer(Modifier.size(AppDimens.MinTouchTarget))
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            StepProgress(current = stepIndex, count = stepCount)
        }
        SkipButton(enabled = enabled, onClick = onSkip)
    }
}

/** Two 24×4dp segments; a segment is filled once its step is reached. One a11y node: "Step 1 of 2". */
@Composable
internal fun StepProgress(current: Int, count: Int, modifier: Modifier = Modifier) {
    val description = stringResource(Res.string.onboarding_progress_a11y, current, count)
    val active = MaterialTheme.colorScheme.primary
    val inactive = MaterialTheme.colorScheme.outline.copy(alpha = INACTIVE_ALPHA)
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(SegmentGap),
    ) {
        repeat(count) { i ->
            Box(
                Modifier
                    .size(SegmentWidth, SegmentHeight)
                    .clip(AppShapes.extraSmall)
                    .background(if (i < current) active else inactive),
            )
        }
    }
}

/**
 * "Skip ›" with a 48dp hit zone (the old row was 36dp). Its label is capped at [SKIP_FONT_SCALE_CAP]:
 * the bar has ~270dp for back + progress + Skip at 320dp, and an uncapped RU label at 2.0 eats the
 * progress slot whole.
 */
@Composable
private fun SkipButton(enabled: Boolean, onClick: () -> Unit) {
    val label = stringResource(Res.string.onboarding_skip)
    val color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else DISABLED_ALPHA)
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, density.fontScale.coerceAtMost(SKIP_FONT_SCALE_CAP)),
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = AppDimens.MinTouchTarget)
                .clip(AppShapeTokens.Pill)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(start = AppDimens.SpacingMd, end = AppDimens.SpacingXs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge, color = color, maxLines = 1)
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private val SegmentWidth = 24.dp
private val SegmentHeight = 4.dp
private val SegmentGap = 6.dp
private const val INACTIVE_ALPHA = 0.28f
private const val DISABLED_ALPHA = 0.38f
private const val SKIP_FONT_SCALE_CAP = 1.5f
