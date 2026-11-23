# DESIGN_SPEC: Telegram channel choice in the reminder sheet + Telegram promo banner on the home tab

- **Platform:** Android + Web (wasmJs). Both surfaces are `commonMain` Compose Multiplatform, Material 3, design system "Soft & tactile". iOS (unreleased) follows Android.
- **Method:** native
- **Surfaces:**
  1. `ReminderSheet` (`feature/checklist/.../ui/reminder/ReminderSheet.kt`): a new **"Where all reminders go"** row.
  2. The **Inbox tab** (`feature/home/.../inbox/InboxScreen.kt`, `InboxPageList`): a new **Telegram promo banner**, shown as a list item.
- **Builds on:** `docs/designs/telegram-screen-2026-11-19/spec.md` (Telegram screen) and `reminders-addendum.md` (channel radio group, web mapping, saving/revert). The strings, `webReminderTarget`, the saving spinner in the control slot and revert-to-confirmed are reused here unchanged.
- **FEATURE_SPEC:** none was provided. §3.4 and §4.3 list the state set this spec designs. `@feature-expert` turns them into the contract.

> ⛔ **Anti-steering (Android).** No string on either surface mentions Stars, payment, premium, prices, limits or AI credits. The banner sells three things: reminders arrive as Telegram messages, a message turns into a checklist, and you can chat with Gisti AI in the bot. **The owner decided on 2026-11-21 (Q4) that the AI chat is mentioned**, as a feature only, with no word about how it is paid for or limited.

---

## 1. Owner decisions this spec implements (not reopened)

| # | Decision | Where |
|---|---|---|
| 1 | The channel choice lives in the reminder sheet. It changes the **user's** channel, and the copy says so. When Telegram is not linked, the row leads to linking instead of showing radios. The Telegram screen and its section stay. | §3 |
| 2 | Home banner: a list item that scrolls with the content. Shown only to users without Telegram linked. Has an ×. Returns 14 days after a dismissal, for at most 3 cycles. Gone for good once Telegram is linked. Tapping it opens the Telegram screen. | §4 | <!-- docs-leak-scan: reviewed — product rule, not a metric -->
| 3 | No Stars / payment / premium / prices on Android. | all copy, §6 |

## 2. Where "home" is (finding)

