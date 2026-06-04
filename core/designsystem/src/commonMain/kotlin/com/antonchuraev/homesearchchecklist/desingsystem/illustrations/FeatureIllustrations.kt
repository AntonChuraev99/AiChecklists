package com.antonchuraev.homesearchchecklist.desingsystem.illustrations

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.*
import com.antonchuraev.homesearchchecklist.desingsystem.emoji.LocalEmojiFont
import com.antonchuraev.homesearchchecklist.desingsystem.emoji.rememberEmojiAwareText
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Shared illustrations for onboarding and paywall screens.
 * Located in designsystem to avoid circular dependencies between feature modules.
 *
 * Brand-critical surfaces (gradient backgrounds, hero UI) use hardcoded colors to prevent
 * Material You dynamic color from overriding brand identity on API 31+.
 */

// Brand colors — hardcoded to prevent Material You dynamic color drift on brand surfaces
private val BrandBlue = Color(0xFF2196F3)
private val BrandIndigo = Color(0xFF6366F1)
private val BrandPurple = Color(0xFFA855F7)
private val BrandText = Color(0xFF212121)
private val BrandTextSecondary = Color(0xFF757575)
private val BrandWhite = Color(0xFFFFFFFF)
private val BrandCheckBlue = Color(0xFF1976D2)
private val BrandBezel = Color(0xFF1A1A1A)
private val ScreenshotBg = Color(0xFFFEF7FF)
private val BgPrimaryContainer = Color(0xFFE3F2FD)
private val BgWarm = Color(0xFFFFE0B2)
private val SurfaceLight = Color(0xFFF5F5F5)
private val Outline = Color(0xFFE0E0E0)
private val GradientHero = Brush.linearGradient(
    colorStops = arrayOf(0f to BrandBlue, 1f to BrandIndigo),
    start = Offset(0f, Float.POSITIVE_INFINITY),
    end = Offset(Float.POSITIVE_INFINITY, 0f)
)
private val GradientEverywhere = Brush.linearGradient(
    colorStops = arrayOf(0f to BrandBlue, 0.6f to BrandIndigo, 1f to BrandPurple),
    start = Offset(0f, Float.POSITIVE_INFINITY),
    end = Offset(Float.POSITIVE_INFINITY, 0f)
)

/**
 * Page 1: AI Chat Hero
 * Gradient background (brand-critical) + phone frame mockup with chat bubbles.
 * The illustration itself IS the gradient — fills the 24dp-rounded illustration box.
 */
