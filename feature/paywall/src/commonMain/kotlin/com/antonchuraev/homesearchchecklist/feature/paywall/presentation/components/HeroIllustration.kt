package com.antonchuraev.homesearchchecklist.feature.paywall.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * HeroIllustration — abstract checklist + sparkle stack, used at the top of
 * the timeline / features paywall variants. Composed entirely from M3 tokens
 * so it adapts to dark mode + accent-hue swaps for free.
 */
@Composable
internal fun HeroIllustration(modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(140.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Halo — radial gradient sized to fit within parent (140dp) so the
        // gradient fades to transparent before hitting the parent edges. Old
        // 220×220dp halo was clipped top + bottom by the 140dp parent, leaving
        // a hard arc edge that read as a "weird coloured shape" behind the
        // card. Hardcoded to Material Blue 50 (#E3F2FD) — same brand colour as
        // the FeatureRow icon tiles for visual coherence (replaces the M3
        // primaryContainer token which on dynamic-colour builds drifted to
        // unexpected hues).
        Box(
            modifier = Modifier
                .width(190.dp)
                .height(120.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFE3F2FD).copy(alpha = 0.85f),
                            Color.Transparent,
                        ),
                        radius = 220f,
                    )
                )
        )

        // Front checklist card
        Surface(
            modifier = Modifier.size(width = 130.dp, height = 90.dp),
            shape = RoundedCornerShape(14.dp),
            color = cs.surface,
            border = BorderStroke(1.dp, cs.outlineVariant),
            shadowElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                repeat(3) { i ->
                    val checked = i < 2
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (checked) cs.primary else Color.Transparent)
                                .border(
                                    2.dp,
                                    if (checked) cs.primary else cs.outline,
                                    RoundedCornerShape(4.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (checked) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = cs.onPrimary,
                                    modifier = Modifier.size(10.dp),
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    if (checked) cs.outlineVariant.copy(alpha = 0.6f)
                                    else cs.surfaceVariant
                                )
                        )
                    }
                }
            }
        }

        // Sparkle pill
        Box(
            modifier = Modifier
                .offset(x = 70.dp, y = (-50).dp)
                .size(36.dp)
                .clip(CircleShape)
                .background(cs.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = cs.onPrimary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
