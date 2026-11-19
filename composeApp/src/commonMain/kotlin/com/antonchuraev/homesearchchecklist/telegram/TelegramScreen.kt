package com.antonchuraev.homesearchchecklist.telegram

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.cancel
import aichecklists.core.designsystem.generated.resources.due_date
import aichecklists.core.designsystem.generated.resources.month_april
import aichecklists.core.designsystem.generated.resources.month_august
import aichecklists.core.designsystem.generated.resources.month_december
import aichecklists.core.designsystem.generated.resources.month_february
import aichecklists.core.designsystem.generated.resources.month_january
import aichecklists.core.designsystem.generated.resources.month_july
import aichecklists.core.designsystem.generated.resources.month_june
import aichecklists.core.designsystem.generated.resources.month_march
import aichecklists.core.designsystem.generated.resources.month_may
import aichecklists.core.designsystem.generated.resources.month_november
import aichecklists.core.designsystem.generated.resources.month_october
import aichecklists.core.designsystem.generated.resources.month_september
import aichecklists.core.designsystem.generated.resources.telegram_benefit_capture
import aichecklists.core.designsystem.generated.resources.telegram_benefit_lists
import aichecklists.core.designsystem.generated.resources.telegram_benefit_sync
import aichecklists.core.designsystem.generated.resources.telegram_connect_cta
import aichecklists.core.designsystem.generated.resources.telegram_connect_hint
import aichecklists.core.designsystem.generated.resources.telegram_disconnect_confirm
import aichecklists.core.designsystem.generated.resources.telegram_disconnect_cta
import aichecklists.core.designsystem.generated.resources.telegram_disconnect_dialog_body
import aichecklists.core.designsystem.generated.resources.telegram_disconnect_dialog_title
import aichecklists.core.designsystem.generated.resources.telegram_disconnecting
import aichecklists.core.designsystem.generated.resources.telegram_hero_body
import aichecklists.core.designsystem.generated.resources.telegram_hero_title
import aichecklists.core.designsystem.generated.resources.telegram_linked_date
import aichecklists.core.designsystem.generated.resources.telegram_linked_no_username
import aichecklists.core.designsystem.generated.resources.telegram_linked_since
import aichecklists.core.designsystem.generated.resources.telegram_open_bot_cta
import aichecklists.core.designsystem.generated.resources.telegram_pending_body
import aichecklists.core.designsystem.generated.resources.telegram_pending_check_cta
import aichecklists.core.designsystem.generated.resources.telegram_pending_open_cta
import aichecklists.core.designsystem.generated.resources.telegram_pending_step_open
import aichecklists.core.designsystem.generated.resources.telegram_pending_step_start
import aichecklists.core.designsystem.generated.resources.telegram_pending_title
import aichecklists.core.designsystem.generated.resources.telegram_retry
import aichecklists.core.designsystem.generated.resources.telegram_signin_body
import aichecklists.core.designsystem.generated.resources.telegram_signin_cta
import aichecklists.core.designsystem.generated.resources.telegram_signin_title
import aichecklists.core.designsystem.generated.resources.telegram_status_cd
import aichecklists.core.designsystem.generated.resources.telegram_title
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.Login
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButton
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButtonDestructive
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButtonSecondary
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButtonText
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppCard
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppSkeletonLine
import com.antonchuraev.homesearchchecklist.desingsystem.components.GroupPosition
import com.antonchuraev.homesearchchecklist.desingsystem.components.groupSegment
import com.antonchuraev.homesearchchecklist.desingsystem.containers.AppScaffold
import com.antonchuraev.homesearchchecklist.desingsystem.containers.adaptiveContentWidth
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppMotion
import com.antonchuraev.homesearchchecklist.desingsystem.theme.GistiColors
import com.antonchuraev.homesearchchecklist.desingsystem.theme.LocalReducedMotion
import com.antonchuraev.homesearchchecklist.integrations.IntegrationNoteRow
import com.antonchuraev.homesearchchecklist.integrations.IntegrationRowTile
import com.antonchuraev.homesearchchecklist.integrations.ValuePropRow
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

// Component-local sizes (spec §3) — the same dimensions McpScreen uses.
private val HeroTileSize = 56.dp
private val HeroIconSize = 28.dp
private val StepDotSize = 24.dp
private val SpinnerSize = 20.dp
private val SpinnerStroke = 2.dp
private val ErrorIconSize = 24.dp
private val AccountCardMinHeight = 72.dp

