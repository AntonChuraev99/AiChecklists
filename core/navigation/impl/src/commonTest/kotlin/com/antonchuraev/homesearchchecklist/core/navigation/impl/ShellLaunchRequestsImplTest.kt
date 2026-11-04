package com.antonchuraev.homesearchchecklist.core.navigation.impl

import com.antonchuraev.homesearchchecklist.core.navigation.api.ShellLaunch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ShellLaunchRequestsImplTest {

    @Test
    fun request_isStickyUntilConsumed() {
        val requests = ShellLaunchRequestsImpl()
        val launch = ShellLaunch.OpenChat(prefill = "Pack for a trip")

        requests.request(launch)

        assertEquals(launch, requests.pending.value)
        requests.consume(launch)
        assertNull(requests.pending.value)
    }

    @Test
    fun consume_ofAnOlderRequest_keepsTheNewerOne() {
        val requests = ShellLaunchRequestsImpl()
        val older = ShellLaunch.OpenChat(prefill = null)
        requests.request(older)
        requests.request(ShellLaunch.StartVoiceCall)

        requests.consume(older)

        assertEquals(ShellLaunch.StartVoiceCall, requests.pending.value)
    }
}
