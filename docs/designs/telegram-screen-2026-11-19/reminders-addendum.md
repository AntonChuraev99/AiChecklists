# DESIGN_SPEC addendum — reminder channel on the Telegram screen (phase 4)

- **Platform:** Android + Web (the same `commonMain` `TelegramScreen`), Material 3, design system "Soft & tactile". iOS (unreleased) follows the Android copy.
- **Method:** native
- **Extends:** `spec.md` in this folder (Telegram screen, 2026-11-19). Everything there stays as is. This addendum adds one section to the **Linked** state, plus one optional line in the Disconnect dialog.
- **Product input:** `docs/archive/telegram-bot-phase4-reminders-2026-11-20.md`. The owner's decisions from 2026-11-20: the channel is per user, `push` | `telegram` | `both`; a missing value means `push`; after linking the channel stays `push`; the user can change it in the bot (`/reminders`) and on this screen.
- **FEATURE_SPEC:** none was provided. The states below are the full set, and `@feature-expert` turns them into the `Linked` fields and actions listed in §6.

> ⛔ **Anti-steering.** This section never mentions Stars, payment, premium or prices. All strings below are checked against that rule.

---

## 1. Placement in the Linked state

```
Linked (spec.md §4.5), new order:
├─ AccountCard                                   (unchanged)
├─ Spacer(SpacingLg)
├─ AppButton  Open bot                           (unchanged)
├─ Spacer(SpacingLg)                             ── + AppSectionHeader's own 12dp top = a 28dp section break
├─ ReminderChannelSection                        ── NEW (§2)
│  ├─ AppSectionHeader  telegram_reminders_title
│  └─ Column(selectableGroup)                    ── one grouped container, 2 or 3 rows
├─ Spacer(SpacingXl)
├─ Benefits                                      (unchanged)
├─ Spacer(SpacingXl)
└─ Disconnect                                    (unchanged)
```

**Why it goes after Open bot and before the benefits:** the channel is a setting of *this* link, so it belongs with the account card and its action, not after the "what you can do" list. The main CTA (Open bot) stays directly under the account card. At 320dp × 2.0 the section header still lands on the first screen under the hero (frame `a`, first column).

**Visibility:** the section renders only when the server reported a channel (`Linked.reminderChannel != null`). Until the phase-4 Cloud Function is live, `status` does not return the field, and the section stays hidden. That is the rollout guard, so no feature flag is needed. The section is never shown in any other state.

## 2. Component choice

| Element | Project component | Tokens | States |
|---|---|---|---|
| Section header | `AppSectionHeader(label = telegram_reminders_title, icon = Icons.Outlined.NotificationsActive, iconContainer = colorScheme.primaryContainer, iconContent = colorScheme.onPrimaryContainer)` | built into the component: `AppTextStyles.sectionHeader`, top `AppDensity.SectionHeaderTop`, bottom `AppSectionHeaderDefaults.BottomPadding` | static, `heading()` |
| Group | `Column(Modifier.selectableGroup())` of rows, each row painted with `Modifier.groupSegment(groupPositionOf(i, n))` | `AppSurface.card()`, `outlineVariant` hairline, `AppShapeTokens.Group` 24dp, inset divider `AppDensity.GroupDividerInsetStart` | enabled · disabled (while disconnecting) |
| Option row | **custom on tokens, the radio-row pattern already used by `ActivationReminderSheet.ReminderPreviewCard` and the Settings theme picker:** `Row(Modifier.fillMaxWidth().heightIn(min = AppDimens.MinTouchTarget).groupSegment(pos).selectable(selected, enabled = !isDisconnecting, role = Role.RadioButton, onClick).padding(horizontal = AppDensity.GroupRowPaddingHorizontal, vertical = AppDimens.SpacingMd), verticalAlignment = Top, spacedBy(AppDensity.GroupRowGap))` → [control slot 24dp] [Column(weight 1f): title, optional supporting] | title `bodyLarge` onSurface · supporting `bodyMedium` onSurfaceVariant | unselected · selected · saving · disabled |
| Control slot | `RadioButton(selected, onClick = null, enabled = !isDisconnecting)` in M3 default colours (selected `primary`, unselected `onSurfaceVariant`). With `onClick = null` it lays out at 24dp (20dp dot + 2dp padding), which equals `AppDensity.RoundCheckboxBox`, so the existing divider inset lines up under the text with no new constant. **While saving**, the tapped row shows `CircularProgressIndicator(size 20dp, strokeWidth 2dp, color = primary)` centred in the same 24dp box **instead of** its radio | primary | — |
| Failure snackbar | the screen's existing `SnackbarHost` (`TelegramSnackbar` → `resolveTelegramSnackbar`) | M3 defaults | Long, action `telegram_retry` |

