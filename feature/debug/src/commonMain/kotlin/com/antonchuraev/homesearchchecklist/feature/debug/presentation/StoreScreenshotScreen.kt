package com.antonchuraev.homesearchchecklist.feature.debug.presentation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens

// ─────────────────────────────────────────────────────────────────────────────
// Brand colors (hardcoded marketing constants — NOT in strings.xml, EN-only)
// ─────────────────────────────────────────────────────────────────────────────
private val BrandBlue = Color(0xFF2196F3)
private val BrandIndigo = Color(0xFF6366F1)
private val BrandPurple = Color(0xFFA855F7)
private val BrandText = Color(0xFF212121)
private val BrandTextSecondary = Color(0xFF757575)
private val BrandWhite = Color(0xFFFFFFFF)
private val BrandCheckBlue = Color(0xFF1976D2)

private val GradientHero = Brush.linearGradient(listOf(BrandBlue, BrandIndigo))
private val GradientPremium = Brush.linearGradient(listOf(BrandIndigo, BrandPurple))
private val BgCreate = Color(0xFFE3F2FD)
private val BgTemplates = Color(0xFFE8F5E9)
private val BgReminders = Color(0xFFFFE0B2)
private val BgWeekly = Color(0xFFE1BEE7)
private val BgExport = Color(0xFFECEFF1)