@Composable
fun AiChatHeroIllustration() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GradientHero),
        contentAlignment = Alignment.Center
    ) {
        // Soft blur blobs via semi-transparent circles
        Box(
            modifier = Modifier
                .size(140.dp)
                .align(Alignment.TopEnd)
                .offset(x = 20.dp, y = (-20).dp)
                .background(BrandWhite.copy(alpha = 0.15f), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(120.dp)
                .align(Alignment.BottomStart)
                .offset(x = (-16).dp, y = 16.dp)
                .background(BrandPurple.copy(alpha = 0.35f), CircleShape)
        )

        // Phone frame with real screenshot
        PhoneFrameIllustration(
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .fillMaxHeight(0.90f)
        ) {
            Image(
                painter = painterResource(Res.drawable.ob_screen_1_chat),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                alignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        }
    }
}

/**
 * Page 2: Create from Anything
 * Light blue gradient background + 5 input chips + phone frame with generated checklist.
 */
@Composable
fun CreateFromAnythingIllustration() {
    val bgGradient = Brush.verticalGradient(
        colorStops = arrayOf(
            0f to BgPrimaryContainer,
            1f to BgPrimaryContainer.copy(alpha = 0.4f)
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradient)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 5 input chips row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
        ) {
            InputChip(icon = "📷", label = "Photo")
            InputChip(icon = "📄", label = "PDF")
            InputChip(icon = "✏️", label = "Text")
            InputChip(icon = "🔗", label = "Link")
            InputChip(icon = "🎙️", label = "Voice")
        }

        // Phone frame with real screenshot
        PhoneFrameIllustration(
            modifier = Modifier
                .fillMaxWidth(0.70f)
                .weight(1f)
        ) {
            Image(
                painter = painterResource(Res.drawable.ob_screen_2_checklist),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                alignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        }
    }
}

/**
 * Page 3: Reminders & Calendar
 * Warm orange background + notification toast + phone frame with mini calendar.
 */
@Composable
fun RemindersCalendarIllustration() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgWarm.copy(alpha = 0.55f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Notification toast
        Row(
            modifier = Modifier
                .fillMaxWidth(0.90f)
                .clip(RoundedCornerShape(14.dp))
                .background(BrandText.copy(alpha = 0.92f))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Bell icon in circle
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(BrandWhite.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Notifications,
                    contentDescription = null,
                    tint = BrandWhite,
                    modifier = Modifier.size(16.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Grocery List",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = BrandWhite
                )
                Text(
                    text = "Buy eggs — due now",
                    style = MaterialTheme.typography.labelSmall,
                    color = BrandWhite.copy(alpha = 0.75f)
                )
            }
            Text(
                text = "now",
                style = MaterialTheme.typography.labelSmall,
                color = BrandWhite.copy(alpha = 0.55f)
            )
        }

        // Phone frame with real calendar screenshot
        PhoneFrameIllustration(
            modifier = Modifier
                .fillMaxWidth(0.78f)
                .weight(1f)
        ) {
            Image(
                painter = painterResource(Res.drawable.ob_screen_3_calendar),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                alignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        }

        // Schedule chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
        ) {
            ScheduleChip("🔁 Daily")
            ScheduleChip("📅 Weekly")
            ScheduleChip("📌 Mon–Fri")
        }
    }
}

/**
 * Page 4: Works Everywhere
 * Gradient background (brand-critical) + browser window + phone frame overlapping.
 */
@Composable
fun WorksEverywhereIllustration() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GradientEverywhere),
        contentAlignment = Alignment.Center
    ) {
        // Soft blur blobs
        Box(
            modifier = Modifier
                .size(130.dp)
                .align(Alignment.TopStart)
                .offset(x = (-16).dp, y = (-16).dp)
                .background(BrandWhite.copy(alpha = 0.18f), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(140.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 16.dp, y = 16.dp)
                .background(BrandPurple.copy(alpha = 0.40f), CircleShape)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Single phone, centered
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                PhoneFrameIllustration(
                    modifier = Modifier
                        .fillMaxWidth(0.62f)
                        .fillMaxHeight(0.90f)
                ) {
                    Image(
                        painter = painterResource(Res.drawable.ob_screen_4_phone),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        alignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )
                }
            }

            // Sync pill
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(BrandWhite)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(GradientHero),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Done,
                        contentDescription = null,
                        tint = BrandWhite,
                        modifier = Modifier.size(12.dp)
                    )
                }
                Text(
                    text = "Synced to your account",
                    style = MaterialTheme.typography.labelSmall,
                    color = BrandText,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * Page 5: Paywall Preview
 * Shows premium benefits: Unlimited AI, Export, Credits.
 * Uses theme colors (non-brand surface, Material You OK here).
 */
@Composable
fun PremiumBenefitsIllustration() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Premium benefits list
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PremiumBenefitRow("✨", stringResource(Res.string.ob_benefit_unlimited_ai))
            PremiumBenefitRow("📅", stringResource(Res.string.ob_benefit_calendar))
            PremiumBenefitRow("🔔", stringResource(Res.string.ob_benefit_reminders))
            PremiumBenefitRow("⚡", stringResource(Res.string.ob_benefit_credits))
        }
    }
}

// ---------------------------------------------------------------------------
// Private shared helper composables
// ---------------------------------------------------------------------------

/**
 * Phone frame mockup — thin bezel, rounded corners, white interior.
 * Brand-critical: bezel color hardcoded (not theme-derived).
 */
