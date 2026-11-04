package com.antonchuraev.homesearchchecklist.feature.home.presentation.detail

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The detail screen hides the dock and repads the list off ONE boolean — "is the keyboard open".
 * That boolean must follow the live ime inset. It used to be a `remember { derivedStateOf { } }` over
 * two Dp values captured at first composition: no snapshot state was read inside the derivation, so
 * the value froze at whatever the keyboard was doing when the screen first composed.
 *
 * The second test pins the perf half: while the keyboard animates through intermediate heights on
 * the same side of the threshold, the composable that reads the boolean must NOT recompose.
 *
 * Run:
 *   ./gradlew :feature:home:testAndroidHostTest --tests "*ImeOpenStateTest*"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImeOpenStateTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    /** A WindowInsets whose bottom is snapshot state — the same contract the real ime inset has. */
    private class FakeBottomInsets(initialPx: Int) : WindowInsets {
        val bottomPx = mutableIntStateOf(initialPx)
        override fun getLeft(density: Density, layoutDirection: LayoutDirection) = 0
        override fun getTop(density: Density) = 0
        override fun getRight(density: Density, layoutDirection: LayoutDirection) = 0
        override fun getBottom(density: Density) = bottomPx.intValue
    }

    @Test
    fun imeOpen_followsTheLiveImeInset_bothWays() {
        val ime = FakeBottomInsets(0)
        val nav = FakeBottomInsets(0)
        var observed: Boolean? = null
        composeTestRule.setContent {
            val open = rememberImeOpenState(ime = ime, navigationBars = nav).value
            SideEffect { observed = open }
        }
        composeTestRule.waitForIdle()
        assertEquals(false, observed, "keyboard down at first composition")

        ime.bottomPx.intValue = 900
        composeTestRule.waitForIdle()
        assertEquals(true, observed, "keyboard opened after first composition must flip imeOpen")

        ime.bottomPx.intValue = 0
        composeTestRule.waitForIdle()
        assertEquals(false, observed, "keyboard closed again must flip imeOpen back")
    }

    @Test
    fun imeOpen_doesNotRecomposeReaderOnEveryKeyboardFrame() {
        val ime = FakeBottomInsets(0)
        val nav = FakeBottomInsets(0)
        var compositions = 0
        var observed = false
        composeTestRule.setContent {
            val open = rememberImeOpenState(ime = ime, navigationBars = nav).value
            SideEffect {
                compositions++
                observed = open
            }
        }
        composeTestRule.waitForIdle()
        ime.bottomPx.intValue = 600
        composeTestRule.waitForIdle()
        assertTrue(observed)
        val afterFlip = compositions
        // Keyboard keeps animating above the threshold — the boolean does not change.
        listOf(650, 700, 750, 800, 900).forEach { px ->
            ime.bottomPx.intValue = px
            composeTestRule.waitForIdle()
        }
        assertEquals(afterFlip, compositions, "intermediate ime frames must not recompose the reader")
        ime.bottomPx.intValue = 0
        composeTestRule.waitForIdle()
        assertFalse(observed)
    }
}