// ─────────────────────────────────────────────────────────────────────────────
// Root screen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StoreScreenshotScreen() {
    val pageCount = 8
    val pagerState = rememberPagerState(initialPage = 0) { pageCount }

    // Root Column is fullscreen — no inset padding so background/gradient covers
    // the status bar zone on every slide.
    Column(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { pageIndex ->
            when (pageIndex) {
                0 -> Slide1Hero(modifier = Modifier.fillMaxSize())
                1 -> Slide2Create(modifier = Modifier.fillMaxSize())
                2 -> Slide3Templates(modifier = Modifier.fillMaxSize())
                3 -> Slide4Fill(modifier = Modifier.fillMaxSize())
                4 -> Slide5Reminders(modifier = Modifier.fillMaxSize())
                5 -> Slide6Weekly(modifier = Modifier.fillMaxSize())
                6 -> Slide7Export(modifier = Modifier.fillMaxSize())
                7 -> Slide8Premium(modifier = Modifier.fillMaxSize())
                else -> Box(modifier = Modifier.fillMaxSize())
            }
        }

        // Page indicator dots — placed below pager, respects nav bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = AppDimens.SpacingXxl),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(pageCount) { index ->
                ScreenshotPageIndicator(
                    isSelected = index == pagerState.currentPage,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun ScreenshotPageIndicator(
    isSelected: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(if (isSelected) 10.dp else 8.dp)
            .clip(CircleShape)
            .background(
                if (isSelected) BrandBlue
                else Color(0xFF9E9E9E).copy(alpha = 0.5f)
            )
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared layout helpers
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Standard slide layout:
 *   top 10% → headline + sub
 *   center 65% → visual content
 *   bottom padding → breathing room
 */
@Composable
private fun SlideContainer(
    headline: String,
    sub: String,
    headlineColor: Color = BrandWhite,
    subColor: Color = BrandWhite.copy(alpha = 0.82f),
    background: @Composable (Modifier) -> Unit,
    content: @Composable () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Background layer
        background(Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = AppDimens.SpacingXxl)
                .padding(top = AppDimens.SpacingXxl, bottom = AppDimens.SpacingXl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Headline — above visual (competitor best practice)
            Text(
                text = headline,
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = headlineColor,
                textAlign = TextAlign.Center,
                lineHeight = 38.sp,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(AppDimens.SpacingMd))

            // Sub
            Text(
                text = sub,
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
                color = subColor,
                textAlign = TextAlign.Center,
                lineHeight = 21.sp,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(AppDimens.SpacingXl))

            // Visual area — 65% of remaining height
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                content()
            }
        }
    }
}

/**
 * Generic phone-frame mock: thin black bezel, 32dp corners, white bg.
 * Pass [useAspectRatio]=true (default) for slides that use fillMaxWidth — frame
 * keeps 9:18 proportions. Pass false when the caller uses fillMaxHeight to let
 * the frame fill available vertical space without re-constraining width.
 */
@Composable
private fun PhoneFrame(
    modifier: Modifier = Modifier,
    useAspectRatio: Boolean = true,
    content: @Composable () -> Unit
) {
    val frameModifier = if (useAspectRatio) {
        modifier.aspectRatio(9f / 18f)
    } else {
        modifier
    }
    Box(
        modifier = frameModifier
            .clip(RoundedCornerShape(28.dp))
            .border(2.dp, Color(0xFF1A1A1A), RoundedCornerShape(28.dp))
            .background(BrandWhite)
    ) {
        // Status bar strip
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
                .background(Color(0xFFF5F5F5))
        )
        // Content below status bar
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 16.dp)
        ) {
            content()
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Slide 1 — HERO
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun Slide1Hero(modifier: Modifier = Modifier) {
    SlideContainer(
        headline = "Any content.\nAny checklist.",
        sub = "Photo, PDF, voice, link — AI does the rest.",
        headlineColor = BrandWhite,
        subColor = BrandWhite.copy(alpha = 0.85f),
        background = { m ->
            Box(modifier = m.background(brush = GradientHero))
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingLg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left card — "real-world content" mock
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(0.88f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(BrandWhite.copy(alpha = 0.95f))
                    .padding(AppDimens.SpacingMd),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("🧾", fontSize = 28.sp)
                    Text(
                        "Receipt",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = BrandText
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    HeroReceiptLine("Eggs × 12")
                    HeroReceiptLine("Milk 1L")
                    HeroReceiptLine("Bread")
                    HeroReceiptLine("Butter")
                    HeroReceiptLine("Coffee beans")
                    HeroReceiptLine("Orange juice")
                }
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color(0xFFE0E0E0))
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Total: \$24.50",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = BrandText
                    )
                }
            }

            // Arrow with sparkle
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = BrandWhite,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text("→", fontSize = 22.sp, color = BrandWhite, fontWeight = FontWeight.Bold)
            }

            // Right card — phone checklist mock
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(0.88f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(BrandWhite.copy(alpha = 0.95f))
                    .padding(AppDimens.SpacingMd),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("🛒", fontSize = 24.sp)
                    Text(
                        "Grocery List",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = BrandText
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    HeroCheckItem("Eggs × 12", checked = true)
                    HeroCheckItem("Milk 1L", checked = true)
                    HeroCheckItem("Bread", checked = true)
                    HeroCheckItem("Butter", checked = false)
                    HeroCheckItem("Coffee beans", checked = false)
                    HeroCheckItem("Orange juice", checked = false)
                }
                // Progress hint at bottom
                Text(
                    "3 of 6 done",
                    fontSize = 9.sp,
                    color = BrandTextSecondary
                )
            }
        }
    }
}