The product shell is **v2** (`NavVariant.V2`, "the product's navigation since 2026-08-03", `core/common/api/.../NavExperimentResolver.kt:6`). It cold-starts on the **Inbox** tab (`App.kt:1633`: `if (arm == NavVariant.V2) AppNavRoute.Inbox else AppNavRoute.Main`). So "the home screen" that every user lands on is **Inbox**, not the Projects list (`MainScreen`, which is the v2 Projects tab and the CONTROL shell's root).

Inbox already has exactly the slot the owner asked for. `InboxPageList(listHeader = …)` is the first item of the page's `LazyColumn`, and it scrolls away with the rows. The notifications-off banner already lives there (`InboxScreen.kt:1398-1413`, owner on 2026-11-16: "банер должен скролиться вместе с пунктами"). The Telegram banner goes **into the same slot**, with a priority rule (§4.3).

Not covered: `MainScreen` (Projects tab / CONTROL shell root). It already carries `SyncAccountBanner` (top) and `PremiumBanner` (bottom), and a third promo there would make it a billboard. The owner confirmed Inbox only (Q1).

---

## 3. Reminder sheet: "Where all reminders go" row

### 3.1 Placement

The row is the **same row in both tabs, in the same place: directly above "Add to Google Calendar"**. That is the "where does this reminder go" cluster, next to the full-screen-alarm toggle when it is shown.

```
ONCE tab (OnceTabContent, Column spacedBy(SpacingXs))
├─ [CurrentReminderCard]                       (unchanged)
├─ 4 × ReminderPresetRow                       (unchanged)
├─ [HorizontalDivider + FullScreenReminderRow] (unchanged, per-item only)
├─ HorizontalDivider(vertical = SpacingSm)     ── the divider that used to sit above Calendar
├─ ReminderDeliveryRow                         ── NEW, only when state.delivery != null
├─ CalendarActionRow                           (unchanged; no divider between it and the new row)
└─ [Remove reminder]                           (unchanged)

REPEAT tab (RepeatTabContent, Column spacedBy(SpacingSm))
├─ … options · time · reset · [full-screen] · Ends row   (unchanged)
├─ HorizontalDivider(vertical = SpacingSm)     ── the divider that used to sit above Calendar
├─ ReminderDeliveryRow                         ── NEW
├─ CalendarActionRow                           (unchanged)
├─ Spacer(SpacingSm) · Save · [Remove repeat]  (unchanged)
```

When `delivery == null`, the sheet is **pixel-identical to today**: one divider, then Calendar.

Not shown in the locked state (`isLocked`), because that branch replaces the whole tab content.

### 3.2 Three forms of the row

| Form | When | Anatomy | Tap |
|---|---|---|---|
| **Connect** | Telegram not linked (signed in **or** guest) | `AppCard(onClick)` → Row(Top, spacedBy(SpacingMd)): Send icon · Column(title, body) · chevron › | Opens the **Telegram screen** (`onConnectTelegramClick`) |
| **Choice, collapsed** | Linked **and** the server reports a channel | `AppCard(contentPadding = 0)` → header Row (clickable, `Role.Button`): Send icon · Column(title, current value) · expand arrow ⌄ | Expands in place |
| **Choice, expanded** | the user tapped the header | header (arrow ⌃) + inset divider + caption + radio group (Android 3 rows / web 2 rows) + optional error line, all inside the same card | Header tap collapses. A radio tap saves at once (§3.4). |

**Why inline expansion, not a dialog** (changed after the check, §8): the first pass put the radios in an `AlertDialog` over the sheet (the `EndConditionDialog` precedent). At 320 × fontScale 2.0 RU, the `headlineSmall` title "Куда приходят все напоминания" was 48sp: the word "напоминания" did not fit the 232dp dialog column, and the dialog was taller than the screen. Expanding in place keeps every line in `bodyLarge`, scrolls with the sheet (which already scrolls), and has a precedent in this same sheet: the "Custom" repeat section uses `AnimatedVisibility`. It also avoids a dialog over a modal sheet, and the reminder sheet has no snackbar host for the error.

**Why not three radio rows always visible:** they add about 170dp to a sheet that is already long (the Repeat tab), for a setting most users set once. Collapsed, the row costs one card.

### 3.3 Components → project design system

| Element | Project component | Tokens | States |
|---|---|---|---|
| Connect row | `AppCard(onClick = onConnectTelegramClick)`, the anatomy of `CalendarActionRow`: Row(`verticalAlignment = Top`, `spacedBy(AppDimens.SpacingMd)`) | icon `Icons.AutoMirrored.Outlined.Send` 24dp `primary` (same size and tint as the preset rows) · title `bodyLarge` onSurface · body `bodyMedium` onSurfaceVariant · trailing `Icons.AutoMirrored.Filled.KeyboardArrowRight` onSurfaceVariant | enabled |
| Choice card | `AppCard(contentPadding = PaddingValues(0))`, not clickable as a whole | AppCard defaults (cream `surfaceContainerLowest`, `outlineVariant` hairline, `shapes.medium`) | — |
| Choice header | `Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = …).padding(AppDimens.CardPadding), Top, spacedBy(SpacingMd))` | the same icon / title / value styles as Connect · trailing `Icons.Filled.KeyboardArrowDown`, rotated 180° when expanded | collapsed · expanded · saving · failed |
| Leading slot (24dp) | Send icon. **While saving**: `CircularProgressIndicator(20dp, strokeWidth 2dp, primary)` centred in the same 24dp box. This applies only when the card is **collapsed**; when it is expanded, the spinner sits in the tapped radio slot instead (one spinner on screen, never two). | primary | — |
| Inset divider | `HorizontalDivider(Modifier.padding(start = CardPadding + 24dp + SpacingMd))`, so it starts at the text column | `outlineVariant` | expanded only |
| Caption | `Text(reminder_channel_caption, bodySmall, onSurfaceVariant)`, `padding(start = text column, end = CardPadding, top = SpacingSm)` | — | expanded only |
| Radio group | `Column(Modifier.selectableGroup().padding(horizontal = CardPadding))` with rows = **the addendum's option row**: `Row(heightIn(min = MinTouchTarget).selectable(selected, role = RadioButton).padding(vertical = SpacingMd), Top, spacedBy(SpacingMd))` → [24dp control slot: `RadioButton(onClick = null)` or spinner] [Column: title `bodyLarge`, optional supporting `bodyMedium` onSurfaceVariant]. **No `groupSegment` skin here**: the rows are already inside a card, and a group inside a card is a double frame. | M3 radio defaults (primary / onSurfaceVariant) | unselected · selected · saving |
| Error line | `Text(telegram_reminders_save_failed, bodySmall, colorScheme.error)`, `padding(start = text column, end = CardPadding, bottom = SpacingMd)`, `semantics { liveRegion = Polite }` | `error` | after a failed save |

The control slot (24dp) sits directly under the header's icon column, and the radio titles start on the same x as the header title (frame C1).

**No new design-system component.** These are private composables inside `ReminderSheet.kt` (`ReminderDeliveryRow`, `DeliveryChoiceRow`), built from `AppCard`, M3 `RadioButton` / `CircularProgressIndicator` and tokens.

### 3.4 States (what the user sees)

The row reads `ReminderSheetState.delivery` (contract in §5.2).

| State | Collapsed card | Expanded card |
|---|---|---|
| **Hidden** (`delivery == null`): unknown link state, linked but channel not reported, locked sheet, or the caller did not wire it | no row, the sheet as today | — |
| **Connect, Android** | "Get reminders in Telegram" / "Connect the Gisti bot — works for all reminders" › | — (navigates) |
| **Connect, web** | "Get reminders in Telegram" / "No notifications on the web — connect the Gisti bot" › | — |
| **Idle, Android** | "Where all reminders go" / value = `telegram_reminders_option_*` of the confirmed channel ⌄ | caption + 3 radios, the confirmed one selected |
| **Idle, web** | "Where all reminders go" / "In Telegram" (`telegram`, `both`) or "Not sent to Telegram" (`push`) ⌄ | caption + 2 radios (addendum §2.1): "Send to Telegram" (+ "The web app can't send notifications") / "Don't send to Telegram" |
| **Saving** | leading icon → 20dp spinner. The value line keeps the **confirmed** value until the server answers. | the tapped row's radio → spinner; **no** row shows a selected radio (addendum §4) |
| **Saved** | spinner → icon; the value line shows the new value | the spinner becomes the selected radio. No toast. The card **stays expanded**: auto-collapsing would remove the focused radio from under TalkBack. |
| **Failed → revert** | the value line is replaced by `reminder_channel_save_failed_short` in `error` until the next tap on the card | the radio returns to the last **confirmed** value, and the error line appears under the group. Retry = tap the radio again. |
| **Disabled** | — (nothing in the sheet disables the row; an unlink in progress happens on the Telegram screen, not here) | — |

Expanded / collapsed is UI-local state: `rememberSaveable { false }`, collapsed on every sheet open. The failed flag clears on the next radio tap and on the next sheet open.

A save that is still running when the user closes the sheet **completes in the background**: the request runs in a repository or app scope, not in the sheet host's ViewModel scope (§5.2). The next sheet open shows the result. A failure that lands after the sheet closed has no UI. It is logged, and the server value wins on the next status refresh. This is acceptable because the radio never claimed success.

### 3.5 Copy rules

- The title says **"all"** ("Where all reminders go" / «Куда приходят все напоминания»), so a user who never expands the row still learns it is global. When expanded, the caption repeats it once at the point of choice: "For every reminder, not just this one."
- The web never shows "Phone notification" (addendum §2.1). The web values describe Telegram only.
- The Connect body on Android says "works for all reminders", so the user knows linking is not per reminder.

---

## 4. Home (Inbox) banner: Telegram promo

### 4.1 Placement

```
InboxContent
├─ AppPageChipRow                         (unchanged)
└─ HorizontalPager
   └─ InboxPageList(page, listHeader = …)
      └─ LazyColumn
         ├─ item: listHeader              ── ONE slot, at most ONE banner:
         │     notifications-off banner   (if visible)    ── priority 1
         │     else Telegram promo        (if visible)    ── priority 2, NEW
         ├─ section headers + task rows   (unchanged)
         └─ add-task row / AI source row  (unchanged)
```

- **Inbox page only** (page 0, `page.isInbox`), not on project pages. The notifications banner repeats on every page because it reports a problem. A suggestion does not need to follow the user through every project.
- Same modifier as the notifications banner: `padding(horizontal = ScreenPaddingHorizontal).padding(bottom = SpacingSm)`. On Medium / Expanded it takes the same width as the list rows (frame A6).
- It scrolls with the rows: it is a `LazyColumn` item, not sticky, not an overlay.

### 4.2 Component: extend `InlineNudgeCard`, don't fork it

`InlineNudgeCard` (`feature/home/.../detail/InlineNudgeCard.kt`) already owns the tile, title/body, the 48dp ×, the tile-hides-at-fontScale-1.5 rule and the screen-reader order. Add three optional parameters. Their defaults keep the notifications banner and the recurring nudge **byte-identical**:

| New param | Default | Promo passes | Effect |
|---|---|---|---|
| `onClick: (() -> Unit)?` | `null` | `onOpenTelegram` | `AppCard(onClick = …)`: the whole card is tappable, × excluded (the `IconButton` consumes its own tap) |
| `actionStyle: NudgeActionStyle` | `Button` | `Link` | `Button` = today's full-width `AppButtonSecondary`. `Link` = the action label as `Text(labelLarge, colorScheme.primary)` **inside the text column**, under the body, `padding(top = SpacingXs)`, **not separately clickable** (the card is the click target) |
| `hideBodyAtLargeFont: Boolean` | `false` | `true` | At `fontScale ≥ 1.5` (the existing `TILE_HIDE_FONT_SCALE`) the body is not composed. Title + link stay. |

Promo call:
```
InlineNudgeCard(
  icon = Icons.AutoMirrored.Outlined.Send,
  title = telegram_promo_title,
  body = if (isWeb) telegram_promo_body_web else telegram_promo_body,
  actionLabel = if (fontScale >= 1.5f) telegram_promo_cta_short   // "Connect"; the title already names Telegram
                else telegram_connect_cta,                          // reused key, "Connect Telegram"
  onAction = onOpenTelegram,                     // the same lambda as onClick
  onClick = onOpenTelegram,
  actionStyle = NudgeActionStyle.Link,
  hideBodyAtLargeFont = true,
  onDismiss = onDismissTelegramPromo,
  dismissLabel = telegram_promo_dismiss_cd,
  rowAlignment = Alignment.Top,
)
```
Wrap it in the same enter/exit recipe as `AnimatedNotificationsOffBanner`: `MutableTransitionState(initial = visible)`, `fadeIn + expandVertically` / `fadeOut + shrinkVertically`, and `EnterTransition.None` / `ExitTransition.None` under `LocalReducedMotion`. If the banner is visible on the first frame, it starts settled. Either generalise that wrapper or add `AnimatedTelegramPromoBanner` beside it.

**AI in the title, not only in the body (owner Q4, 2026-11-21).** The body is not composed at fontScale ≥ 1.5, so an AI mention that lived only in the body would disappear for exactly the large-font users. The title carries it: "Reminders and AI chat in Telegram". The body stays as it was, about reminders and capture.

**Height after Q4 at 320 × 2.0 RU (calculated, not re-rendered).** Text column = 320 − 2×16 (list inset) − 2×12 (card padding) − 48 (×) − 12 (gap) = 204dp. The title «Напоминания и ИИ-чат в Telegram» in titleSmall × 2.0 (28sp / 40sp line) takes **3 lines** instead of 2 (+40dp). The link would also take 2 lines («Подключить / Telegram»), so at fontScale ≥ 1.5 it uses the short `telegram_promo_cta_short` «Подключить» (1 line, −40dp). Net: 12 + 120 + 4 + 40 + 12 ≈ **188dp**, the same as the checked frame A2 (about 190dp). At 1.0 the body is unchanged, and the title wraps the same way the old one did in RU (frame A4: 2 lines with the tile). The implementer's A2 frame with the real bottom plate is the binding check (§8).

**Why a quieter anatomy than the notifications banner (changed after the check, §8):** with the full-width button the promo was about 400dp at 320 × 2.0 RU and filled the whole first screen, so no task was visible (frame A2, first pass). With the link and the body dropped at large font it is about 190dp, and the first task is above the fold. It is also the correct hierarchy. The notifications banner reports something broken and earns a button. The promo is a suggestion and earns a link. The two never show together (§4.3), so the slot never shows two styles at once.

### 4.3 When the banner shows: a pure function for the ViewModel

`shouldShowTelegramPromo(input): Boolean`, unit-tested. It lives next to `shouldShowNotificationsBanner` (`feature/home/.../notifications/NotificationsBannerSession.kt`).

```kotlin
data class TelegramPromoInput(
    val link: TelegramLinkKnowledge,       // Unknown | SignedOut | NotLinked | Linked
    val retired: Boolean,                  // persisted: true once Linked was EVER observed
    val dismissCount: Int,                 // persisted lifetime dismissals, 0..3
    val lastDismissedAtMs: Long?,          // persisted
    val nowMs: Long,
    val isInboxPage: Boolean,
    val inboxHasTasks: Boolean,            // page.tasks.isNotEmpty()
    val notificationsBannerVisible: Boolean,
    val dismissedThisSession: Boolean,     // in-memory, hides instantly before DataStore writes
    val cycleSeenDays: Int,                // persisted, distinct calendar days shown in the current cycle
)

const val TG_PROMO_MAX_CYCLES = 3
const val TG_PROMO_REAPPEAR_DAYS = 14
const val TG_PROMO_AUTO_REST_SEEN_DAYS = 7   // owner decision 2026-11-21 (Q3)
```

Rules, in order (the first match returns `false`):

| # | Rule | Why |
|---|---|---|
| 1 | `retired` | The owner: gone for good after linking, even if the user unlinks later |
| 2 | `link` is `Unknown` or `Linked` | `Linked` covers rule 1. `Unknown` (signed in, but status never fetched, or the last fetch failed with nothing cached): never advertise to someone who may already be linked. **`SignedOut` (guest) and `NotLinked` both pass** (owner, Q2). A guest is known locally from the auth state, so no network call is needed. The guest path is in §4.5. |
| 3 | `dismissCount >= MAX_CYCLES` | at most 3 cycles |
| 4 | `dismissCount > 0 && nowMs - lastDismissedAtMs < 14 days` (a negative difference, i.e. the clock moved back, also counts as "not yet") | returns 14 days after a dismissal |
| 5 | `dismissedThisSession` | instant hide on × |
| 6 | `!isInboxPage` | Inbox page only (§4.1) |
| 7 | `!inboxHasTasks` | never over the empty state: the empty Inbox hosts the user's first action (`emptyStateHostsAddTask`), and a promo there competes with it |
| 8 | `notificationsBannerVisible` | one banner per slot; the broken-reminders warning wins |
| 9 | `cycleSeenDays >= AUTO_REST_SEEN_DAYS` | the cycle closes silently after 7 distinct days of showing and counts toward the 3-cycle limit, the same as a dismissal (owner, Q3) |
| — | otherwise `true` | |

**Writes (VM / repository):**
- × → `dismissedThisSession = true`; persist `dismissCount + 1`, `lastDismissedAtMs = now`, `cycleSeenDays = 0`.
- Rule 9 hit → persist the same as a dismissal (count + 1, timestamp, seen days 0). No analytics `dismissed`; fire `auto_rested` (§6).
- The banner composes visible on a calendar day not counted yet → `cycleSeenDays + 1`, `lastSeenDay = today`.
- `link == Linked` observed anywhere (status, claim, the Telegram screen) → `retired = true`. Write this in the single writer of the link cache (§5.1), not in the Inbox VM, so a link made while Inbox is not composed still retires the banner.
- Tap → no counter changes. The user goes to the Telegram screen. If they link, rule 1 hides the banner. If they come back without linking, the banner is still there, which is correct: they may want to try again.

**Test cases (minimum):** each rule alone flips the result · `SignedOut` (guest) → **true** when nothing else blocks · `NotLinked` → true · `Unknown` → false · `Linked` → false · the 14-day boundary (13d23h → false, 14d → true) · the third dismissal → never again, even after 14 days · 7 seen days → false and the cycle closes; two auto-rests + one dismissal = 3 cycles → never again · `retired` beats everything, even with `link = NotLinked` or `SignedOut` · clock moved back → false · notifications banner visible → false · empty Inbox → false · project page → false.

### 4.5 Guest path (tap from a guest, verified against the Telegram screen spec)

The Telegram screen already has a state for this. `docs/designs/telegram-screen-2026-11-19/spec.md` §4.2 **SignedOut** shows the hero ("Your checklists in Telegram"), then a card titled **"Sign in to connect Telegram"** with the body "The bot uses your Google account, so your checklists stay the same everywhere" and a **Sign in** button, then the benefits. The route is clear, so no change to that screen is needed. What the guest goes through:

1. Tap the banner → Telegram screen, **SignedOut**: the sign-in card is the first block under the hero (the CTA is above the fold, checked at 320 × 1.5 in that spec).
2. Sign in → the same Google sign-in as the drawer → **Loading** → `status`:
   - **NotLinked** (the usual case) → the **Connect Telegram** button directly under the hero. One more tap finishes the link.
   - **Linked** (an account that was linked before) → the Linked card. The cache writer sets `retired`, so the banner is gone when the user goes back.
3. Sign-in cancelled or failed → stays SignedOut with the app's existing sign-in failure feedback. The banner is still on Inbox (no counters changed).

After signing in, the banner's input switches from `SignedOut` to `NotLinked` (or `Linked` → retired). The cycle counters carry over: signing in does not reset them.

### 4.4 Where the banner never appears

Splash, WelcomeOnboarding, the activation flow, any route other than the Inbox tab, Inbox `Loading` / `Error`, project pages, an empty Inbox, status unknown for a signed-in user, while the notifications-off banner is up, the CONTROL shell / Projects tab (owner, Q1: Inbox only). Guests **do** see it (§4.5).

---

## 5. Data contract for `@feature-expert` / `@core-expert`

### 5.1 One shared link-state source (core)

Both surfaces live in feature modules (`feature/checklist`, `feature/home`, `feature/create`). They cannot reach `TelegramLinkRepository`, which is in `composeApp`. They need, from core:

```kotlin
sealed interface TelegramLinkKnowledge {
    data object Unknown : TelegramLinkKnowledge
    data object SignedOut : TelegramLinkKnowledge
    data object NotLinked : TelegramLinkKnowledge
    data class Linked(val reminderChannel: ReminderChannel?) : TelegramLinkKnowledge // null = server does not report it
}
val telegramLink: Flow<TelegramLinkKnowledge>          // persisted cache, survives restart
suspend fun setReminderChannel(c: ReminderChannel): Result<ReminderChannel>  // the existing call, reachable from features
```
- **Writer:** `TelegramLinkRepositoryImpl` is already the single writer of the channel cache (it writes on every `status` / `unlink` / `set_reminder_channel`). It becomes the single writer of this cache too: `status` → NotLinked/Linked, `claim` OK → Linked, `unlink` OK → NotLinked, sign-out → SignedOut, a failed fetch with no cache → stays Unknown. Linked → also sets the promo `retired` flag.
- **Freshness:** the existing app-foreground refresh (`TelegramModule`, `refreshChannel = { telegram.status() }`) already calls `status` when the app comes to the foreground. No extra network call is needed for either surface.
- Placement (extend `ReminderDeliveryPrefsRepository` or add a new core API) is `@core-expert`'s decision.

### 5.2 `ReminderSheetState` / `ReminderSheetCallbacks` additions

```kotlin
sealed interface ReminderDeliveryRowUi {
    data class Connect(val web: Boolean) : ReminderDeliveryRowUi
    data class Choice(
        val confirmed: ReminderChannel,
        val saving: ReminderChannel?,   // non-null → spinner; addendum §6 semantics
        val failed: Boolean,
        val web: Boolean,
    ) : ReminderDeliveryRowUi
}
// ReminderSheetState
val delivery: ReminderDeliveryRowUi? = null          // null = row hidden (default keeps every caller unchanged)
// ReminderSheetCallbacks (all default to {} so unwired callers compile unchanged)
val onDeliveryChannelClick: (ReminderChannel) -> Unit = {}   // Android
val onDeliveryWebClick: (on: Boolean) -> Unit = {}            // web → VM resolves via webReminderTarget(current, on)
val onConnectTelegramClick: () -> Unit = {}
```
- **Mapping** of `TelegramLinkKnowledge` → `delivery`: Unknown → null · SignedOut / NotLinked → `Connect` · `Linked(null)` → null · `Linked(c)` → `Choice(confirmed = c, …)`.
- **Save semantics** are the same as the Telegram screen (addendum §6): latest tap wins; revert to the last confirmed value; the same tapped value → no request; the device zone goes in the same write. Do not copy the logic into each host VM. Put one shared **delivery-channel controller** in core or a shared feature module, and let each host VM expose its state. The save must run in a scope that outlives the sheet (§3.4).
- **Hosts to wire** (every `ReminderSheet(` call site): `ChecklistDetailScreen.kt:2213, 2254, 2325`, `InboxScreen.kt:954`, `DraftDueSheetHost.kt:38`, `CreateChecklistScreen.kt:617`. The minimum is checklist detail + Inbox. An unwired host shows no row (null default), which is a safe partial rollout.
- **Connect navigation:** `onConnectTelegramClick` is hoisted to `App.kt`, which pushes the Telegram route with analytics `source = reminder_sheet`. On Back the user should land on the **same sheet with its pending Repeat config**, and the row then shows the new link state. Verify in all layouts that the `ModalBottomSheet` is not still drawn over the pushed Telegram screen (two-pane Expanded, web single-pane). If it is, close the sheet before navigating and keep the pending Repeat config in the host VM.

### 5.3 Inbox VM additions

`InboxScreenState.Content.telegramPromoVisible: Boolean` (from §4.3), plus `InboxIntent.OnTelegramPromoClick` / `OnTelegramPromoDismiss`. The click emits a navigation side effect to the Telegram route (`source = home_banner`), following the existing deeplink pattern (`.claude/rules/adaptive-navigation.md`).

---

## 6. Analytics

| Event | When | Params |
|---|---|---|
| `telegram_promo_shown` | the banner is composed visible, at most once per app session | `cycle` = `dismissCount + 1` (1..3), `screen` = `inbox`, `state` = `signed_out` \| `not_linked` |
| `telegram_promo_tapped` | card tap (the link is part of the card) | `cycle`, `state` |
| `telegram_promo_dismissed` | × | `cycle`, `state`, `dismiss_count` = persisted count AFTER this dismissal |
| `telegram_promo_auto_rested` | rule 9 closed a cycle after 7 seen days | `cycle`, `state` |
| `telegram_screen_opened` (existing `SCREEN_OPENED`) | **add** `source` = `home_banner` \| `reminder_sheet` \| `drawer` \| `overview` \| `deep_link` | so `telegram_link_completed` can be attributed to the banner and the sheet through the screen-open funnel |
| `telegram_reminder_row_tapped` | any tap on the sheet row header or the Connect row | `state` = `linked` \| `not_linked` \| `signed_out`; `action` = `expand` \| `collapse` \| `connect`; `surface` = `checklist` \| `item` \| `inbox_task` \| `draft` \| `create` |
| `tg_reminder_channel_set` (existing `REMINDER_CHANNEL_SET`) | the server confirmed a channel from the sheet | `channel`; `source` = **new** `SOURCE_REMINDER_SHEET = "reminder_sheet"` (the screen keeps `app`) |
| `tg_reminder_channel_set_failed` | a save from the sheet or the screen failed | `channel` (attempted), `source`, `reason` = `network` \| `server` \| `account` |

Constants go in `AnalyticsEvents.Telegram`. Params reuse `AnalyticsParams.SOURCE / SCREEN / ACTION / SURFACE / DISMISS_COUNT / REASON`; `cycle` and `state` are new.

---

## 7. Accessibility, adaptive, motion

**Banner**
- The whole card is one button node (`AppCard(onClick)` merges title, body and link). TalkBack reads: "Reminders and AI chat in Telegram, Get reminders as messages…, Connect Telegram, button". The × is a separate 48dp `IconButton` with `contentDescription = telegram_promo_dismiss_cd`, ordered last (`traversalIndex = 1f`, as today). The tile is decorative.
- Touch: the card is ≥ 48dp tall at every scale; the × is 48dp.
- Contrast: link `primary` #1565C0 on the cream card ≈ 5.6:1 (AA text). Dark `primary` #90CAF9 on #221F1B is high.

**Sheet row**
- Header: `Role.Button`, `onClickLabel` = "Show options" / "Hide options" (strings below), and `stateDescription` = expanded / collapsed. Connect row: `Role.Button`, merged.
- Radios: `selectableGroup()`, each row `selectable(role = RadioButton)` with descendants merged; the saving row gets `stateDescription = telegram_reminders_saving_cd` and the spinner `clearAndSetSemantics {}`, as in the addendum.
- Error line: `liveRegion = Polite`. The radio revert is announced by its own state.
- fontScale: no fixed heights; no `maxLines`. **At `fontScale ≥ 1.5` the trailing chevron / arrow is not composed** (new private `const val TRAILING_HIDE_FONT_SCALE = 1.5f` in `ReminderSheet.kt`). At 320 × 2.0 RU the word «Напоминания» overflowed into the chevron (§8). Expanded / collapsed state is still conveyed by the content below the header and by `stateDescription`.
- Touch: the header is ≥ 72dp, radio rows ≥ 48dp (`heightIn(min = MinTouchTarget)`).

**Adaptive.** The sheet is the existing `ModalBottomSheet` at every size; the row is full width inside it. The banner follows the Inbox list's row width (frame A6, 1024 web).

**Motion.** Expand / collapse: `AnimatedVisibility(expandVertically + fadeIn / shrinkVertically + fadeOut)` with `AppMotion` tweens, the same as the "Custom" repeat section. The arrow rotates with `animateFloatAsState(AppMotion.effectsDefaultTween())`. Under `LocalReducedMotion` both are instant. The banner uses the `AnimatedNotificationsOffBanner` recipe (§4.2).

---

## 8. Проверка (one prototype pass, 2026-11-21)

**What was checked:** a disposable HTML prototype that copies this spec's tokens. Hex values come from `Color.kt` (sand / cream / warm-dark, Blue40 / Blue80 primary), type from `Type.kt`, and fontScale is a multiplier on every sp. It was rendered with Playwright headless Chromium, and every frame was viewed. No Compose infrastructure was set up for this.

**Frames (in git, next to this file):**
> **The A frames show the pre-Q4 title** "Reminders in Telegram" and the full link at every fontScale. The Q4 title (+1 line at 2.0 RU) and the short link at ≥ 1.5 (−1 line) were **calculated, not re-rendered** (§4.2: about 188dp at 320 × 2.0 RU, the same as A2). The implementer's frames are the binding ones.

- `frames/a-inbox-banner-320.png`: A1 320 × 1.0 EN light · A2 320 × **2.0** RU light (fold at 640dp marked) · A3 320 × 1.5 HI dark · A4 320 × 1.0 RU light **web** body · A5 priority: notifications-off banner shown, promo hidden.
- `frames/a6-inbox-banner-1024-web.png`: 1024 web EN light, list capped.
- `frames/b-sheet-once-row-states.png`: Once tab: B1 Android linked `both` 320 × 1.0 EN · B2 linked `telegram` 320 × 2.0 RU · B3 **Connect** Android 320 × 2.0 RU · B4 web linked `push` 320 × 1.0 RU · B5 web Connect 320 × 1.5 HI dark.
- `frames/b-sheet-repeat-saving-error.png`: Repeat tab tail: B6 linked 320 × 1.5 HI dark · B7 **saving** (collapsed, spinner in the leading slot) 320 × 2.0 RU · B8 **failed** 320 × 2.0 RU.
- `frames/c-sheet-row-expanded.png`: C1 expanded Android idle 320 × 1.0 EN · C2 expanded **saving** 320 × 2.0 RU · C3 expanded **error → reverted** 320 × 1.5 HI dark · C4 expanded **web off** 320 × 2.0 RU · C5 expanded web on 1.0 EN dark.

**What the first pass found, and how the spec changed:**
1. **Promo banner too tall at 320 × 2.0 RU** (InlineNudgeCard with a full-width button): about 400dp, the whole first screen, with no task above the fold. → `actionStyle = Link`, whole-card tap, and the body dropped at fontScale ≥ 1.5 (§4.2). Now about 190dp, and the first task is visible (A2).
2. **Channel picker as an AlertDialog over the sheet** broke at 320 × 2.0 RU: the 48sp headline split «напоминания», and the dialog was taller than the screen. It also had no snackbar host for the error. → Inline expansion in the same card (§3.2), error line inside the card (C2–C4).
3. **Trailing chevron squeezed the title column** at 320 × 2.0: «Напоминания» overflowed under the chevron (B3, first pass). → Trailing glyphs are hidden at fontScale ≥ 1.5 (§7). The saving spinner moved to the **leading** slot, so it does not depend on the trailing one (B7).
4. **The link with a trailing chevron** hung alone when "Подключить Telegram" wrapped on web RU (A4). → The banner link has no chevron. Long web body shortened: «В веб-версии нет уведомлений — бот пришлёт их в Telegram.»

**After the fixes:** no clipping in RU or HI at 1.5 / 2.0, in light or dark. The promo never pushes the first task below the fold at 320 × 2.0. The expanded radio group lines up under the header's icon and text columns. The web shows no "phone" wording.

**Not covered by the prototype:** real Compose text metrics and Devanagari shaping in Skiko, the real `ModalBottomSheet` scroll with the expanded card, the TalkBack order, and the real Inbox list width on Medium / Expanded. These are in the implementer's matrix.

### Screenshot matrix for `@compose-expert` (screenshot-driven-ui, `@Preview` per state)

| Axis | Values |
|---|---|
| Banner states | visible (Android body) · visible (web body) · notifications banner wins · hidden (empty Inbox) · fontScale ≥ 1.5 (no body, short link «Подключить», 3-line RU title) |
| Sheet row states | Connect Android · Connect web · Choice collapsed (`push` / `telegram` / `both`) · web collapsed on / off · expanded Android · expanded web · saving collapsed · saving expanded · failed collapsed · failed expanded · hidden (`null`, must match today's sheet pixel for pixel) |
| Tabs | Once · Repeat (the row sits above Calendar in both) · per-item sheet with the full-screen toggle on |
| Width | 320dp · 411dp · web 1024 |
| Theme | light · dark |
| fontScale | 1.0 · 1.5 · **2.0** |
| Locale | EN · RU · HI |

**Minimum frames for the report:** A2 and B7 / C2 equivalents at 320 × 2.0 RU light · A3 and C3 at 320 × 1.5 HI dark · B1 at 411 × 1.0 EN (baseline) · the hidden-row sheet against today's golden · A6 on a real wasmJs canvas at 1024.

**Edge data:** the longest task title on the first row (so the fold check is honest) · `telegram_promo_body` in RU (longest) · `reminder_channel_connect_body_web` in HI · `telegram_reminders_save_failed` in HI.

### Reference frame
The PNGs in `frames/` are the reference for anatomy and proportions, not for pixels. The live references are the **notifications-off banner** (same slot, same card family) and the sheet's **CalendarActionRow** (same row family).

---

## 9. Strings (`core/designsystem/src/commonMain/composeResources/values{,-ru,-hi}/strings.xml`)

Write apostrophes literally (`can't`), never `\'`.

| Key | EN | RU | HI |
|---|---|---|---|
| `telegram_promo_title` | Reminders and AI chat in Telegram | Напоминания и ИИ-чат в Telegram | Telegram में रिमाइंडर और AI चैट |
| `telegram_promo_cta_short` | Connect | Подключить | जोड़ें |
| `telegram_promo_body` | Get reminders as messages and turn any message into a checklist. | Получайте напоминания сообщениями и превращайте любое сообщение в чек-лист. | रिमाइंडर मैसेज में पाएं और किसी भी मैसेज को चेकलिस्ट बनाएं। |
| `telegram_promo_body_web` | The web app has no notifications — the bot sends reminders to Telegram. | В веб-версии нет уведомлений — бот пришлёт их в Telegram. | वेब ऐप में नोटिफ़िकेशन नहीं हैं — बॉट रिमाइंडर Telegram पर भेजेगा। |
| `telegram_promo_dismiss_cd` | Hide Telegram tip | Скрыть подсказку о Telegram | Telegram सुझाव छिपाएं |
| `reminder_channel_title` | Where all reminders go | Куда приходят все напоминания | सभी रिमाइंडर कहां आएं |
| `reminder_channel_caption` | For every reminder, not just this one. | Для всех напоминаний, не только для этого. | हर रिमाइंडर के लिए, सिर्फ़ इसके लिए नहीं। |
| `reminder_channel_value_web_on` | In Telegram | В Telegram | Telegram पर |
| `reminder_channel_value_web_off` | Not sent to Telegram | Не присылаются в Telegram | Telegram पर नहीं भेजे जाते |
| `reminder_channel_save_failed_short` | Couldn't save. Try again. | Не сохранилось. Попробуйте ещё раз. | सेव नहीं हुआ। फिर से कोशिश करें। |
| `reminder_channel_connect_title` | Get reminders in Telegram | Напоминания в Telegram | रिमाइंडर Telegram में पाएं |
| `reminder_channel_connect_body` | Connect the Gisti bot — works for all reminders | Подключите бота Gisti — для всех напоминаний | Gisti बॉट जोड़ें — सभी रिमाइंडर के लिए |
| `reminder_channel_connect_body_web` | No notifications on the web — connect the Gisti bot | В веб-версии нет уведомлений — подключите бота Gisti | वेब पर नोटिफ़िकेशन नहीं — Gisti बॉट जोड़ें |
| `reminder_channel_show_options` | Show options | Показать варианты | विकल्प दिखाएं |
| `reminder_channel_hide_options` | Hide options | Скрыть варианты | विकल्प छिपाएं |
| `reminder_channel_expanded_sd` | Expanded | Развёрнуто | खुला है |
| `reminder_channel_collapsed_sd` | Collapsed | Свёрнуто | बंद है |

**Reused keys:** `telegram_connect_cta` (banner link) · `telegram_reminders_option_push` / `_telegram` / `_telegram_body` / `_both` (Android values and radios) · `telegram_reminders_web_on` / `_web_on_body` / `_web_off` (web radios) · `telegram_reminders_save_failed` (expanded error line) · `telegram_reminders_saving_cd`.

**Copy check:** nothing mentions Stars, payment, premium, prices, limits or credits. The AI chat appears **only** in `telegram_promo_title`, as a feature, with nothing about how it is paid for or limited (owner Q4). Web strings never promise phone notifications. "All" / «все» / «для всех» appears in the row title, the caption and the Connect body. The RU / HI lengths were checked at 320 × 2.0 / 1.5 (§8).

---

## 10. Handoff

- **Order:** `@core-expert` (§5.1: shared `TelegramLinkKnowledge` cache + a `setReminderChannel` reachable from features + the promo `retired` write) → `@feature-expert` (§4.3 pure function + tests, Inbox VM fields and intents, persisted promo counters in `HintsRepository`, the `ReminderSheetState.delivery` mapping and the shared delivery-channel controller, host wiring, navigation, analytics §6) → `@compose-expert` (`InlineNudgeCard` params §4.2, banner slot, `ReminderDeliveryRow`, previews, matrix §8).
- **Files:**
  - `feature/home/.../detail/InlineNudgeCard.kt` (3 optional params; defaults leave current callers untouched)
  - `feature/home/.../inbox/InboxScreen.kt` (`listHeader` chooses notifications vs promo, page 0 only), `InboxViewModel.kt`, the Inbox contract
  - `feature/home/.../notifications/` (next to `shouldShowNotificationsBanner`): `shouldShowTelegramPromo` + test
  - `core/datastore/api/.../HintsRepository.kt` (+ impl): promo dismiss count, last dismissed, retired, seen days, last seen day
  - `feature/checklist/.../ui/reminder/ReminderSheet.kt` + `ReminderSheetState.kt`
  - the six `ReminderSheet(` call sites (§5.2), `App.kt` (Telegram route with `source`)
  - `core/common/api/.../AnalyticsEvents.kt`, the three `strings.xml`
- **Reuse:** `InlineNudgeCard`, the `AnimatedNotificationsOffBanner` motion recipe, `AppCard`, M3 `RadioButton(onClick = null)` + `selectable` / `selectableGroup` (the addendum row), `CircularProgressIndicator` 20 / 2dp, `webReminderTarget` (move it to where both the screen and the sheet can call it), `TelegramLinkRepository.setReminderChannel` semantics. **No new design-system component.**
- **Pitfalls:**
  1. **The sheet over the pushed Telegram screen** (§5.2): `ModalBottomSheet` is a window. If the host entry stays composed (two-pane Expanded), the sheet may float over the Telegram screen. Check it, and if it happens, close the sheet first and keep the Repeat draft in the VM.
  2. **Save scope:** a save started in the sheet must not be cancelled when the sheet's host VM scope dies or the sheet closes (§3.4).
  3. **`retired` written in the cache writer, not in the Inbox VM:** a link made from the bot's deep link (`?tg_link` claim) while Inbox is not composed must still retire the banner.
  4. **Inbox `listHeader` is composed on every page.** The promo must be gated by `page.isInbox` inside the lambda, and `telegram_promo_shown` must fire once per session, not once per page.

## Owner decisions on the open questions (2026-11-21, closed)

- **Q1. Inbox only.** No banner on the Projects tab or the CONTROL shell root (`MainScreen`).
- **Q2. Guests see the banner.** A tap opens the Telegram screen, which asks them to sign in first (§4.5). Rule 2 passes `SignedOut` (§4.3). The sheet's Connect row was already shown to guests.
- **Q3. Auto-rest accepted.** A cycle closes after 7 distinct days of showing and counts toward the 3-cycle limit (rule 9).
- **Q4. The AI chat is mentioned** in the banner text, in all three languages (`telegram_promo_title`), with nothing about payment, Stars, premium, prices or limits. The banner height at 320 × 2.0 RU was recalculated (§4.2).

## Out of scope — lines for the lead
- **Full-screen alarm + `telegram` channel:** the per-item sheet's full-screen toggle does nothing when the channel is Telegram-only (the phone posts no notification). This spec does not grey it out. A one-line hint or a disabled state is a separate decision.
- **Notifications off on Android with `push`/`both`:** that user is exactly the Telegram promo's audience, but the promo yields to the notifications banner (§4.3, rule 8). A combined "turn on notifications or get them in Telegram" variant is a separate idea. <!-- docs-leak-scan: reviewed — no number, audience wording -->
- **Web is moving to the React SPA (ADR 2026-11-18):** this spec covers the Compose wasmJs web. The React port should reuse §3–§4 and the strings.
