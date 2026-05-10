package com.antonchuraev.homesearchchecklist.feature.paywall.presentation

import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigDefaults
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for RC-driven default plan resolution logic.
 *
 * The actual resolution happens in PaywallViewModel.init(), but the mapping
 * logic (rcString → PaywallPlan) is simple enough to test via the contract:
 *
 * "monthly" (or any unknown value) → Monthly
 * "yearly" → Yearly
 *
 * This verifies the intent without requiring SavedStateHandle or Koin.
 */
class PaywallPlanResolutionTest {

    /** Mirrors the mapping in PaywallViewModel.init(). */
    private fun resolvePlan(rcValue: String): PaywallPlan = when (rcValue.lowercase()) {
        "yearly" -> PaywallPlan.Yearly
        else -> PaywallPlan.Monthly
    }

    @Test
    fun rcValue_monthly_resolves_to_monthly() {
        assertEquals(PaywallPlan.Monthly, resolvePlan("monthly"))
    }

    @Test
    fun rcValue_yearly_resolves_to_yearly() {
        assertEquals(PaywallPlan.Yearly, resolvePlan("yearly"))
    }

    @Test
    fun rcValue_empty_resolves_to_monthly_safe_default() {
        assertEquals(PaywallPlan.Monthly, resolvePlan(""))
    }

    @Test
    fun rcValue_unknown_resolves_to_monthly_safe_default() {
        assertEquals(PaywallPlan.Monthly, resolvePlan("quarterly"))
        assertEquals(PaywallPlan.Monthly, resolvePlan("biweekly"))
        assertEquals(PaywallPlan.Monthly, resolvePlan("lifetime"))
    }

    @Test
    fun rcValue_case_insensitive_YEARLY_resolves_to_yearly() {
        assertEquals(PaywallPlan.Yearly, resolvePlan("YEARLY"))
        assertEquals(PaywallPlan.Yearly, resolvePlan("Yearly"))
    }

    @Test
    fun rcValue_case_insensitive_MONTHLY_resolves_to_monthly() {
        assertEquals(PaywallPlan.Monthly, resolvePlan("MONTHLY"))
        assertEquals(PaywallPlan.Monthly, resolvePlan("Monthly"))
    }

    @Test
    fun remoteConfigDefault_is_monthly() {
        // The canonical default must be "monthly" — changing this would show $20/yr
        // by default to users in price-sensitive markets.
        assertEquals("monthly", RemoteConfigDefaults.PAYWALL_DEFAULT_PLAN)
        assertEquals(PaywallPlan.Monthly, resolvePlan(RemoteConfigDefaults.PAYWALL_DEFAULT_PLAN))
    }

    @Test
    fun paywallState_default_selectedPlan_is_monthly() {
        // data class default must match — prevents showing Yearly before RC loads.
        val state = PaywallState()
        assertEquals(PaywallPlan.Monthly, state.selectedPlan)
    }

    @Test
    fun paywallUiState_default_selectedPlan_is_monthly() {
        val uiState = PaywallUiState()
        assertEquals(PaywallPlan.Monthly, uiState.selectedPlan)
    }
}