@Composable
private fun HeroReceiptLine(text: String) {
    Text(
        text = text,
        fontSize = 10.sp,
        color = BrandTextSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun HeroCheckItem(text: String, checked: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(
            imageVector = if (checked) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (checked) BrandCheckBlue else Color(0xFFBDBDBD),
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = text,
            fontSize = 10.sp,
            color = if (checked) BrandTextSecondary else BrandText,
            textDecoration = if (checked) TextDecoration.LineThrough else TextDecoration.None,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Slide 2 — CREATE WITH AI
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun Slide2Create(modifier: Modifier = Modifier) {
    SlideContainer(
        headline = "Create with AI\nin seconds",
        sub = "Snap. Speak. Drop a link. Done.",
        headlineColor = BrandText,
        subColor = BrandTextSecondary,
        background = { m -> Box(modifier = m.background(BgCreate)) }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd)
        ) {
            // Floating input chips above the phone
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                InputChip(emoji = "📷", label = "Photo", modifier = Modifier.weight(1f))
                InputChip(emoji = "📄", label = "PDF", modifier = Modifier.weight(1f))
                InputChip(emoji = "✏", label = "Text", modifier = Modifier.weight(1f))
                InputChip(emoji = "🔗", label = "Link", modifier = Modifier.weight(1f))
                InputChip(emoji = "🎙", label = "Voice", modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(AppDimens.SpacingSm))

            // Phone frame with Analyze screen mock
            PhoneFrame(modifier = Modifier.fillMaxWidth(0.7f)) {
                AnalyzeScreenMock()
            }
        }
    }
}

@Composable
private fun InputChip(emoji: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(BrandWhite)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(emoji, fontSize = 18.sp)
        Text(
            label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            color = BrandText,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
private fun AnalyzeScreenMock() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        // Top bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "✨ Create Checklist",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = BrandText
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(0xFFE0E0E0))
        )
        Spacer(modifier = Modifier.height(4.dp))

        // Subtitle
        Text(
            "Choose how to create",
            fontSize = 9.sp,
            color = BrandTextSecondary,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(2.dp))

        // 5 input option cards
        listOf(
            Triple(Icons.Filled.CameraAlt, "Photo", "Take or upload a photo"),
            Triple(Icons.Filled.PictureAsPdf, "PDF", "Import a document"),
            Triple(Icons.Filled.Edit, "Text", "Paste or type content"),
            Triple(Icons.Filled.Link, "Link", "Enter a URL"),
            Triple(Icons.Filled.Mic, "Voice", "Speak your list"),
        ).forEach { (icon, title, desc) ->
            AnalyzeOptionRow(icon = icon, title = title, desc = desc)
        }
    }
}