@Composable
private fun PhoneFrameIllustration(
    modifier: Modifier = Modifier,
    backgroundColor: Color = ScreenshotBg,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .border(3.dp, BrandBezel, RoundedCornerShape(28.dp))
            .background(backgroundColor)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            content()
        }
    }
}

/**
 * Browser window mockup for WorksEverywhere illustration.
 */
@Composable
private fun BrowserWindow(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(2.dp, BrandBezel, RoundedCornerShape(12.dp))
            .background(BrandWhite)
    ) {
        // Chrome bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceLight)
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Traffic lights
            Box(modifier = Modifier.size(6.dp).background(Color(0xFFFF5F57), CircleShape))
            Box(modifier = Modifier.size(6.dp).background(Color(0xFFFFBD2E), CircleShape))
            Box(modifier = Modifier.size(6.dp).background(Color(0xFF28C840), CircleShape))
            Spacer(modifier = Modifier.width(4.dp))
            // URL bar
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(BrandWhite)
                    .border(0.5.dp, Outline, RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "checklists.gisti.app",
                    fontSize = 6.sp,
                    color = BrandTextSecondary
                )
            }
        }
        // Browser body — real screenshot (landscape image cropped to fill portrait frame)
        Image(
            painter = painterResource(Res.drawable.ob_screen_4_browser),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopStart,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun ChatBubbleUser(text: String) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterEnd
    ) {
        Box(
            modifier = Modifier
                .wrapContentWidth()
                .fillMaxWidth(0.80f)
                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 4.dp, bottomStart = 12.dp, bottomEnd = 12.dp))
                .background(BrandBlue)
                .padding(horizontal = 8.dp, vertical = 5.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = BrandWhite,
                fontSize = 8.sp
            )
        }
    }
}

@Composable
private fun ChatBubbleAssistant(text: String, actionText: String?) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterStart
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 12.dp, bottomStart = 12.dp, bottomEnd = 12.dp))
                .background(SurfaceLight)
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = BrandText,
                fontSize = 8.sp
            )
            if (actionText != null) {
                Text(
                    text = actionText,
                    style = MaterialTheme.typography.labelSmall,
                    color = BrandBlue,
                    fontWeight = FontWeight.Medium,
                    fontSize = 7.sp
                )
            }
        }
    }
}

@Composable
private fun InputChip(icon: String, label: String) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(BrandWhite)
            .border(0.5.dp, Outline, RoundedCornerShape(12.dp))
            .padding(horizontal = 6.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // Pure-emoji icon → emoji font (no system emoji fallback on wasmJs/Skiko).
        Text(text = icon, fontFamily = LocalEmojiFont.current, fontSize = 14.sp)
        Text(
            text = label,
            fontSize = 7.sp,
            color = BrandText,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ScheduleChip(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(BrandWhite)
            .border(0.5.dp, Outline, RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        // Mixed emoji + Latin ("🔁 Daily") → emoji font on the Text, Latin runs overridden to
        // FontFamily.Default so both resolve on wasmJs/Skiko.
        val emojiAware = rememberEmojiAwareText(text)
        Text(
            text = emojiAware.text,
            fontFamily = emojiAware.fontFamily,
            fontSize = 9.sp,
            color = BrandText,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun IllustrationChecklistItem(
    text: String,
    checked: Boolean,
    compact: Boolean = false
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp)
    ) {
        Icon(
            imageVector = if (checked) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
            contentDescription = null,
            modifier = Modifier.size(if (compact) 10.dp else 14.dp),
            tint = if (checked) BrandCheckBlue else Outline
        )
        Text(
            text = text,
            fontSize = if (compact) 7.sp else 9.sp,
            color = if (checked) BrandTextSecondary else BrandText
        )
    }
}

@Composable
private fun PremiumBenefitRow(emoji: String, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = emoji,
            fontFamily = LocalEmojiFont.current,
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
    }
}
