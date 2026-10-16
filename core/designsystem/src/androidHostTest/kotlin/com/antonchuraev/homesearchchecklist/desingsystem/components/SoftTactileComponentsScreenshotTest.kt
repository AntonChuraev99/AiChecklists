package com.antonchuraev.homesearchchecklist.desingsystem.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.AppChecklistHeaderCard
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.AskGistiBar
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.AskGistiBarStyle
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDensity
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppShapeTokens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppSurface
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTextStyles
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTheme
import com.antonchuraev.homesearchchecklist.desingsystem.theme.GistiColors
import com.antonchuraev.homesearchchecklist.desingsystem.theme.GistiSchedule
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

/**
 * Golden frames for the redesign-B "Soft & tactile" design-system primitives (step a).
 *
 * One showcase per component, each shot across the same 6-point matrix: 412dp light/dark EN at
 * 1.0, 412dp light HI, and the 320dp worst cases (EN light 1.5 · RU light 1.5 · HI dark 1.5).
 * Strings the components resolve themselves (ring description, plan-nudge copy) follow the JVM
 * default locale; showcase copy is passed per locale below.
 *
 * Record + inspect:
 *   ./gradlew :core:designsystem:testAndroidHostTest -Proborazzi.test.record=true --tests "*SoftTactile*"
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SoftTactileComponentsScreenshotTest(private val frame: Frame) {

    data class Frame(
        val name: String,
        val widthDp: Int,
        val dark: Boolean,
        val fontScale: Float,
        val lang: Lang,
    ) {
        override fun toString() = name
    }

    enum class Lang(val locale: Locale, val qualifier: String) {
        EN(Locale.ENGLISH, ""),
        RU(Locale("ru", "RU"), "ru-rRU-"),
        HI(Locale("hi", "IN"), "hi-rIN-"),
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun frames(): List<Array<Any>> = listOf(
            Frame("412_light_en", 412, dark = false, fontScale = 1f, lang = Lang.EN),
            Frame("412_dark_en", 412, dark = true, fontScale = 1f, lang = Lang.EN),
            Frame("412_light_hi", 412, dark = false, fontScale = 1f, lang = Lang.HI),
            Frame("320_light_en_fs15", 320, dark = false, fontScale = 1.5f, lang = Lang.EN),
            Frame("320_light_ru_fs15", 320, dark = false, fontScale = 1.5f, lang = Lang.RU),
            Frame("320_dark_hi_fs15", 320, dark = true, fontScale = 1.5f, lang = Lang.HI),
        ).map { arrayOf(it) }
    }

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val defaultLocale: Locale = Locale.getDefault()

    @After
    fun restoreLocale() {
        Locale.setDefault(defaultLocale)
    }

    private val copy: Copy get() = when (frame.lang) {
        Lang.EN -> Copy.En
        Lang.RU -> Copy.Ru
        Lang.HI -> Copy.Hi
    }

    private fun shoot(component: String, content: @Composable (Copy) -> Unit) {
        Locale.setDefault(frame.lang.locale)
        RuntimeEnvironment.setQualifiers("${frame.lang.qualifier}w${frame.widthDp}dp-h900dp")
        composeTestRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = base.density, fontScale = frame.fontScale),
            ) {
                AppTheme(darkTheme = frame.dark) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(AppSurface.ground())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        content(copy)
                    }
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(
            "src/androidHostTest/roborazzi/SoftTactile.${component}_${frame.name}.png",
        )
    }

    @Test fun groupedSection() = shoot("groupedSection") { GroupedSectionShowcase(it) }

    @Test fun roundCheckbox() = shoot("roundCheckbox") { RoundCheckboxShowcase() }

    @Test fun sectionHeader() = shoot("sectionHeader") { SectionHeaderShowcase(it) }

    @Test fun pageChipRow() = shoot("pageChipRow") { PageChipRowShowcase(it) }

    @Test fun progressRing() = shoot("progressRing") { ProgressRingShowcase() }

    @Test fun headerCard() = shoot("headerCard") { HeaderCardShowcase(it) }

    @Test fun aiInviteRow() = shoot("aiInviteRow") { AiInviteRowShowcase(it) }

    @Test fun softIconButton() = shoot("softIconButton") { SoftIconButtonShowcase() }

    @Test fun askPill() = shoot("askPill") { AskPillShowcase(it) }
}

// =============================================================================
// Copy per locale — showcase data only (real strings come from composeResources)
// =============================================================================

private data class Copy(
    val today: String,
    val overdue: String,
    val upcoming: String,
    val anytime: String,
    val completed: String,
    val task1: String,
    val task2: String,
    val task3: String,
    val longTask: String,
    val inbox: String,
    val groceries: String,
    val trip: String,
    val longProject: String,
    val shortTitle: String,
    val longTitle: String,
    val folder: String,
    val progress: String,
    val askAboutList: String,
    val askPill: String,
) {
    companion object {
        val En = Copy(
            today = "Today", overdue = "Overdue", upcoming = "Upcoming", anytime = "Anytime",
            completed = "Completed (4)",
            task1 = "Call the plumber", task2 = "Renew passport", task3 = "Book train tickets",
            longTask = "Pick up the dry cleaning before the shop closes on Saturday afternoon",
            inbox = "Inbox", groceries = "Groceries", trip = "Weekend trip",
            longProject = "Apartment renovation and moving plan",
            shortTitle = "Weekly groceries",
            longTitle = "Weekly groceries for the whole family and the dinner party",
            folder = "Dairy", progress = "2 left · 4 of 6 done",
            askAboutList = "Ask Gisti about this list", askPill = "Ask Gisti or add a task",
        )
        val Ru = Copy(
            today = "Сегодня", overdue = "Просрочено", upcoming = "Предстоящие", anytime = "В любое время",
            completed = "Выполнено (4)",
            task1 = "Позвонить сантехнику", task2 = "Продлить паспорт", task3 = "Купить билеты на поезд",
            longTask = "Забрать вещи из химчистки до закрытия в субботу после обеда",
            inbox = "Входящие", groceries = "Продукты", trip = "Поездка на выходные",
            longProject = "Ремонт квартиры и план переезда",
            shortTitle = "Покупки на неделю",
            longTitle = "Еженедельные покупки продуктов",
            folder = "Молочное", progress = "Осталось 2 · готово 4 из 6",
            askAboutList = "Спросить Gisti об этом списке", askPill = "Спросите Gisti или добавьте задачу",
        )
        val Hi = Copy(
            today = "आज", overdue = "देय तिथि बीत गई", upcoming = "आगामी", anytime = "कभी भी",
            completed = "पूरे हुए (4)",
            task1 = "प्लंबर को कॉल करें", task2 = "पासपोर्ट नवीनीकरण", task3 = "ट्रेन टिकट बुक करें",
            longTask = "शनिवार दोपहर दुकान बंद होने से पहले ड्राई क्लीनिंग ले आएँ",
            inbox = "इनबॉक्स", groceries = "किराना", trip = "वीकेंड ट्रिप",
            longProject = "अपार्टमेंट नवीनीकरण और शिफ्टिंग योजना",
            shortTitle = "साप्ताहिक किराना",
            longTitle = "पूरे परिवार के लिए साप्ताहिक किराने की खरीदारी",
            folder = "डेयरी", progress = "2 बाकी · 6 में से 4 पूरे",
            askAboutList = "इस सूची के बारे में Gisti से पूछें", askPill = "Gisti से पूछें या टास्क जोड़ें",
        )
    }
}

// =============================================================================
// Showcases
// =============================================================================

/** A minimal grouped row: the anatomy step (b) gives TaskRow(Grouped). Showcase only. */
@Composable
private fun DemoGroupedRow(
    text: String,
    position: GroupPosition,
    checked: Boolean = false,
    important: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .groupSegment(position)
            .heightIn(min = AppDensity.RowMinHeightCompact)
            .padding(horizontal = AppDensity.GroupRowPaddingHorizontal, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDensity.GroupRowGap),
    ) {
        AppRoundCheckbox(checked = checked, important = important)
        Text(
            text = text,
            style = AppTextStyles.taskTitle,
            color = if (checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            textDecoration = if (checked) TextDecoration.LineThrough else null,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (important) {
            Icon(Icons.Filled.Star, contentDescription = null, tint = GistiColors.star, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun GroupedSectionShowcase(c: Copy) {
    Column {
        AppSectionHeader(
            label = c.today,
            icon = Icons.Filled.WbSunny,
            iconContainer = GistiColors.paletteTint(2),
            iconContent = GistiColors.paletteContent(2),
            count = 3,
        )
        DemoGroupedRow(c.task1, GroupPosition.First)
        DemoGroupedRow(c.task2, GroupPosition.Middle, important = true)
        DemoGroupedRow(c.longTask, GroupPosition.Last, checked = true)
        AppSectionHeader(
            label = c.anytime,
            icon = Icons.Filled.AllInclusive,
            iconContainer = GistiColors.paletteTint(3),
            iconContent = GistiColors.paletteContent(3),
            count = 1,
        )
        DemoGroupedRow(c.task3, GroupPosition.First)
        AppPlanNudge(
            onClick = {},
            style = AiInviteStyle.GroupRow,
            modifier = Modifier.fillMaxWidth().groupSegment(GroupPosition.Last),
        )
        AppSectionHeader(
            label = c.upcoming,
            icon = Icons.Filled.Event,
            iconContainer = MaterialTheme.colorScheme.primaryContainer,
            iconContent = MaterialTheme.colorScheme.primary,
            count = 1,
        )
        DemoGroupedRow(c.task3, GroupPosition.Single)
    }
}

@Composable
private fun RoundCheckboxShowcase() {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        AppRoundCheckbox(checked = false)
        AppRoundCheckbox(checked = false, important = true)
        AppRoundCheckbox(checked = true)
        AppRoundCheckbox(checked = true, important = true)
    }
    Row(
        modifier = Modifier.fillMaxWidth().groupSegment(GroupPosition.Single).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AppRoundCheckbox(checked = false)
        AppRoundCheckbox(checked = false, important = true)
        AppRoundCheckbox(checked = true)
    }
}

@Composable
private fun SectionHeaderShowcase(c: Copy) {
    AppSectionHeader(
        label = c.overdue,
        icon = Icons.Filled.History,
        iconContainer = GistiSchedule.overdueContainer,
        iconContent = GistiSchedule.overdueContent,
        count = 2,
    )
    AppSectionHeader(
        label = c.today,
        icon = Icons.Filled.WbSunny,
        iconContainer = GistiColors.paletteTint(2),
        iconContent = GistiColors.paletteContent(2),
        count = 12,
    )
    AppSectionHeader(
        label = c.upcoming,
        icon = Icons.Filled.Event,
        iconContainer = MaterialTheme.colorScheme.primaryContainer,
        iconContent = MaterialTheme.colorScheme.primary,
        count = 104,
    )
    AppSectionHeader(
        label = c.anytime,
        icon = Icons.Filled.AllInclusive,
        iconContainer = GistiColors.paletteTint(3),
        iconContent = GistiColors.paletteContent(3),
    )
    AppSectionHeader(
        label = c.completed,
        icon = Icons.Filled.CheckCircle,
        iconContainer = AppSurface.recessed(),
        iconContent = MaterialTheme.colorScheme.onSurfaceVariant,
        trailing = {
            Icon(
                Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp).padding(12.dp),
            )
        },
    )
}

@Composable
private fun PageChipRowShowcase(c: Copy) {
    val pages = listOf(
        PageChip(c.inbox, leadingIcon = Icons.Outlined.Inbox),
        PageChip(c.groceries, dotColor = GistiColors.avatarContent(0L)),
        PageChip(c.trip, dotColor = GistiColors.avatarContent(2L)),
        PageChip(c.longProject, dotColor = GistiColors.avatarContent(3L)),
    )
    AppPageChipRow(pages = pages, selectedIndex = 0, onSelect = {}, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp))
    AppPageChipRow(pages = pages.take(2), selectedIndex = 1, onSelect = {}, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp))
    // 13 pages, selected in the middle — auto-centred, nothing collapses to zero width.
    val many = pages + (1..9).map { PageChip("${c.groceries} $it", dotColor = GistiColors.avatarContent(it.toLong())) }
    AppPageChipRow(pages = many, selectedIndex = 7, onSelect = {})
}

@Composable
private fun ProgressRingShowcase() {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        AppProgressRing(checked = 0, total = 6)
        AppProgressRing(checked = 4, total = 6)
        AppProgressRing(checked = 6, total = 6)
        AppProgressRing(checked = 37, total = 120)
    }
}

