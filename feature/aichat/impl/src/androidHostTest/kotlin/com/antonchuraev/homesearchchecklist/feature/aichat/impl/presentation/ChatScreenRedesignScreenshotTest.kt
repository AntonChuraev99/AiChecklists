package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.chat_message_copy
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTheme
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatAttachment
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatChoice
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatMessage
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatRole
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChoiceAction
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChoiceOption
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChoiceKind
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChoiceObjectRow
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChoiceRole
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.RowEmphasis
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.RowKind
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.UndoHandle
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components.usesOptionRows
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

/**
 * The full-screen AI chat in redesign B "Soft & tactile" (DESIGN_SPEC_B §3.3, §8(d)).
 *
 * Frames the REAL stateless [ChatScreen] on fake states: the mock frame (360×660), the narrow phone,
 * dark, fontScale 1.5, RU / HI, transcribing, a pending choice, the out-of-credits banner above the
 * input (every variant + edge cases), three consecutive assistant replies (avatar only on the last), and the pricing /
 * settings sheets (sheets live in their own window → `captureScreenRoboImage`).
 *
 *   ./gradlew :feature:aichat:impl:recordRoborazziAndroidHostTest --tests "*ChatScreenRedesignScreenshotTest*"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChatScreenRedesignScreenshotTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val defaultLocale: Locale = Locale.getDefault()

    /** `Locale.setDefault` is JVM-global and Gradle reuses one JVM for the whole task. */
    @After
    fun restoreLocale() {
        Locale.setDefault(defaultLocale)
    }

    // ── Frames ───────────────────────────────────────────────────────────────

    @Test fun chat_360x660_light() = shoot("w360dp-h660dp", mockState(english))

    @Test fun chat_320x640_light() = shoot("w320dp-h640dp", mockState(english))

    @Test fun chat_360x660_dark() = shoot("w360dp-h660dp", mockState(english), dark = true)

    @Test fun chat_360x800_light_fontScale15() =
        shoot("w360dp-h800dp", mockState(english), fontScale = 1.5f)

    @Test fun chat_320x640_light_ru() =
        shoot("ru-rRU-w320dp-h640dp", mockState(russian), locale = Locale("ru", "RU"))

    @Test fun chat_360x660_light_hi() =
        shoot("hi-rIN-w360dp-h660dp", mockState(hindi), locale = Locale("hi", "IN"))

    @Test fun chat_360x660_transcribing() = shoot(
        "w360dp-h660dp",
        mockState(english).copy(isProcessing = false, isTranscribing = true),
    )

    @Test fun chat_360x660_pendingChoice() = shoot(
        "w360dp-h660dp",
        mockState(english).copy(isProcessing = false, pendingChoice = pendingChoice),
    )

    // 2026-11-26: a refusal has no assistant bubble — the answer is the banner pinned above the input
    // (DESIGN_SPEC credits banner §11: variant × light/dark × fontScale + edge cases).
    @Test fun chat_360x660_outOfCredits() = shoot("w360dp-h660dp", outOfCreditsState())

    @Test fun chat_360x660_outOfCredits_dark() = shoot("w360dp-h660dp", outOfCreditsState(), dark = true)

    @Test fun chat_360x660_outOfCredits_fontScale13() =
        shoot("w360dp-h660dp", outOfCreditsState(), fontScale = 1.3f)

    @Test fun chat_320x640_outOfCredits_ru_fontScale15() = shoot(
        "ru-rRU-w320dp-h640dp",
        outOfCreditsState(user = "Составь список вещей в Рим"),
        fontScale = 1.5f,
        locale = Locale("ru", "RU"),
    )

    @Test fun chat_360x660_outOfCredits_low() = shoot(
        "w360dp-h660dp",
        outOfCreditsState(banner = OutOfCreditsBannerState.Free(premiumDailyCredits = 300, lowBalance = true)),
    )

    @Test fun chat_360x660_outOfCredits_premium() =
        shoot("w360dp-h660dp", outOfCreditsState(banner = OutOfCreditsBannerState.Premium()))

    @Test fun chat_360x660_outOfCredits_premium_dark_fontScale13() = shoot(
        "w360dp-h660dp",
        outOfCreditsState(banner = OutOfCreditsBannerState.Premium()),
        dark = true,
        fontScale = 1.3f,
    )

    @Test fun chat_360x660_outOfCredits_guest() =
        shoot("w360dp-h660dp", outOfCreditsState(banner = OutOfCreditsBannerState.Guest))

    @Test fun chat_320x640_outOfCredits_guest_hi_dark_fontScale15() = shoot(
        "hi-rIN-w320dp-h640dp",
        outOfCreditsState(banner = OutOfCreditsBannerState.Guest),
        dark = true,
        fontScale = 1.5f,
        locale = Locale("hi", "IN"),
    )

    /** Banner + the pending-attachment strip + a draft in the field: banner stays on top. */
    @Test fun chat_360x660_outOfCredits_attachmentAndDraft() = shoot(
        "w360dp-h660dp",
        outOfCreditsState().copy(
            inputText = "Add milk",
            pendingAttachments = listOf(ChatAttachment("doc://recipe", "application/pdf", "Recipe.pdf", 20_480L)),
        ),
    )

    /** A pending question hides the input; the banner stays in the plate above it. */
    @Test fun chat_360x660_outOfCredits_pendingChoice() = shoot(
        "w360dp-h660dp",
        outOfCreditsState().copy(pendingChoice = pendingChoice),
    )

    @Test fun chat_840x900_outOfCredits() = shoot("w840dp-h900dp", outOfCreditsState())

    /** Balance not loaded yet (`outOfCredits == null` while `creditBalance` is still 0): no banner. */
    @Test fun chat_360x660_balanceNotLoaded_noBanner() =
        shoot("w360dp-h660dp", outOfCreditsState().copy(outOfCredits = null))

    /** Nudge peak: a refused send bumps the counter → the outline flashes 2dp primary. */
    @Test
    fun chat_360x660_outOfCredits_nudge() {
        setUp("w360dp-h660dp", Locale.ENGLISH, 1f)
        composeTestRule.mainClock.autoAdvance = false
        var nudge by mutableIntStateOf(0)
        composeTestRule.setContent {
            AppTheme(darkTheme = false) {
                ChatScreen(
                    state = outOfCreditsState().copy(inputText = "Add milk"),
                    onIntent = {},
                    onNavigateToPaywall = {},
                    outOfCreditsNudge = nudge,
                )
            }
        }
        composeTestRule.mainClock.advanceTimeBy(PIN_MS)
        // A write from the test thread reaches composition only once the snapshot is applied.
        composeTestRule.runOnUiThread { nudge++ }
        Snapshot.sendApplyNotifications()
        // Past the 140 ms rise and the 180 ms shake, inside the 400 ms hold.
        composeTestRule.mainClock.advanceTimeBy(NUDGE_PEAK_MS)
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test fun chat_360x660_threeAssistantInARow() = shoot(
        "w360dp-h660dp",
        ChatScreenState(
            messages = listOf(
                ChatMessage(id = "u1", role = ChatRole.User, content = "What's left for the trip?", timestamp = 0L),
                ChatMessage(id = "a1", role = ChatRole.Assistant, content = "Three things are still open.", timestamp = 0L),
                ChatMessage(id = "a2", role = ChatRole.Assistant, content = "Passport and tickets are due **tomorrow**.", timestamp = 0L),
                ChatMessage(
                    id = "a3",
                    role = ChatRole.Assistant,
                    content = "Want me to remind you tonight?",
                    timestamp = 0L,
                    linkedChecklistId = 7L,
                ),
            ),
            creditBalance = 80,
        ),
    )

    // ── Pass 2 (variant B "Actions inside the turn") ─────────────────────────

    @Test fun chat_360x660_empty_light() = shoot("w360dp-h660dp", ChatScreenState(creditBalance = 80))

    @Test fun chat_360x660_empty_dark() = shoot("w360dp-h660dp", ChatScreenState(creditBalance = 80), dark = true)

    /** Spec risk 4: headline wraps, the group needs the scroll, the avatar steps aside. */
    @Test fun chat_320x640_empty_ru_fontScale15() = shoot(
        "ru-rRU-w320dp-h640dp",
        ChatScreenState(creditBalance = 80),
        fontScale = 1.5f,
        locale = Locale("ru", "RU"),
    )

    @Test fun chat_360x660_empty_hi() = shoot(
        "hi-rIN-w360dp-h660dp",
        ChatScreenState(creditBalance = 80),
        locale = Locale("hi", "IN"),
    )

    /** Post-action offer: Undo + Move live INSIDE the result bubble, not in a second block. */
    @Test fun chat_360x660_postActionUndo() = shoot("w360dp-h660dp", postActionState)

    @Test fun chat_360x660_postActionUndo_dark() = shoot("w360dp-h660dp", postActionState, dark = true)

    @Test fun chat_360x660_batch() = shoot("w360dp-h660dp", batchState)

    @Test fun chat_360x660_batch_dark() = shoot("w360dp-h660dp", batchState, dark = true)

    @Test fun chat_360x660_whichList() = shoot("w360dp-h660dp", whichListState)

    @Test fun chat_360x660_whichList_dark() = shoot("w360dp-h660dp", whichListState, dark = true)

    @Test fun chat_320x640_whichList_ru_fontScale15() = shoot(
        "ru-rRU-w320dp-h640dp",
        whichListStateRu,
        fontScale = 1.5f,
        locale = Locale("ru", "RU"),
    )

    @Test fun chat_360x660_createChecklist() = shoot("w360dp-h660dp", createChecklistState)

    @Test fun chat_360x800_pricingSheet() = shootScreen(
        "w360dp-h800dp",
        mockState(english).copy(isProcessing = false, showPricingSheet = true),
    )

    @Test fun chat_360x800_settingsSheet() = shootScreen(
        "w360dp-h800dp",
        mockState(english).copy(isProcessing = false, showSettingsSheet = true),
    )

    // ── Behaviour: feedback row only on the latest reply; a tap reveals it on an older one ──

    @Test
    fun feedbackRow_onlyOnLatestReply_tapRevealsOlder() {
        Locale.setDefault(Locale.ENGLISH)
        RuntimeEnvironment.setQualifiers("w360dp-h900dp")
        val copyLabel = runBlocking { getString(Res.string.chat_message_copy) }
        composeTestRule.setContent {
            AppTheme(darkTheme = false) {
                ChatScreen(
                    state = ChatScreenState(
                        messages = listOf(
                            ChatMessage(id = "a1", role = ChatRole.Assistant, content = "Older reply", timestamp = 0L),
                            ChatMessage(id = "u1", role = ChatRole.User, content = "Thanks", timestamp = 0L),
                            ChatMessage(id = "a2", role = ChatRole.Assistant, content = "Newest reply", timestamp = 0L),
                        ),
                    ),
                    onIntent = {},
                )
            }
        }
        composeTestRule.onAllNodesWithContentDescription(copyLabel).fetchSemanticsNodes().size
            .let { assertEquals("only the latest reply shows Copy", 1, it) }

        // Tap the bubble's padding corner, not the text: under Robolectric a press on text inside a
        // SelectionContainer crashes in the platform Magnifier (null Surface) — a host artefact.
        val olderBubble = hasText("Older reply") and hasClickAction()
        composeTestRule.onNode(olderBubble).performTouchInput { click(Offset(6f, 6f)) }
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodesWithContentDescription(copyLabel).fetchSemanticsNodes().size
            .let { assertEquals("tapping the older reply reveals its actions", 2, it) }

        composeTestRule.onNode(olderBubble).performTouchInput { click(Offset(6f, 6f)) }
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodesWithContentDescription(copyLabel).fetchSemanticsNodes().size
            .let { assertEquals("a second tap hides them again", 1, it) }
    }

    // ── Pure logic: which assistant messages carry the avatar ──

    @Test
    fun avatar_onlyOnLastOfEachAssistantRun() {
        val msgs = listOf(
            msg("a1", ChatRole.Assistant), msg("a2", ChatRole.Assistant), msg("u1", ChatRole.User),
            msg("a3", ChatRole.Assistant), msg("u2", ChatRole.User), msg("a4", ChatRole.Assistant),
        )
        assertEquals(setOf("a2", "a3", "a4"), assistantRunTailIds(msgs, trailingAssistantBlock = false))
        // A typing indicator / prompt bubble after the newest message carries the run's avatar.
        assertEquals(setOf("a2", "a3"), assistantRunTailIds(msgs, trailingAssistantBlock = true))
        assertEquals(emptySet<String>(), assistantRunTailIds(listOf(msg("u", ChatRole.User)), false))
    }

    // ── Pure logic: where a pending choice renders (B pass 2) ──

    @Test
    fun attachedChoice_onlyBlankPromptOnTheNewestAssistantReply() {
        val reply = listOf(msg("u1", ChatRole.User), msg("a1", ChatRole.Assistant))
        val blank = postActionState.pendingChoice
        val prompted = pendingChoice
        assertEquals("blank prompt rides inside the newest reply", "a1", attachedChoiceMessageId(reply, blank))
        assertEquals("a prompt carries its own bubble", null, attachedChoiceMessageId(reply, prompted))
        assertEquals("no pending choice", null, attachedChoiceMessageId(reply, null))
        assertEquals(
            "newest message is the user's — nothing to attach to, the block stands alone",
            null,
            attachedChoiceMessageId(reply + msg("u2", ChatRole.User), blank),
        )
        assertEquals("empty history", null, attachedChoiceMessageId(emptyList(), blank))
    }

    @Test
    fun optionRows_forPickListOrDestinationOptionsOnly() {
        val plain = ChoiceOption(id = "a", label = "Add", role = ChoiceRole.Primary, action = ChoiceAction.Dismiss)
        val seeded = plain.copy(id = "s", seedId = 7L)
        val withMeta = plain.copy(id = "m", meta = "12")
        assertEquals(true, usesOptionRows(ChoiceKind.PickList, listOf(plain)))
        assertEquals("the move picker: destinations carry a seed", true, usesOptionRows(ChoiceKind.Move, listOf(seeded)))
        assertEquals(true, usesOptionRows(null, listOf(withMeta)))
        assertEquals("a single-action Move question keeps its chips", false, usesOptionRows(ChoiceKind.Move, listOf(plain)))
        assertEquals(false, usesOptionRows(ChoiceKind.AddItems, listOf(plain)))
        assertEquals(false, usesOptionRows(ChoiceKind.PickList, emptyList()))
    }

    // ── Harness ──────────────────────────────────────────────────────────────

    private fun msg(id: String, role: ChatRole) = ChatMessage(id = id, role = role, content = id, timestamp = 0L)

    private fun setUp(qualifiers: String, locale: Locale, fontScale: Float) {
        // Both: setQualifiers moves the Android configuration, Compose Resources reads the JVM locale.
        Locale.setDefault(locale)
        RuntimeEnvironment.setQualifiers(qualifiers)
        RuntimeEnvironment.setFontScale(fontScale)
    }

    private fun shoot(
        qualifiers: String,
        state: ChatScreenState,
        dark: Boolean = false,
        fontScale: Float = 1f,
        locale: Locale = Locale.ENGLISH,
    ) {
        setUp(qualifiers, locale, fontScale)
        // Typing dots / transcribing spinner are infinite animations → pin the clock.
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            AppTheme(darkTheme = dark) { ChatScreen(state = state, onIntent = {}, onNavigateToPaywall = {}) }
        }
        composeTestRule.mainClock.advanceTimeBy(PIN_MS)
        composeTestRule.onRoot().captureRoboImage()
    }

    private fun shootScreen(qualifiers: String, state: ChatScreenState) {
        setUp(qualifiers, Locale.ENGLISH, 1f)
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            AppTheme(darkTheme = false) { ChatScreen(state = state, onIntent = {}, onNavigateToPaywall = {}) }
        }
        composeTestRule.mainClock.advanceTimeBy(SHEET_SETTLE_MS)
        captureScreenRoboImage()
    }

    private data class Copy(
        val added: String,
        val openUser: String,
        val done: String,
        val plan: String,
    )

    private val english = Copy(
        added = "Added 3 items to Groceries.",
        openUser = "Remind me to water the plants every Friday at 6 pm",
        done = "Done. Water the plants repeats every Friday at 6:00 PM.",
        plan = "Plan a packing list for a weekend trip",
    )

    private val russian = Copy(
        added = "Добавил 3 пункта в «Продукты».",
        openUser = "Напомни поливать цветы каждую пятницу в 18:00",
        done = "Готово. «Полить цветы» повторяется каждую пятницу в 18:00.",
        plan = "Составь список вещей в поездку на выходные",
    )

    private val hindi = Copy(
        added = "किराना में 3 आइटम जोड़े।",
        openUser = "हर शुक्रवार शाम 6 बजे पौधों को पानी देने की याद दिलाएं",
        done = "हो गया। पौधों को पानी देना हर शुक्रवार शाम 6:00 बजे दोहराया जाएगा।",
        plan = "वीकेंड ट्रिप के लिए पैकिंग लिस्ट बनाएं",
    )

    /** The mock's B-Chat frame: reply with Open checklist → user → reply → user → typing. */
    private fun mockState(copy: Copy) = ChatScreenState(
        messages = listOf(
            ChatMessage(id = "a1", role = ChatRole.Assistant, content = copy.added, timestamp = 0L, linkedChecklistId = 1L),
            ChatMessage(id = "u1", role = ChatRole.User, content = copy.openUser, timestamp = 0L),
            ChatMessage(id = "a2", role = ChatRole.Assistant, content = copy.done, timestamp = 0L),
            ChatMessage(id = "u2", role = ChatRole.User, content = copy.plan, timestamp = 0L, costCredits = 3),
        ),
        isProcessing = true,
        creditBalance = 80,
    )

    private fun outOfCreditsState(
        banner: OutOfCreditsBannerState = OutOfCreditsBannerState.Free(premiumDailyCredits = 300),
        user: String = "Make a packing list for Rome",
    ) = ChatScreenState(
        messages = listOf(
            ChatMessage(id = "u1", role = ChatRole.User, content = user, timestamp = 0L),
        ),
        creditBalance = 0,
        outOfCredits = banner,
    )

    private val pendingChoice = PendingChoice(
        choice = ChatChoice(
            prompt = "Add **sunscreen** to Weekend trip?",
            options = listOf(
                ChoiceOption(id = "execute", label = "Add", role = ChoiceRole.Primary, action = ChoiceAction.Dismiss),
                ChoiceOption(id = "edit", label = "Edit", role = ChoiceRole.Default, action = ChoiceAction.Edit),
            ),
            escape = ChoiceOption(
                id = "escape",
                label = "Something else",
                role = ChoiceRole.Escape,
                action = ChoiceAction.Dismiss,
            ),
            kind = ChoiceKind.AddItems,
        ),
    )

    private val addedHandle = UndoHandle.AddedItem(
        checklistId = 2L,
        checklistName = "Groceries",
        fillId = 10L,
        fillItemId = "fill-1",
        templateItemId = "tmpl-1",
        itemText = "eggs",
    )

    /** The D1 offer as the ViewModel builds it: blank prompt, Undo (escape) + Move (default). */
    private val postActionState = ChatScreenState(
        messages = listOf(
            ChatMessage(id = "u1", role = ChatRole.User, content = "add eggs to groceries", timestamp = 0L, costCredits = 1),
            ChatMessage(id = "a1", role = ChatRole.Assistant, content = "Added «eggs» to Groceries.", timestamp = 0L),
        ),
        pendingChoice = PendingChoice(
            choice = ChatChoice(
                prompt = "",
                options = listOf(
                    ChoiceOption("undo", "Undo", role = ChoiceRole.Escape, action = ChoiceAction.Undo(addedHandle)),
                    ChoiceOption(
                        "move",
                        "Move to another list",
                        role = ChoiceRole.Default,
                        action = ChoiceAction.MoveToList(addedHandle),
                    ),
                ),
            ),
        ),
        creditBalance = 80,
    )

    private val batchState = ChatScreenState(
        messages = listOf(
            ChatMessage(id = "u1", role = ChatRole.User, content = "Tidy up my lists for the week", timestamp = 0L, costCredits = 3),
        ),
        pendingChoice = PendingChoice(
            choice = ChatChoice(
                prompt = "Here's my plan — apply all?",
                options = listOf(
                    ChoiceOption(id = "all", label = "Do it all", role = ChoiceRole.Primary, action = ChoiceAction.ExecuteAll),
                ),
                escape = ChoiceOption(id = "escape", label = "Cancel", role = ChoiceRole.Escape, action = ChoiceAction.Dismiss),
                kind = ChoiceKind.Batch,
            ),
            batchItems = listOf(
                AgentPlanItem("Add «milk» to Shopping"),
                AgentPlanItem("Add «eggs» to Shopping"),
                AgentPlanItem("Delete «expired yogurt» from Fridge", isDestructive = true),
                AgentPlanItem("Set a reminder for «call the plumber»"),
            ),
        ),
        creditBalance = 80,
    )

    private fun whichList(prompt: String, item: String, lists: List<Triple<String, String, Long>>, cancel: String, user: String) =
        ChatScreenState(
            messages = listOf(
                ChatMessage(id = "u1", role = ChatRole.User, content = user, timestamp = 0L, costCredits = 1),
            ),
            pendingChoice = PendingChoice(
                choice = ChatChoice(
                    prompt = prompt,
                    objectRows = listOf(ChoiceObjectRow(item, RowKind.Item, RowEmphasis.Primary, item)),
                    options = lists.mapIndexed { index, (name, meta, id) ->
                        ChoiceOption(
                            id = "c$index",
                            label = name,
                            meta = meta,
                            role = ChoiceRole.Default,
                            action = ChoiceAction.Dismiss,
                            seedId = id,
                        )
                    },
                    escape = ChoiceOption(id = "escape", label = cancel, role = ChoiceRole.Escape, action = ChoiceAction.Dismiss),
                    kind = ChoiceKind.PickList,
                ),
                showMemoryToggle = true,
            ),
            creditBalance = 80,
        )

    private val whichListState = whichList(
        prompt = "Which list?",
        item = "eggs",
        lists = listOf(Triple("Shopping", "12 • 3 Jul", 1L), Triple("Groceries", "8", 2L), Triple("Weekend", "3", 3L)),
        cancel = "Cancel",
        user = "add eggs",
    )

    private val whichListStateRu = whichList(
        prompt = "В какой список?",
        item = "яйца",
        lists = listOf(
            Triple("Покупки", "12 • 3 июл", 1L),
            Triple("Продукты на неделю для всей семьи", "8", 2L),
            Triple("Выходные", "3", 3L),
        ),
        cancel = "Отмена",
        user = "добавь яйца",
    )

    private val createChecklistState = ChatScreenState(
        messages = listOf(
            ChatMessage(id = "u1", role = ChatRole.User, content = "Plan a packing list for a weekend in Paris", timestamp = 0L, costCredits = 3),
        ),
        pendingChoice = PendingChoice(
            choice = ChatChoice(
                prompt = "Create this checklist?",
                objectRows = buildList {
                    add(ChoiceObjectRow("Paris Trip", RowKind.Name, RowEmphasis.Primary, "New list name: Paris Trip"))
                    listOf("Passport", "Plane tickets", "Hotel booking", "Euros", "Power adapter", "Camera", "Sunscreen", "Guidebook")
                        .forEach { add(ChoiceObjectRow(it, RowKind.Preview, RowEmphasis.Detail, it)) }
                },
                options = listOf(
                    ChoiceOption(id = "create", label = "Create 8 items", role = ChoiceRole.Primary, action = ChoiceAction.Dismiss),
                ),
                escape = ChoiceOption(id = "escape", label = "Cancel", role = ChoiceRole.Escape, action = ChoiceAction.Dismiss),
                kind = ChoiceKind.CreateChecklist,
            ),
        ),
        creditBalance = 80,
    )

    private companion object {
        const val PIN_MS = 700L
        const val SHEET_SETTLE_MS = 3_000L
        const val NUDGE_PEAK_MS = 300L
    }
}
