package com.antonchuraev.homesearchchecklist.store

import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.common.api.AttachmentOpener
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTheme
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatMessage
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatRole
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.ChatScreen
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.ChatScreenState
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.InputDataType
import com.antonchuraev.homesearchchecklist.feature.analyze.presentation.AnalyzeScreenContent
import com.antonchuraev.homesearchchecklist.feature.analyze.presentation.AnalyzeScreenState
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFill
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistViewMode
import com.antonchuraev.homesearchchecklist.feature.create.data.repository.TemplatesRepositoryImpl
import com.antonchuraev.homesearchchecklist.feature.create.domain.model.TemplateCategory
import com.antonchuraev.homesearchchecklist.feature.create.presentation.templates.TemplatesScreen
import com.antonchuraev.homesearchchecklist.feature.create.presentation.templates.TemplatesScreenState
import com.antonchuraev.homesearchchecklist.feature.home.presentation.calendar.CalendarScreen
import com.antonchuraev.homesearchchecklist.feature.home.presentation.calendar.CalendarState
import com.antonchuraev.homesearchchecklist.feature.home.presentation.detail.ChecklistDetailContent
import com.antonchuraev.homesearchchecklist.feature.home.presentation.detail.ChecklistDetailState
import com.antonchuraev.homesearchchecklist.feature.home.presentation.inbox.InboxPage
import com.antonchuraev.homesearchchecklist.feature.home.presentation.inbox.InboxScreen
import com.antonchuraev.homesearchchecklist.feature.home.presentation.inbox.InboxScreenState
import com.antonchuraev.homesearchchecklist.feature.home.presentation.inbox.InboxTask
import com.antonchuraev.homesearchchecklist.feature.home.presentation.today.TodayReminderItem
import com.antonchuraev.homesearchchecklist.feature.home.presentation.today.TodayScreenState
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.CreditsBadge
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.premium.CreditsBadgeProvider
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.premium.PremiumEntryPoint
import com.antonchuraev.homesearchchecklist.feature.paywall.presentation.components.CreditsChipSource
import com.antonchuraev.homesearchchecklist.navigation.V2Destination
import com.antonchuraev.homesearchchecklist.navigation.V2NavigationShell
import com.antonchuraev.homesearchchecklist.navigation.V2ShellMetrics
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.awt.image.BufferedImage
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Google Play listing frames built from REAL 1.21.0 screens on fake state — not a screenshot golden.
 *
 * Three passes, ordered by method name ([FixMethodOrder]) because each pass reads the previous one's
 * files:
 *  - `a*` — the raw screen, 360x660dp at xxxhdpi (1440x2640px). A window of its own, so every screen
 *    that reads `LocalConfiguration.screenWidthDp` resolves Compact exactly as on a phone. The web
 *    capture for frame 7 gets a 1280dp desktop window instead.
 *  - `b*` — the store frame, 720x1280dp at xxhdpi = 2160x3840px: brand gradient, caption, device, and
 *    the raw capture drawn into the device screen.
 *  - `c*` — the 1024x500 feature graphic.
 *
 * Every frame is then flattened onto an opaque ground and re-encoded as RGB PNG, because Play rejects
 * RGBA and Roborazzi writes ARGB. Marketing copy — captions, the frame-2 result card, the frame-4
 * "filled by AI" pill, the frame-7 browser chrome and "Synced" pill — is literal on purpose: it is
 * frame composition, not app UI, and never ships in the APK. Layout numbers come from the store
 * DESIGN_SPEC §1/§3/§5 (base px x 2/3 = dp).
 *
 * Run (all outputs under `composeApp/build/store-listing/`, final RGB set in `final/`):
 *   ./gradlew :composeApp:testAndroidHostTest --tests "*StoreListingFramesScreenshotTest*" --rerun
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class StoreListingFramesScreenshotTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val defaultLocale: Locale = Locale.getDefault()
    private val defaultTimeZone: java.util.TimeZone = java.util.TimeZone.getDefault()

    @Before
    fun setUp() {
        System.setProperty("java.awt.headless", "true")
        Locale.setDefault(Locale.ENGLISH)
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"))
        stopKoin()
        startKoin {
            modules(
                module {
                    single<AnalyticsTracker> { NoopAnalytics }
                    single<AppLogger> { NoopLogger }
                    single { AttachmentOpener() }
                    single<PremiumEntryPoint> { PremiumEntryPoint { _, _ -> } }
                    // A Free user well above any out-of-credits UI: the chip shows a bare number, never
                    // the "Get More" CTA (store policy forbids upsell wording inside the device screen).
                    single<CreditsBadgeProvider> { FixedBadge }
                },
            )
        }
    }

    @After
    fun tearDown() {
        Locale.setDefault(defaultLocale)
        java.util.TimeZone.setDefault(defaultTimeZone)
        stopKoin()
    }

    // ── Pass a: raw screens ──────────────────────────────────────────────────────────────────────

    @Test
    @Config(qualifiers = RawQualifiers)
    fun a1_rawChat() = captureRaw("chat") { ChatUnderTest() }

    @Test
    @Config(qualifiers = RawQualifiers)
    fun a2_rawAnalyze() = captureRaw("analyze") { AnalyzeUnderTest() }

    /**
     * A TALLER window than the other screens, cropped in pass b to the 660dp band BELOW the top bar.
     *
     * Store policy: the bar's title is "New project" (or "New Checklist" in the control arm) and "new"
     * is a forbidden word anywhere on the graphic, inside the device included. The crop shows a real
     * region of the real screen — starting at "Create with AI" — instead of rewriting the title, and the
     * extra window height is what lets a third row of templates into the band.
     */
    @Test
    @Config(qualifiers = "w360dp-h960dp-port-xxxhdpi")
    fun a3_rawTemplates() {
        // The REAL bundled gallery (the same Compose Resource the app reads), not a hand-made list.
        val categories = runBlocking { TemplatesRepositoryImpl(NoopLogger).getTemplatesByCategory() }
        captureRaw("templates") { TemplatesUnderTest(categories) }
    }

    @Test
    @Config(qualifiers = RawQualifiers)
    fun a4_rawDetail() = captureRaw("detail") { DetailUnderTest(groceries()) }

    @Test
    @Config(qualifiers = RawQualifiers)
    fun a5_rawCalendar() = captureRaw("calendar") { CalendarUnderTest() }

    @Test
    @Config(qualifiers = RawQualifiers)
    fun a6_rawWeekly() = captureRaw("weekly") { DetailUnderTest(morningRoutine(), weeklyToday = 3) }

    @Test
    @Config(qualifiers = RawQualifiers)
    fun a7_rawTripPhone() = captureRaw("trip_phone") { DetailUnderTest(tripPacking()) }

    /** The same checklist in a desktop window: the v2 shell resolves its permanent drawer here. */
    @Test
    @Config(qualifiers = "w1280dp-h900dp-land-xhdpi")
    fun a7_rawTripWeb() = captureRaw("trip_web") {
        V2NavigationShell(
            selectedTab = V2Destination.Projects,
            onNavigate = {},
            onOpenChat = {},
            onOpenSettings = {},
            onOpenUpdates = {},
        ) { DetailUnderTest(tripPacking()) }
    }

    @Test
    @Config(qualifiers = RawQualifiers)
    fun a8_rawInbox() = captureRaw("inbox") { InboxUnderTest() }

    // ── Pass b: store frames ─────────────────────────────────────────────────────────────────────

    @Test
    @Config(qualifiers = FrameQualifiers)
    fun b1_frame01() = composeFrame("01", "Just tell Gisti\nwhat you need", "Add tasks, reminders and lists by chat", "chat", 165f)

    @Test
    @Config(qualifiers = FrameQualifiers)
    fun b2_frame02() = composeFrame("02", "Any content\n→ checklist", "Photo, PDF, voice, text or link", "analyze", 195f) {
        ResultCard(modifier = Modifier.offset(x = 373.3.dp, y = 760.dp))
    }

    @Test
    @Config(qualifiers = FrameQualifiers)
    fun b3_frame03() = composeFrame(
        "03", "81 templates, or ask AI", "Home, travel, study, shopping and more", "templates", 165f,
        cropTopDp = CompactTopBarDp,
    )

    @Test
    @Config(qualifiers = FrameQualifiers)
    fun b4_frame04() = composeFrame("04", "AI fills your list\nfrom a photo", "Snap it, AI ticks the matching items", "detail", 195f) {
        // A frame ANNOTATION, never on the device: the app has no such toast, and a dark pill on the
        // screen (the spec's position) or on the bezel read as app UI. It sits in the band between the
        // caption (ends ~265dp) and the device (starts 346.7dp), styled like frame 7's "Synced".
        FilledByAiPill(modifier = Modifier.align(Alignment.TopCenter).offset(y = 283.dp))
    }

    @Test
    @Config(qualifiers = FrameQualifiers)
    fun b5_frame05() = composeFrame("05", "Reminders that repeat", "Every due task on one calendar", "calendar", 165f)

    @Test
    @Config(qualifiers = FrameQualifiers)
    fun b6_frame06() = composeFrame("06", "Plan your week,\nday by day", "Weekly mode for routines and habits", "weekly", 195f)

    @Test
    @Config(qualifiers = FrameQualifiers)
    fun b7_frame07() {
        val phone = loadRaw("trip_phone")
        val web = loadRaw("trip_web")
        composeTestRule.setContent { SyncFrame(phone = phone, web = web) }
        exportFlattened("07", expectedWidth = 2160, expectedHeight = 3840)
    }

    @Test
    @Config(qualifiers = FrameQualifiers)
    fun b8_frame08() = composeFrame("08", "Inbox, Projects,\nOverview", "Capture now, sort it later", "inbox", 195f)

    // ── Pass c: feature graphic ──────────────────────────────────────────────────────────────────

    @Test
    @Config(qualifiers = "w512dp-h250dp-land-xxxhdpi")
    fun c0_featureGraphic() {
        val screen = loadRaw("chat")
        composeTestRule.setContent { FeatureGraphic(screen) }
        // Rendered at 2048x1000 and area-averaged down: a direct 1024 render sampled the 1440px chat
        // capture into a ~285px screen and the in-phone text broke into aliased fragments.
        exportFlattened("feature_graphic", expectedWidth = 2048, expectedHeight = 1000, outWidth = 1024, outHeight = 500)
    }

    // ── Screens under test (real composables, fake state from DESIGN_SPEC §4) ────────────────────

    @Composable
    private fun ChatUnderTest() {
        val t = FixedNow
        fun user(id: String, text: String, at: Long) =
            ChatMessage(id = id, role = ChatRole.User, content = text, timestamp = at)
        fun ai(id: String, text: String, at: Long, linked: Long? = null) =
            ChatMessage(id = id, role = ChatRole.Assistant, content = text, timestamp = at, linkedChecklistId = linked)
        ChatScreen(
            state = ChatScreenState(
                messages = listOf(
                    user("u1", "Add milk, eggs and bread to Groceries", t - 300_000),
                    ai("a1", "Added 3 items to Groceries.", t - 290_000, linked = 1L),
                    user("u2", "Remind me to water the plants every Friday at 6 pm", t - 200_000),
                    ai("a2", "Done. Water the plants repeats every Friday at 6:00 PM.", t - 190_000),
                    user("u3", "Plan a packing list for a weekend trip", t - 10_000),
                ),
                creditBalance = Credits,
            ),
            onIntent = {},
        )
    }

    /**
     * Photo picked. In 1.21.0 the picker shows the chosen file by NAME only ("Selected receipt.jpg"),
     * not as an image, so no receipt photo — and no price — is drawn anywhere on this screen.
     */
    @Composable
    private fun AnalyzeUnderTest() {
        AnalyzeScreenContent(
            screenState = AnalyzeScreenState(
                selectedInputType = InputDataType.PHOTO,
                selectedFilePath = "/storage/emulated/0/Pictures/receipt.jpg",
                selectedFileName = "receipt.jpg",
                aiCredits = Credits,
                aiActionCost = 20,
            ),
            onIntent = {},
        )
    }

    @Composable
    private fun TemplatesUnderTest(categories: List<TemplateCategory>) {
        TemplatesScreen(
            state = TemplatesScreenState(
                isLoading = false,
                categories = categories,
                filteredCategories = categories,
            ),
            onIntent = {},
            // v2 arm, as App.kt mounts the gallery from the Projects tab.
            useProjectTitle = true,
            onCreateWithAi = {},
        )
    }

    /** The detail screen as the v2 arm mounts it: inline add row, AI action in the bar, no dock. */
    @Composable
    private fun DetailUnderTest(state: ChecklistDetailState.Content, weeklyToday: Int? = null) {
        ChecklistDetailContent(
            state = state,
            onIntent = {},
            useInlineAddRow = true,
            onOpenChat = {},
            weeklyTodayWeekday = weeklyToday,
        )
    }

    private fun checklistState(
        id: Long,
        name: String,
        rows: List<Triple<String, Boolean, Int?>>,
        viewMode: ChecklistViewMode = ChecklistViewMode.Standard,
    ): ChecklistDetailState.Content {
        val templateItems = rows.map { (text, _, weekday) -> ChecklistItem(text = text, weekday = weekday) }
        val fillItems = rows.zip(templateItems).map { (row, template) ->
            ChecklistFillItem(
                text = row.first,
                checked = row.second,
                weekday = row.third,
                templateItemId = template.id,
            )
        }
        return ChecklistDetailState.Content(
            checklist = Checklist(id = id, name = name, items = templateItems, viewMode = viewMode),
            defaultFill = ChecklistFill(id = id, checklistId = id, name = name, items = fillItems, isDefault = true),
        )
    }

    private fun groceries() = checklistState(
        id = 1L,
        name = "Weekly groceries",
        rows = listOf(
            Triple("Eggs", true, null),
            Triple("Milk", true, null),
            Triple("Whole-grain bread", true, null),
            Triple("Coffee beans", true, null),
            Triple("Orange juice", false, null),
            Triple("Olive oil", false, null),
        ),
    )

    private fun morningRoutine() = checklistState(
        id = 2L,
        name = "Morning routine",
        viewMode = ChecklistViewMode.Weekly,
        rows = listOf(
            Triple("Stretch for 10 minutes", true, 1),
            Triple("Drink a glass of water", true, 1),
            Triple("Stretch for 10 minutes", true, 2),
            Triple("Drink a glass of water", true, 2),
            Triple("Stretch for 10 minutes", true, 3),
            Triple("Drink a glass of water", true, 3),
            Triple("Plan the day", true, 3),
            Triple("Read 20 pages", false, 3),
            Triple("Walk outside", false, 3),
        ),
    )

    private fun tripPacking() = checklistState(
        id = 3L,
        name = "Weekend trip packing",
        rows = listOf(
            Triple("Passport", true, null),
            Triple("Phone charger", true, null),
            Triple("Sunscreen", false, null),
            Triple("Hiking shoes", false, null),
            Triple("Rain jacket", false, null),
        ),
    )

    @Composable
    private fun CalendarUnderTest() {
        fun row(id: String, name: String, time: String, recurring: Boolean) = TodayReminderItem(
            id = id,
            itemName = name,
            checklistName = "Home",
            checklistId = 1L,
            fillId = null,
            timeLabel = time,
            isPastDue = false,
            isRecurring = recurring,
        )
        V2NavigationShell(
            selectedTab = V2Destination.Calendar,
            onNavigate = {},
            onOpenChat = {},
            onOpenSettings = {},
            onOpenUpdates = {},
            content = {
                CalendarScreen(
                    todayState = TodayScreenState.Success(
                        dateLabel = "Friday, September 18",
                        pastDue = emptyList(),
                        today = listOf(
                            row("r1", "Gym", "7:30 AM", recurring = true),
                            row("r2", "Call mom", "5:00 PM", recurring = false),
                            row("r3", "Water the plants", "6:00 PM", recurring = true),
                            row("r4", "Take out recycling", "8:00 PM", recurring = true),
                        ),
                    ),
                    calendarState = CalendarState.Empty,
                    drawerState = null,
                    onTodayReminderClick = { _, _ -> },
                    onTodayRetry = {},
                    onCalendarIntent = {},
                    captureEnabled = true,
                    contentBottomPadding = V2ShellMetrics.FabBandPadding,
                    creditsSource = CreditsChipSource.V2_CALENDAR,
                )
            },
        )
    }

    @Composable
    private fun InboxUnderTest() {
        // Dock CLOSED, inside the real v2 shell. The open dock was captured first and rejected on the
        // frame: its real content scrim dims the whole list grey, hides two of the five rows and takes
        // the bottom navigation away — the three tabs this frame's caption names.
        V2NavigationShell(
            selectedTab = V2Destination.Inbox,
            onNavigate = {},
            onOpenChat = {},
            onOpenSettings = {},
            onOpenUpdates = {},
        ) { InboxInShell() }
    }

    @Composable
    private fun InboxInShell() {
        val hour = 3_600_000L
        fun task(text: String, dueAt: Long? = null, important: Boolean = false) = InboxTask(
            item = ChecklistFillItem(
                text = text,
                checked = false,
                priority = if (important) 1 else 0,
                templateItemId = "t-$text",
            ).withReminderAt(dueAt),
        )
        InboxScreen(
            state = InboxScreenState.Content(
                pages = listOf(
                    InboxPage(
                        checklistId = 1L,
                        title = "Inbox",
                        isInbox = true,
                        tasks = listOf(
                            task("Call the plumber", dueAt = FixedNow + 7 * hour),
                            task("Book train tickets", dueAt = FixedNow + 24 * hour),
                            task("Renew passport", dueAt = FixedNow + 48 * hour, important = true),
                            task("Buy a birthday gift"),
                            task("Send the weekly report"),
                        ),
                    ),
                    InboxPage(checklistId = 2L, title = "Home", isInbox = false, tasks = emptyList()),
                    InboxPage(checklistId = 3L, title = "Work", isInbox = false, tasks = emptyList()),
                    InboxPage(checklistId = 4L, title = "Trip", isInbox = false, tasks = emptyList()),
                ),
                nowMillis = FixedNow,
            ),
            contentBottomPadding = V2ShellMetrics.FabBandPadding,
            onIntent = {},
            snackbarHostState = SnackbarHostState(),
            swallowRootBack = false,
            createDockOpen = false,
            onCreateDockDismiss = {},
            creditsSource = CreditsChipSource.V2_INBOX,
        )
    }

    // ── Composition ──────────────────────────────────────────────────────────────────────────────

    @Composable
    private fun Caption(headline: String, sub: String) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 74.67.dp, start = 40.dp, end = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = headline,
                style = TextStyle(
                    color = Color.White,
                    fontSize = 64.sp,
                    lineHeight = 66.67.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-1.33).sp,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 2,
            )
            Spacer(modifier = Modifier.height(18.67.dp))
            Text(
                text = sub,
                style = TextStyle(
                    color = Color.White.copy(alpha = 0.94f),
                    fontSize = 30.67.sp,
                    lineHeight = 38.67.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 1,
            )
        }
    }

    @Composable
    private fun StoreFrame(
        headline: String,
        sub: String,
        screen: ImageBitmap,
        angle: Float,
        floats: @Composable BoxScope.() -> Unit,
    ) {
        Box(modifier = Modifier.fillMaxSize().cssGradient(angle)) {
            Caption(headline, sub)
            Device(
                width = 480.dp,
                screen = screen,
                shadow = true,
                modifier = Modifier.offset(x = 120.dp, y = 346.67.dp),
            )
            floats()
        }
    }

    /** Frame 7: the browser behind (bleeding off the right edge), the phone in front, "Synced" on top. */
    @Composable
    private fun SyncFrame(phone: ImageBitmap, web: ImageBitmap) {
        Box(modifier = Modifier.fillMaxSize().cssGradient(165f)) {
            Caption("Your lists follow you", "Phone, tablet or browser, always in sync")
            Box(
                modifier = Modifier
                    .offset(x = 220.dp, y = 373.3.dp)
                    .size(666.7.dp, 506.7.dp)
                    .softShadow(0.9f)
                    .background(Color(0xFF0E0C11), RoundedCornerShape(18.67.dp))
                    .padding(9.33.dp),
            ) {
                Column(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)).background(Color.White)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.67.dp)
                            .background(Color(0xFFF1EFF4))
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        repeat(3) {
                            Box(Modifier.size(9.dp).clip(CircleShape).background(Color(0xFFC9C5CE)))
                            Spacer(Modifier.width(6.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Row(
                            modifier = Modifier
                                .height(28.dp)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White)
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Lock, null, tint = AddressInk, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("app.gisti-ai.com", style = TextStyle(color = AddressInk, fontSize = 16.sp))
                        }
                    }
                    // The phone covers the browser's left ~230dp, and the detail pane's text is
                    // left-aligned, so a plain fit showed only empty row outlines. The 1280x900dp
                    // capture is drawn at 0.6 and shifted so the pane (starting ~280dp into the
                    // capture, after the permanent drawer) begins just right of the phone.
                    Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
                        Image(
                            bitmap = web,
                            contentDescription = null,
                            contentScale = ContentScale.FillBounds,
                            filterQuality = FilterQuality.High,
                            modifier = Modifier
                                .wrapContentSize(Alignment.TopStart, unbounded = true)
                                .offset(x = 62.dp)
                                .size(768.dp, 540.dp),
                        )
                    }
                }
            }
            Device(
                width = 412.8.dp,
                screen = phone,
                shadow = true,
                modifier = Modifier.offset(x = 46.67.dp, y = 480.dp),
            )
            Row(
                modifier = Modifier
                    .offset(x = 400.dp, y = 333.3.dp)
                    .height(56.dp)
                    .softShadow(0.35f)
                    .background(Color.White, RoundedCornerShape(28.dp))
                    .padding(horizontal = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(13.3.dp).clip(CircleShape).background(Color(0xFF2E9E5B)))
                Spacer(Modifier.width(10.dp))
                Text("Synced", style = TextStyle(color = StatusInk, fontSize = 22.67.sp, fontWeight = FontWeight.SemiBold))
            }
        }
    }

    /** Frame 2 float: the checklist the photo turns into (spec §3). Plain frame text, not app UI. */
    @Composable
    private fun ResultCard(modifier: Modifier) {
        Column(
            modifier = modifier
                .width(313.3.dp)
                .softShadow(0.5f)
                .background(Color.White, RoundedCornerShape(18.67.dp))
                .padding(horizontal = 22.67.dp, vertical = 20.dp),
        ) {
            Text("Weekly groceries", style = TextStyle(color = StatusInk, fontSize = 22.67.sp, fontWeight = FontWeight.Bold))
            Text("6 items", style = TextStyle(color = AddressInk, fontSize = 16.sp, fontWeight = FontWeight.Medium))
            Spacer(Modifier.height(10.dp))
            listOf("Eggs", "Milk", "Whole-grain bread", "Coffee beans").forEach { row ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                    Icon(Icons.Outlined.CheckBoxOutlineBlank, null, tint = AddressInk, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(row, style = TextStyle(color = StatusInk, fontSize = 18.67.sp))
                }
            }
        }
    }

    /** Frame 4 float (spec §3). The app has no such string — this is frame composition. */
    @Composable
    private fun FilledByAiPill(modifier: Modifier) {
        Row(
            modifier = modifier
                .height(48.dp)
                .softShadow(0.2f)
                .background(Color.White, RoundedCornerShape(24.dp))
                .padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.AutoAwesome, null, tint = GradientStart, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                "4 of 6 filled by AI",
                style = TextStyle(color = StatusInk, fontSize = 21.sp, fontWeight = FontWeight.SemiBold),
            )
        }
    }

    @Composable
    private fun FeatureGraphic(screen: ImageBitmap) {
        Box(modifier = Modifier.fillMaxSize().cssGradient(120f)) {
            Column(modifier = Modifier.offset(x = 64.dp, y = 52.dp).width(250.dp)) {
                Row(
                    modifier = Modifier
                        .height(17.dp)
                        .clip(RoundedCornerShape(8.5.dp))
                        .background(Color.White.copy(alpha = 0.18f))
                        .padding(horizontal = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(Modifier.size(4.5.dp).clip(CircleShape).background(Color(0xFF5FD08A)))
                    Text(
                        text = "AI · CHECKLISTS · REMINDERS",
                        style = TextStyle(color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.3.sp),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Just tell Gisti —\nAI does the rest",
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 25.sp,
                        lineHeight = 26.5.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.5).sp,
                    ),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Add tasks, reminders and lists by chat",
                    style = TextStyle(
                        color = Color.White.copy(alpha = 0.94f),
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }
            Device(
                width = 104.4.dp,
                screen = screen,
                shadow = false,
                modifier = Modifier.offset(x = 335.dp, y = 25.dp),
            )
        }
    }

    /**
     * The phone: near-black bezel, a drawn status bar (the capture has none — Robolectric reports no
     * insets), then the raw capture scaled into the screen. All sizes proportional to [width] so the
     * frames and the feature graphic share one device.
     */
    @Composable
    private fun Device(width: Dp, screen: ImageBitmap, shadow: Boolean, modifier: Modifier = Modifier) {
        val k = width.value / 480f
        val bezel = (10f * k).dp
        val screenW = width - bezel * 2
        val u = screenW.value / 360f // one app-dp inside the device, in canvas dp
        val statusH = (24f * u).dp
        val captureH = (660f * u).dp
        val outer = RoundedCornerShape((56f * k).dp)
        val statusColor = Color(screen.pixelAt(20, 20))
        Box(
            modifier = modifier
                .size(width, bezel * 2 + statusH + captureH)
                // Spec shadow: y 40 / blur 80 / #0A143C 28% (base px). Robolectric renders
                // `Modifier.shadow` as a flat pale slab below the device — seen on the first frames —
                // so the blur is approximated by stacked, growing, near-transparent rounded rects.
                .then(if (shadow) Modifier.softShadow(k) else Modifier)
                .background(Color(0xFF0E0C11), outer)
                .padding(bezel),
        ) {
            Column(modifier = Modifier.clip(RoundedCornerShape((46.67f * k).dp)).background(statusColor)) {
                Row(
                    modifier = Modifier
                        .size(screenW, statusH)
                        .padding(horizontal = (20f * u).dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "10:00",
                        style = TextStyle(color = StatusInk, fontSize = (13f * u).sp, fontWeight = FontWeight.Medium),
                        modifier = Modifier.padding(start = (8f * u).dp),
                    )
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Filled.Wifi, null, tint = StatusInk, modifier = Modifier.size((15f * u).dp))
                    Spacer(Modifier.width((4f * u).dp))
                    Icon(Icons.Filled.BatteryFull, null, tint = StatusInk, modifier = Modifier.size((15f * u).dp))
                    Spacer(Modifier.width((8f * u).dp))
                }
                Image(
                    bitmap = screen,
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    filterQuality = FilterQuality.High,
                    modifier = Modifier.size(screenW, captureH),
                )
            }
        }
    }

    // ── Harness ──────────────────────────────────────────────────────────────────────────────────

    private fun captureRaw(name: String, content: @Composable () -> Unit) {
        composeTestRule.setContent { AppTheme(darkTheme = false) { content() } }
        val file = File("$OutDir/raw/$name.png")
        file.parentFile?.mkdirs()
        composeTestRule.onRoot().captureRoboImage(
            filePath = file.path,
            roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record),
        )
    }

    private fun composeFrame(
        id: String,
        headline: String,
        sub: String,
        raw: String,
        angle: Float,
        cropTopDp: Int = 0,
        floats: @Composable BoxScope.() -> Unit = {},
    ) {
        val screen = loadRaw(raw, cropTopDp)
        composeTestRule.setContent { StoreFrame(headline, sub, screen, angle, floats) }
        exportFlattened(id, expectedWidth = 2160, expectedHeight = 3840)
    }

    /** [cropTopDp] > 0 cuts a 360x660dp band starting that far down a taller xxxhdpi capture. */
    private fun loadRaw(name: String, cropTopDp: Int = 0): ImageBitmap {
        val file = File("$OutDir/raw/$name.png")
        check(file.exists()) { "Raw capture ${file.absolutePath} missing — run the whole class, pass a before b/c." }
        val bitmap = BitmapFactory.decodeFile(file.path)
        if (cropTopDp == 0) return bitmap.asImageBitmap()
        val top = cropTopDp * RawPxPerDp
        val height = 660 * RawPxPerDp
        check(bitmap.height >= top + height) { "$name: ${bitmap.height}px is too short for a crop at ${cropTopDp}dp" }
        return android.graphics.Bitmap.createBitmap(bitmap, 0, top, bitmap.width, height).asImageBitmap()
    }

    private fun exportFlattened(
        name: String,
        expectedWidth: Int,
        expectedHeight: Int,
        outWidth: Int = expectedWidth,
        outHeight: Int = expectedHeight,
    ) {
        val rgba = File("$OutDir/rgba/$name.png")
        rgba.parentFile?.mkdirs()
        composeTestRule.onRoot().captureRoboImage(
            filePath = rgba.path,
            roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record),
        )
        val src = ImageIO.read(rgba)
        check(src.width == expectedWidth && src.height == expectedHeight) {
            "$name: captured ${src.width}x${src.height}, expected ${expectedWidth}x$expectedHeight"
        }
        val rgb = BufferedImage(outWidth, outHeight, BufferedImage.TYPE_INT_RGB)
        val g = rgb.createGraphics()
        g.color = java.awt.Color(0x1E, 0x88, 0xE5)
        g.fillRect(0, 0, outWidth, outHeight)
        val scaled = if (outWidth == src.width) {
            src
        } else {
            src.getScaledInstance(outWidth, outHeight, java.awt.Image.SCALE_AREA_AVERAGING)
        }
        g.drawImage(scaled, 0, 0, null)
        g.dispose()
        val out = File("$OutDir/final/$name.png")
        out.parentFile?.mkdirs()
        ImageIO.write(rgb, "png", out)
        check(!ImageIO.read(out).colorModel.hasAlpha()) { "$name: exported PNG still carries alpha" }
    }

    private fun ImageBitmap.pixelAt(x: Int, y: Int): Int {
        val buffer = IntArray(1)
        readPixels(buffer, startX = x, startY = y, width = 1, height = 1)
        return buffer[0]
    }

    private fun Modifier.softShadow(k: Float): Modifier = drawBehind {
        val steps = 16
        val offsetY = 26.67f * k * density
        val blur = 53.3f * k * density
        for (i in 0 until steps) {
            val spread = blur * (i + 1) / steps
            drawRoundRect(
                color = Color(0xFF0A143C).copy(alpha = 0.28f / steps),
                topLeft = Offset(-spread / 2, offsetY - spread / 2),
                size = Size(size.width + spread, size.height + spread),
                cornerRadius = CornerRadius(minOf(56f * k * density, size.height / 2) + spread / 2),
            )
        }
    }

    /** CSS `linear-gradient(<angle>deg, Start, End)` over the whole node. */
    private fun Modifier.cssGradient(angleDeg: Float): Modifier = drawBehind {
        val rad = Math.toRadians(angleDeg.toDouble())
        val dx = sin(rad).toFloat()
        val dy = -cos(rad).toFloat()
        val half = (abs(size.width * dx) + abs(size.height * dy)) / 2f
        drawRect(
            Brush.linearGradient(
                colors = listOf(GradientStart, GradientEnd),
                start = Offset(center.x - dx * half, center.y - dy * half),
                end = Offset(center.x + dx * half, center.y + dy * half),
            ),
        )
    }

    private object FixedBadge : CreditsBadgeProvider {
        private val badge = CreditsBadge(credits = Credits, isPremium = false)
        override fun badge(): Flow<CreditsBadge> = flowOf(badge)
        override fun currentBadge(): CreditsBadge = badge
    }

    private object NoopLogger : AppLogger {
        override fun debug(tag: String, message: String) {}
        override fun info(tag: String, message: String) {}
        override fun warning(tag: String, message: String) {}
        override fun error(tag: String, message: String, throwable: Throwable?) {}
    }

    private object NoopAnalytics : AnalyticsTracker {
        override fun setUserId(userId: String) {}
        override fun setUserProperties(properties: Map<String, Any>) {}
        override fun screenView(name: String) {}
        override fun event(name: String, params: Map<String, Any>) {}
    }

    private companion object {
        const val RawQualifiers = "w360dp-h660dp-port-xxxhdpi"
        const val FrameQualifiers = "w720dp-h1280dp-port-xxhdpi"
        const val OutDir = "build/store-listing"
        const val Credits = 80
        const val RawPxPerDp = 4 // xxxhdpi

        /** Compact `CenterAlignedTopAppBar` height; Robolectric reports no status-bar inset above it. */
        const val CompactTopBarDp = 64

        /** Friday 2026-09-18 10:00 UTC — the spec's "Friday, 18 Sep", clock 10:00. */
        const val FixedNow = 1_789_725_600_000L

        val GradientStart = Color(0xFF1E88E5)
        val GradientEnd = Color(0xFF5C6BC0)
        val StatusInk = Color(0xFF1D1B20)
        val AddressInk = Color(0xFF49454F)
    }
}
