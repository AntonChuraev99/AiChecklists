# DESIGN_SPEC — Telegram screen (account linking)

- **Platform:** Android + Web (one `commonMain` Compose Multiplatform screen, Material 3, project design system "Soft & tactile")
- **Method:** native
- **Screen:** `TelegramScreen` (drawer row "Telegram", next to "Gisti MCP")
- **Contract:** `docs/active/telegram-bot-phase1-2026-11-19.md`, section "App" (CF `telegram_account`: `create_token` · `claim` · `status` · `unlink`)
- **FEATURE_SPEC:** none was provided. This spec designs the full state set below, and `@feature-expert` turns it into the `UiState` matrix.
- **Reference implementation for the screen's anatomy:** `composeApp/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/mcp/McpScreen.kt`. Same shell, hero, value-prop rows and content width.

> ⛔ **Google Play anti-steering.** This screen never mentions payment, Stars, premium in the bot, prices, "cheaper" or AI credits. Copy covers checklists, chat and (later) notifications only. All strings below have been checked against this rule. Any future copy edit must be checked again.

---

## 1. Structure

The hero stays fixed. The **state slot** below it swaps by state. A tall state can never push the main action below the fold, because the action sits directly under the hero and the benefit rows come after it.

```
AppScaffold(title = telegram_title, onBackButtonClick = onBack, snackbarHost = SnackbarHost(state))
└─ Column(fillMaxSize, verticalScroll, horizontalAlignment = CenterHorizontally)
   └─ Column(Modifier.adaptiveContentWidth(600).fillMaxWidth()
             .padding(horizontal = AppDimens.ScreenPaddingHorizontal))
      ├─ Spacer(SpacingLg)
      ├─ HeroTile 56dp                                  ── decorative
      ├─ Spacer(SpacingMd)
      ├─ Text telegram_hero_title   (titleLarge, onBackground, heading())
      ├─ Spacer(SpacingSm)
      ├─ Text telegram_hero_body    (bodyLarge, onSurfaceVariant)
      ├─ Spacer(SpacingXl)                              ── section break
      ├─ AnimatedContent(targetState = stateKind) {     ── STATE SLOT, see §4
      │     SignedOut | NotLinked | Pending | Linked | Error | Loading
      │  }
      └─ Spacer(SpacingXl)
```

Block order inside each state, top to bottom:

| State | Order |
|---|---|
| SignedOut | sign-in card → Spacer(SpacingXl) → benefits |
| NotLinked | Connect CTA → Spacer(SpacingMd) → hint note → Spacer(SpacingXl) → benefits |
| Pending | pending card (no benefits) |
| Linked | account card → Spacer(SpacingLg) → Open bot CTA → Spacer(SpacingXl) → benefits → Spacer(SpacingXl) → Disconnect |
| Error | error card (no benefits) |
| Loading | skeleton card (no benefits) |

**Benefits** = 3 `ValuePropRow`s with `Spacer(SpacingLg)` between rows, identical in every state that shows them.

## 2. Components → project design system

