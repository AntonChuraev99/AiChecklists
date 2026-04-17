package com.antonchuraev.homesearchchecklist.feature.updatefeed.data.repository

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigDefaults
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UpdateFeedRepositoryImplTest {

    private class FakeRemoteConfigProvider(
        private val jsonValue: String
    ) : RemoteConfigProvider {
        override suspend fun fetchAndActivate(): Boolean = true
        override fun getBoolean(key: String, defaultValue: Boolean): Boolean = defaultValue
        override fun getString(key: String, defaultValue: String): String = jsonValue
        override fun getLong(key: String, defaultValue: Long): Long = defaultValue
    }

    private class FakeLogger : AppLogger {
        val errors = mutableListOf<String>()
        override fun debug(tag: String, message: String) {}
        override fun info(tag: String, message: String) {}
        override fun warning(tag: String, message: String) {}
        override fun error(tag: String, message: String, throwable: Throwable?) {
            errors.add(message)
        }
    }

    @Test
    fun `getPosts_withDefaultJson_returnsThreePostsSortedDescending`() = runTest {
        val provider = FakeRemoteConfigProvider(RemoteConfigDefaults.UPDATE_FEED_JSON)
        val logger = FakeLogger()
        val repository = UpdateFeedRepositoryImpl(provider, logger)

        val posts = repository.getPosts()

        assertEquals(3, posts.size)
        // Sorted by publishedAtMillis descending: premium_promo_v1 > ai_analyze_v1 > welcome_v1
        assertEquals("premium_promo_v1", posts[0].id)
        assertEquals("ai_analyze_v1", posts[1].id)
        assertEquals("welcome_v1", posts[2].id)
    }

    @Test
    fun `getPosts_withDefaultJson_postsHaveCorrectFields`() = runTest {
        val provider = FakeRemoteConfigProvider(RemoteConfigDefaults.UPDATE_FEED_JSON)
        val logger = FakeLogger()
        val repository = UpdateFeedRepositoryImpl(provider, logger)

        val posts = repository.getPosts()
        val premiumPost = posts.first { it.id == "premium_promo_v1" }

        assertEquals("Go Premium", premiumPost.title)
        assertEquals("Star", premiumPost.iconName)
        assertEquals(2, premiumPost.actions.size)
        assertEquals("Start Free Trial", premiumPost.actions[0].label)
        assertEquals("gisti://paywall?source=update_feed", premiumPost.actions[0].deepLink)
        assertEquals("See plans", premiumPost.actions[1].label)
    }

    @Test
    fun `getPosts_withEmptyPostsJson_returnsEmptyList`() = runTest {
        val provider = FakeRemoteConfigProvider("""{"posts":[]}""")
        val logger = FakeLogger()
        val repository = UpdateFeedRepositoryImpl(provider, logger)

        val posts = repository.getPosts()

        assertTrue(posts.isEmpty())
    }

    @Test
    fun `getPosts_withInvalidJson_returnsEmptyListAndLogsError`() = runTest {
        val provider = FakeRemoteConfigProvider("not valid json {{{{")
        val logger = FakeLogger()
        val repository = UpdateFeedRepositoryImpl(provider, logger)

        val posts = repository.getPosts()

        assertTrue(posts.isEmpty())
        assertTrue(logger.errors.isNotEmpty())
    }

    @Test
    fun `getPosts_withPostsMissingOptionalFields_parsesSuccessfully`() = runTest {
        val minimalJson = """{"posts":[{"id":"test_id","title":"Test","description":"Desc","publishedAtMillis":1000}]}"""
        val provider = FakeRemoteConfigProvider(minimalJson)
        val logger = FakeLogger()
        val repository = UpdateFeedRepositoryImpl(provider, logger)

        val posts = repository.getPosts()

        assertEquals(1, posts.size)
        assertEquals("test_id", posts[0].id)
        assertEquals(null, posts[0].iconName)
        assertTrue(posts[0].actions.isEmpty())
    }

    @Test
    fun `getPosts_sortsByPublishedAtMillisDescending`() = runTest {
        val json = """{"posts":[
            {"id":"a","title":"A","description":"","publishedAtMillis":100},
            {"id":"b","title":"B","description":"","publishedAtMillis":300},
            {"id":"c","title":"C","description":"","publishedAtMillis":200}
        ]}"""
        val provider = FakeRemoteConfigProvider(json)
        val logger = FakeLogger()
        val repository = UpdateFeedRepositoryImpl(provider, logger)

        val posts = repository.getPosts()

        assertEquals(listOf("b", "c", "a"), posts.map { it.id })
    }
}