/**
 * Stateless Telegram account-link screen (`docs/designs/telegram-screen-2026-11-19/spec.md`).
 *
 * Fixed hero, then a state slot that cross-fades between state KINDS (flag changes inside one kind —
 * a button going `loading` — update in place, they do not fade). The main action always sits
 * directly under the hero; benefit rows come after it, so a tall state never pushes the CTA below
 * the fold.
 *
 * UI-local state: the Disconnect dialog visibility (`rememberSaveable`) — a background refresh of
 * [state] must not close it.
 */
@Composable
fun TelegramScreen(
    state: TelegramScreenState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onAction: (TelegramAction) -> Unit,
) {
    var showDisconnectDialog by rememberSaveable { mutableStateOf(false) }
    TelegramScreenLayout(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onAction = onAction,
        showDisconnectDialog = showDisconnectDialog,
        onDisconnectDialogChange = { showDisconnectDialog = it },
    )
}

/** [TelegramScreen] with the dialog visibility hoisted — lets screenshot tests pin the dialog open. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TelegramScreenLayout(
    state: TelegramScreenState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onAction: (TelegramAction) -> Unit,
    showDisconnectDialog: Boolean,
    onDisconnectDialogChange: (Boolean) -> Unit,
) {
    AppScaffold(
        title = stringResource(Res.string.telegram_title),
        onBackButtonClick = onBack,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) {
        // Outer Column centres the capped content column on wide (tablet / web) viewports.
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .adaptiveContentWidth(600)
                    .fillMaxWidth()
                    .padding(horizontal = AppDimens.ScreenPaddingHorizontal),
            ) {
                Spacer(Modifier.height(AppDimens.SpacingLg))
                TelegramHero()
                Spacer(Modifier.height(AppDimens.SpacingXl))
                StateSlot(
                    state = state,
                    onAction = onAction,
                    onDisconnectClick = { onDisconnectDialogChange(true) },
                )
                Spacer(Modifier.height(AppDimens.SpacingXl))
            }
        }
    }

    // The dialog survives Loading (a refresh may pass through it) but is reset once the account is
    // definitely no longer Linked (e.g. unlinked from the bot), so it cannot pop up again later.
    if (showDisconnectDialog) {
        when (state) {
            is TelegramScreenState.Linked -> DisconnectDialog(
                onConfirm = {
                    onDisconnectDialogChange(false)
                    onAction(TelegramAction.DisconnectConfirm)
                },
                onDismiss = { onDisconnectDialogChange(false) },
            )
            TelegramScreenState.Loading -> Unit
            else -> LaunchedEffect(Unit) { onDisconnectDialogChange(false) }
        }
    }
}

@Composable
private fun TelegramHero() {
    Box(
        modifier = Modifier
            .size(HeroTileSize)
            .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.Send,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(HeroIconSize),
        )
    }
    Spacer(Modifier.height(AppDimens.SpacingMd))
    Text(
        text = stringResource(Res.string.telegram_hero_title),
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.semantics { heading() },
    )
    Spacer(Modifier.height(AppDimens.SpacingSm))
    Text(
        text = stringResource(Res.string.telegram_hero_body),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun StateSlot(
    state: TelegramScreenState,
    onAction: (TelegramAction) -> Unit,
    onDisconnectClick: () -> Unit,
) {
    val reduced = LocalReducedMotion.current
    val enterMillis = if (reduced) AppMotion.ReducedMotionMillis else AppMotion.EffectsDefaultMillis
    val exitMillis = if (reduced) AppMotion.ReducedMotionMillis else AppMotion.EffectsFastMillis
    AnimatedContent(
        targetState = state,
        // Fade only between state kinds; a flag flip inside one kind updates the same content.
        contentKey = { it::class },
        transitionSpec = {
            fadeIn(tween(enterMillis)) togetherWith fadeOut(tween(exitMillis)) using
                SizeTransform(clip = false)
        },
        contentAlignment = Alignment.TopCenter,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        label = "TelegramStateSlot",
    ) { target ->
        Column(Modifier.fillMaxWidth()) {
            when (target) {
                TelegramScreenState.Loading -> LoadingCard()
                is TelegramScreenState.SignedOut -> SignedOutContent(target, onAction)
                is TelegramScreenState.NotLinked -> NotLinkedContent(target, onAction)
                is TelegramScreenState.Pending -> PendingCard(target, onAction)
                is TelegramScreenState.Linked -> LinkedContent(target, onAction, onDisconnectClick)
                is TelegramScreenState.Error -> ErrorCard(target, onAction)
            }
        }
    }
}

// ── States ──────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun LoadingCard() {
    // Decorative placeholder: nothing for a screen reader to read.
    Box(Modifier.clearAndSetSemantics { }) {
        AppCard {
            Column {
                AppSkeletonLine(0.72f)
                Spacer(Modifier.height(AppDimens.SpacingMd))
                AppSkeletonLine(0.55f)
                Spacer(Modifier.height(AppDimens.SpacingMd))
                AppSkeletonLine(0.84f)
                Spacer(Modifier.height(AppDimens.SpacingLg))
                AppSkeletonLine(1f, height = AppDimens.ButtonHeight)
            }
        }
    }
}

@Composable
private fun SignedOutContent(state: TelegramScreenState.SignedOut, onAction: (TelegramAction) -> Unit) {
    AppCard {
        Column {
            Text(
                text = stringResource(Res.string.telegram_signin_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(AppDimens.SpacingSm))
            Text(
                text = stringResource(Res.string.telegram_signin_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(AppDimens.SpacingLg))
            AppButton(
                text = stringResource(Res.string.telegram_signin_cta),
                onClick = { onAction(TelegramAction.SignInClick) },
                icon = Icons.Outlined.Login,
                loading = state.isSigningIn,
                modifier = Modifier.fillMaxWidth().testTag("telegram_signin"),
            )
        }
    }
    Spacer(Modifier.height(AppDimens.SpacingXl))
    Benefits()
}

@Composable
private fun NotLinkedContent(state: TelegramScreenState.NotLinked, onAction: (TelegramAction) -> Unit) {
    AppButton(
        text = stringResource(Res.string.telegram_connect_cta),
        onClick = { onAction(TelegramAction.ConnectClick) },
        icon = Icons.AutoMirrored.Outlined.Send,
        loading = state.isConnecting,
        modifier = Modifier.fillMaxWidth().testTag("telegram_connect"),
    )
    Spacer(Modifier.height(AppDimens.SpacingMd))
    IntegrationNoteRow(
        icon = Icons.Outlined.Info,
        text = stringResource(Res.string.telegram_connect_hint),
        verticalAlignment = Alignment.Top,
    )
    Spacer(Modifier.height(AppDimens.SpacingXl))
    Benefits()
}

@Composable
private fun PendingCard(state: TelegramScreenState.Pending, onAction: (TelegramAction) -> Unit) {
    AppCard {
        Column {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(top = AppDimens.SpacingXxs).size(SpinnerSize),
                    strokeWidth = SpinnerStroke,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(Res.string.telegram_pending_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(AppDimens.SpacingMd))
            StepRow(number = 1, text = stringResource(Res.string.telegram_pending_step_open))
            Spacer(Modifier.height(AppDimens.SpacingSm))
            StepRow(number = 2, text = stringResource(Res.string.telegram_pending_step_start))
            Spacer(Modifier.height(AppDimens.SpacingMd))
            Text(
                text = stringResource(Res.string.telegram_pending_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(AppDimens.SpacingLg))
            AppButton(
                text = stringResource(Res.string.telegram_pending_open_cta),
                onClick = { onAction(TelegramAction.OpenTelegramClick) },
                icon = Icons.AutoMirrored.Filled.OpenInNew,
                loading = state.isOpening,
                modifier = Modifier.fillMaxWidth().testTag("telegram_open_telegram"),
            )
            Spacer(Modifier.height(AppDimens.SpacingSm))
            AppButtonSecondary(
                text = stringResource(Res.string.telegram_pending_check_cta),
                onClick = { onAction(TelegramAction.CheckAgainClick) },
                enabled = !state.isChecking,
                modifier = Modifier.fillMaxWidth().testTag("telegram_check_again"),
            )
        }
    }
}

@Composable
private fun StepRow(number: Int, text: String) {
    Row(
        modifier = Modifier.semantics(mergeDescendants = true) { },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd),
    ) {
        Box(
            modifier = Modifier
                .size(StepDotSize)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun LinkedContent(
    state: TelegramScreenState.Linked,
    onAction: (TelegramAction) -> Unit,
    onDisconnectClick: () -> Unit,
) {
    AccountCard(state)
    Spacer(Modifier.height(AppDimens.SpacingLg))
    AppButton(
        text = stringResource(Res.string.telegram_open_bot_cta),
        onClick = { onAction(TelegramAction.OpenBotClick) },
        icon = Icons.AutoMirrored.Filled.OpenInNew,
        enabled = !state.isDisconnecting,
        modifier = Modifier.fillMaxWidth().testTag("telegram_open_bot"),
    )
    Spacer(Modifier.height(AppDimens.SpacingXl))
    Benefits()
    Spacer(Modifier.height(AppDimens.SpacingXl))
    AppButtonSecondary(
        text = stringResource(Res.string.telegram_disconnect_cta),
        onClick = onDisconnectClick,
        icon = Icons.Outlined.LinkOff,
        enabled = !state.isDisconnecting,
        accentColor = MaterialTheme.colorScheme.error,
        modifier = Modifier.fillMaxWidth().testTag("telegram_disconnect"),
    )
}

/**
 * Not clickable. The visible name is single-line with ellipsis (a 32-char username would otherwise
 * break into many lines at 320dp × 1.5); the full name reaches screen readers via the merged
 * `contentDescription`.
 */
