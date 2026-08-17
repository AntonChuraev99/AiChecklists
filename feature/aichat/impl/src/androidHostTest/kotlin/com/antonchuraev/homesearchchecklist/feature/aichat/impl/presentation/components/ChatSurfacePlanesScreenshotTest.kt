package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.components.QuickCaptureDock
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.GistiChatDock
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.GistiFullChatOverlay
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.GistiGlassChatDock
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.GistiItemCreateAction
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.GistiPromptChip
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.GistiPromptChips
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.GistiQuickAction
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.GistiSelectableChipRow
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.gistiItemCreatePromptChips
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.rememberDockFullExpandState
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppSurface
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTheme
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatAttachment
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatChoice
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatMessage
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatRole
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChoiceAction
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChoiceOption
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChoiceRole
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.PendingChoice
import com.github.takahirom.roborazzi.captureRoboImage
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

/**
 * The chat rendered on BOTH of its planes, in one frame.
 *
 * ## Why this test exists as its own file
 * The chat has no single background. `ChatScreen` paints `colorScheme.surface`; the chat dock, the
 * full-screen chat overlay and the quick-capture dock paint `AppSurface.bottomChrome()`, and
 * `App.kt` hands the overlay the SAME `ChatBody` the page renders. So every chat component is drawn
 * on two different surfaces, and a component spelled with one absolute colour role can only be
 * correct on one of them.
 *
 * That is not hypothetical — it is the shipped defect this file was written for. The unselected
 * reminder chip was `Color.Transparent` plus a 1dp `outlineVariant`; measured on the bottom chrome
 * that line was **1.04 : 1** while its label stayed at 6.82 : 1, so the owner saw *"the In 1 hour
 * buttons blend into the background"* — readable words with no button under them. In dark, the AI
 * bubble and the input pill were `surfaceContainerLowest` `#0D0E11`, ΔL\* −6.2 BELOW the `#1A1C20`
 * chrome: holes punched in the dock.
 *
 * **Neither half was covered.** Every chat golden framed its component on the page, and the only
 * dock goldens were light. A defect that only appears on the second plane cannot be caught by a
 * suite that only ever records the first — so the two planes are stacked in ONE image here, and a
 * diff shows immediately if a role stops resolving against the surface under it.
 *
 * The chrome halves use the REAL hosts rather than a Box painted with the chrome colour, so the
 * `LocalChatSurfaceTone` provider is exercised where it actually lives. Swapping them for a
 * hand-painted background would make this test pass while the production hosts forgot to provide the
 * plane. All THREE providers are covered: [GistiGlassChatDock] and [QuickCaptureDock] stack into
 * [ProbeBody]; [GistiFullChatOverlay] is `fillMaxSize()` and would cover them, so it has its own
 * pair of frames below.
 *
 * ⚠️ These are wide-coverage frames: a colour, a metric or a string change anywhere in the chat
 * moves them. That is the intent — but re-record only after LOOKING at the result, because
 * `record` writes whatever rendered, defect included.
 *
 *   ./gradlew :feature:aichat:impl:recordRoborazziAndroidHostTest --tests "*ChatSurfacePlanesScreenshotTest*"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChatSurfacePlanesScreenshotTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val defaultLocale: Locale = Locale.getDefault()

    /** `Locale.setDefault` is JVM-global and Gradle reuses one JVM for the whole task. */
    @After
    fun restoreLocale() {
        Locale.setDefault(defaultLocale)
    }

    @Test fun planes_360_light() = shoot("w360dp-h2000dp")

    @Test fun planes_360_dark() = shoot("w360dp-h2000dp", dark = true)

    @Test fun planes_320_light_ru_fontScale13() = shoot(
        qualifiers = "ru-rRU-w320dp-h2400dp",
        copy = russian,
        fontScale = 1.3f,
        locale = Locale("ru", "RU"),
    )

    @Test fun planes_412_dark_fontScale15() = shoot(
        qualifiers = "w412dp-h2600dp",
        dark = true,
        fontScale = 1.5f,
    )

    @Test fun planes_600_light() = shoot("w600dp-h2000dp")

    @Test fun planes_360_light_hi_fontScale13() = shoot(
        qualifiers = "hi-rIN-w360dp-h2400dp",
        copy = hindi,
        fontScale = 1.3f,
        locale = Locale("hi", "IN"),
    )

    // ── The THIRD provider ───────────────────────────────────────────────────
    //
    // Three hosts declare `LocalChatSurfaceTone = BottomChrome`: `GistiGlassChatDock`,
    // `QuickCaptureDock` and `GistiFullChatOverlay`. The frames above exercise the first two — the
    // overlay was named in this file's KDoc and covered by nothing, here or anywhere else. Deleting
    // its `CompositionLocalProvider` broke no test, and the full-screen chat would have fallen back
    // to `Page` in silence: in dark that puts a `#1A1C20` bubble on a `#1A1C20` chrome, ΔL\* 0.0, the
    // exact hole this whole mechanism exists to prevent.
    //
    // Its own test rather than a third section of [ProbeBody]: the overlay is `fillMaxSize()` and
    // bottom-anchored, so stacked in that Column it would simply cover the other two planes.

    @Test fun fullOverlay_360_light() = shootFullOverlay("w360dp-h880dp")

    @Test fun fullOverlay_360_dark() = shootFullOverlay("w360dp-h880dp", dark = true)

    // =========================================================================
    // Harness
    // =========================================================================

    private fun shootFullOverlay(qualifiers: String, dark: Boolean = false, copy: Copy = english) {
        Locale.setDefault(Locale.ENGLISH)
        RuntimeEnvironment.setQualifiers(qualifiers)
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            AppTheme(darkTheme = dark) { FullOverlayProbe(copy) }
        }
        // The overlay opens by animating a spring across the WHOLE window height, so [PIN_MS] — a
        // pin for the typing dots — would photograph it mid-grow, at a height that changes with the
        // qualifier. One large advance of the test clock is simulated time, not wall time: it costs
        // nothing, settles the spring on any window, and stays byte-deterministic.
        composeTestRule.mainClock.advanceTimeBy(OVERLAY_SETTLE_MS)
        composeTestRule.onRoot().captureRoboImage()
    }

    /**
     * The full-screen chat overlay, opened, carrying the same cast as the two dock planes.
     *
     * `dockStartHeightPx = 0` — the overlay grows from the very bottom of the window. The value only
     * decides where the growth STARTS; at the settled `Full` anchor the surface covers the window
     * either way, and 0 keeps the fixture from depending on a dock height measured elsewhere.
     */
    @Composable
    private fun FullOverlayProbe(copy: Copy) {
        val state = rememberDockFullExpandState()
        val attachments = remember { probeAttachments }
        LaunchedEffect(state) {
            // `open()` is a documented no-op until the overlay has measured the window and published
            // its anchors (it is NaN-guarded), so this waits for the offset to become a number rather
            // than firing once on the first composition and quietly doing nothing.
            snapshotFlow { state.anchored.offset }.first { !it.isNaN() }
            state.open()
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppSurface.ground()),
        ) {
            GistiFullChatOverlay(
                state = state,
                dockStartHeightPx = 0,
                onCollapse = {},
                historyContent = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = AppDimens.ScreenPaddingHorizontal,
                                vertical = AppDimens.SpacingSm,
                            ),
                        verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm),
                    ) {
                        PlaneLabel("BOTTOM-CHROME plane — GistiFullChatOverlay")
                        ChatDayDivider()
                        ChatMessageBubble(
                            message = ChatMessage(id = "a4", role = ChatRole.Assistant, content = copy.aiCode, timestamp = 0L),
                            showSenderLabel = true,
                        )
                        ChatMessageBubble(
                            message = ChatMessage(
                                id = "u3",
                                role = ChatRole.User,
                                content = copy.user,
                                timestamp = 0L,
                                costCredits = 1,
                            ),
                        )
                        AiChoiceResponse(
                            pending = planePending(copy),
                            onSelect = {},
                            onEditChange = {},
                            onEditConfirm = {},
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm)) {
                            AiChoiceChip(label = copy.escape, role = ChoiceRole.Escape, onClick = {})
                            AiChoiceChip(
                                label = copy.add,
                                role = ChoiceRole.Add,
                                onClick = {},
                                leadingIcon = Icons.Outlined.Add,
                            )
                        }
                        ChatAttachmentChipStrip(attachments = attachments, onRemove = {})
                    }
                },
                inputContent = {
                    ChatInputRow(
                        text = copy.draft,
                        onTextChange = {},
                        onSend = {},
                        onAttachFileClick = {},
                        onVoiceRecordingStarted = {},
                        onVoiceRecordingStopped = {},
                        onVoiceRecordingCancelled = {},
                        canSend = true,
                    )
                },
            )
        }
    }

    private fun shoot(
        qualifiers: String,
        dark: Boolean = false,
        copy: Copy = english,
        fontScale: Float = 1f,
        locale: Locale = Locale.ENGLISH,
    ) {
        // Both, and neither alone is enough: setQualifiers moves the Android resource configuration,
        // Compose Resources resolves off the JVM default locale.
        Locale.setDefault(locale)
        RuntimeEnvironment.setQualifiers(qualifiers)
        // The typing dots are an infinite animation — without pinning, the clock never idles.
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = base.density, fontScale = fontScale),
            ) {
                AppTheme(darkTheme = dark) { ProbeBody(copy) }
            }
        }
        composeTestRule.mainClock.advanceTimeBy(PIN_MS)
        composeTestRule.onRoot().captureRoboImage()
    }

    private data class Copy(
        val in1Hour: String,
        val tomorrowMorning: String,
        val tonight: String,
        /** The longest label the selectable row ever carries — a resolved absolute date. */
        val pickTime: String,
        val important: String,
        val repeat: String,
        val createAi: String,
        val photo: String,
        val remind: String,
        val aiLong: String,
        val aiShort: String,
        /**
         * A short answer carrying an INLINE-CODE run.
         *
         * The chrome half used to show [aiShort] — plain, markdown-free text — so the one component
         * whose colour is resolved against the BUBBLE rather than against the plane was never drawn
         * on the second plane at all. That is how a fixed `surfaceContainerHigh` code background
         * survived: it is byte-for-byte the dark chrome's own bubble colour, i.e. the patch was
         * invisible there and no frame in the suite contained one.
         */
        val aiCode: String,
        /** The pending-choice question — [AiChoiceResponse]'s own bubble, a second bubble spelling. */
        val choicePrompt: String,
        val choiceOptions: List<String>,
        val user: String,
        val draft: String,
        val escape: String,
        val add: String,
        val dockHint: String,
    )

    private val english = Copy(
        in1Hour = "In 1 hour",
        tomorrowMorning = "Tomorrow morning",
        tonight = "Tonight",
        pickTime = "Tue, 24 Sep at 18:30",
        important = "Important",
        repeat = "Every weekday",
        createAi = "Create with AI",
        photo = "Photo ➡️ list",
        remind = "Remind me…",
        aiLong = "Here's what I can do:\n\n" +
            "- **Add** items to any list\n" +
            "- **Create** a checklist from a photo or PDF\n" +
            "- Set `reminders` in natural language\n\n" +
            "Just tell me what you need — I'll keep the list tidy.",
        aiShort = "Done.",
        aiCode = "Done — I set `every weekday` on it.",
        choicePrompt = "Which list should it go in?",
        choiceOptions = listOf("Groceries", "Work"),
        user = "set a reminder for the milk run",
        draft = "remind me to call the plumber about the leaking pipe\n" +
            "tomorrow morning before the meeting\n" +
            "and add the invoice to the folder\n" +
            "then archive the old list",
        escape = "Cancel",
        add = "Add anyway",
        dockHint = "Ask Gisti to add, remind, or plan…",
    )

    private val russian = english.copy(
        in1Hour = "Через час",
        tomorrowMorning = "Завтра утром",
        tonight = "Сегодня вечером",
        pickTime = "Вт, 24 сен в 18:30",
        important = "Важное",
        repeat = "По будням",
        createAi = "Создать с ИИ",
        photo = "Фото ➡️ список",
        remind = "Напомнить…",
        aiShort = "Готово.",
        aiCode = "Готово — поставил `по будням`.",
        choicePrompt = "В какой список добавить?",
        choiceOptions = listOf("Покупки", "Работа"),
        user = "напомни купить молоко",
        escape = "Отмена",
        add = "Всё равно добавить",
        dockHint = "Спросите Gisti…",
    )

    private val hindi = english.copy(
        in1Hour = "1 घंटे में",
        tomorrowMorning = "कल सुबह",
        tonight = "आज रात",
        pickTime = "मंगल, 24 सित 18:30",
        important = "महत्वपूर्ण",
        repeat = "हर कार्यदिवस",
        createAi = "AI से बनाएं",
        photo = "फ़ोटो ➡️ सूची",
        remind = "याद दिलाएं…",
        aiShort = "हो गया।",
        aiCode = "हो गया — `हर कार्यदिवस` सेट किया।",
        choicePrompt = "किस सूची में जोड़ें?",
        choiceOptions = listOf("किराना", "काम"),
        user = "दूध खरीदने की याद दिलाएं",
        escape = "रद्द करें",
        add = "फिर भी जोड़ें",
        dockHint = "Gisti से पूछें…",
    )

    @Composable
    private fun ProbeBody(copy: Copy) {
        val hazeState = remember { HazeState() }
        val attachments = remember { probeAttachments }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppSurface.ground()),
        ) {
            // ── PLANE 1: the page. This is what ChatScreen paints behind the same components. ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppDimens.ScreenPaddingHorizontal, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm),
            ) {
                PlaneLabel("PAGE plane — ChatScreen")
                ChatDayDivider()
                ChatMessageBubble(
                    message = ChatMessage(id = "a1", role = ChatRole.Assistant, content = copy.aiLong, timestamp = 0L),
                    showSenderLabel = true,
                )
                ChatMessageBubble(message = ChatMessage(id = "a2", role = ChatRole.Assistant, content = copy.aiShort, timestamp = 0L))
                ChatMessageBubble(
                    message = ChatMessage(id = "u1", role = ChatRole.User, content = copy.user, timestamp = 0L, costCredits = 1),
                )
                ChatTypingIndicator()
                // The choice block belongs on BOTH halves. It was recorded only on the chrome, which
                // is the same asymmetry that let its question bubble keep a fixed
                // `surfaceContainerLowest` for a whole iteration — a defect is only visible on the
                // plane it was photographed on, and a second spelling of "the AI said this" can drift
                // from `ChatMessageBubble` in either direction.
                AiChoiceResponse(
                    pending = planePending(copy),
                    onSelect = {},
                    onEditChange = {},
                    onEditConfirm = {},
                )
                Row(horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm)) {
                    // Add takes its leading "+" here because production does: the icon comes from
                    // `ChoiceRole.leadingIcon()` at the AiChoiceResponse call site, not from inside
                    // the chip. Omitting it made the pair look MORE alike in the frame than the app
                    // ever shows them, which is the wrong direction for evidence to be wrong in.
                    AiChoiceChip(label = copy.escape, role = ChoiceRole.Escape, onClick = {})
                    AiChoiceChip(
                        label = copy.add,
                        role = ChoiceRole.Add,
                        onClick = {},
                        leadingIcon = Icons.Outlined.Add,
                    )
                }
                ChatAttachmentChipStrip(attachments = attachments, onRemove = {})
            }
            ChatInputRow(
                text = "",
                onTextChange = {},
                onSend = {},
                onAttachFileClick = {},
                onVoiceRecordingStarted = {},
                onVoiceRecordingStopped = {},
                onVoiceRecordingCancelled = {},
            )

            // ── PLANE 2: the bottom chrome. The SAME components inside the real dock hosts. ──
            GistiGlassChatDock(
                hazeState = hazeState,
                chipsContent = {
                    Column(verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm)) {
                        PlaneLabel(
                            text = "BOTTOM-CHROME plane — GistiGlassChatDock",
                            modifier = Modifier.padding(
                                horizontal = AppDimens.ScreenPaddingHorizontal,
                            ),
                        )
                        // Last chip selected, and the longest label in the row resolved to an
                        // absolute date — the two cases that squeeze the neighbours.
                        GistiSelectableChipRow(
                            chips = gistiItemCreatePromptChips(
                                in1HourLabel = copy.in1Hour,
                                tomorrowMorningLabel = copy.tomorrowMorning,
                                tonightLabel = copy.tonight,
                                pickTimeLabel = copy.pickTime,
                                importantLabel = copy.important,
                                repeatLabel = copy.repeat,
                                selectedReminder = null,
                                importantSelected = false,
                                repeatSelected = true,
                            ),
                            onChipClick = {},
                        )
                        GistiPromptChips(
                            chips = listOf(
                                GistiPromptChip("✨", copy.createAi, GistiQuickAction.CREATE_WITH_AI),
                                GistiPromptChip("📷", copy.photo, GistiQuickAction.PHOTO),
                                GistiPromptChip("🔔", copy.remind, GistiQuickAction.REMIND),
                            ),
                            onChipClick = {},
                        )
                    }
                },
                pillContent = {
                    Column {
                        Column(
                            modifier = Modifier.padding(
                                horizontal = AppDimens.ScreenPaddingHorizontal,
                            ),
                            verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm),
                        ) {
                            // The chrome half must carry the SAME cast as the page half above, or a
                            // component whose colour stops resolving against the surface under it is
                            // simply absent from the evidence. Everything from here to the attachment
                            // strip was missing, and each line is one of the roles that can only be
                            // wrong on this plane: the divider and the cost badge are `quietFill`
                            // (no outline, so the fill is the only channel), the choice bubble is
                            // `raised` + a soft hairline, the two neutral chips are `raised` + the
                            // firm one, and `aiCode` is the inline-code patch that has to step off
                            // the BUBBLE rather than off the plane.
                            ChatDayDivider()
                            ChatMessageBubble(
                                message = ChatMessage(id = "a3", role = ChatRole.Assistant, content = copy.aiCode, timestamp = 0L),
                            )
                            ChatMessageBubble(
                                message = ChatMessage(
                                    id = "u2",
                                    role = ChatRole.User,
                                    content = copy.user,
                                    timestamp = 0L,
                                    costCredits = 1,
                                ),
                            )
                            AiChoiceResponse(
                                pending = planePending(copy),
                                onSelect = {},
                                onEditChange = {},
                                onEditConfirm = {},
                                compact = true,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm)) {
                                AiChoiceChip(label = copy.escape, role = ChoiceRole.Escape, onClick = {})
                                AiChoiceChip(
                                    label = copy.add,
                                    role = ChoiceRole.Add,
                                    onClick = {},
                                    leadingIcon = Icons.Outlined.Add,
                                )
                            }
                            ChatAttachmentChipStrip(attachments = attachments, onRemove = {})
                        }
                        ChatInputRow(
                            text = copy.draft,
                            onTextChange = {},
                            onSend = {},
                            onAttachFileClick = {},
                            onVoiceRecordingStarted = {},
                            onVoiceRecordingStopped = {},
                            onVoiceRecordingCancelled = {},
                            canSend = true,
                        )
                        GistiChatDock(
                            placeholder = copy.dockHint,
                            onClick = {},
                            onMicClick = {},
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = AppDimens.ScreenPaddingHorizontal),
                        )
                    }
                },
            )

            // ── PLANE 2b: the quick-capture dock — the third bottom-chrome host. ──
            QuickCaptureDock(
                text = "",
                onTextChange = {},
                onAdd = {},
                placeholder = copy.dockHint,
                aboveInput = {
                    GistiSelectableChipRow(
                        chips = gistiItemCreatePromptChips(
                            in1HourLabel = copy.in1Hour,
                            tomorrowMorningLabel = copy.tomorrowMorning,
                            tonightLabel = copy.tonight,
                            pickTimeLabel = copy.pickTime,
                            importantLabel = copy.important,
                            repeatLabel = copy.repeat,
                            selectedReminder = GistiItemCreateAction.REMIND_TONIGHT,
                            importantSelected = false,
                            repeatSelected = false,
                        ),
                        onChipClick = {},
                    )
                },
            )
        }
    }

    /**
     * The pending-choice block: a question bubble plus its chips. Rendered on every plane.
     *
     * That bubble is a SECOND spelling of "the AI said this" — a different composable from
     * [ChatMessageBubble] doing the same job — which is exactly how it kept a fixed
     * `surfaceContainerLowest` (ΔL\* −6.2 below the dark chrome, a hole in the dock) for a whole
     * iteration after the message bubble was moved off it. One frame with both is what makes two
     * spellings of one thing diverging visible at a glance.
     *
     * Every action is [ChoiceAction.Dismiss]: nothing here is executed, the block is rendered and
     * photographed.
     */
    private fun planePending(copy: Copy): PendingChoice = PendingChoice(
        choice = ChatChoice(
            prompt = copy.choicePrompt,
            options = copy.choiceOptions.mapIndexed { index, label ->
                ChoiceOption(
                    id = "candidate_$index",
                    label = label,
                    role = ChoiceRole.Default,
                    action = ChoiceAction.Dismiss,
                )
            },
            escape = ChoiceOption(
                id = "escape",
                label = copy.escape,
                role = ChoiceRole.Escape,
                action = ChoiceAction.Dismiss,
            ),
        ),
    )

    @Composable
    private fun PlaneLabel(text: String, modifier: Modifier = Modifier) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
    }

    private companion object {
        /** Past the enter animations, so only the ongoing typing dots are pinned at a fixed phase. */
        const val PIN_MS = 700L

        /**
         * Enough simulated time for the full overlay's open spring to settle on any window height.
         * Deliberately far past it: the frame has to show the SETTLED overlay, and a value tuned to
         * the millisecond would photograph a partly-grown surface the next time the window changes.
         */
        const val OVERLAY_SETTLE_MS = 5_000L

        val probeAttachments = listOf(
            ChatAttachment("doc://recipe", "application/pdf", "Recipe.pdf", 20_480L),
            ChatAttachment("doc://notes", "text/plain", "notes.txt", 512L),
            ChatAttachment("audio://memo", "audio/mp4", "memo.m4a", 64_000L),
        )
    }
}
