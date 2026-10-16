package com.antonchuraev.homesearchchecklist.desingsystem.components

import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.AppChecklistHeaderCardDefaults
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDensity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure rules behind the redesign-B primitives — no Compose, no Robolectric. */
class SoftTactileRulesTest {

    @Test
    fun groupPositionOf_coversEveryRunShape() {
        assertEquals(GroupPosition.Single, groupPositionOf(0, 1))
        assertEquals(GroupPosition.Single, groupPositionOf(0, 0))
        assertEquals(GroupPosition.First, groupPositionOf(0, 2))
        assertEquals(GroupPosition.Last, groupPositionOf(1, 2))
        assertEquals(GroupPosition.First, groupPositionOf(0, 5))
        assertEquals(GroupPosition.Middle, groupPositionOf(1, 5))
        assertEquals(GroupPosition.Middle, groupPositionOf(3, 5))
        assertEquals(GroupPosition.Last, groupPositionOf(4, 5))
    }

    @Test
    fun dividerIsDrawnOnlyWhenARowFollows() {
        assertTrue(GroupPosition.First.hasDividerBelow())
        assertTrue(GroupPosition.Middle.hasDividerBelow())
        assertFalse(GroupPosition.Last.hasDividerBelow())
        assertFalse(GroupPosition.Single.hasDividerBelow())
    }

    @Test
    fun dividerInsetIsDerivedFromRowAnatomy() {
        assertEquals(52.dp, AppDensity.GroupDividerInsetStart)
    }

    @Test
    fun ringLabel_onlyForOneTo99_belowFontScale13() {
        assertFalse(AppProgressRingDefaults.showsLabel(total = 0, fontScale = 1f))
        assertTrue(AppProgressRingDefaults.showsLabel(total = 1, fontScale = 1f))
        assertTrue(AppProgressRingDefaults.showsLabel(total = 99, fontScale = 1.29f))
        assertFalse(AppProgressRingDefaults.showsLabel(total = 100, fontScale = 1f))
        assertFalse(AppProgressRingDefaults.showsLabel(total = 6, fontScale = 1.3f))
    }

    @Test
    fun headerTile_hiddenBelowNarrowWidthTimesFontScale() {
        // 360dp window − 2×16 gutters = 328dp card: tile shown at 1.0, hidden at 1.5.
        assertTrue(AppChecklistHeaderCardDefaults.showsTile(328.dp, 1f))
        assertFalse(AppChecklistHeaderCardDefaults.showsTile(328.dp, 1.5f))
        // 320dp window → 288dp card: hidden.
        assertFalse(AppChecklistHeaderCardDefaults.showsTile(288.dp, 1f))
        assertTrue(AppChecklistHeaderCardDefaults.showsTile(308.dp, 1f))
    }
}