@Composable
private fun AnalyzeOptionRow(icon: ImageVector, title: String, desc: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFF5F5F5))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(BrandBlue.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(13.dp))
        }
        Column {
            Text(title, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = BrandText)
            Text(desc, fontSize = 7.5.sp, color = BrandTextSecondary, maxLines = 1)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Slide 3 — TEMPLATES
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun Slide3Templates(modifier: Modifier = Modifier) {
    SlideContainer(
        headline = "Templates\nthat just work",
        sub = "10+ ready packs — cooking, study, travel.",
        headlineColor = BrandText,
        subColor = BrandTextSecondary,
        background = { m -> Box(modifier = m.background(BgTemplates)) }
    ) {
        PhoneFrame(
            modifier = Modifier.fillMaxHeight(0.92f),
            useAspectRatio = false
        ) {
            TemplatesScreenMock()
        }
    }
}

@Composable
private fun TemplatesScreenMock() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Top bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Choose a Template",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = BrandText
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(0xFFE0E0E0))
        )
        Spacer(modifier = Modifier.height(2.dp))

        // Category filter chips
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            FilterChipMock("All", selected = true)
            FilterChipMock("Food", selected = false)
            FilterChipMock("Study", selected = false)
            FilterChipMock("Travel", selected = false)
        }

        Spacer(modifier = Modifier.height(2.dp))

        // 3×2 template grid — fills available height
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TemplateCard("🍽", "Dinner\nParty", modifier = Modifier.weight(1f))
            TemplateCard("📚", "Study\nPlan", modifier = Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TemplateCard("📦", "Moving\nHouse", modifier = Modifier.weight(1f))
            TemplateCard("🥗", "Meal\nPrep", modifier = Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TemplateCard("✈", "Travel\nPack", modifier = Modifier.weight(1f))
            TemplateCard("🏋", "Workout\nPlan", modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun FilterChipMock(label: String, selected: Boolean) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) BrandBlue else Color(0xFFE0E0E0))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            label,
            fontSize = 8.sp,
            color = if (selected) BrandWhite else BrandTextSecondary,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun TemplateCard(emoji: String, title: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(BrandWhite)
            .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(12.dp))
            .padding(AppDimens.SpacingSm),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(emoji, fontSize = 20.sp)
        Text(
            title,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            color = BrandText,
            lineHeight = 12.sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(BrandBlue.copy(alpha = 0.10f))
                .padding(horizontal = 5.dp, vertical = 2.dp)
        ) {
            Text("Tap to use", fontSize = 7.5.sp, color = BrandBlue, fontWeight = FontWeight.Medium)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Slide 4 — FILL VIA AI (USP — highest visual effort)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun Slide4Fill(modifier: Modifier = Modifier) {
    SlideContainer(
        headline = "Auto-fill from\nnew content",
        sub = "Drop a photo — AI ticks the matching boxes.",
        headlineColor = BrandWhite,
        subColor = BrandWhite.copy(alpha = 0.85f),
        background = { m -> Box(modifier = m.background(brush = GradientPremium)) }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left — "apartment photo" stylized card
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(0.88f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFFE8EAF6))
                    .border(2.dp, BrandWhite.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    // "Photo" header strip
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF7986CB).copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🏠", fontSize = 28.sp)
                    }
                    Text(
                        "Apartment",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = BrandText
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    FillPhotoDetail("Kitchen", "Clean surfaces")
                    FillPhotoDetail("Windows", "Double glazed")
                    FillPhotoDetail("Plumbing", "No leaks")
                    FillPhotoDetail("Flooring", "Hardwood")
                    FillPhotoDetail("Electrical", "Safety check")
                    FillPhotoDetail("Roof", "No damage")
                }
                Text(
                    "6 details found",
                    fontSize = 8.sp,
                    color = BrandTextSecondary
                )
            }

            // Center arrow + sparkle
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = BrandWhite,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text("→", fontSize = 22.sp, color = BrandWhite, fontWeight = FontWeight.Bold)
            }

            // Right — phone frame with auto-filled checklist
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(0.88f)
                    .clip(RoundedCornerShape(20.dp))
                    .border(2.dp, Color(0xFF1A1A1A), RoundedCornerShape(20.dp))
                    .background(BrandWhite)
                    .padding(10.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        "🏠 Apartment Check",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = BrandText
                    )
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFE0E0E0)))
                    Spacer(modifier = Modifier.height(2.dp))
                    FillCheckItem("Kitchen", checked = true)
                    FillCheckItem("Windows", checked = true)
                    FillCheckItem("Plumbing", checked = true)
                    FillCheckItem("Flooring", checked = false)
                    FillCheckItem("Heating", checked = false)
                    FillCheckItem("Electrical", checked = false)
                }
                // AI badge at the bottom of the card
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(BrandIndigo.copy(alpha = 0.10f))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = BrandIndigo,
                        modifier = Modifier.size(9.dp)
                    )
                    Text(
                        "3 items filled by AI",
                        fontSize = 7.5.sp,
                        color = BrandIndigo,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun FillPhotoDetail(title: String, detail: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(BrandIndigo.copy(alpha = 0.6f))
        )
        Column {
            Text(title, fontSize = 8.sp, fontWeight = FontWeight.SemiBold, color = BrandText)
            Text(detail, fontSize = 7.sp, color = BrandTextSecondary, maxLines = 1)
        }
    }
}

@Composable
private fun FillCheckItem(text: String, checked: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(
            imageVector = if (checked) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (checked) BrandCheckBlue else Color(0xFFBDBDBD),
            modifier = Modifier.size(12.dp)
        )
        Text(
            text = text,
            fontSize = 9.sp,
            color = if (checked) BrandTextSecondary else BrandText,
            textDecoration = if (checked) TextDecoration.LineThrough else TextDecoration.None,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Slide 5 — REMINDERS
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun Slide5Reminders(modifier: Modifier = Modifier) {
    SlideContainer(
        headline = "Reminders that\nactually fire",
        sub = "Per-item, recurring, smart.",
        headlineColor = BrandText,
        subColor = BrandTextSecondary,
        background = { m -> Box(modifier = m.background(BgReminders)) }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd)
        ) {
            // Notification toast — visually above the phone frame, no overlap
            NotificationToastMock(
                title = "📋 Apartment Check",
                body = "Kitchen condition due now"
            )

            // Phone frame below the toast
            PhoneFrame(modifier = Modifier.fillMaxWidth(0.68f)) {
                ChecklistDetailWithReminderMock()
            }
        }
    }
}

