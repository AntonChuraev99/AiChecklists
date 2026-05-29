package com.antonchuraev.homesearchchecklist.navigation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NavLayoutPolicyTest {

    @Test
    fun shouldUseSinglePaneLayout_web_returnsTrue() {
        // Web must use single-pane: list fills the content area, detail replaces it.
        assertTrue(shouldUseSinglePaneLayout("web"))
    }

    @Test
    fun shouldUseSinglePaneLayout_android_returnsFalse() {
        // Android keeps list-detail two-pane on Medium/Expanded — must not change.
        assertFalse(shouldUseSinglePaneLayout("android"))
    }

    @Test
    fun shouldUseSinglePaneLayout_ios_returnsFalse() {
        // iOS keeps list-detail two-pane on Medium/Expanded — must not change.
        assertFalse(shouldUseSinglePaneLayout("ios"))
    }

    @Test
    fun shouldUseSinglePaneLayout_unknownPlatform_returnsFalse() {
        // Defensive default: anything that is not explicitly web keeps two-pane.
        assertFalse(shouldUseSinglePaneLayout("desktop"))
        assertFalse(shouldUseSinglePaneLayout(""))
        assertFalse(shouldUseSinglePaneLayout("Web")) // case-sensitive on purpose
    }
}
