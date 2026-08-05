package com.antonchuraev.homesearchchecklist.desingsystem.containers

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.*
import com.antonchuraev.homesearchchecklist.desingsystem.adaptive.AppWindowSizeClass
import com.antonchuraev.homesearchchecklist.desingsystem.adaptive.rememberAppWindowSizeClass
import org.jetbrains.compose.resources.stringResource

/**
 * Breathing room between the title and the subtitle, on top of the subtitle's own line box.
 *
 * Stays a fixed dp on purpose: it is spacing, not text. A bar sized to exactly the glyph box lets
 * the two lines touch and clips descenders — the failure mode the v2 pills already hit on the hi
 * locale — but the gap itself has no reason to grow with the font scale.
 */
private val SubtitleBreathingRoom = 4.dp

/**
 * Line height assumed for the subtitle when the theme expresses `bodyMedium`'s in em or leaves it
 * unspecified: neither resolves to dp without font metrics. Matches the app theme's 20sp.
 */
private val SubtitleFallbackLineHeight = 20.sp

/**
 * Extra bar height reserved for [AppScaffold]'s optional subtitle line.
 *
 * Derived from the resolved `bodyMedium` line height rather than hardcoded, because that line grows
 * with the system font scale while a dp constant does not: at a large accessibility scale a fixed
 * reserve leaves the second line clipped by the bar and drawn over whatever sits below it (the Inbox
 * pager dots). Comes out at 24.dp at fontScale 1.0 — 20sp line plus the 4dp gap — so nothing moves
 * for an ordinary user.
 */
@Composable
private fun subtitleExtraHeight(): Dp {
    val subtitleLineHeight = MaterialTheme.typography.bodyMedium.lineHeight
    val resolved = if (subtitleLineHeight.isSp) subtitleLineHeight else SubtitleFallbackLineHeight
    return with(LocalDensity.current) { resolved.toDp() } + SubtitleBreathingRoom
}