@Composable
private fun ChecklistDetailWithReminderMock() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        // Top bar
        Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("🏠 Apartment Check", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = BrandText)
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFE0E0E0)))

        Spacer(modifier = Modifier.height(2.dp))

        // Checklist items — several have reminder chips
        ReminderCheckItem("Kitchen condition", checked = false, reminderLabel = "🔔 9:00")
        ReminderCheckItem("Windows checked", checked = true, reminderLabel = null)
        ReminderCheckItem("Plumbing OK", checked = true, reminderLabel = null)
        ReminderCheckItem("Check heating", checked = false, reminderLabel = "🔔 Daily")
        ReminderCheckItem("Electrical safety", checked = false, reminderLabel = null)
        ReminderCheckItem("Roof inspection", checked = false, reminderLabel = "🔔 Weekly")
        ReminderCheckItem("Water pressure", checked = true, reminderLabel = null)

        Spacer(modifier = Modifier.height(4.dp))
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFE0E0E0)))
        Spacer(modifier = Modifier.height(4.dp))

        // Reminder info strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFFFF3E0))
                .padding(7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.NotificationsActive,
                contentDescription = null,
                tint = Color(0xFFEF6C00),
                modifier = Modifier.size(14.dp)
            )
            Column {
                Text(
                    "Recurring reminder",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = BrandText
                )
                Text(
                    "Every day at 9:00",
                    fontSize = 7.sp,
                    color = BrandTextSecondary
                )
            }
        }
    }
}

@Composable
private fun ReminderCheckItem(text: String, checked: Boolean, reminderLabel: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = if (checked) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (checked) BrandCheckBlue else Color(0xFFBDBDBD),
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = text,
                fontSize = 9.sp,
                color = if (checked) BrandTextSecondary else BrandText,
                textDecoration = if (checked) TextDecoration.LineThrough else TextDecoration.None,
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
        }
        if (reminderLabel != null) {
            Box(
                modifier = Modifier
                    .padding(start = 18.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFFFFE0B2))
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            ) {
                Text(reminderLabel, fontSize = 7.5.sp, color = Color(0xFFEF6C00), fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun NotificationToastMock(title: String, body: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF212121).copy(alpha = 0.88f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Notifications,
            contentDescription = null,
            tint = BrandWhite,
            modifier = Modifier.size(18.dp)
        )
        Column {
            Text(
                title,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = BrandWhite,
                maxLines = 1
            )
            Text(
                body,
                fontSize = 9.sp,
                color = BrandWhite.copy(alpha = 0.75f),
                maxLines = 1
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Slide 6 — WEEKLY MODE
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun Slide6Weekly(modifier: Modifier = Modifier) {
    SlideContainer(
        headline = "Plan your week,\nday by day",
        sub = "Weekly mode for routines.",
        headlineColor = BrandText,
        subColor = BrandTextSecondary,
        background = { m -> Box(modifier = m.background(BgWeekly)) }
    ) {
        PhoneFrame(
            modifier = Modifier.fillMaxHeight(0.92f),
            useAspectRatio = false
        ) {
            WeeklyScreenMock()
        }
    }
}

@Composable
private fun WeeklyScreenMock() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(imageVector = Icons.Filled.CalendarMonth, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(14.dp))
            Text("My Week", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BrandText)
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFE0E0E0)))

        // Day sections
        WeekDaySection(
            day = "Mon",
            items = listOf("Standup" to true, "Code review" to true)
        )
        WeekDaySection(
            day = "Tue",
            items = listOf("Gym" to false, "Read 30 min" to false)
        )
        WeekDaySection(
            day = "Wed",
            items = listOf("Date night" to false)
        )
        WeekDaySection(
            day = "Thu",
            items = listOf("Submit report" to false, "Team lunch" to false)
        )
        WeekDaySection(
            day = "Fri",
            items = listOf("Project demo" to false)
        )
        WeekDaySection(
            day = "Sat",
            items = listOf("Brunch with friends" to false, "Hike" to false)
        )
        WeekDaySection(
            day = "Sun",
            items = listOf("Meal prep" to false, "Plan next week" to false)
        )
    }
}