@Composable
private fun HeaderCardShowcase(c: Copy) {
    AppChecklistHeaderCard(seed = 0L, title = c.shortTitle, subtitle = c.progress, checked = 4, total = 6, isFolder = false)
    AppChecklistHeaderCard(seed = 2L, title = c.longTitle, subtitle = c.progress, checked = 4, total = 6, isFolder = false)
    AppChecklistHeaderCard(seed = 3L, title = c.folder, subtitle = c.progress, checked = 6, total = 6, isFolder = true)
    AppChecklistHeaderCard(seed = 4L, title = c.shortTitle, subtitle = null, checked = 0, total = 0, isFolder = false)
}

@Composable
private fun AiInviteRowShowcase(c: Copy) {
    AppAiInviteRow(title = c.askAboutList, onClick = {}, style = AiInviteStyle.Outlined, modifier = Modifier.fillMaxWidth())
    AppPlanNudge(onClick = {}, modifier = Modifier.fillMaxWidth())
    Column {
        DemoGroupedRow(c.task1, GroupPosition.First)
        AppAiInviteRow(
            title = c.askAboutList,
            onClick = {},
            style = AiInviteStyle.GroupRow,
            modifier = Modifier.fillMaxWidth().groupSegment(GroupPosition.Last),
        )
    }
}

