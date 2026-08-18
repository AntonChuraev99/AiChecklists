# UX Overhaul — Roadmap

**Living document.** It survives the branch that created it: `feat/ux-overhaul` ships in slices, and
each slice leaves the rest of this list untouched. Update it in the same MR that changes it — a
roadmap edited "later" is a roadmap nobody trusts.

**Started:** 2026-08-13 · **First shipped slice:** 1.20.0 (vc82)
**Working doc with the full iteration log, owner quotes and measurements:** `docs/active/ux-overhaul-2026-08-13.md` (gitignored, local only)

> **This file is tracked in a PUBLIC repository.** It carries product intent and code paths, never
> business figures — no revenue, purchase counts, conversion rates or pricing. Those live in
> `docs/PRODUCT.md` and `docs/unit-economics.md`, both gitignored. When you add an item here,
> phrase the "why" as user behaviour, not as a number.

## Legend

| Mark | Meaning |
|---|---|
| ✅ | Shipped and verified |
| 🔨 | Next up — specified, nothing blocking it |
| 🧊 | Planned, not specified yet |
| ❓ | Waiting on an owner decision, not on work |

---

## The product goal this serves

One sentence, so every item below can be checked against it: **anything a user throws at the app —
a list, a photo, a link, a voice note — becomes a checklist, its tasks land in Inbox, and a reminder
is what brings the user back tomorrow.** Simplicity first, AI as the layer on top of it, not as the
thing the user has to operate.

Two standing constraints from the owner:

1. **Reuse the v1 surface, do not build a thinner v2 twin.** The classic layout is years of shipped,
   debugged UX. When a v2 screen needs a sheet, a row or a flow that v1 already has, lift the v1
   component and extend it. A reduced re-implementation reads to the user as lost features — and it
   is one, because it re-opens defects v1 already closed. Say which part does not fit *before*
   writing a replacement.
2. **A reminder is a return mechanism, not a discipline requirement.** It must never become a
   required field. The success shape is: share of tasks carrying a reminder goes up while the number
   of tasks created does not go down.

---

## ✅ Shipped

### 1.19.x — the v2 shell became the default
Inbox · Calendar · Projects · Overview in one bottom bar, AI in the middle of it. v1 stayed as an
escape hatch in Settings. `NavExperimentResolverImpl` resolves an absent stored choice to
`NavVariant.V2`; Remote Config is no longer consulted, because the `nav_v2_arm` key was never
created in the console and therefore always resolved to the control arm.

### 1.20.0 — the vertical becomes reachable and legible
- **Analyze is findable again.** Turning content into a checklist is the product's stated
  differentiator, and the v2 shell shipped without any entry point to it — a lost function, not a
  design choice. Entries now sit in the capture dock (Photo · File/PDF · Link · Voice) and as a
  full-width row in the empty Inbox.
- **Analyze redesigned.** Six materials as an equal-width `SourcePillGrid` that collapses to a
  single pill once chosen (`AnalyzeSourcePicker.kt`), replacing a stack of full-width cards that
  pushed the editor below the fold on a 320dp window. Entering on Photo or PDF opens the system
  picker immediately (`EntryMaterialPicker.kt`) instead of asking the user to name the same thing
  twice.
- **AI creation is findable on Templates.**
- **Due dates are visible in the Inbox list**, and setting one takes fewer taps.
- **The credits chip — the entry into premium — is on all four v2 tabs**, each with its own
  analytics `source` so the surfaces stay separately measurable.
- **One bottom surface.** The bar, the capture dock and the chat dock stopped reading as three
  different docks (`ChatColors.kt`: `ChatSurfaceTone`, `LocalChatSurfaceTone`).

---

## 🔨 Next up

### R1 — Capture dock, second pass
The dock captures text and parses a date, but the date rail from the design spec is missing.

- Swipe rail for setting a due date directly on the row.
- Six presets (this evening / tomorrow morning / weekend / …) instead of sending the user to a full
  calendar, which is expensive in UI and rarely used.
- Bring back **Pick time** and **Repeat**, lost when the dock was rebuilt.
- `ReminderTarget` for the draft, so a reminder can be attached before the task exists.

*Where:* `core/designsystem/.../components/QuickCaptureDock.kt`, `SmartDateParser` (RU/EN lexicons
already exist), `ChecklistFillItem.reminderAt` (no Room migration needed).
*Done when:* a task can get a due date without leaving the dock, and the parsed date is visible as a
chip that a tap edits.

### R2 — Calendar and Today earn their tabs
- Empty state that leads into the daily review instead of dead-ending.
- A "Ready to schedule" section — tasks that exist but carry no date, i.e. exactly the population
  the reminder mechanism is for.
- Today = Inbox filtered to the day, driven by reminders.

*Where:* `feature/home/.../calendar/CalendarScreen.kt`, `.../today/TodayScreen.kt`.
*Library note:* `com.kizitonwose.calendar:compose-multiplatform` targets wasmJs; verify the version
on Maven Central before adopting — do not take a version from memory.

### R3 — `DailyReviewScreen` — one task at a time
The owner's "daily slideshow": one card, AI asks a question about it, the user answers, AI proposes
the next step. This is the screen the Calendar/Today empty states point at, so R2 and R3 land
together or R2 points at nothing.

### R4 — AI that asks back
- `AppAiProposalCard` and follow-up chips at the end of an AI message, ChatGPT-style, so the
  conversation continues without the user composing the next prompt.
- `AppAiSuggestionRow` on the list surfaces.