@Composable
private fun AccountCard(state: TelegramScreenState.Linked) {
    val label = state.username?.let { "@$it" }
        ?: state.firstName
        ?: stringResource(Res.string.telegram_linked_no_username)
    val date = state.linkedAtMs?.let { formatLinkedDate(it) }
    val cd = if (date != null) stringResource(Res.string.telegram_status_cd, label, date) else label
    val subtitle = when {
        state.isDisconnecting -> stringResource(Res.string.telegram_disconnecting)
        date != null -> stringResource(Res.string.telegram_linked_since, date)
        else -> null
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AccountCardMinHeight)
            .groupSegment(GroupPosition.Single)
            .padding(horizontal = AppDimens.SpacingLg, vertical = AppDimens.SpacingMd)
            .semantics(mergeDescendants = true) { contentDescription = cd },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd),
    ) {
        IntegrationRowTile(
            icon = Icons.AutoMirrored.Outlined.Send,
            tileColor = GistiColors.successContainer,
            iconTint = GistiColors.onSuccessContainer,
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ErrorCard(state: TelegramScreenState.Error, onAction: (TelegramAction) -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(AppDimens.SpacingLg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd),
        ) {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(ErrorIconSize),
            )
            Text(
                text = stringResource(state.reason.messageRes()),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            AppButtonSecondary(
                text = stringResource(Res.string.telegram_retry),
                onClick = { onAction(TelegramAction.RetryClick) },
                accentColor = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.testTag("telegram_retry"),
            )
        }
    }
}

