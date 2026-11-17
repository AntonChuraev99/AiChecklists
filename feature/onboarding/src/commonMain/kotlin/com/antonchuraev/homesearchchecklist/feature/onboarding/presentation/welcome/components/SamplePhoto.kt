package com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.welcome.components

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.onboarding_sample_badge
import aichecklists.core.designsystem.generated.resources.onboarding_sample_body
import aichecklists.core.designsystem.generated.resources.onboarding_sample_cta
import aichecklists.core.designsystem.generated.resources.onboarding_sample_image_a11y
import aichecklists.core.designsystem.generated.resources.onboarding_sample_note
import aichecklists.core.designsystem.generated.resources.onboarding_sample_note_1
import aichecklists.core.designsystem.generated.resources.onboarding_sample_note_2
import aichecklists.core.designsystem.generated.resources.onboarding_sample_note_3
import aichecklists.core.designsystem.generated.resources.onboarding_sample_note_4
import aichecklists.core.designsystem.generated.resources.onboarding_sample_note_5
import aichecklists.core.designsystem.generated.resources.onboarding_sample_note_6
import aichecklists.core.designsystem.generated.resources.onboarding_sample_note_struck
import aichecklists.core.designsystem.generated.resources.onboarding_sample_note_title
import aichecklists.core.designsystem.generated.resources.onboarding_sample_title
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButton
import com.antonchuraev.homesearchchecklist.desingsystem.components.GroupPosition
import com.antonchuraev.homesearchchecklist.desingsystem.components.groupSegment
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppShapes
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppSurface
import com.antonchuraev.homesearchchecklist.desingsystem.theme.LocalIsDarkTheme
import org.jetbrains.compose.resources.imageResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/**
 * Source rectangles of the sample photo, as FRACTIONS of the source (DESIGN_SPEC 2026-11-17 §3.3), so
 * they hold for any export size. The photo is a 2:3 portrait; the text block sits at ≈ x 8–70 %,
 * y 12–62 % in every locale.
 */
internal enum class SampleCrop(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    /** Photo card media, fontScale ≤ 1.3 (aspect 1024 : 800). */
    Band(0f, 0.098f, 1f, 0.618f),

    /** Photo card media above fontScale 1.3 — keeps the CTA on the first screen (aspect 1024 : 700). */
    BandTight(0f, 0.124f, 1f, 0.580f),

    /** The handwriting only: ResultCard thumbnail, Showcase Attach stage. */
    Text(0.078f, 0.117f, 0.703f, 0.612f),

    /** The whole print: the overlay's "Reading" frame. */
    Full(0f, 0f, 1f, 1f),
    ;

    /** Width / height of the crop on the 2:3 source. */
    val aspect: Float get() = (right - left) * SOURCE_W / ((bottom - top) * SOURCE_H)
}

/**
 * Test seam: a stand-in 2:3 bitmap for the sample photo. `null` (always, in the app) draws the real
 * localized resource. Screenshot tests provide a flat placeholder so the goldens that only check
 * layout do not each store a photo; the crop, dark dim, badge and overlay paths stay the same.
 */
internal val LocalSampleNoteImage = staticCompositionLocalOf<ImageBitmap?> { null }

/**
 * The sample note photo (localized: `drawable{,-ru,-hi}/onboarding_sample_note.webp`) cut to [crop]
 * with a painter crop — exact on both platforms, unlike `ContentScale.Crop` + alignment, which drifts
 * onto the desk between box shapes (DESIGN_SPEC pitfall 2). Dimmed to 0.9 in the dark theme.
 * Decorative: the host owns the semantics ([sampleNoteDescription]).
 */
@Composable
internal fun SamplePhoto(crop: SampleCrop, modifier: Modifier = Modifier) {
    val image = LocalSampleNoteImage.current ?: imageResource(Res.drawable.onboarding_sample_note)
    val painter = remember(image, crop) {
        val x = (crop.left * image.width).roundToInt()
        val y = (crop.top * image.height).roundToInt()
        val w = ((crop.right - crop.left) * image.width).roundToInt().coerceAtMost(image.width - x)
        val h = ((crop.bottom - crop.top) * image.height).roundToInt().coerceAtMost(image.height - y)
        BitmapPainter(image, IntOffset(x, y), IntSize(w, h))
    }
    Image(
        painter = painter,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        colorFilter = if (LocalIsDarkTheme.current) DarkDim else null,
        modifier = modifier,
    )
}