*Where:* `feature/aichat/`. Server side is the `chat_agent` Cloud Function; adding a tool means
touching all six registration sites — see `.claude/rules/ai-chat.md` before starting.

### R5 — Sources screen
A single place listing everything the app can turn into a checklist, reached from the dock's source
row. Currently the sources exist only as the collapsed row.

---

## 🧊 Planned

### R6 — Premium screen, full redesign — **priority track**
The whole monetising surface: `PaywallScreen`, `SubscriptionStatus`, `PostCancelReason`,
`WebInstallApp`, `HeroIllustration`, plus `PremiumBanner`, `CalmUpgradeHint`, `AppCreditsChip`,
`WebCreditsGateBanner`.

Scope agreed with the owner: three states · a disclosure block that satisfies Play's subscription
rules (`docs/guidelines/paywall-compliance.md`) · personal proof of value rather than a generic
feature table · a sheet for the credit dead-end.

Two hard requirements, both of which have bitten before:

- **Limits come from Remote Config, never from a local constant.** `PaywallScreen.kt` has shipped a
  hardcoded comparison table that disagreed with the served config. A comment saying "mirrors X" is
  not checked by the compiler — treat it as a smell, not documentation.
- **A regression test must pin two different config values.** A fake pinned to the value currently
  served passes against a hardcoded number just as happily as against a correct lookup. Reference
  fix: `ToolCallDispatcherImpl.freeChecklistCeilingReached()`.

The failure is not only visual — most of the loss happens *after* the user taps to buy, which points
at conductivity through the purchase flow, not at the screen's looks. Do not treat this as a
repaint. Numbers: `docs/PRODUCT.md`.

### R7 — Motion language
Today the only motion in the codebase is `animateColorAsState`, `animateFloatAsState` and the chat
dock's `AnchoredDraggableState`. `AppMotion.kt` defines six explicit springs; the language still has
to decide what glides (sheet opening, sliding) and what is springy (tap, bounce).

⚠️ M3 `MotionScheme` is **not usable on CMP 1.11.0** — the symbols exist in the klib but are
`internal`. On wasmJs springs convert to cubic-bezier plus a length.

### R8 — Typography
The scale is the default M3 one, 99 lines of `Type.kt`, with no character of its own.

### R9 — Remaining screens
Create · Settings · Debug, driven by where users actually go.

### R10 — Inbox auto-triggers (server work, separate track)
An intake email address with a rate limit, a webhook endpoint, messengers via IFTTT/Zapier. Server
side lives in `firebase-functions/` and does not ship with an app release.

---

## ❓ Owner decisions, not work

| Fork | The trade-off |
|---|---|
| **No AI button while the capture dock is open** | The AI button was the raised centre of the navigation bar, and the dock replaces the bar — so the one-tap hop from capturing straight into chat is gone. This follows directly from the owner's own 2026-08-17 request to clear the bottom chrome. If it should come back, the place is a fifth icon in the dock's source row. |
| **Dock input field is transparent while the pills beside it are filled** | The primary action reads weaker than the secondary ones. The fix touches `AppTextField` and its four other call sites, which is why it was not done inline. Record: `docs/backlog/2026-08-17-capture-dock-input-field-unfilled-on-bottom-chrome.md`. |
| **Weight of the seam in light theme** | `bottomChromeSeam()` uses `outline`, raised on three screens at once (home, checklist detail, chat). Reverting is a one-line change. |
| **`ChoiceRole.Add` is dead in production** | No emitter ever creates an option with this role. Use it or delete it. |

---

## Verification that automation cannot cover

Screenshot tests run under Robolectric, which **never raises the keyboard** — `WindowInsets.ime` is
always 0 there. Anything about the IME is unproven by a frame and has to be checked by hand:

1. Dock open → system Back once (keyboard gone, dock still up): the strip under the dock must be the
   dock's colour, not the light page. This is the only check of the "tail" where the bar used to be.
2. Dock plus a raised keyboard: the input sits against it with no empty band, nothing protruding
   below. This is what proves `consume` was narrowed to `navigationBars`.
3. The same dock on **Calendar** — a second host whose scrim is drawn by a different mechanism
   (`drawWithContent`, not an overlay `Box`).
4. **wasmJs: the bottom bar's shoulders have never been checked visually by anything.** A Chrome
   window will not narrow under automation, because Compose reads the size of the *window* rather
   than the container, and a wide window shows the drawer instead of the bar — where shoulders do
   not exist by construction. Needs a manual pass in a narrow window on `:9090`.

---

## Ground rules for anyone continuing this branch

- **Do not break v1 "Classic".** It is the escape hatch; a user who switches to it must find the app
  they had.
- **Inbox is a system checklist** (`isInbox=true`) and must stay invisible in project lists, pickers,
  limits, the widget, sharing and MCP.
- **Zero user-facing string literals in Kotlin.** `stringResource` in a `@Composable`, `getString`
  (suspend) in a ViewModel — including default names and error text. The domain layer never touches
  Compose Resources; pass the resolved string in from presentation.
- **In `strings.xml` write apostrophes literally** (`can't`), never Android-style `\'`. Compose
  Resources is not parsed by AAPT, so `\'` renders the backslash on screen.
- **Verify wasmJs on `:9090` before pushing.** `compile*` and `commonTest` stay green while the live
  path is broken — they cover neither the Compose runtime, nor Coil, nor JS interop.
- **Every error path logs** via `AppLogger.error(tag, message, throwable)`. A silent early return on
  a UX path is a bug, not a guard.
- **Do not drive the device UI** (`adb shell input` and friends). Build, install, logcat and
  screencap are fine.