| Element | Project component | Tokens | States |
|---|---|---|---|
| Shell, top bar, back | `AppScaffold(title, onBackButtonClick, snackbarHost)` | as on McpScreen | — |
| Content width | `Modifier.adaptiveContentWidth(600)` | same as MCP | — |
| Hero tile | **custom on tokens** (MCP's `HeroSparkleTile` shape, but NOT the AI gradient): `Box(size 56dp, clip(MaterialTheme.shapes.medium), background(colorScheme.primary))` + `Icon(Icons.AutoMirrored.Outlined.Send, tint = colorScheme.onPrimary)` at 28dp, the same glyph size as MCP's hero | primary / onPrimary, `shapes.medium` (16dp) | static |
| Benefit row | **lift `ValuePropRow` out of McpScreen** into a shared internal composable (see Handoff) with one change: text `bodyLarge`, NOT `titleMedium` (see §8, "Check") | tile 40dp `primaryContainer` / icon `onPrimaryContainer` 22dp, clip `MaterialTheme.shapes.small` (12dp, same value MCP hardcodes); text `onSurface` | static |
| Sign-in card | `AppCard` (flat cream, hairline), `contentPadding = PaddingValues(AppDimens.CardPadding)` | title `titleMedium` onSurface; body `bodyMedium` onSurfaceVariant | — |
| Sign-in CTA | `AppButton(text = telegram_signin_cta, icon = Icons.Outlined.Login, modifier = fillMaxWidth)` | primary, `AppShapeTokens.Button` | enabled · loading (while Google sign-in is running) |
| Connect CTA | `AppButton(text = telegram_connect_cta, icon = Icons.AutoMirrored.Outlined.Send, loading = isCreatingToken, fillMaxWidth)` | primary | enabled · loading |
| Hint note | Row = `Icon(Icons.Outlined.Info, 16dp, onSurfaceVariant)` + `Text(bodySmall, onSurfaceVariant)` — MCP `RequiresGoogleNote` anatomy, `verticalAlignment = Top` | `SpacingSm` gap | — |
| Pending card | `AppCard`, Column `spacedBy(0)` with explicit spacers (§4.3) | — | idle · checking |
| Pending spinner | `CircularProgressIndicator(size 20dp, strokeWidth 2dp, color = primary)` | primary | always on (indeterminate) |
| Step number | **custom on tokens**: `Box(24dp, CircleShape, primaryContainer)` + `Text(labelMedium, onPrimaryContainer)` | primaryContainer | — |
| Open Telegram (pending) | `AppButton(icon = Icons.AutoMirrored.Filled.OpenInNew, fillMaxWidth)` | primary | enabled · loading (token refresh) |
| Check again (pending) | `AppButtonSecondary(fillMaxWidth, enabled = !isChecking)` | default palette | enabled · disabled while checking |
| Account card | One group segment: `Modifier.groupSegment(GroupPosition.Single)` on a Row, `heightIn(min = 72dp)`, padding `horizontal = SpacingLg, vertical = SpacingMd` | `AppSurface.card()`, `outlineVariant` hairline, `AppShapeTokens.Group` 24dp | linked · disconnecting |
| Account tile | Same 40dp tile anatomy as the benefit row, colours `GistiColors.successContainer` / `GistiColors.onSuccessContainer`, icon `Icons.AutoMirrored.Outlined.Send` | success roles | — |
| Open bot CTA | `AppButton(text = telegram_open_bot_cta, icon = Icons.AutoMirrored.Filled.OpenInNew, fillMaxWidth, enabled = !isDisconnecting)` | primary | enabled · disabled |
| Disconnect | `AppButtonSecondary(text = telegram_disconnect_cta, icon = Icons.Outlined.LinkOff, accentColor = colorScheme.error, fillMaxWidth, enabled = !isDisconnecting)` | error outline + label | enabled · disabled |
| Disconnect confirm | `AlertDialog` with the project's dialog pattern (as `ChecklistDetailScreen` folder-delete): `confirmButton = AppButtonDestructive`, `dismissButton = AppButtonText(stringResource(Res.string.cancel))`, `containerColor = colorScheme.surface`, `shape = MaterialTheme.shapes.large` | error / onError | open · closed |
| Error card | Copy of the `InboxErrorState` anatomy (`InboxScreen.kt:2222`), but inline (not `fillMaxSize`): `Surface(shape = shapes.medium, color = errorContainer, contentColor = onErrorContainer)` → Column(padding SpacingLg, CenterHorizontally, `spacedBy(SpacingMd)`) → `Icon(Icons.Outlined.ErrorOutline, 24dp, null)` → `Text(bodyMedium, TextAlign.Center, fillMaxWidth)` → `AppButtonSecondary(telegram_retry, accentColor = onErrorContainer)` | errorContainer roles | — |
| Skeleton | `AppCard` containing `AppSkeletonLine(0.72f)`, `SpacingMd`, `AppSkeletonLine(0.55f)`, `SpacingMd`, `AppSkeletonLine(0.84f)`, `SpacingLg`, `AppSkeletonLine(1f, height = AppDimens.ButtonHeight)` | recessed | reduced-motion handled by the component |
| Snackbars | `SnackbarHost(snackbarHostState)` in `AppScaffold` (screen-local), as on McpScreen | M3 snackbar defaults | — |
| Drawer row | `NavigationDrawerItem` in `AppNavigationDrawerContent`, directly **after** the MCP row | `drawerItemColors` as for neighbours | selected when `selectedItemId == DrawerDestination.Telegram` |

**No new design-system component.** Every element is an existing `App*` component or a private composable built from tokens inside the feature file. Both custom pieces (the 24dp step dot and the 56dp hero tile) are screen-local.

### Icons — no Telegram logo

The Telegram paper-plane logo is a trademark and is **not drawn**. The neutral Material glyph `Icons.AutoMirrored.Outlined.Send` (paper plane) is used everywhere: drawer, hero, account tile. The word "Telegram" in text is plain nominative use and is fine.

| Where | Icon |
|---|---|
| Drawer row | `Icons.AutoMirrored.Outlined.Send` |
| Hero tile, account tile, Connect CTA | `Icons.AutoMirrored.Outlined.Send` |
| Benefit 1 (send a note / photo / voice) | `Icons.Outlined.EditNote` |
| Benefit 2 (open and tick lists) | `Icons.Outlined.TaskAlt` (already used on MCP) |
| Benefit 3 (one account, synced) | `Icons.Outlined.Sync` |
| Sign-in CTA | `Icons.Outlined.Login` (same glyph as the drawer's "Sign In") |
| Open Telegram / Open bot | `Icons.AutoMirrored.Filled.OpenInNew` |
| Disconnect | `Icons.Outlined.LinkOff` |
| Hint note | `Icons.Outlined.Info` |
| Error | `Icons.Outlined.ErrorOutline` |

### Sign-in button — deliberately not a branded "Sign in with Google" button

Google's branding guidelines (developers.google.com/identity/branding-guidelines, checked 2026-11-19) require a self-built "Sign in with Google" button to use the standard-colour "G" logo on a white background, and they forbid any other use of the Google brand. The app has no official Google button asset; every existing sign-in entry is a plain button or row labelled "Sign In". The screen therefore uses a plain `AppButton` labelled **"Sign in"**, and the body copy explains in plain text that the account is the Google one. A branded Google button would be the first in the app and is a separate decision for the owner, using the official asset.

## 3. Tokens

- **Colour (roles only):** page `colorScheme.background` (handled by AppScaffold) · text `onBackground` / `onSurface` / `onSurfaceVariant` · hero tile `primary` / `onPrimary` · benefit tiles and step dots `primaryContainer` / `onPrimaryContainer` · account tile `GistiColors.successContainer` / `GistiColors.onSuccessContainer` · cards `AppCard` / `groupSegment` defaults (`AppSurface.card()`, `outlineVariant`) · error card `errorContainer` / `onErrorContainer` · Disconnect `colorScheme.error` · spinner `primary`. **No AI gradient**: `GistiColors.aiGradient` is the AI mark, and this screen is not an AI feature.
- **Type:** hero title `titleLarge` (Bold 20sp per `Type.kt`) · hero body `bodyLarge` · benefit text `bodyLarge` · card titles and username `titleMedium` · card bodies and pending steps `bodyMedium` · "Connected since…", pending footnote and hint `bodySmall` · step number `labelMedium`. No `fontSize`/`fontWeight` overrides, with one exception: the hero title keeps MCP's `fontWeight = FontWeight.Bold`, which is redundant with Bold `titleLarge` and can be dropped.
- **Shape:** hero tile `MaterialTheme.shapes.medium` (16dp) · benefit and account tiles 12dp. There is no token for 12dp; the nearest is `shapes.small` (12dp, same value), so **use `MaterialTheme.shapes.small`**, not a literal. Account card `groupSegment(Single)` → `AppShapeTokens.Group` · buttons `AppShapeTokens.Button` (inside `AppButton*`) · error card `shapes.medium` · step dot `CircleShape`.
- **Spacing:** only `AppDimens.*`: `SpacingXxs` 2 · `SpacingSm` 8 · `SpacingMd` 12 · `SpacingLg` 16 · `SpacingXl` 24 (section breaks only). Component sizes 56 / 40 / 24 / 20dp are the same component-local dimensions McpScreen already uses. Declare them as private `val`s at the top of the file (`HeroTileSize`, `RowTileSize`, `StepDotSize`, `SpinnerSize`), not as scattered literals.

## 4. States

`stateKind` is derived by the ViewModel. UI-local-only state: whether the dialog is visible (`rememberSaveable`).

### 4.1 Loading (signed in, status not known yet)
Hero + skeleton card. Shown on first entry and after Retry. Status is one quick call, so no spinner. `liveRegion` stays silent here.

### 4.2 SignedOut (no Google account linked in the app)
- `AppCard`: `telegram_signin_title` (titleMedium) → `SpacingSm` → `telegram_signin_body` (bodyMedium, onSurfaceVariant) → `SpacingLg` → **Sign in** `AppButton` (fillMaxWidth).
- Below: benefits.
- Tap → the same Google sign-in the drawer uses (`handleSignIn` in `App.kt`), passed in as `onSignInClick`. While the sign-in sheet is up: `loading = true`. On success the auth state changes → Loading → status. On failure or cancel: back to SignedOut, and the existing app-level sign-in failure feedback applies. No new string.

### 4.3 NotLinked
- **Connect Telegram** `AppButton`, `loading` while a token is being created.
- `SpacingMd`, hint note `telegram_connect_hint`.
- `SpacingXl`, benefits.
- Tap → `create_token` → open `deep_link` (`https://t.me/gisti_app_bot?start=<token>`) through `safeOpenUri` → state **Pending**.
- `create_token` fails → stay NotLinked, show snackbar `telegram_error_network` / `telegram_error_server` with action `telegram_retry` (re-runs Connect).
- `openUri` fails (`safeOpenUri` logged a failure; the VM needs a Boolean or Result back from it) → snackbar `telegram_open_failed`, stay NotLinked.

### 4.4 Pending (user went to Telegram, link not confirmed yet)
`AppCard` content:
```
Row(verticalAlignment = Top, spacedBy(SpacingMd))  [spinner 20dp, padding(top = SpacingXxs)] [telegram_pending_title — titleMedium, weight(1f)]
SpacingMd
StepRow 1: [dot "1"] telegram_pending_step_open   (Row, CenterVertically, spacedBy(SpacingMd), bodyMedium)
SpacingSm
StepRow 2: [dot "2"] telegram_pending_step_start
SpacingMd
telegram_pending_body  (bodySmall, onSurfaceVariant)
SpacingLg
AppButton  telegram_pending_open_cta   (OpenInNew)
SpacingSm
AppButtonSecondary  telegram_pending_check_cta
```
- **Open Telegram** re-opens the deep link. The VM reuses the token when it has more than 60 s left, otherwise it creates a new one, with `loading` on the button meanwhile. Expiry is never shown to the user as an error.
- **Check again** → `status`. While checking, the button is `enabled = false`. Still not linked → snackbar `telegram_pending_not_yet`. Linked → §4.5 plus snackbar.
- **Automatic checks**, which make `telegram_pending_body` true: (a) on resume (Android `ON_RESUME`, web `visibilitychange` → visible); (b) recommended: poll `status` every 3 s while Pending is on screen and visible, stopping when the token expires. Without (b), a desktop user with Telegram in a side window never gets a resume.
- Pending lives in the ViewModel only. Leaving the screen and coming back starts with Loading → status.

### 4.5 Linked
- **Account card** (`groupSegment(Single)` Row, `CenterVertically`, `spacedBy(SpacingMd)`):
  - tile 40dp (success roles, Send glyph);
  - Column(weight 1f): `@{username}` (titleMedium, **`maxLines = 1`, `overflow = Ellipsis`**). If the username is null, show `telegram_linked_no_username`. Below it, `telegram_linked_since` with a localized date-only string (bodySmall, onSurfaceVariant). While disconnecting, this line becomes `telegram_disconnecting`.
  - **No trailing "Connected" chip.** It was removed after the check (§8). The sentence "Connected since…" carries the status in words, so colour is not the only indicator.
- `SpacingLg` → **Open bot** `AppButton` → `https://t.me/gisti_app_bot` (constant, no token) through `safeOpenUri`. Failure → snackbar `telegram_open_failed`.
- `SpacingXl` → benefits (here they read as "what to do in the bot").
- `SpacingXl` → **Disconnect** `AppButtonSecondary(accentColor = error)` → AlertDialog: `telegram_disconnect_dialog_title`, `telegram_disconnect_dialog_body`, confirm `telegram_disconnect_confirm` (`AppButtonDestructive`), dismiss `cancel`.
- Confirm → dialog closes → `isDisconnecting = true`: subtitle shows `telegram_disconnecting`, and Open bot and Disconnect are disabled → `unlink`:
  - success → NotLinked + snackbar `telegram_disconnected_snackbar`;
  - failure → stays Linked, controls re-enabled, snackbar `telegram_disconnect_failed`.

### 4.6 Error (status could not be loaded)
Hero + error card with one message by cause:
- network / timeout → `telegram_error_network`;
- 5xx / unexpected → `telegram_error_server`;
- 409 `no_account` → `telegram_error_account`.

**Try again** → Loading → status. Errors from actions (connect, disconnect, open) **do not** use this state. They keep the current state and show a snackbar (§4.3–4.5), so the user never loses a working screen to a transient failure.

### 4.7 Success snackbar, including links that arrive from outside
| Trigger | Where | Text |
|---|---|---|
| Pending → Linked (auto or Check again) | this screen | `telegram_linked_snackbar` with the username, or `telegram_linked_snackbar_plain` when the username is null |
| `?tg_link=<token>` on web start / App Link, user **signed in** → `claim` OK | whatever screen is current (app-level snackbar host; the owner of the deep-link handler picks it) | same two strings |
| `?tg_link`, user **not signed in** | navigate to the Telegram screen; it shows **SignedOut**, whose title already says "Sign in to connect Telegram". After sign-in → `claim` → Linked + snackbar | — |
| `claim` → 410 (expired/used) or 404 | current screen | `telegram_link_expired` |
| `claim` → network / 5xx | current screen | `telegram_link_failed` |

Snackbar duration: `Short` for success, `Long` for errors and for anything with an action.

## 5. Flow / side effects expected from the ViewModel

| Intent | Effect |
|---|---|
| screen enter / resume | auth → SignedOut or Loading → `status` → NotLinked / Linked / Error |
| SignInClick | host's Google sign-in; button `loading` until it returns |
| ConnectClick | `create_token` (or a cached token) → SideEffect `OpenUrl(deep_link)` → Pending |
| OpenTelegramClick (Pending) | reuse or refresh the token → `OpenUrl` |
| CheckAgainClick | `status` → Linked + snackbar / snackbar `not_yet` |
| OpenBotClick | `OpenUrl(https://t.me/gisti_app_bot)` |
| DisconnectClick | show dialog (UI-local) |
| DisconnectConfirm | `unlink` → NotLinked + snackbar / snackbar error |
| RetryClick (Error) | Loading → `status` |
| OpenUrl failed | snackbar `telegram_open_failed` |

Analytics events are outside this spec. `@feature-expert` adds them per project convention.

## 6. Accessibility

- Touch targets: every action is an `AppButton*` (≥ 48dp, `heightIn`). The drawer row is a `NavigationDrawerItem` (56dp). Nothing smaller is clickable. The account card is **not** clickable.
- Hero title `Modifier.semantics { heading() }`. All tiles and icons are decorative (`contentDescription = null`); every button carries its own text.
- **Account card:** `semantics(mergeDescendants = true) { contentDescription = telegram_status_cd(fullUsernameOrFallback, date) }`. The visible username may be ellipsized, so the screen reader gets the full one.
- **Step rows:** `mergeDescendants = true` on each row, so a row reads "1, Open the Gisti bot in Telegram".
- **State slot:** `semantics { liveRegion = LiveRegionMode.Polite }` on the `AnimatedContent` container, so Pending → Linked is announced even before the snackbar. The skeleton gets `contentDescription = null` and `invisibleToUser()`, so it is not read as blank text.
- Spinner: the default `CircularProgressIndicator` semantics (indeterminate progress) are enough.
- Focus order follows reading order: back → hero → state block → benefits → Disconnect. The dialog traps focus (M3 default).
- Contrast (from `Color.kt` / `GistiColors.kt` values): white glyph on `primary` #1565C0 ≈ 5.7:1 (non-text, ≥ 3:1). Dark: `onPrimary` on #90CAF9 is high. `onSuccessContainer` on `successContainer` ≈ 7:1 light, high in dark. `error` #BA1A1A label on the sand page clears AA for text. The error-card pairing is an M3 role pair. Status never relies on colour alone.
- fontScale: nothing has a fixed height (`heightIn` everywhere). Labels wrap to 2 lines (`AppButton` contract). The username is the only intentional single line, and its full value is in `contentDescription`.

## 7. Adaptive / motion

- **Compact (< 600dp):** single column, full-width buttons.
- **Medium / Expanded (web and tablet):** `AppScaffold` switches to the Medium top bar automatically. Content is capped at 600dp and centred (`adaptiveContentWidth(600)`), as on MCP. No two-pane layout, because there is nothing to put beside it.
- **Landscape phone:** the column scrolls (`verticalScroll`). Nothing is pinned.
- Navigation: a **pushed detail screen with a back arrow**, exactly like MCP (see McpScreen KDoc), reached from the drawer and from the v2 Overview page. `AppNavigationDrawerContent` serves both, so one row covers both.
- **Motion:** state slot `AnimatedContent` with `fadeIn(tween(AppMotion.EffectsDefaultMillis)) togetherWith fadeOut(tween(AppMotion.EffectsFastMillis))` plus `SizeTransform(clip = false)`. Under `LocalReducedMotion` use `AppMotion.ReducedMotionMillis`. No slide: this is a status change, not navigation. Spinner and skeleton are the component defaults.

## 8. Check (prototype run, 2026-11-19)

**What was checked:** a disposable HTML prototype that mirrors this spec's tokens (hex values copied from `Color.kt` / `GistiColors.kt`, type sizes from `Type.kt`, M3 dp = CSS px). It was rendered in Chromium with Playwright and every frame was viewed. No Compose infrastructure was added just for this spec. Frames (local, outside git):

- `…/scratchpad/tg-shots/tg-320-fs15-ru-light.png`: 320dp, fontScale 1.5, RU, light, all 7 states + dialog + snackbar (**first pass**, before fixes)
- `…/scratchpad/tg-shots/tg-320-fs15-hi-dark.png`: 320dp, fontScale 1.5, HI, dark, 5 states (after fixes)
- `…/scratchpad/tg-shots/tg-1024-en-light.png`: 1024dp web, EN, light, NotLinked + Linked (after fixes)

The frames live in the design session's scratch directory (`tg-shots/`, the full path was handed to the lead in the session report). They do not survive the session; the binding frames are the ones the implementer takes (matrix below).

**What the first pass found, and how the spec was changed:**
1. **Linked:** the trailing "Connected" chip squeezed a long `@username` to about 3 characters per line, so the name broke into 7 lines at 320 × 1.5. → The chip was removed; the status is carried by "Connected since…", and the username is single-line with ellipsis and its full value in `contentDescription`.
2. **SignedOut / NotLinked:** with benefits *above* the CTA, the main button landed below the fold at fontScale 1.5. → The CTA moved directly under the hero, with benefits after it.
3. **Benefit rows** in `titleMedium` SemiBold (as on MCP) wrapped to 3–4 heavy lines in RU at 1.5, and the icon tile centred against the whole block. → Text is now `bodyLarge`, and the row is `verticalAlignment = Top` with the text padded by `SpacingSm` from the top, so the first line sits on the 40dp tile's visual centre.
4. **RU pending title** "Ждём подтверждения в Telegram" took 3 lines next to the spinner. → Shortened to "Ждём вас в Telegram", and the spinner row is top-aligned.

**After the fixes:** HI dark at 320 × 1.5 had no clipping and no orphaned controls, and every CTA sat in the first screen for SignedOut / NotLinked. On 1024 web the column is capped and reads as one block.
Not covered by the prototype: real Compose text metrics and Devanagari shaping in Skiko. Those are in the implementer's matrix.

### Screenshot matrix for `@compose-expert` (screenshot-driven-ui, `@Preview` per state)

| Axis | Values |
|---|---|
| States | Loading · SignedOut · NotLinked · NotLinked+connecting (CTA loading) · Pending · Pending+checking · Linked · Linked+disconnecting · Linked (username null) · Error network · Error account · Disconnect dialog |
| Width | 320dp (Compact, narrowest) · 411dp (Pixel) · 840dp+ (web Expanded, column capped at 600) |
| Theme | light · dark |
| fontScale | 1.0 · 1.5 (2.0 for Linked and Pending only) |
| Locale | EN · RU (longest Cyrillic) · HI (Devanagari shaping) |

**Minimum set of frames to attach to the report** (state × theme × width):
1. every state above × light × 320dp × fontScale 1.5 × RU;
2. NotLinked, Pending, Linked, Error × dark × 411dp × EN;
3. NotLinked, Linked × light × 1024dp web (wasmJs) × EN;
4. Linked + Pending × dark × 320dp × fontScale 1.5 × HI;
5. Disconnect dialog × light and dark × 320dp × RU.

**Edge data to put in the fakes:**
- username `@anton_churaev_very_long_username_32`, the 32-character Telegram maximum;
- username `null`;
- date in RU and HI month form;
- the longest error, `telegram_error_account` in HI.

### Reference frame
None as a raster mock-up. The prototype frames above are a sanity check, not a pixel reference. The visual reference for anatomy is the live **McpScreen**, which shares hero, rows and width.

## 9. Strings (`core/designsystem/src/commonMain/composeResources/values{,-ru,-hi}/strings.xml`)

Write apostrophes literally (`Couldn't`), never `\'`. `%1$s` placeholders work the same in all three locales. In RU the Telegram button is localized as «Запустить», so the RU copy names it that way; **verify on a RU-language Telegram client** before release. HI uses Telegram's English "Start", since Telegram ships no Hindi UI.

| Key | EN | RU | HI |
|---|---|---|---|
| `drawer_item_telegram` | Telegram | Telegram | Telegram |
| `telegram_title` | Telegram | Telegram | Telegram |
| `telegram_hero_title` | Your checklists in Telegram | Ваши чек-листы в Telegram | आपकी चेकलिस्ट अब Telegram में |
| `telegram_hero_body` | Send anything to the Gisti bot and get a checklist — on the same account as the app. | Отправьте боту Gisti что угодно — и получите чек-лист в том же аккаунте, что и в приложении. | Gisti बॉट को कुछ भी भेजें और चेकलिस्ट पाएं — उसी अकाउंट पर जो ऐप में है। |
| `telegram_benefit_capture` | Send a note, photo or voice — get a checklist | Заметка, фото или голос — и готов чек-лист | नोट, फ़ोटो या वॉइस भेजें — चेकलिस्ट तैयार |
| `telegram_benefit_lists` | Open and tick off your lists right in the chat | Открывайте списки и отмечайте пункты прямо в чате | चैट में ही अपनी लिस्ट खोलें और टिक करें |
| `telegram_benefit_sync` | One account — everything syncs with the app | Один аккаунт — всё синхронизируется с приложением | एक ही अकाउंट — सब कुछ ऐप के साथ सिंक |
| `telegram_signin_title` | Sign in to connect Telegram | Войдите, чтобы подключить Telegram | Telegram जोड़ने के लिए साइन इन करें |
| `telegram_signin_body` | The bot uses your Google account, so your checklists stay the same everywhere. | Бот работает с вашим аккаунтом Google — чек-листы везде одни и те же. | बॉट आपके Google अकाउंट से काम करता है, इसलिए आपकी चेकलिस्ट हर जगह एक जैसी रहती हैं। |
| `telegram_signin_cta` | Sign in | Войти | साइन इन करें |
| `telegram_connect_cta` | Connect Telegram | Подключить Telegram | Telegram जोड़ें |
| `telegram_connect_hint` | Opens Telegram. Tap Start in the bot to finish. | Откроется Telegram. Нажмите «Запустить» в боте, чтобы завершить. | Telegram खुलेगा। पूरा करने के लिए बॉट में Start दबाएं। |
| `telegram_pending_title` | Waiting for Telegram | Ждём вас в Telegram | Telegram का इंतज़ार है |
| `telegram_pending_step_open` | Open the Gisti bot in Telegram | Откройте бота Gisti в Telegram | Telegram में Gisti बॉट खोलें |
| `telegram_pending_step_start` | Tap Start | Нажмите «Запустить» | Start दबाएं |
| `telegram_pending_body` | We'll update this screen once you're connected. | Экран обновится сам, когда всё подключится. | जुड़ते ही यह स्क्रीन अपने आप अपडेट हो जाएगी। |
| `telegram_pending_open_cta` | Open Telegram | Открыть Telegram | Telegram खोलें |
| `telegram_pending_check_cta` | Check again | Проверить ещё раз | फिर से जांचें |
| `telegram_pending_not_yet` | Not connected yet. Tap Start in the bot, then check again. | Пока не подключено. Нажмите «Запустить» в боте и проверьте ещё раз. | अभी जुड़ा नहीं है। बॉट में Start दबाएं, फिर दोबारा जांचें। |
| `telegram_linked_since` | Connected since %1$s | Подключено с %1$s | %1$s से जुड़ा हुआ |
| `telegram_linked_no_username` | Telegram account | Аккаунт Telegram | Telegram अकाउंट |
| `telegram_status_cd` | Telegram connected as %1$s since %2$s | Telegram подключён: %1$s, с %2$s | Telegram %1$s के रूप में %2$s से जुड़ा है |
| `telegram_open_bot_cta` | Open bot | Открыть бота | बॉट खोलें |
| `telegram_disconnect_cta` | Disconnect | Отключить | डिस्कनेक्ट करें |
| `telegram_disconnecting` | Disconnecting… | Отключаем… | डिस्कनेक्ट हो रहा है… |
| `telegram_disconnect_dialog_title` | Disconnect Telegram? | Отключить Telegram? | Telegram डिस्कनेक्ट करें? |
| `telegram_disconnect_dialog_body` | The bot will stop working with your account. Your checklists stay in the app. | Бот перестанет работать с вашим аккаунтом. Чек-листы останутся в приложении. | बॉट आपके अकाउंट के साथ काम करना बंद कर देगा। आपकी चेकलिस्ट ऐप में बनी रहेंगी। |
| `telegram_disconnect_confirm` | Disconnect | Отключить | डिस्कनेक्ट करें |
| `telegram_disconnected_snackbar` | Telegram disconnected | Telegram отключён | Telegram डिस्कनेक्ट हो गया |
| `telegram_disconnect_failed` | Couldn't disconnect. Try again. | Не удалось отключить. Попробуйте ещё раз. | डिस्कनेक्ट नहीं हो सका। फिर से कोशिश करें। |
| `telegram_linked_snackbar` | Telegram connected: @%1$s | Telegram подключён: @%1$s | Telegram जुड़ गया: @%1$s |
| `telegram_linked_snackbar_plain` | Telegram connected | Telegram подключён | Telegram जुड़ गया |
| `telegram_error_network` | Couldn't reach Gisti. Check your connection and try again. | Не удалось связаться с Gisti. Проверьте интернет и попробуйте ещё раз. | Gisti से कनेक्ट नहीं हो सका। अपना इंटरनेट जांचें और फिर से कोशिश करें। |
| `telegram_error_server` | Something went wrong on our side. Try again in a moment. | Что-то пошло не так на нашей стороне. Попробуйте чуть позже. | हमारी तरफ़ से कुछ गड़बड़ हुई। थोड़ी देर बाद फिर कोशिश करें। |
| `telegram_error_account` | We couldn't find your account. Sign out, sign in again, then retry. | Не нашли ваш аккаунт. Выйдите, войдите снова и повторите. | आपका अकाउंट नहीं मिला। साइन आउट करके फिर से साइन इन करें, फिर दोबारा कोशिश करें। |
| `telegram_retry` | Try again | Повторить | फिर से कोशिश करें |
| `telegram_open_failed` | Couldn't open Telegram on this device. | Не удалось открыть Telegram на этом устройстве. | इस डिवाइस पर Telegram नहीं खुल सका। |
| `telegram_link_expired` | This link has expired. Send /start to the bot to get a new one. | Ссылка устарела. Отправьте боту /start, чтобы получить новую. | यह लिंक एक्सपायर हो गया है। नया लिंक पाने के लिए बॉट को /start भेजें। |
| `telegram_link_failed` | Couldn't connect Telegram. Open the link from the bot again. | Не удалось подключить Telegram. Откройте ссылку из бота ещё раз. | Telegram नहीं जुड़ सका। बॉट से लिंक फिर से खोलें। |

Reused existing key: `cancel` (dialog dismiss).

Phase-1 honesty: the three benefits describe only what phase 1 ships (capture → checklist, browse and tick lists, one synced account). Chat (phase 2) and Telegram reminders (phase 4) get their benefit row **when they ship**. Do not add a "coming soon" row now.

## 10. Handoff

- **Target:** `@feature-expert` (state matrix, ViewModel, repo for `telegram_account`, navigation, deep-link `?tg_link` handler) → `@compose-expert` (stateless `TelegramScreen` / `TelegramContent` per this spec, previews per state, screenshot matrix §8).
- **Files (suggested):**
  - `composeApp/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/telegram/TelegramScreen.kt` (next to `mcp/`);
  - drawer: `navigation/AppNavigationDrawerContent.kt` (`DrawerDestination.Telegram = "telegram"`, `onTelegramClick`, row after MCP);
  - wiring: `AdaptiveNavigationShell.kt` (both call sites that pass `onMcpClick`), `OverviewScreen.kt`, `App.kt` (entry + route), exactly mirroring MCP;
  - strings: the three `strings.xml` files.
- **Reuse:**
  - `AppScaffold`, `adaptiveContentWidth`, `AppButton`, `AppButtonSecondary(accentColor)`, `AppButtonText`, `AppButtonDestructive`, `AppCard`, `groupSegment`, `AppSkeletonLine`, `safeOpenUri`, `GistiColors.success*`, `AppMotion`.
  - **Lift `ValuePropRow` (and the note-row anatomy) out of `McpScreen.kt`** into a shared internal file (e.g. `composeApp/.../integrations/IntegrationRows.kt`) instead of copying them. The `bodyLarge` change applies only to the Telegram call site via a `textStyle` parameter defaulting to MCP's current `titleMedium`, so MCP renders exactly as today.
- **Build order:**
  1. strings;
  2. drawer row + route (MCP mirror);
  3. stateless content with previews for all §8 states;
  4. ViewModel + repo;
  5. resume / visibility status refresh;
  6. `?tg_link` claim + app-level snackbar;
  7. screenshot matrix.
- **Pitfalls:**
  1. **Popup blocker on web:** `window.open` after an `await` (`create_token` round-trip) is blocked as a non-user-gesture popup. Pre-fetch the token as soon as NotLinked renders (refresh it when under 60 s remain), so the Connect tap opens the URL synchronously. Show the CTA loading state only if the token is not ready yet. `@wasmjs-expert` should confirm how `UriHandler.openUri` maps on wasmJs.
  2. `safeOpenUri` currently returns `Unit` and only logs. The screen needs the failure to show `telegram_open_failed`, so add a result-returning variant and leave the drawer callers untouched.
  3. Resume refresh must not reset an open Disconnect dialog or an in-flight unlink. Guard the refresh while `isDisconnecting`.

## Out of scope — lines for the lead
- `values-ru/strings.xml` has **no `mcp_*` strings** (EN and HI have them), so the MCP screen shows English on a RU UI. This is pre-existing and not touched here.
- `SyncAccountBanner` says "Sign in with Google" as plain text without the official button. This is pre-existing and should be brought up with the owner together with the decision in §2.
