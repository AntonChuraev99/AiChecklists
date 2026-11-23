package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import coil3.ColorImage
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppSurface
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTheme
import com.antonchuraev.homesearchchecklist.desingsystem.theme.ChatSurfaceTone
import com.antonchuraev.homesearchchecklist.desingsystem.theme.LocalChatSurfaceTone
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatAttachment
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The pending-attachment chip's remove badge against every backdrop it can land on.
 *
 * The badge sits in the tile's corner, ON the thumbnail. A badge spelled as a bare grey glyph
 * vanished into photos (owner report 2026-11-23: «может слиться с картинкой»), so each frame stacks
 * the worst cases side by side: a near-white photo, a near-black photo, a mid-grey photo, a photo the
 * colour of the page itself, and the icon tiles (PDF / text / audio) — on the page plane and on the
 * bottom-chrome plane, light and dark.
 *
 * Coil decodes nothing under Robolectric, so each photo is a solid [ColorImage] painted through the
 * preview handler (it only fires with [LocalInspectionMode] on — see AiChatVariantsScreenshotTest).
 *
 *   ./gradlew :feature:aichat:impl:recordRoborazziAndroidHostTest --tests "*ChatAttachmentChipScreenshotTest*"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChatAttachmentChipScreenshotTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test fun removeBadge_light() = shoot(dark = false)

    @Test fun removeBadge_dark() = shoot(dark = true)

    @OptIn(ExperimentalCoilApi::class)
    private fun shoot(dark: Boolean) {
        composeTestRule.setContent {
            AppTheme(darkTheme = dark) {
                Column(modifier = Modifier.background(AppSurface.ground())) {
                    Plane(label = "PAGE", tone = ChatSurfaceTone.Page, background = { AppSurface.ground() })
                    Plane(
                        label = "BOTTOM CHROME",
                        tone = ChatSurfaceTone.BottomChrome,
                        background = { AppSurface.bottomChrome() },
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    @OptIn(ExperimentalCoilApi::class)
    @Composable
    private fun Plane(
        label: String,
        tone: ChatSurfaceTone,
        background: @Composable () -> androidx.compose.ui.graphics.Color,
    ) {
        CompositionLocalProvider(LocalChatSurfaceTone provides tone) {
            Column(
                modifier = Modifier
                    .background(background())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    photoArgbs.forEach { argb ->
                        CompositionLocalProvider(
                            LocalInspectionMode provides true,
                            LocalAsyncImagePreviewHandler provides AsyncImagePreviewHandler { ColorImage(argb) },
                        ) {
                            ChatAttachmentChip(attachment = photo, onRemove = {})
                        }
                    }
                }
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    iconAttachments.forEach { ChatAttachmentChip(attachment = it, onRemove = {}) }
                }
            }
        }
    }

    private companion object {
        val photo = ChatAttachment("content://photo", "image/jpeg", "photo.jpg", 120_000L)

        /** Near-white, near-black, mid-grey, and the light page's own sand — the four ways to blend. */
        val photoArgbs = listOf(
            0xFFFAFAFA.toInt(),
            0xFF111111.toInt(),
            0xFF808080.toInt(),
            0xFFF4EFE7.toInt(),
        )

        val iconAttachments = listOf(
            ChatAttachment("doc://recipe", "application/pdf", "Recipe.pdf", 20_480L),
            ChatAttachment("doc://notes", "text/plain", "notes.txt", 512L),
            ChatAttachment("audio://memo", "audio/mp4", "audio_65000ms_1.m4a", 64_000L),
        )
    }
}