**Why a radio group, and not the alternatives:**
- **No selection component exists in `core/designsystem`.** `ast-index search Segmented` finds nothing, and there is no `AppRadio*`. The project's single-choice pattern is M3 `RadioButton(onClick = null)` inside a `selectable(role = RadioButton)` row, used in Settings → Theme/Language, `ActivationReminderSheet` and the voice-call screen. This addendum reuses that pattern and does not add a new component. The rows take the `groupSegment` skin, because the Linked account card is already a `groupSegment` and the design system canon says grouped rows go in one group (`.claude/rules/designsystem.md` → "Grouped lists (B)").
- **Not `SingleChoiceSegmentedButtonRow`.** Three segments at 320dp give about 96dp each. "Уведомление на телефоне" at fontScale 2.0 does not fit in that, and segments have no room for the "no notification on the phone" consequence line. M3 also caps segmented buttons at short labels.
- **Not chips.** A choice that changes where something is delivered and takes effect immediately reads as a setting. A precedent with the same verdict: `ActivationReminderSheet` (time picker, "radio, not chips").
- **Not an `AppSwitch` on web.** The radio form is the same component on both platforms. Its saving state (spinner in place of the control, no reflow) works only because the control slot is a fixed 24dp. `AppSwitch` exposes no `thumbContent`, and putting a spinner next to it changes the text width mid-save.

### 2.1 Option rows by platform

The platform flag is the one the drawer already uses: `platformName == "web"` (`AppNavigationDrawerContent.kt:446`, source `getPlatformName()` in `feature/user`).

**Android (and iOS): 3 rows, in the order the owner gave them.**

| # | Value | Title | Supporting |
|---|---|---|---|
| 1 | `push` | `telegram_reminders_option_push` "Phone notification" | none |
| 2 | `telegram` | `telegram_reminders_option_telegram` "Telegram message" | `telegram_reminders_option_telegram_body` "No notification on the phone" |
| 3 | `both` | `telegram_reminders_option_both` "Phone and Telegram" | none |

Only the row with a consequence that is not obvious from its title gets a supporting line: choosing Telegram silences the phone. The first prototype pass had a supporting line on every row, and that made the group about two screens tall at 320 × 2.0 RU (§8).

**Web: 2 rows.** The web app has no notifications. A "Phone notification" row would promise something this surface cannot deliver, and for a web-only user it means no reminders at all.

| # | Selected when | Title | Supporting | Tap writes |
|---|---|---|---|---|
| 1 | value ∈ {`telegram`, `both`} | `telegram_reminders_web_on` "Send to Telegram" | `telegram_reminders_web_on_body` "The web app can't send notifications" | `both` if the current value is `push`. Otherwise the row is already selected, and tapping it does nothing, as with any radio |
| 2 | value = `push` | `telegram_reminders_web_off` "Don't send to Telegram" | none | `push` |

**Why web "on" writes `both` and not `telegram`.** The channel is per user, not per device. A web user may also have the Android app. `telegram` would silently switch their phone notifications off from a screen where phones are not even mentioned, and that is a lost function the user never asked to lose. For a web-only user, `both` and `telegram` deliver exactly the same thing. "Off" writes `push`, which on the web means "nothing here" and on a phone means "phone only". Both readings are true, and the copy does not claim more ("Don't send to Telegram", not "Off"). This mapping is logic, so it belongs in the VM as a pure function with a unit test (§6), not inside the composable.

## 3. Tokens