/**
 * App-level scaffold wrapper that centralises TopAppBar configuration, system-inset
 * handling, and adaptive TopAppBar type selection.
 *
 * @param scrollBehavior When non-null, the TopAppBar will collapse on scroll using
 *   [TopAppBarDefaults.exitUntilCollapsedScrollBehavior]. On Compact, a
 *   [CenterAlignedTopAppBar] is used; on Medium/Expanded, a [MediumTopAppBar] (denser,
 *   larger title area — better information density on tablet/desktop). The nested scroll
 *   connection is automatically applied to the Box content area so callers only need to
 *   pass the LazyColumn/Column with no extra modifier.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    title: String? = null,
    /**
     * Second line under [title] — a count, a date, a state. Ignored when [title] is null.
     *
     * Rendered as a Column inside the title slot rather than via Material3's own subtitle overload:
     * that overload is a separate experimental API whose shape is still moving between releases, and
     * it would not apply to [MediumTopAppBar] on Medium/Expanded, leaving the subtitle silently
     * absent on tablet. A Column renders identically on every size class.
     *
     * The bar is grown by [subtitleExtraHeight] when this is set: the small TopAppBar's 64dp is
     * measured for ONE line, so a second line is clipped rather than laid out. That fixed budget is
     * also why [title] is capped to one line only in this case — see the title slot.
     */
    subtitle: String? = null,
    /**
     * Aligns the title to the start on Compact instead of centring it.
     *
     * Only Compact is affected — [MediumTopAppBar] is start-aligned by construction, so
     * Medium/Expanded already look this way regardless of the flag.
     *
     * Default false keeps every existing screen centred; the v2 navigation screens opt in, because a
     * centred title cannot carry a left-aligned subtitle without reading as two unrelated centred
     * labels stacked on top of each other.
     */
    startAlignedTitle: Boolean = false,
    onBackButtonClick: (() -> Unit)? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null,
    /**
     * When true, the content area extends edge-to-edge to the PHYSICAL bottom of the screen
     * (behind the navigation bar) instead of being inset above it. Use for screens that render their
     * own bottom overlay which must paint INTO the navbar zone — e.g. MainScreen's glassmorphism
     * chat dock, whose backdrop blur must cover the navbar so it doesn't stand out as an un-blurred
     * strip. The overlay is then responsible for its own navigationBarsPadding(). Default false
     * keeps the safe inset-above-navbar behaviour for ordinary screens.
     */
    contentExtendsBehindNavBar: Boolean = false,
    content: @Composable () -> Unit
) {
    val windowSizeClass = rememberAppWindowSizeClass()
    val isCompact = windowSizeClass == AppWindowSizeClass.Compact

    val resolvedNavigationIcon: @Composable () -> Unit = {
        when {
            navigationIcon != null -> navigationIcon()
            onBackButtonClick != null -> {
                IconButton(onClick = onBackButtonClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBackIos,
                        contentDescription = stringResource(Res.string.back),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }

    val topBarColors = TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.background,
        scrolledContainerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onBackground
    )

    // Wrap the caller's bottomBar so that it always sits above both the navigation
    // bar and the software keyboard. The wrapper applies
    // windowInsetsPadding(ime ∪ navigationBars) which equals max(ime, navBar) per
    // side — never a sum. Because the wrapper consumes navigationBars before the
    // caller's content sees them, any existing .navigationBarsPadding() inside
    // the bottomBar slot resolves to 0 and does not double-up.
    //
    // EXCEPTION — contentExtendsBehindNavBar: the slot adds NO navbar height, so the content area
    // reaches the physical screen bottom (behind the navbar). The screen's own bottom overlay is then
    // responsible for painting the navbar zone and applying its own navigationBarsPadding().
    val wrappedBottomBar: @Composable () -> Unit = {
        if (contentExtendsBehindNavBar) {
            bottomBar()
        } else {
            Box(modifier = Modifier.windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))) {
                bottomBar()
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // Keep the status-bar inset for the content area (screens that render NO
        // top bar — e.g. MainScreen — rely on it so their content starts below the
        // status bar), but drop the bottom (navigation-bar) inset: that side is
        // managed by wrappedBottomBar (ime ∪ navigationBars). Using the default
        // systemBars here would double up the navbar against wrappedBottomBar;
        // using WindowInsets(0) would let content slide under the status bar on
        // top-bar-less screens. statusBars (top only, bottom = 0) is the safe middle.
        contentWindowInsets = WindowInsets.statusBars,
        snackbarHost = snackbarHost,
        topBar = {
            if (title != null || onBackButtonClick != null || navigationIcon != null) {
                // One title slot for every bar type below, so the subtitle cannot exist on one size
                // class and quietly vanish on another.
                val titleSlot: @Composable (titleStyle: TextStyle) -> Unit = { titleStyle ->
                    if (title != null) {
                        Column {
                            Text(
                                text = title,
                                style = titleStyle,
                                color = MaterialTheme.colorScheme.onBackground,
                                // Capped to one line ONLY next to a subtitle: the bar then has a
                                // fixed two-line budget, and a wrapping title would push the
                                // subtitle out of it. A lone title keeps the pre-v2 behaviour of
                                // wrapping — capping it everywhere truncated the titles that need
                                // the second line most (ru/hi run 30-50% longer than en, and
                                // checklist-detail titles are user-authored).
                                maxLines = if (subtitle != null) 1 else Int.MAX_VALUE,
                                overflow = if (subtitle != null) TextOverflow.Ellipsis else TextOverflow.Clip,
                            )
                            if (subtitle != null) {
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }

                // Zero without a subtitle, so a bar that has only a title keeps Material's own
                // height token untouched. Gated on the title too, because the subtitle renders
                // inside the title slot: with no title there is no second line to reserve for.
                val extraBarHeight =
                    if (title != null && subtitle != null) subtitleExtraHeight() else 0.dp

                if (isCompact) {
                    // expandedHeight, not a Modifier.height: the bar's own height token is what
                    // scrollBehavior interpolates against while collapsing, so sizing it from outside
                    // would let the collapse animation and the laid-out height disagree.
                    val compactHeight = TopAppBarDefaults.TopAppBarExpandedHeight + extraBarHeight

                    if (startAlignedTitle) {
                        TopAppBar(
                            title = { titleSlot(MaterialTheme.typography.titleLarge) },
                            navigationIcon = resolvedNavigationIcon,
                            colors = topBarColors,
                            actions = { actions() },
                            scrollBehavior = scrollBehavior,
                            expandedHeight = compactHeight,
                        )
                    } else {
                        CenterAlignedTopAppBar(
                            title = { titleSlot(MaterialTheme.typography.titleLarge) },
                            navigationIcon = resolvedNavigationIcon,
                            colors = topBarColors,
                            actions = { actions() },
                            scrollBehavior = scrollBehavior,
                            expandedHeight = compactHeight,
                        )
                    }
                } else {
                    MediumTopAppBar(
                        title = { titleSlot(MaterialTheme.typography.headlineSmall) },
                        navigationIcon = resolvedNavigationIcon,
                        colors = TopAppBarDefaults.mediumTopAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background,
                            scrolledContainerColor = MaterialTheme.colorScheme.surface,
                            titleContentColor = MaterialTheme.colorScheme.onBackground
                        ),
                        actions = { actions() },
                        scrollBehavior = scrollBehavior,
                        expandedHeight = TopAppBarDefaults.MediumAppBarExpandedHeight + extraBarHeight,
                    )
                }
            }
        },
        bottomBar = wrappedBottomBar
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(it)
                .then(
                    if (scrollBehavior != null) Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
                    else Modifier
                )
        ) {
            content.invoke()
        }
    }
}