/** The three phase-1 benefits, identical in every state that shows them. */
@Composable
private fun Benefits() {
    val style = MaterialTheme.typography.bodyLarge
    ValuePropRow(
        icon = Icons.Outlined.EditNote,
        label = stringResource(Res.string.telegram_benefit_capture),
        textStyle = style,
        alignToTileTop = true,
    )
    Spacer(Modifier.height(AppDimens.SpacingLg))
    ValuePropRow(
        icon = Icons.Outlined.TaskAlt,
        label = stringResource(Res.string.telegram_benefit_lists),
        textStyle = style,
        alignToTileTop = true,
    )
    Spacer(Modifier.height(AppDimens.SpacingLg))
    ValuePropRow(
        icon = Icons.Outlined.Sync,
        label = stringResource(Res.string.telegram_benefit_sync),
        textStyle = style,
        alignToTileTop = true,
    )
}

@Composable
private fun DisconnectDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.telegram_disconnect_dialog_title)) },
        text = { Text(stringResource(Res.string.telegram_disconnect_dialog_body)) },
        confirmButton = {
            AppButtonDestructive(
                text = stringResource(Res.string.telegram_disconnect_confirm),
                onClick = onConfirm,
                modifier = Modifier.testTag("telegram_disconnect_confirm"),
            )
        },
        dismissButton = {
            AppButtonText(
                text = stringResource(Res.string.cancel),
                onClick = onDismiss,
                modifier = Modifier.testTag("telegram_disconnect_cancel"),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
    )
}

// ── Date ────────────────────────────────────────────────────────────────────────────────────────

/**
 * Date-only, locale-ordered: "November 19, 2026" / "19 ноября 2026 г." / "19 नवंबर 2026".
 * Day/month order comes from `due_date` (reversed for RU and HI), the year slot from
 * `telegram_linked_date`; Kotlin only supplies the parts. RU month names are genitive.
 */
@Composable
private fun formatLinkedDate(epochMs: Long): String {
    val date = remember(epochMs) {
        Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(TimeZone.currentSystemDefault()).date
    }
    val dayMonth = stringResource(
        Res.string.due_date,
        stringResource(monthRes(date.month.number)),
        date.dayOfMonth.toString(),
    )
    return stringResource(Res.string.telegram_linked_date, dayMonth, date.year.toString())
}

private fun monthRes(month: Int): StringResource = when (month) {
    1 -> Res.string.month_january
    2 -> Res.string.month_february
    3 -> Res.string.month_march
    4 -> Res.string.month_april
    5 -> Res.string.month_may
    6 -> Res.string.month_june
    7 -> Res.string.month_july
    8 -> Res.string.month_august
    9 -> Res.string.month_september
    10 -> Res.string.month_october
    11 -> Res.string.month_november
    else -> Res.string.month_december
}
