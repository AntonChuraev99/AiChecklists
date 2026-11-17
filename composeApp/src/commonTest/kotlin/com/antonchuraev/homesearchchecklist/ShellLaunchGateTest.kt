package com.antonchuraev.homesearchchecklist

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** [isShellStableForLaunch]: when App.kt may act on a pending onboarding Chat / Call hand-off. */
class ShellLaunchGateTest {

    @Test
    fun armUnresolved_neverStable() {
        assertFalse(isShellStableForLaunch(armResolved = false, showShell = true, showV2Shell = true, v2RootApplied = true, v2InboxRooted = true))
    }

    @Test
    fun v2_beforeInboxReRoot_isNotStable() {
        // First launch out of onboarding: the stack is [Main], the re-root has not run yet.
        assertFalse(isShellStableForLaunch(armResolved = true, showShell = true, showV2Shell = true, v2RootApplied = false, v2InboxRooted = false))
        // Re-root flag set but the Inbox is not (yet) in the stack.
        assertFalse(isShellStableForLaunch(armResolved = true, showShell = true, showV2Shell = true, v2RootApplied = true, v2InboxRooted = false))
    }

    @Test
    fun v2_afterInboxReRoot_isStable() {
        assertTrue(isShellStableForLaunch(armResolved = true, showShell = false, showV2Shell = true, v2RootApplied = true, v2InboxRooted = true))
    }

    @Test
    fun v1_mountedShell_isStable_regardlessOfV2Root() {
        assertTrue(isShellStableForLaunch(armResolved = true, showShell = true, showV2Shell = false, v2RootApplied = false, v2InboxRooted = false))
    }

    @Test
    fun noShellMounted_isNotStable() {
        assertFalse(isShellStableForLaunch(armResolved = true, showShell = false, showV2Shell = false, v2RootApplied = false, v2InboxRooted = false))
    }
}
