package com.antonchuraev.homesearchchecklist.feature.updatefeed.domain.deeplink

import androidx.navigation.NavController
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UpdateFeedDeepLinkHandlerTest {

    private class FakeNavigator : AppNavigator {
        val paywallCalls = mutableListOf<String>()
        var templatesCallCount = 0
        var analyzeCallCount = 0
        var createCallCount = 0
        var subscriptionStatusCallCount = 0
        var updateFeedCallCount = 0
        var backCallCount = 0

        override fun installNavController(navController: NavController) {}
        override fun onBack() { backCallCount++ }
        override fun navigateToOnboarding() {}
        override fun navigateToInteractiveOnboarding() {}
        override fun navigateToMainScreen(clearBackStack: Boolean) {}
        override fun navigateToDebugMenu() {}
        override fun navigateToStoreScreenshot() {}
        override fun navigateToCreateChecklistScreen(templateId: Int?) { createCallCount++ }
        override fun navigateToEditChecklist(checklistId: Long) {}
        override fun navigateToTemplatesScreen() { templatesCallCount++ }
        override fun navigateToTemplatePreview(templateId: String) {}
        override fun navigateToAnalyzeScreen(checklistId: Long?, fillDefault: Boolean) { analyzeCallCount++ }
        override fun navigateToAnalyzeResultPreview() {}
        override fun navigateToChecklistDetail(checklistId: Long, clearBackStack: Boolean) {}
        override fun navigateToFillDetail(fillId: Long, clearBackStack: Boolean) {}
        override fun navigateToFillsList(checklistId: Long) {}
        override fun navigateToPaywall(source: String) { paywallCalls.add(source) }
        override fun navigateToSubscriptionStatus(showSuccessMessage: Boolean) { subscriptionStatusCallCount++ }
        override fun navigateToShareChecklist(checklistId: Long) {}
        override fun navigateToUpdateFeed() { updateFeedCallCount++ }
    }

    private fun createHandler(): Pair<UpdateFeedDeepLinkHandler, FakeNavigator> {
        val nav = FakeNavigator()
        return UpdateFeedDeepLinkHandler(nav) to nav
    }

    @Test
    fun `handle_validPaywallUri_navigatesToPaywallWithSource`() {
        val (handler, nav) = createHandler()
        val result = handler.handle("gisti://paywall?source=test_source")
        assertTrue(result)
        assertEquals(1, nav.paywallCalls.size)
        assertEquals("test_source", nav.paywallCalls[0])
    }

    @Test
    fun `handle_paywallUriWithoutSource_usesDefaultSource`() {
        val (handler, nav) = createHandler()
        val result = handler.handle("gisti://paywall")
        assertTrue(result)
        assertEquals("update_feed", nav.paywallCalls[0])
    }

    @Test
    fun `handle_templatesUri_navigatesToTemplates`() {
        val (handler, nav) = createHandler()
        val result = handler.handle("gisti://templates")
        assertTrue(result)
        assertEquals(1, nav.templatesCallCount)
    }

    @Test
    fun `handle_analyzeUri_navigatesToAnalyze`() {
        val (handler, nav) = createHandler()
        val result = handler.handle("gisti://analyze")
        assertTrue(result)
        assertEquals(1, nav.analyzeCallCount)
    }

    @Test
    fun `handle_createUri_navigatesToCreate`() {
        val (handler, nav) = createHandler()
        val result = handler.handle("gisti://create")
        assertTrue(result)
        assertEquals(1, nav.createCallCount)
    }

    @Test
    fun `handle_subscriptionStatusUri_navigatesToSubscriptionStatus`() {
        val (handler, nav) = createHandler()
        val result = handler.handle("gisti://subscription_status")
        assertTrue(result)
        assertEquals(1, nav.subscriptionStatusCallCount)
    }

    @Test
    fun `handle_updateFeedUri_navigatesToUpdateFeed`() {
        val (handler, nav) = createHandler()
        val result = handler.handle("gisti://update_feed")
        assertTrue(result)
        assertEquals(1, nav.updateFeedCallCount)
    }

    @Test
    fun `handle_unknownHost_returnsFalse`() {
        val (handler, nav) = createHandler()
        val result = handler.handle("gisti://unknown_route")
        assertFalse(result)
        assertEquals(0, nav.paywallCalls.size)
        assertEquals(0, nav.templatesCallCount)
    }

    @Test
    fun `handle_nullDeepLink_returnsFalse`() {
        val (handler, nav) = createHandler()
        val result = handler.handle(null)
        assertFalse(result)
    }

    @Test
    fun `handle_emptyDeepLink_returnsFalse`() {
        val (handler, nav) = createHandler()
        val result = handler.handle("")
        assertFalse(result)
    }

    @Test
    fun `handle_blankDeepLink_returnsFalse`() {
        val (handler, nav) = createHandler()
        val result = handler.handle("   ")
        assertFalse(result)
    }

    @Test
    fun `handle_wrongScheme_returnsFalse`() {
        val (handler, nav) = createHandler()
        val result = handler.handle("https://example.com/paywall")
        assertFalse(result)
        assertEquals(0, nav.paywallCalls.size)
    }

    @Test
    fun `handle_httpScheme_returnsFalse`() {
        val (handler, nav) = createHandler()
        val result = handler.handle("http://paywall?source=test")
        assertFalse(result)
    }

    @Test
    fun `handle_multipleQueryParams_parsesCorrectly`() {
        val (handler, nav) = createHandler()
        val result = handler.handle("gisti://paywall?source=update_feed&extra=ignored")
        assertTrue(result)
        assertEquals("update_feed", nav.paywallCalls[0])
    }
}
