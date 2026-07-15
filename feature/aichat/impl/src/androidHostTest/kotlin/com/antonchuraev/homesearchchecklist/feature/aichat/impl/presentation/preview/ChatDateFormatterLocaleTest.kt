package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.preview

import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatAttachment
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ToolCall
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// ════════════════════════════════════════════════════════════════════════════
// Localization of the chat preview copy (D1, 2026-07-15).
//
// WHY THIS LIVES IN androidHostTest AND NOT IN commonTest
//   Every string here comes from Compose Resources (`getString`). In a plain unit host that call
//   throws ("Resources.getSystem not mocked") — which is exactly why ChatViewModel.choiceString
//   swallows it and returns "…". A commonTest assertion on this copy could therefore only ever
//   assert "…". Robolectric + `isIncludeAndroidResources = true` (see this module's build.gradle)
//   is the ONE host configuration where composeResources/values-ru actually resolves.
//
// HOW THE LOCALE IS SELECTED
//   Compose Resources on Android reads the platform locale, so the test drives it the same way
//   production does (LocalAppLocale.android.kt): the Robolectric `qualifiers = "ru"` config sets
//   the Configuration, and Locale.setDefault covers the JVM-global half. Both are restored in
//   @After so this test cannot leak a locale into the rest of the suite.
//
// WHAT IS BEING PROTECTED
//   Before D1 the preview built its copy in Kotlin: `"$dayName, $monthName ${dt.dayOfMonth}"` plus
//   literals "Attach ", " (in ". That hardcodes BOTH the English words and the English day/month
//   ORDER, so a Russian user reading a Russian UI got "Monday, July 20 at 09:00". Day/month order
//   is locale DATA, not code — it belongs in the format string.
// ════════════════════════════════════════════════════════════════════════════
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ru")
class ChatDateFormatterLocaleTest {

    private lateinit var previousLocale: Locale

    private val renderer = ToolCallPreviewRendererImpl(ChatDateFormatterImpl())

    /** 2026-07-20 09:00 *local* time. Built forwards (wall clock → epoch) so the expectation is */
    /** independent of the machine's timezone: the renderer converts it back the other way. */
    private val july20At09: Long =
        LocalDateTime(2026, 7, 20, 9, 0).toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()

    @Before
    fun setUp() {
        previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("ru"))
    }

    @After
    fun tearDown() {
        Locale.setDefault(previousLocale)
    }

    /**
     * "20 июля" — the day-number/month order AND the genitive case are Russian grammar, neither of
     * which survives Kotlin-side concatenation of an English-shaped template.
     */
    @Test
    fun render_setItemReminder_ruLocale_containsRussianMonthGenitive() = runTest {
        val preview = renderer.render(
            ToolCall.SetItemReminder(checklistHint = "Покупки", itemText = "Молоко", at = july20At09),
        )

        assertTrue(
            "RU preview must use the genitive month after the day number («20 июля»), was: '$preview'",
            preview.contains("20 июля"),
        )
        assertFalse(
            "RU preview must not fall back to the English month name, was: '$preview'",
            preview.contains("July", ignoreCase = true),
        )
        assertFalse(
            "«июль» is the nominative — after a day number Russian requires the genitive «июля», was: '$preview'",
            Regex("""июль(?!я)""").containsMatchIn(preview),
        )
        assertTrue(
            "the reminder time must still be shown, was: '$preview'",
            preview.contains("09:00"),
        )
        assertTrue("the item must still be named, was: '$preview'", preview.contains("Молоко"))
    }

    /**
     * The attach preview glued English literals ("Attach ", " (in ") around localized data. Asserts
     * the object survives AND no English chrome is left; the exact RU wording is the copywriter's
     * to change, so it is deliberately not pinned here.
     */
    @Test
    fun render_attachToItem_ruLocale_hasNoEnglishHardcode() = runTest {
        val preview = renderer.render(
            ToolCall.AttachToItem(
                checklistHint = "Покупки",
                itemText = "Молоко",
                attachments = listOf(
                    ChatAttachment(
                        sourcePath = "/tmp/receipt.jpg",
                        fileName = "receipt.jpg",
                        mimeType = "image/jpeg",
                        sizeBytes = 1024L,
                    ),
                ),
            ),
        )

        assertFalse(
            "«Attach» is a hardcoded English literal in the renderer — it must come from strings.xml, was: '$preview'",
            preview.contains("Attach", ignoreCase = true),
        )
        assertFalse(
            "«(in …)» is a hardcoded English literal — it must come from strings.xml, was: '$preview'",
            preview.contains("(in "),
        )
        assertTrue("the target item must be named, was: '$preview'", preview.contains("Молоко"))
        assertTrue("the target list must be named, was: '$preview'", preview.contains("Покупки"))
        assertTrue("the file being attached must be named, was: '$preview'", preview.contains("receipt.jpg"))
    }

    /**
     * The English side of the same contract: it must keep reading naturally ("July 20"), proving the
     * format-string indirection did not merely swap one hardcoded order for another.
     */
    @Test
    @Config(sdk = [34], qualifiers = "en")
    fun render_setItemReminder_enLocale_containsEnglishMonthName() = runTest {
        Locale.setDefault(Locale.forLanguageTag("en"))

        val preview = renderer.render(
            ToolCall.SetItemReminder(checklistHint = "Shopping", itemText = "Milk", at = july20At09),
        )

        assertTrue(
            "EN preview must read «July 20», was: '$preview'",
            preview.contains("July 20"),
        )
        assertTrue("the reminder time must be shown, was: '$preview'", preview.contains("09:00"))
    }
}