@Composable
private fun WeekDaySection(day: String, items: List<Pair<String, Boolean>>) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        // Day header
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(BrandBlue.copy(alpha = 0.12f))
                .padding(horizontal = 7.dp, vertical = 2.dp)
        ) {
            Text(day, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = BrandBlue)
        }
        items.forEach { (text, checked) ->
            Row(
                modifier = Modifier.padding(start = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = if (checked) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (checked) BrandCheckBlue else Color(0xFFBDBDBD),
                    modifier = Modifier.size(11.dp)
                )
                Text(
                    text = text,
                    fontSize = 8.5.sp,
                    color = if (checked) BrandTextSecondary else BrandText,
                    textDecoration = if (checked) TextDecoration.LineThrough else TextDecoration.None,
                    maxLines = 1
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Slide 7 — EXPORT & SHARE
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun Slide7Export(modifier: Modifier = Modifier) {
    SlideContainer(
        headline = "Export. Share.\nKeep records.",
        sub = "Save as PDF or text.",
        headlineColor = BrandText,
        subColor = BrandTextSecondary,
        background = { m -> Box(modifier = m.background(BgExport)) }
    ) {
        // Phone frame inside a Box that constrains the badge positioning scope
        // to the frame width, not the full slide.
        // Using fillMaxWidth on outer Box so pager lays it out correctly,
        // then phone frame is centered at 70% width.
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            // Badge anchor Box — sized to phone frame width so TopEnd aligns correctly
            Box(
                modifier = Modifier.fillMaxWidth(0.70f)
            ) {
                PhoneFrame(modifier = Modifier.fillMaxWidth()) {
                    ExportScreenMock()
                }

                // PDF thumbnail badge — sits fully inside phone-frame, top-right.
                // y=56.dp puts it below the mock status bar (16dp) AND below
                // the "Share Checklist" title bar (~32dp), so it floats over
                // the checklist body without obscuring the header.
                PdfThumbnailMock(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-10).dp, y = 56.dp)
                )
            }
        }
    }
}