@Composable
private fun SoftIconButtonShowcase() {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AppSoftIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = {})
        AppSoftIconButton(Icons.Outlined.NotificationsNone, "Reminder", onClick = {})
        AppSoftIconButton(Icons.Filled.Notifications, "Reminder set", onClick = {}, tint = MaterialTheme.colorScheme.primary)
        AppSoftIconButton(Icons.Filled.MoreHoriz, "More", onClick = {})
        AppSoftIconButton(Icons.Filled.MoreHoriz, "More", onClick = {}, enabled = false)
    }
}

@Composable
private fun AskPillShowcase(c: Copy) {
    // The pill lives on the bottom chrome — paint that plate behind it, as the shell will.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppSurface.bottomChrome(), AppShapeTokens.SheetTop)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AskGistiBar(
            placeholder = c.askPill,
            onClick = {},
            onMicClick = {},
            micContentDescription = "Voice input",
            style = AskGistiBarStyle.ChromePill,
        )
        AskGistiBar(
            placeholder = c.askPill,
            onClick = {},
            onMicClick = null,
            style = AskGistiBarStyle.ChromePill,
        )
    }
    // v1 Card style keeps its look; with no mic handler the mic is gone.
    AskGistiBar(placeholder = c.askPill, onClick = {}, onMicClick = null)
}