/** TalkBack text of the photo: the badge's "sample note" line plus what is written on it (Q5). */
@Composable
internal fun sampleNoteDescription(): String {
    val lines = listOf(
        stringResource(Res.string.onboarding_sample_note_title),
        stringResource(Res.string.onboarding_sample_note_1),
        stringResource(Res.string.onboarding_sample_note_2),
        stringResource(Res.string.onboarding_sample_note_3),
        stringResource(Res.string.onboarding_sample_note_4),
        stringResource(Res.string.onboarding_sample_note_5),
        stringResource(Res.string.onboarding_sample_note_struck),
        stringResource(Res.string.onboarding_sample_note_6),
    )
    return stringResource(Res.string.onboarding_sample_image_a11y) + ": " + lines.joinToString(", ")
}

/**
 * A primary band (vertical gradient 0 → [BAND_ALPHA] → 0, [BAND_HEIGHT] of the node's height) centred
 * at [position] (`0..1` of the node height, read in the draw phase only). Outside `0..1` nothing is
 * drawn.
 */
internal fun Modifier.scanBand(color: androidx.compose.ui.graphics.Color, position: () -> Float): Modifier =
    drawWithContent {
        drawContent()
        val p = position()
        if (p < 0f || p > 1f) return@drawWithContent
        val band = size.height * BAND_HEIGHT
        val top = size.height * p - band / 2
        drawRect(
            brush = Brush.verticalGradient(
                0f to color.copy(alpha = 0f),
                0.5f to color.copy(alpha = BAND_ALPHA),
                1f to color.copy(alpha = 0f),
                startY = top,
                endY = top + band,
            ),
            topLeft = Offset(0f, top),
            size = Size(size.width, band),
        )
    }

/**
 * The Attach screen's photo card (DESIGN_SPEC §3.3): the real photo of a handwritten note, its title,
 * and the screen's only filled button. Above fontScale 1.3 ([compact]) the media takes the tight band
 * and the body hides (its sense moves into the button's click label) so the CTA stays on the first
 * screen at RU × 2.0.
 */
@Composable
internal fun SamplePhotoCard(
    compact: Boolean,
    enabled: Boolean,
    loading: Boolean,
    onTry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val crop = if (compact) SampleCrop.BandTight else SampleCrop.Band
    val description = sampleNoteDescription()
    val body = stringResource(Res.string.onboarding_sample_body)
    Column(modifier.groupSegment(GroupPosition.Single)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(crop.aspect)
                .clearAndSetSemantics {
                    contentDescription = description
                    role = Role.Image
                },
        ) {
            SamplePhoto(crop, Modifier.fillMaxSize())
            SampleBadge(Modifier.align(Alignment.TopStart).padding(AppDimens.SpacingMd))
        }
        Column(
            Modifier.padding(
                start = AppDimens.SpacingLg,
                top = AppDimens.SpacingMd,
                end = AppDimens.SpacingLg,
                bottom = AppDimens.SpacingLg,
            ),
        ) {
            Text(
                text = stringResource(Res.string.onboarding_sample_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            if (!compact) {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(AppDimens.SpacingMd))
            AppButton(
                text = stringResource(Res.string.onboarding_sample_cta),
                onClick = onTry,
                enabled = enabled,
                loading = loading,
                icon = Icons.Filled.AutoAwesome,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (compact) Modifier.semantics { onClick(label = body, action = null) } else Modifier),
            )
        }
    }
}

/** Read-only data badge "Sample": fontScale-locked, on its own card fill (not on the photo). */
@Composable
internal fun SampleBadge(modifier: Modifier = Modifier) {
    FontScaleLocked {
        Text(
            text = stringResource(Res.string.onboarding_sample_badge),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = modifier
                .clip(AppShapes.extraSmall)
                .background(AppSurface.card())
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/** Decorative illustration content: text inside never scales with the user's fontScale. */
@Composable
internal fun FontScaleLocked(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1f), content = content)
}

/** The photo's text block, as fractions of the FULL photo height: where the overlay band sweeps. */
internal const val TEXT_BLOCK_TOP = 0.12f
internal const val TEXT_BLOCK_BOTTOM = 0.62f

/** Dark theme: brightness 0.9 so the oak does not glare against the dark page (SamplePhotoDarkDim). */
private val DarkDim = ColorFilter.colorMatrix(ColorMatrix().apply { setToScale(0.9f, 0.9f, 0.9f, 1f) })

private const val SOURCE_W = 2f
private const val SOURCE_H = 3f
private const val BAND_HEIGHT = 0.16f
private const val BAND_ALPHA = 0.38f