@Composable
private fun ExportScreenMock() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        // Top bar
        Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Share Checklist", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BrandText)
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFE0E0E0)))
        Spacer(modifier = Modifier.height(4.dp))

        // Checklist preview
        Text("📋 Project Review", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = BrandText)
        Spacer(modifier = Modifier.height(2.dp))
        listOf("Define scope ✓", "Gather feedback ✓", "Final revision", "Submit report").forEach { item ->
            val done = item.endsWith("✓")
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(start = 4.dp)
            ) {
                Icon(
                    imageVector = if (done) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (done) BrandCheckBlue else Color(0xFFBDBDBD),
                    modifier = Modifier.size(11.dp)
                )
                Text(
                    item.removeSuffix(" ✓"),
                    fontSize = 8.sp,
                    color = if (done) BrandTextSecondary else BrandText,
                    textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Format options
        Text("Export as:", fontSize = 8.sp, color = BrandTextSecondary)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ExportFormatButton("📄 PDF", selected = true, modifier = Modifier.weight(1f))
            ExportFormatButton("Aa Text", selected = false, modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Share button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(BrandBlue)
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Share,
                contentDescription = null,
                tint = BrandWhite,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Share", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = BrandWhite)
        }
        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
private fun ExportFormatButton(label: String, selected: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) BrandBlue.copy(alpha = 0.12f) else Color(0xFFF5F5F5))
            .border(
                1.dp,
                if (selected) BrandBlue else Color(0xFFE0E0E0),
                RoundedCornerShape(8.dp)
            )
            .padding(vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 8.5.sp,
            color = if (selected) BrandBlue else BrandTextSecondary,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun PdfThumbnailMock(modifier: Modifier = Modifier) {
    // Sized ~25% smaller than original to feel like a floating chip, not a banner
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(BrandWhite)
            .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.PictureAsPdf,
            contentDescription = null,
            tint = Color(0xFFE53935),
            modifier = Modifier.size(12.dp)
        )
        Column {
            Text("Project_Review.pdf", fontSize = 6.5.sp, fontWeight = FontWeight.SemiBold, color = BrandText)
            Text("2 pages", fontSize = 6.sp, color = BrandTextSecondary)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Slide 8 — PREMIUM
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun Slide8Premium(modifier: Modifier = Modifier) {
    SlideContainer(
        headline = "Go Premium ·\n3-day free trial",
        sub = "Unlimited AI · 300 credits/day · No ads.",
        headlineColor = BrandWhite,
        subColor = BrandWhite.copy(alpha = 0.85f),
        background = { m -> Box(modifier = m.background(brush = GradientPremium)) }
    ) {
        PhoneFrame(modifier = Modifier.fillMaxWidth(0.72f)) {
            PaywallScreenMock()
        }
    }
}

@Composable
private fun PaywallScreenMock() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 9.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        // Header
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Filled.WorkspacePremium,
                contentDescription = null,
                tint = Color(0xFFFFC107),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                "Gisti Premium",
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                color = BrandIndigo
            )
        }
        Text(
            "Unlock the full power of AI",
            fontSize = 8.sp,
            color = BrandTextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFE0E0E0)))

        // Feature rows
        PremiumFeatureRow("⚡", "300 AI requests per day")
        PremiumFeatureRow("✓", "Unlimited checklists")
        PremiumFeatureRow("↻", "Unlimited fills")
        PremiumFeatureRow("🔔", "Unlimited reminders")

        Spacer(modifier = Modifier.height(2.dp))
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFE0E0E0)))
        Spacer(modifier = Modifier.height(2.dp))

        // Plan cards
        PlanCard(
            period = "Yearly",
            price = "\$1.99/mo",
            detail = "billed \$23.99/year",
            badge = "BEST VALUE",
            selected = true
        )
        PlanCard(
            period = "Monthly",
            price = "\$3.99/mo",
            detail = "billed monthly",
            badge = null,
            selected = false
        )

        Spacer(modifier = Modifier.height(2.dp))

        // CTA button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.linearGradient(listOf(BrandIndigo, BrandPurple))
                )
                .padding(vertical = 9.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Start 3-day free trial",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = BrandWhite
            )
        }

        // Trial disclosure
        Text(
            "3-day free trial, then \$1.99/mo (yearly). Auto-renews. Cancel anytime.",
            fontSize = 6.5.sp,
            color = BrandTextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(2.dp))

        // Footer links
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Text("Restore", fontSize = 6.sp, color = BrandBlue)
            Text(" · ", fontSize = 6.sp, color = BrandTextSecondary)
            Text("Terms", fontSize = 6.sp, color = BrandBlue)
            Text(" · ", fontSize = 6.sp, color = BrandTextSecondary)
            Text("Privacy", fontSize = 6.sp, color = BrandBlue)
        }
    }
}

@Composable
private fun PremiumFeatureRow(emoji: String, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(BrandIndigo.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            Text(emoji, fontSize = 9.sp)
        }
        Text(
            text,
            fontSize = 8.5.sp,
            color = BrandText,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.weight(1f))
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = BrandIndigo,
            modifier = Modifier.size(11.dp)
        )
    }
}

@Composable
private fun PlanCard(
    period: String,
    price: String,
    detail: String,
    badge: String?,
    selected: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) BrandIndigo.copy(alpha = 0.08f) else Color(0xFFF5F5F5))
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) BrandIndigo else Color(0xFFE0E0E0),
                shape = RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 9.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    period,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) BrandIndigo else BrandText
                )
                if (badge != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(BrandIndigo)
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(badge, fontSize = 6.5.sp, color = BrandWhite, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Text(detail, fontSize = 7.sp, color = BrandTextSecondary)
        }
        Text(
            price,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (selected) BrandIndigo else BrandText
        )
    }
}
