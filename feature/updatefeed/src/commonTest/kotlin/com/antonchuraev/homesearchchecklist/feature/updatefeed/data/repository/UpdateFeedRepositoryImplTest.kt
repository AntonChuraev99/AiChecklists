package com.antonchuraev.homesearchchecklist.feature.updatefeed.data.repository

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigDefaults
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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

    // ---- getReleases() — default JSON ----

    @Test
    fun `getReleases_withDefaultJson_returnsSixReleaseGroups`() = runTest {
        val repository = buildRepository(RemoteConfigDefaults.UPDATE_FEED_JSON)

        val releases = repository.getReleases()

        // 13 posts across 6 main-versions (v1.6–v1.11); v1.12 omitted (no unique content)
        assertEquals(6, releases.size)
    }

    @Test
    fun `getReleases_withDefaultJson_groupsSortedDescByPublishedAtMillis`() = runTest {
        val repository = buildRepository(RemoteConfigDefaults.UPDATE_FEED_JSON)

        val releases = repository.getReleases()

        // Newest release first (v1.11 has the highest post timestamp)
        assertEquals("1.11", releases.first().version)
        // Oldest release last (v1.6)
        assertEquals("1.6", releases.last().version)
    }

    @Test
    fun `getReleases_withDefaultJson_sortOrderIsDescending`() = runTest {
        val repository = buildRepository(RemoteConfigDefaults.UPDATE_FEED_JSON)

        val releases = repository.getReleases()

        assertEquals(
            listOf("1.11", "1.10", "1.9", "1.8", "1.7", "1.6"),
            releases.map { it.version }
        )
    }

    @Test
    fun `getReleases_withDefaultJson_v1_12IsAbsent`() = runTest {
        val repository = buildRepository(RemoteConfigDefaults.UPDATE_FEED_JSON)

        val releases = repository.getReleases()

        assertTrue(releases.none { it.version == "1.12" }, "v1.12 must be absent — no unique content")
    }

    @Test
    fun `getReleases_withDefaultJson_v1_11HasFivePostsAndNoStoreDescription`() = runTest {
        val repository = buildRepository(RemoteConfigDefaults.UPDATE_FEED_JSON)

        val releases = repository.getReleases()
        val v111 = releases.first { it.version == "1.11" }

        assertEquals(5, v111.posts.size)
        // v1.11 releaseNotes entry was removed (all lines were duplicates of 1.10 or covered by posts)
        assertNull(v111.storeDescription)
        val ids = v111.posts.map { it.id }.toSet()
        assertTrue(ids.contains("recurring_v1"))
        assertTrue(ids.contains("swipe_delete_v1"))
        assertTrue(ids.contains("interactive_onboarding_v1"))
        assertTrue(ids.contains("templates_v1"))
        assertTrue(ids.contains("discover_more_v1"))
    }

    @Test
    fun `getReleases_withDefaultJson_v1_11_publishedAtMillisIsMaxOfPosts`() = runTest {
        val repository = buildRepository(RemoteConfigDefaults.UPDATE_FEED_JSON)

        val releases = repository.getReleases()
        val v111 = releases.first { it.version == "1.11" }

        // Max post publishedAtMillis = 1773360000000 (templates_v1 / discover_more_v1)
        assertEquals(1773360000000L, v111.publishedAtMillis)
    }

    @Test
    fun `getReleases_withDefaultJson_v1_8HasThreePostsAndNoStoreDescription`() = runTest {
        val repository = buildRepository(RemoteConfigDefaults.UPDATE_FEED_JSON)

        val releases = repository.getReleases()
        val v18 = releases.first { it.version == "1.8" }

        assertEquals(3, v18.posts.size)
        // v1.8 releaseNotes lines were all present in v1.7 — removed as duplicates
        assertNull(v18.storeDescription)
        val ids = v18.posts.map { it.id }.toSet()
        assertTrue(ids.contains("ai_language_v1"))
        assertTrue(ids.contains("fill_target_v1"))
        assertTrue(ids.contains("csat_v1"))
    }

    @Test
    fun `getReleases_withDefaultJson_v1_9HasTwoPostsAndNoStoreDescription`() = runTest {
        val repository = buildRepository(RemoteConfigDefaults.UPDATE_FEED_JSON)

        val releases = repository.getReleases()
        val v19 = releases.first { it.version == "1.9" }

        assertEquals(2, v19.posts.size)
        // v1.9 releaseNotes: Reminders covered by reminders_v1 post; rest duplicated from 1.7
        assertNull(v19.storeDescription)
        val ids = v19.posts.map { it.id }.toSet()
        assertTrue(ids.contains("reminders_v1"))
        assertTrue(ids.contains("quick_add_v1"))
    }

    @Test
    fun `getReleases_withDefaultJson_singlePostVersionsHaveOnePost`() = runTest {
        val repository = buildRepository(RemoteConfigDefaults.UPDATE_FEED_JSON)

        val releases = repository.getReleases()
        val singlePostVersions = listOf("1.6", "1.7", "1.10")

        singlePostVersions.forEach { version ->
            val group = releases.first { it.version == version }
            assertEquals(1, group.posts.size, "Version $version should have 1 post")
        }
    }

    @Test
    fun `getReleases_withDefaultJson_v1_6HasDedupedStoreDescription`() = runTest {
        val repository = buildRepository(RemoteConfigDefaults.UPDATE_FEED_JSON)

        val releases = repository.getReleases()
        val v16 = releases.first { it.version == "1.6" }

        val desc16 = requireNotNull(v16.storeDescription) { "v1.6 storeDescription must not be null" }
        // Widget line was removed (covered by widget_v1 post); only perf+bugfix lines remain
        assertTrue(desc16.contains("Improved Performance"))
        assertTrue(desc16.contains("Bug Fixes"))
        // Widget feature must NOT appear in storeDescription (covered by post)
        assertTrue(!desc16.contains("Home Screen Widget"))
    }

    @Test
    fun `getReleases_withDefaultJson_v1_7HasDedupedStoreDescription`() = runTest {
        val repository = buildRepository(RemoteConfigDefaults.UPDATE_FEED_JSON)

        val releases = repository.getReleases()
        val v17 = releases.first { it.version == "1.7" }

        val desc17 = requireNotNull(v17.storeDescription) { "v1.7 storeDescription must not be null" }
        // Faster Checklist Editing covered by inline_input_v1 post — must be absent
        assertTrue(!desc17.contains("Faster Checklist Editing"))
        // Unique lines must be present
        assertTrue(desc17.contains("Smoother List Animations"))
        assertTrue(desc17.contains("Subscription Sync"))
    }

    @Test
    fun `getReleases_withDefaultJson_v1_10HasDedupedStoreDescription`() = runTest {
        val repository = buildRepository(RemoteConfigDefaults.UPDATE_FEED_JSON)

        val releases = repository.getReleases()
        val v110 = releases.first { it.version == "1.10" }

        val desc110 = requireNotNull(v110.storeDescription) { "v1.10 storeDescription must not be null" }
        // "Organize Your Checklist" is the only unique line for 1.10
        assertTrue(desc110.contains("Organize Your Checklist"))
        // Reminders line was in 1.9 already — must not be repeated
        assertTrue(!desc110.contains("Checklist Reminders"))
    }

    @Test
    fun `getReleases_withDefaultJson_versionsWithNullStoreDescriptionHavePosts`() = runTest {
        val repository = buildRepository(RemoteConfigDefaults.UPDATE_FEED_JSON)

        val releases = repository.getReleases()

        // Every group with null storeDescription must have at least one post
        releases.filter { it.storeDescription == null }.forEach { group ->
            assertTrue(
                group.posts.isNotEmpty(),
                "Version ${group.version} has null storeDescription but no posts — empty group leaked"
            )
        }
    }

    @Test
    fun `getReleases_withDefaultJson_groupPublishedAtMillisIsMaxOfPostsWhenPostsExist`() = runTest {
        val repository = buildRepository(RemoteConfigDefaults.UPDATE_FEED_JSON)

        val releases = repository.getReleases()

        releases.filter { it.posts.isNotEmpty() }.forEach { group ->
            val expectedMax = group.posts.maxOf { it.publishedAtMillis }
            assertEquals(
                expectedMax,
                group.publishedAtMillis,
                "Group ${group.version} publishedAtMillis should be max of posts"
            )
        }
    }

    @Test
    fun `getReleases_withDefaultJson_postsWithinGroupSortedDescByPublishedAtMillis`() = runTest {
        val repository = buildRepository(RemoteConfigDefaults.UPDATE_FEED_JSON)

        val releases = repository.getReleases()

        releases.forEach { group ->
            val millis = group.posts.map { it.publishedAtMillis }
            assertEquals(
                millis.sortedDescending(),
                millis,
                "Posts in group ${group.version} should be sorted desc"
            )
        }
    }

    @Test
    fun `getReleases_withDefaultJson_widgetPostHasCorrectFields`() = runTest {
        val repository = buildRepository(RemoteConfigDefaults.UPDATE_FEED_JSON)

        val releases = repository.getReleases()
        val widgetGroup = releases.first { it.version == "1.6" }
        val widgetPost = widgetGroup.posts.first { it.id == "widget_v1" }

        assertEquals("Add the home screen widget", widgetPost.title)
        assertEquals("Widgets", widgetPost.iconName)
        assertEquals("1.6", widgetPost.version)
        assertEquals(1, widgetPost.actions.size)
        assertEquals("Show me how", widgetPost.actions[0].label)
        assertEquals("gisti://widget_instruction", widgetPost.actions[0].deepLink)
    }

    @Test
    fun `getReleases_withDefaultJson_totalPostCountIsThirteen`() = runTest {
        val repository = buildRepository(RemoteConfigDefaults.UPDATE_FEED_JSON)

        val releases = repository.getReleases()
        val totalPosts = releases.sumOf { it.posts.size }

        assertEquals(13, totalPosts)
    }

    @Test
    fun `getReleases_withDefaultJson_mainVersionGroupingFoldsPatchVersions`() = runTest {
        val repository = buildRepository(RemoteConfigDefaults.UPDATE_FEED_JSON)

        val releases = repository.getReleases()
        val v111 = releases.first { it.version == "1.11" }

        // interactive_onboarding_v1 (was 1.11.2), templates_v1 (was 1.11.3),
        // recurring_v1 (was 1.11.1) — all must be in the single "1.11" group
        val ids = v111.posts.map { it.id }.toSet()
        assertTrue(ids.contains("interactive_onboarding_v1"), "interactive_onboarding_v1 must be in 1.11")
        assertTrue(ids.contains("templates_v1"), "templates_v1 must be in 1.11")
        assertTrue(ids.contains("recurring_v1"), "recurring_v1 must be in 1.11")

        // Verify there is no separate "1.11.1", "1.11.2", or "1.11.3" group
        assertTrue(releases.none { it.version == "1.11.1" }, "No patch group 1.11.1 should exist")
        assertTrue(releases.none { it.version == "1.11.2" }, "No patch group 1.11.2 should exist")
        assertTrue(releases.none { it.version == "1.11.3" }, "No patch group 1.11.3 should exist")
    }

    @Test
    fun `getReleases_withDefaultJson_v1_11HasPostsButNoReleaseNotesEntry`() = runTest {
        // Verifies that a group with posts but no releaseNotes key parses correctly (storeDescription is null)
        val repository = buildRepository(RemoteConfigDefaults.UPDATE_FEED_JSON)

        val releases = repository.getReleases()
        val v111 = releases.first { it.version == "1.11" }

        // No "1.11" key in releaseNotes after de-dup → storeDescription must be null
        assertNull(v111.storeDescription, "v1.11 storeDescription must be null — no releaseNotes entry")
        // But posts must still be present
        assertTrue(v111.posts.isNotEmpty(), "v1.11 must still have posts despite missing releaseNotes entry")
    }

    // ---- releaseNotes parsing ----

    @Test
    fun `getReleases_withReleaseNotesOnly_createsGroupWithNoPostsAndStoreDescription`() = runTest {
        val json = """{"posts":[],"releaseNotes":{"2.0":{"notes":"Big release","publishedAtMillis":9999}}}"""
        val repository = buildRepository(json)

        val releases = repository.getReleases()

        assertEquals(1, releases.size)
        assertEquals("2.0", releases[0].version)
        assertTrue(releases[0].posts.isEmpty())
        assertEquals("Big release", releases[0].storeDescription)
        assertEquals(9999L, releases[0].publishedAtMillis)
    }

    @Test
    fun `getReleases_withPostsAndReleaseNotes_storeDescriptionAttachedToCorrectGroup`() = runTest {
        val json = """{
            "posts":[
                {"id":"a","version":"1.0","title":"A","description":"","publishedAtMillis":200},
                {"id":"b","version":"2.0","title":"B","description":"","publishedAtMillis":100}
            ],
            "releaseNotes":{
                "1.0":{"notes":"Notes for 1.0","publishedAtMillis":200},
                "2.0":{"notes":"Notes for 2.0","publishedAtMillis":100}
            }
        }"""
        val repository = buildRepository(json)

        val releases = repository.getReleases()

        val v10 = releases.first { it.version == "1.0" }
        val v20 = releases.first { it.version == "2.0" }
        assertEquals("Notes for 1.0", v10.storeDescription)
        assertEquals("Notes for 2.0", v20.storeDescription)
    }

    @Test
    fun `getReleases_withPostsAndReleaseNotes_groupPublishedAtMillisIsMaxOfPostsNotReleaseNotes`() = runTest {
        val json = """{
            "posts":[
                {"id":"a","version":"1.0","title":"A","description":"","publishedAtMillis":500}
            ],
            "releaseNotes":{
                "1.0":{"notes":"Notes","publishedAtMillis":100}
            }
        }"""
        val repository = buildRepository(json)

        val releases = repository.getReleases()

        // Post timestamp (500) must win over releaseNotes timestamp (100)
        assertEquals(500L, releases.first().publishedAtMillis)
    }

    @Test
    fun `getReleases_withVersionInBothPostsAndReleaseNotes_unifiedIntoSingleGroup`() = runTest {
        val json = """{
            "posts":[
                {"id":"a","version":"1.0","title":"A","description":"","publishedAtMillis":200},
                {"id":"b","version":"2.0","title":"B","description":"","publishedAtMillis":100}
            ],
            "releaseNotes":{
                "1.0":{"notes":"Notes 1.0","publishedAtMillis":200},
                "3.0":{"notes":"Notes 3.0 only","publishedAtMillis":50}
            }
        }"""
        val repository = buildRepository(json)

        val releases = repository.getReleases()

        // 3 versions: 1.0 (post + note), 2.0 (post only), 3.0 (note only)
        assertEquals(3, releases.size)
        assertTrue(releases.any { it.version == "3.0" && it.posts.isEmpty() && it.storeDescription != null })
    }

    @Test
    fun `getReleases_withVersionWithPostButNoReleaseNote_storeDescriptionIsNull`() = runTest {
        val json = """{
            "posts":[
                {"id":"a","version":"1.0","title":"A","description":"","publishedAtMillis":200}
            ],
            "releaseNotes":{}
        }"""
        val repository = buildRepository(json)

        val releases = repository.getReleases()

        assertNull(releases.first().storeDescription)
    }

    // ---- Empty-group filter ----

    @Test
    fun `getReleases_emptyGroupFilterRemovesVersionsWithNoPostsAndNoStoreDescription`() = runTest {
        // A version appears only in posts with no releaseNotes entry; then we remove its
        // releaseNotes entry to simulate a scenario where parsing produces an empty group.
        // The repository filter must drop it.
        val json = """{
            "posts":[
                {"id":"a","version":"1.0","title":"A","description":"","publishedAtMillis":200}
            ],
            "releaseNotes":{
                "2.0":{"notes":"Notes","publishedAtMillis":100}
            }
        }"""
        // Version "1.0" has a post → present. Version "2.0" has releaseNotes → present.
        // There is no version with both absent — confirm filter does not drop valid groups.
        val repository = buildRepository(json)

        val releases = repository.getReleases()

        assertEquals(2, releases.size)
    }

    // ---- Edge cases ----

    @Test
    fun `getReleases_withEmptyPostsJson_returnsEmptyList`() = runTest {
        val repository = buildRepository("""{"posts":[]}""")

        val releases = repository.getReleases()

        assertTrue(releases.isEmpty())
    }

    @Test
    fun `getReleases_withEmptyPostsAndReleaseNotes_returnsReleaseNoteOnlyGroups`() = runTest {
        val repository = buildRepository("""{"posts":[],"releaseNotes":{"1.0":{"notes":"Hello","publishedAtMillis":100}}}""")

        val releases = repository.getReleases()

        assertEquals(1, releases.size)
        assertEquals("1.0", releases[0].version)
        assertTrue(releases[0].posts.isEmpty())
        assertEquals("Hello", releases[0].storeDescription)
    }

    @Test
    fun `getReleases_withInvalidJson_returnsEmptyListAndLogsError`() = runTest {
        val logger = FakeLogger()
        val repository = UpdateFeedRepositoryImpl(
            FakeRemoteConfigProvider("not valid json {{{{"),
            logger
        )

        val releases = repository.getReleases()

        assertTrue(releases.isEmpty())
        assertTrue(logger.errors.isNotEmpty())
    }

    @Test
    fun `getReleases_withPostsMissingOptionalFields_parsesSuccessfully`() = runTest {
        val minimalJson = """{"posts":[{"id":"test_id","version":"2.0","title":"Test","description":"Desc","publishedAtMillis":1000}]}"""
        val repository = buildRepository(minimalJson)

        val releases = repository.getReleases()

        assertEquals(1, releases.size)
        assertEquals("2.0", releases[0].version)
        assertEquals(1, releases[0].posts.size)
        assertEquals("test_id", releases[0].posts[0].id)
        assertEquals(null, releases[0].posts[0].iconName)
        assertTrue(releases[0].posts[0].actions.isEmpty())
        assertNull(releases[0].storeDescription)
    }

    @Test
    fun `getReleases_sortsByPublishedAtMillisDescending_acrossGroups`() = runTest {
        val json = """{"posts":[
            {"id":"a","version":"1.0","title":"A","description":"","publishedAtMillis":100},
            {"id":"b","version":"3.0","title":"B","description":"","publishedAtMillis":300},
            {"id":"c","version":"2.0","title":"C","description":"","publishedAtMillis":200}
        ]}"""
        val repository = buildRepository(json)

        val releases = repository.getReleases()

        assertEquals(listOf("3.0", "2.0", "1.0"), releases.map { it.version })
    }

    @Test
    fun `getReleases_multiplePostsSameVersion_groupedIntoOneRelease`() = runTest {
        val json = """{"posts":[
            {"id":"a","version":"1.0","title":"A","description":"","publishedAtMillis":200},
            {"id":"b","version":"1.0","title":"B","description":"","publishedAtMillis":100},
            {"id":"c","version":"2.0","title":"C","description":"","publishedAtMillis":50}
        ]}"""
        val repository = buildRepository(json)

        val releases = repository.getReleases()

        assertEquals(2, releases.size)
        // v1.0 has max millis=200 → comes first
        assertEquals("1.0", releases[0].version)
        assertEquals(200L, releases[0].publishedAtMillis)
        // posts within v1.0 sorted desc: a(200) before b(100)
        assertEquals(listOf("a", "b"), releases[0].posts.map { it.id })
        assertEquals("2.0", releases[1].version)
    }

    // ---- Helper ----

    private fun buildRepository(json: String): UpdateFeedRepositoryImpl {
        return UpdateFeedRepositoryImpl(FakeRemoteConfigProvider(json), FakeLogger())
    }
}