- **Colour (roles only):** group `AppSurface.card()` / `outlineVariant` (via `groupSegment`) · header tile `primaryContainer` / `onPrimaryContainer` · radio and spinner `primary`, unselected ring `onSurfaceVariant` (M3 defaults) · title `onSurface` · supporting `onSurfaceVariant` · snackbar M3 inverse roles.
- **Disabled (while disconnecting):** the RadioButton uses its own disabled colours (`enabled = false`). Text colours take `onSurface` / `onSurfaceVariant` at the M3 disabled content alpha 0.38. There is **no alpha token** in the design system. The nearest precedent is a file-private `const val` (`AskGistiBar.kt:404` `DisabledGlyphAlpha = 0.38f`), so declare the same private constant in `TelegramScreen.kt` rather than an inline literal.
- **Type:** title `bodyLarge`, supporting `bodyMedium`, header `AppTextStyles.sectionHeader` (inside `AppSectionHeader`). No overrides.
- **Shape:** `groupSegment` → `AppShapeTokens.Group`. Nothing else.
- **Spacing:** row padding `AppDensity.GroupRowPaddingHorizontal` (16) / `AppDimens.SpacingMd` (12) · control→text gap `AppDensity.GroupRowGap` (12) · divider inset `AppDensity.GroupDividerInsetStart` · section breaks `AppDimens.SpacingLg` above (plus the header's 12) and `AppDimens.SpacingXl` below · row min height `AppDimens.MinTouchTarget`. Component sizes 24dp (control box) and 20 / 2dp (spinner) are the same private `val`s the screen already declares (`SpinnerSize`, `SpinnerStroke`). Add `ChannelControlBox = 24.dp` next to them.
- **First-line alignment:** the row is `verticalAlignment = Top`, and the 24dp control is centred on the **first** title line, so at fontScale 2.0 the radio sits next to the first line, not in the middle of a 4-line block. Use the helper that already exists for this in `IntegrationRows.kt` (`firstLineCentring(leading, lineHeightDp(style))`). Today it is `private`. Make it and `lineHeightDp` `internal`, and leave their behaviour unchanged: MCP does not call them, so MCP renders exactly as today.

## 4. States

The value comes in the same `status` response that produces `Linked` (spec.md §4.1). So there is **no separate "loading the value" state**: while `status` is running, the whole screen shows the existing Loading skeleton, and the section appears together with the account card.

| State | What the user sees |
|---|---|
| **Value loading** | Screen-level Loading skeleton (unchanged). The section is not shown on its own. |
| **Hidden** | `reminderChannel == null` (the server does not support it yet): no header and no group. The Linked layout is exactly as in phase 1. |
| **Idle** | The radio on the server value. Android: one of 3 rows. Web: row 1 for `telegram`/`both`, row 2 for `push`. |
| **Saving** | Immediately on tap, the tapped row's radio is replaced by the 20dp spinner (frame `a`, column 3). The other rows show their radios **unselected**, and the old selection is gone, so the tap reads as accepted. The rows stay enabled. A second tap during a save moves the spinner to the newly tapped row (latest wins, §6). |
| **Saved** | The spinner turns into the selected radio on the same row. No snackbar: the radio already shows the result, and a confirmation toast on every settings change is noise. A screen reader hears the radio's "selected" state change. |
| **Error → revert** | The selection returns to the **last server-confirmed** value, and the Long snackbar `telegram_reminders_save_failed` appears with action `telegram_retry`. The action re-sends the value the user tried to set. The same text covers network, server and 409: the user's next step is the same in each case (frame `a`, column 4). |
| **Disconnecting** | The whole group is disabled (radios disabled, text at 0.38), the same as Open bot and Disconnect during `unlink` (frame `a`, column 5). |
| **Changed elsewhere** | The user changed the channel in the bot (`/reminders`) and comes back. The resume refresh (spec.md §5) updates the radio. No snackbar. |

**Time zone note: deliberately not designed.** The app sends the device zone on its own. In addition, the channel-change request should carry the device's IANA zone in the same write (§6). Then the app never creates "Telegram selected, zone unknown", and the bot handles its own path with `/timezone`. A permanent hint for a state the app cannot produce would only add height at 320 × 2.0.

### 4.1 Disconnect dialog: consequence line

When `reminderChannel` ≠ `push` (and ≠ null), the dialog shows a second paragraph below `telegram_disconnect_dialog_body`. It is a `Column` inside `text = { }` with `Spacer(AppDimens.SpacingSm)` between the two `Text`s, both in the dialog's default text style:
- Android: `telegram_reminders_disconnect_note` "Reminders will go back to phone notifications."
- Web: `telegram_reminders_disconnect_note_web` "Reminders in Telegram will stop."

For `push`, there is no extra line (frame `a`, column 6; frame `f`, column 3).

> **Prerequisite (server, not design):** the Android line is true only if unlinking **resets `reminder_channel` to `push`**, or if the client treats an unlinked user as `push`. Otherwise an Android user who had `telegram` keeps a phone that stays silent and a bot that no longer sends anything, so they get no reminders at all. → `@feature-expert` / backend owner.

## 5. Copy and strings

File: `core/designsystem/src/commonMain/composeResources/values{,-ru,-hi}/strings.xml`. Write apostrophes literally (`can't`, `Don't`), never `\'`.

| Key | EN | RU | HI |
|---|---|---|---|
| `telegram_reminders_title` | Reminders | Напоминания | रिमाइंडर |
| `telegram_reminders_option_push` | Phone notification | Уведомление на телефоне | फ़ोन पर नोटिफ़िकेशन |
| `telegram_reminders_option_telegram` | Telegram message | Сообщение в Telegram | Telegram मैसेज |
| `telegram_reminders_option_telegram_body` | No notification on the phone | Без уведомления на телефоне | फ़ोन पर नोटिफ़िकेशन नहीं |
| `telegram_reminders_option_both` | Phone and Telegram | Телефон и Telegram | फ़ोन और Telegram |
| `telegram_reminders_web_on` | Send to Telegram | Присылать в Telegram | Telegram पर भेजें |
| `telegram_reminders_web_on_body` | The web app can't send notifications | В веб-версии нет уведомлений | वेब ऐप में नोटिफ़िकेशन नहीं हैं |
| `telegram_reminders_web_off` | Don't send to Telegram | Не присылать в Telegram | Telegram पर न भेजें |
| `telegram_reminders_save_failed` | Couldn't change where reminders go. Try again. | Не удалось изменить, куда приходят напоминания. Попробуйте ещё раз. | रिमाइंडर कहां आएं, यह बदला नहीं जा सका। फिर से कोशिश करें। |
| `telegram_reminders_saving_cd` | Saving | Сохраняем | सेव हो रहा है |
| `telegram_reminders_disconnect_note` | Reminders will go back to phone notifications. | Напоминания снова будут приходить уведомлениями на телефон. | रिमाइंडर फिर से फ़ोन नोटिफ़िकेशन से आएंगे। |
| `telegram_reminders_disconnect_note_web` | Reminders in Telegram will stop. | Напоминания в Telegram прекратятся. | Telegram में रिमाइंडर बंद हो जाएंगे। |

Reused keys: `telegram_retry` (snackbar action).

**Copy checks:**
- Web copy names no phone delivery. The only web strings that mention notifications say the web app *has none*.
- "Phone notification" appears only on Android/iOS, where the device can deliver it.
- Nothing mentions Stars, payment or premium.
- RU / HI wrap without clipping at 320 × 2.0 (§8).

### 5.1 Optional: benefit row (lead's call)

`spec.md` §9 says Telegram reminders get their benefit row "when they ship". If the lead takes it, add a fourth `ValuePropRow` (`Icons.Outlined.NotificationsActive`), with the same gate as the section: show it only once the server reports `reminderChannel`. Without the gate it would promise a feature that is not live yet.

| Key | EN | RU | HI |
|---|---|---|---|
| `telegram_reminders_benefit` | Get reminders as messages and tick them off in the chat | Напоминания приходят сообщениями — отмечайте прямо в чате | रिमाइंडर मैसेज में आते हैं — चैट में ही टिक करें |

## 6. Flow and the contract `@feature-expert` needs

| Field / action | Meaning |
|---|---|
| `Linked.reminderChannel: ReminderChannel?` | `Push` / `Telegram` / `Both`, as last confirmed by the server. `null` = not reported → section hidden. |
| `Linked.channelSaving: ReminderChannel?` | The value being saved right now. Non-null → that row shows the spinner, and no row shows a selected radio. |
| `TelegramAction.ReminderChannelClick(target: ReminderChannel)` | Android: the row's value. Web: the UI sends `WebOn` / `WebOff`, or a `Boolean`, and the **VM** resolves it with a pure function `webTarget(current, on)`: on + `Push` → `Both`; on + other → unchanged (no request); off → `Push`. This function gets a unit test. |
| Save request | Writes `reminder_channel` **together with the device IANA time zone**. |
| Latest wins | A click while a save is in flight replaces the pending target. When the in-flight call returns, the VM sends the latest target if it differs. Revert always goes to the last **confirmed** value, never to an intermediate one. |
| Failure | Revert + `TelegramSnackbar.ReminderSaveFailed(attempted)` (Long, action `telegram_retry` → the same click again). |
| Same value tapped | No request and no feedback beyond the radio already being selected (standard radio behaviour, not a silent skip). |
| Refresh while saving | A resume or poll `status` that arrives during a save must **not** overwrite `channelSaving` or move the radio. This is the same guard as the existing one for `isDisconnecting` (spec.md §10, pitfall 3). |

## 7. Accessibility

- **Touch targets:** each row is the whole click target, at least 48dp tall (`heightIn(min = MinTouchTarget)`). In practice two-line rows are about 72dp. The RadioButton itself is not clickable (`onClick = null`), so the toggle cannot fire twice.
- **Semantics:** the group `Column` has `selectableGroup()`, so TalkBack announces "1 of 3". Each row is `selectable(role = RadioButton)` and merges its descendants, so it reads "Telegram message, No notification on the phone, not selected, radio button". The header is a `heading()` (built into `AppSectionHeader`), so the user can jump to it.
- **Saving:** the tapped row gets `stateDescription = telegram_reminders_saving_cd` while the spinner shows, so the screen reader hears "Saving" instead of a radio with no state. The spinner itself is decorative (`clearAndSetSemantics {}` on the spinner only).
- **Failure:** the snackbar is announced by the M3 host. The radio change back is announced by its own state.
- **Live region pitfall:** spec.md puts `liveRegion = Polite` on the whole state slot. A radio or spinner change inside Linked may make TalkBack re-read the **entire** Linked block. `@compose-expert` has to check this with TalkBack. If the whole block is read, move the `liveRegion` to the transition between state kinds only, or exclude the section from it.
- **Contrast:** radio `primary` #1565C0 on the cream card ≈ 5.6:1, and dark `primary` #90CAF9 on #221F1B is high (non-text ≥ 3:1 ✓). Supporting `onSurfaceVariant` on the card passes AA for body text. Selection never relies on colour alone: the radio dot shows it too.
- **fontScale 2.0:** nothing has a fixed height, titles and supporting lines wrap freely (no `maxLines`), and the control is top-aligned to the first line.

## 8. Проверка (one prototype pass, 2026-11-20)

**What was checked:** a disposable HTML prototype that copies this addendum's tokens. Hex values come from `Color.kt` and `GistiColors.kt`, type from `Type.kt`, and fontScale is applied as a multiplier on every sp. It was rendered in Chrome via Playwright, and every frame below was viewed. No Compose infrastructure was set up just for this. The frames are local, outside git, in the design session's scratch directory:

- `…/scratchpad/tgrem/a-320-fs20-ru-light-android.png`: 320dp, fontScale 2.0, RU, light. Columns: idle + hero (fold) · idle · saving · error + snackbar · disconnecting · dialog with note
- `…/scratchpad/tgrem/b-320-fs20-hi-dark-android.png`: 320 × 2.0, HI, dark. Columns: idle · saving · error + snackbar · dialog
- `…/scratchpad/tgrem/c-320-fs20-ru-light-web.png`: 320 × 2.0, RU, light, web. Columns: off · saving · on · dialog (web note)
- `…/scratchpad/tgrem/d-320-fs10-en-light-android.png`: 320 × 1.0, EN, light. Columns: idle + hero · idle · saving · `both` selected
- `…/scratchpad/tgrem/e-600-fs10-en-dark-web.png`: 600dp (the content cap on web Expanded), EN, dark, web. Columns: off · on
- `…/scratchpad/tgrem/f-320-fs15-hi-light-web.png`: 320 × 1.5, HI, light, web. Columns: off · saving · dialog

The full scratch path was handed to the lead in the session report. These frames do not survive the session; the binding frames are the ones the implementer takes (matrix below).

**What the first pass found, and how this addendum was changed:**
1. **Too tall at 320 × 2.0 RU.** With a supporting line under every Android row ("От приложения Gisti", "От бота Gisti. Без уведомления на телефоне.", "Уведомление на телефоне и сообщение в Telegram"), the group took about 720dp, more than a full screen. → Only the Telegram row keeps a supporting line, and it is shortened to the consequence ("Без уведомления на телефоне"). "Both" became "Phone and Telegram", which explains itself. The group is now about 470dp at 320 × 2.0 RU.
2. **The web caption took 7 lines** at 320 × 2.0 RU between the header and the group, and the "on" row's supporting line repeated it. → The caption was removed. Its point ("the web app can't send notifications") became the "on" row's single supporting line.
3. **Saving feedback** was checked in the spinner-in-control-slot form: no text reflow, and the row height does not change (frames `a` and `c`, column 2).

**After the fixes:**
- No clipping in RU or HI at 2.0, in light or dark.
- The radio sits on the first title line at fontScale 1.0, 1.5 and 2.0.
- The section header is visible on the first screen under the hero at 320 × 2.0.
- The disabled group (disconnecting) reads as inactive without hiding the current choice.

**Not covered by the prototype:** real Compose text metrics, Devanagari shaping in Skiko, the TalkBack live-region behaviour (§7) and a real 600dp+ web canvas. Those are in the implementer's matrix.

### Screenshot matrix for `@compose-expert` (screenshot-driven-ui, `@Preview` per state)

| Axis | Values |
|---|---|
| States | Linked + section idle (`push`) · idle (`telegram`) · idle (`both`) · saving (target `telegram`) · error reverted + snackbar · disconnecting · section hidden (`null`) · dialog with note (channel ≠ push) · dialog without note (`push`) |
| Platform | Android (3 rows) · web (2 rows: off, on, saving) |
| Width | 320dp · 411dp · web 1024 (column capped at 600) |
| Theme | light · dark |
| fontScale | 1.0 · 1.5 · **2.0** |
| Locale | EN · RU · HI |

**Minimum frames to attach to the report:**
1. Android: idle `push`, saving, error + snackbar, disconnecting × light × 320 × fontScale 2.0 × RU;
2. Android: idle `both` + dialog with note × dark × 320 × fontScale 2.0 × HI;
3. Web: off, saving, on × light × 320 × fontScale 2.0 × RU;
4. Web: off + on × dark × 1024 (wasmJs, real canvas) × EN;
5. Android: idle × light × 411 × 1.0 × EN (baseline);
6. Linked with the section hidden (`null`) × light × 411 × EN. It must match the phase-1 Linked frame pixel for pixel.

**Edge data for the fakes:**
- username `@anton_churaev_very_long_username_32` (account card above the section);
- each of the 3 values as the server value;
- `channelSaving` equal to and different from the server value;
- `reminderChannel = null`;
- the longest strings `telegram_reminders_save_failed` in HI and `telegram_reminders_disconnect_note` in RU.

### Reference frame
None as a pixel reference. The prototype frames above are a sanity check. The visual reference for the row anatomy is the live **Settings → Theme** group and the `ActivationReminderSheet` radio rows, here in the `groupSegment` skin of the Linked account card.

## 9. Handoff

- **Target:** `@feature-expert` (contract §6: `Linked.reminderChannel` / `channelSaving`, the action, `webTarget` plus its unit test, latest-wins save with the zone, the refresh guard, the failure snackbar, the dialog flag) → `@compose-expert` (section, rows, dialog note, previews, matrix §8).
- **Files:** `composeApp/.../telegram/TelegramScreen.kt` (new private `ReminderChannelSection`, `ChannelRow`; the `DisconnectDialog` gets an optional note) · `TelegramScreenContract.kt` · `TelegramViewModel.kt` · `integrations/IntegrationRows.kt` (only the two helpers change from `private` to `internal`) · the three `strings.xml` files.
- **Reuse:** `AppSectionHeader`, `groupSegment` + `groupPositionOf`, `AppDensity.Group*`, M3 `RadioButton(onClick = null)` + `Modifier.selectable` / `selectableGroup` (the Settings / ActivationReminderSheet pattern), `CircularProgressIndicator` with the screen's `SpinnerSize`/`SpinnerStroke`, the existing `SnackbarHost`. **No new design-system component.**
- **Build order:** strings → contract fields and action → stateless section with previews of every state above → VM (save, latest wins, revert, refresh guard, `webTarget` test) → dialog note → matrix.
- **Pitfalls:**
  1. **Unlink must reset the channel to `push`** (§4.1). If it does not, an Android user who chose Telegram gets no reminders after disconnecting.
  2. **Refresh during a save:** the resume or poll `status` must not overwrite the optimistic selection (§6).
  3. **`liveRegion` on the state slot** may re-read the whole Linked block on every radio change. Check with TalkBack (§7).

## Out of scope — lines for the lead
- **Android notification permission off with `push` or `both` selected:** the phone stays silent, and this section does not say so. Those users are a natural audience for Telegram. A hint here ("Notifications are off on this phone") would need the permission state on this screen. That is a separate decision; the existing notifications-off banner lives elsewhere.
- **Android builds released before phase 4** keep posting notifications for `telegram`. The brief says no copy is needed. If such a device and a new device share one account, "No notification on the phone" will be untrue on the old one until it updates.
