# DESIGN_SPEC: Onboarding step 2, one screen per choice

> This spec was written before the code. Component names, keys, timings and crop rectangles are the design plan, not measurements (not verified: the implementation may name them differently). The source of truth after implementation is `feature/onboarding/.../welcome/`.

- **Platform:** Android + Web (wasmJs), Compose Multiplatform, Material 3, redesign B "Soft & tactile". iOS is code-only.
- **Method:** native spec plus a throwaway HTML prototype (`prototype.html` next to this file; binaries and HTML are gitignored, only this `.md` is tracked).
- **Supersedes:** §1.4 (Try it), §1.5 (sample overlay) and §3 (drawn note) of `docs/designs/onboarding-refresh-2026-11-04/DESIGN_SPEC.md`. Step 1 chrome, the AI loader, the error box, the strings that are kept and the motion tokens of that spec still apply unless this file says otherwise.
- **Owner request (2026-11-17), in short:**
  - Step 2 is the same weak screen for every choice; each choice should open its own screen.
  - The sample note's text is cut off. Use a realistic photo.
  - The four file pills look dated.
  - The input does not stand out from the page, and there is no voice message and no conversation entry.
  - "Talk to Gisti" repeats the input, so merge the two.

---

## Owner decisions (2026-11-17) — override §5 and §14 where they differ

- **Variant A** (three screens). The picker first returned B; after the Write ≈ Talk risk was named, the owner chose A.
- **Q3 → yes.** Showcase loses "Try it now" and gets the text button "Show me an example", which runs the sample from step 1.
- **Q1 / Q4 → voice message = dictation into the field, on Write and on Talk alike.** A held mic records a clip, and the clip is transcribed through the chat's existing pipeline (`TranscribeAudioApiService`, `feature:aichat:api`). The text lands in the field, and the person then presses Send (Write: list; Talk: chat) or Call. Dropped: `AnalyzeInputData.Audio` in onboarding, `GenerationInput.Voice`, `ShellLaunch.OpenChat(voiceClip)`, the voice loader frame A_11. Needed: a "Recognizing speech…" state on the composer (same as the chat's `isTranscribing`) and a visible failure that keeps the field editable.
- **Q2 → send at once.** Talk send, a Talk starter, and Chat with typed text all open the chat with the message **already sent** (`ShellLaunch.OpenChat(prefill, autoSend = true)`). On Talk, Send stays disabled while the field is empty: the A frames the owner approved have no "open an empty chat" entry. The empty chat stays one tap away after onboarding (Skip → Inbox → the Ask pill). Implementation note: the ViewModel's `OnChatClick` with a blank field still opens the chat unsent, but no control in the UI reaches that branch.
- **Q5 → keep** `onboarding_sample_note_*`. They are the text source of the photos and feed the photo's TalkBack description.

## 0. Variants (owner picks via `design-picker`)

| id | Name | In one line | Frames | Verdict |
|---|---|---|---|---|
| **A** | **Three screens** | Each Showcase tile opens its own screen: **Write** (a hero composer card + ideas), **Attach** (the photo sample + four source cards), **Talk** (a Gisti greeting + starters + the chat's own composer docked at the bottom). Showcase loses its docked CTA. | `frames/sheet_A.png`, `frames/A_*.png` | **Recommended** |
| B | One screen, hero by choice | One "Make your first list" screen. The chosen mode's block goes first, and the other modes become an "Other ways" list. One unified composer with a Chat chip, Call and Mic in its toolbar. Showcase keeps "Try it now". | `frames/sheet_B.png`, `frames/B_*.png` | Cheaper, but Talk ≈ Write |
| C | Ask dock everywhere | Every branch screen has the product's Ask plate `[+ · field · call · mic⇄send]` docked at the bottom, and only the hero above it changes (ideas grid / photo sample / greeting). Own files go behind "+". | `frames/sheet_C.png`, `frames/C_*.png` | Teaches the real chrome, but repeats the owner's complaint |

**Why A.** It is the only variant where the three choices are visibly different screens. Each screen also makes its own action the visual centre:

- Write: the composer card is the biggest thing on the page.
- Attach: the photo is.
- Talk: the conversation is.

Write and Talk cannot blur into one screen with two titles. Write has a multi-line prompt card at the top and its send makes a list in place. Talk has a docked chat composer at the bottom and its send continues in the chat. These are two different components in two different places (§3).

**Risks of A:**

1. Three screens to build and snapshot instead of one.
2. The sample is only on Attach. Mitigated by "Show me an example" on Showcase, the error box, and the zero-decision path.
3. Talk needs a voice hand-off into the chat (Q1).

**B, what is wrong:** B's frames 02 and 04 differ only by one caption line above the same composer. That is the "same screen for every choice" the owner rejected. The reorder per mode also brings back the problem the 11-04 spec avoided: N orders to build, and "back" lands on a different layout than before.

**C, what is wrong:**

- On Write (C_02), the input is a single-line pill at the bottom of the screen. That is exactly the "input does not stand out" complaint.
- The four file kinds hide behind "+". The owner asked to redesign them, not to hide them.
- The send button means two different things in one identical dock (list on Write, chat on Talk) with nothing visible to tell them apart.

---

## 1. Flow (variant A)

```
Showcase (step 1/2)
 ├─ tile Write   → Write  (step 2/2)
 ├─ tile Attach  → Attach (step 2/2)
 ├─ tile Talk    → Talk   (step 2/2)
 ├─ "Show me an example" (text button) → sample overlay → checklist detail
 └─ Skip → Inbox (tips seeded, as today)
Write / Attach / Talk: ← and system back → Showcase · Skip → Inbox
```

**Showcase CTA "Try it now": removed** (A, C). The three tiles are the choice. A filled CTA under them would have to secretly pick one branch, and a "default" branch is the one-screen-for-all that the owner rejected. Its place goes to an **`AppButtonText` "Show me an example"** (AutoAwesome icon) directly under the tile group, centred, `SpacingSm` above it. It runs the sample overlay straight from step 1. This is the zero-decision path for a person who does not want to choose: no AI, no credits, works offline. No dock: the screen keeps the 1 : 2 free-space split of the 11-04 spec (§1.2 there), with the text button as the last child of the content block.

- Pass-1 finding: a docked outlined "Show me an example" wrapped to two lines at RU × 2.0 and took a 104dp dock (frame A_24, pass 1). An inline text button has no such cost.
- The step progress stays 2 segments. All three branch screens are "step 2 of 2".

---

## 2. Structure

Shared chrome is unchanged from the 11-04 spec §1.1: `OnboardingTopBar` with back, the 2-segment `StepProgress` and Skip; `statusBarsPadding`, `navigationBarsPadding`, `imePadding`. Content max width: 560dp on medium, see §7 for expanded.

### 2.1 Write

```
Column(weight 1f, verticalScroll, padding(h = ScreenPaddingHorizontal, top = SpacingSm, bottom = SpacingLg))
├─ Text onboarding_write_title          headlineSmall, heading()
├─ SpacingXs
├─ Text onboarding_write_subtitle       bodyMedium, onSurfaceVariant
├─ SpacingLg
├─ GoalComposer (§3.1)                  ← autofocus on entry, IME opens
├─ [AiErrorBox]                         SpacingSm above; existing component, see §4
├─ SpacingXl                            (section)
├─ Text onboarding_write_ideas_title    titleSmall, heading()
├─ SpacingXs
└─ GistiPromptChips(WelcomeStarterTemplate)   unchanged; edge-bleed row placed OUTSIDE the column padding (its KDoc)
```

- Chips and their title hide while the composer has text (as today), and come back in the error state so a chip is a way out.
- Pass 1 tried filling the space below the chips with the Showcase Write demo scaled ×2 (prototype, not shipped). It read as a second input with a live send button, which is a decoy. Removed. The empty band only shows after the user dismisses the keyboard; on entry the IME covers it (frame A_07).

### 2.2 Attach

```
Column(weight 1f, verticalScroll, padding as Write)
├─ Text onboarding_attach_title        headlineSmall, heading()
├─ SpacingLg
├─ SamplePhotoCard (§3.3)              groupSegment(Single)
├─ SpacingXl
├─ Text onboarding_attach_own_title    titleSmall, heading()
├─ SpacingSm
└─ SourceCardGrid (§3.4)               4 cards, 2×2 → 1 column (measured)
```

### 2.3 Talk

```
Column(fillMaxSize)
├─ Column(weight 1f, verticalScroll(state), padding as Write)
│   ├─ Text onboarding_try_talk_title   headlineSmall, heading()          (existing key)
│   ├─ SpacingLg
│   ├─ GreetingRow: Row(gap SpacingMd, Top)
│   │    ├─ GistiPresenceOrb(Speak, level { 0f }, size 40dp)   (node 52dp, offset −6dp, as the 11-04 Talk card)
│   │    └─ AiBubble(widthIn(max = 520dp)): onboarding_talk_greeting (bodyLarge)
│   │         + SpacingXs + onboarding_talk_greeting_hint_call | _chat (bodyMedium, onSurfaceVariant)
│   ├─ SpacingLg
│   ├─ Text onboarding_talk_starters_title   labelMedium, onSurfaceVariant, End-aligned
│   └─ 3 × StarterBubble (End-aligned, SpacingXs apart)            hidden while the field has text
└─ TalkComposerPlate (§3.2)             docked, bottomChrome band; NOT inside the scroll
```

- **IME open:** the scroll jumps to its end (`animateScrollTo(maxValue)` keyed on the IME bottom inset; see the reverse-layout trap in the playbook). The starters stay right above the composer and the greeting scrolls up. Pass-1 finding: at 320×640 the starters ended up under the composer (A_26 before/after).
- Top-aligned on phones, like Write and Attach, so the headline sits in the same place on all three screens. The empty band between the starters and the dock is accepted: it is the chat's own "empty conversation" look.

### 2.4 Sample overlay (shared by every variant)

```
Box(fillMaxSize, background, systemBars padding)
 Reading: Column(Center)
   ├─ Box(width = min(280dp, windowW − 64dp, (windowH − 168dp) × 2/3), aspectRatio 2:3)
   │    ├─ SamplePhoto(full frame)                     clip AppShapes.medium
   │    ├─ ScanBand                                    16 % of the photo height; sweeps the text block (§3.3)
   │    ├─ "Sample" badge, top-start 12dp
   │    └─ 4 × CornerBracket, outside the photo by 8dp     primary, 3dp stroke, 28dp arms, outer radius 12dp
   ├─ 28dp  (no token; nearest SpacingXl 24 — the brackets eat 8 of it)
   ├─ Text onboarding_sample_reading     titleMedium
   └─ Text onboarding_sample_found       bodyMedium, onSurfaceVariant, liveRegion polite, counts 1 → 6
 Ready: as the 11-04 spec (ResultCard), header thumbnail = SamplePhoto(thumb crop) 44×52dp, AppShapes.small
```

### 2.5 AI loader

**Superseded 2026-11-17 by §L2 (AI loader v2 — step list instead of the orb).** The existing `AiGeneratingLoader` stays as it is. One addition: for a voice goal from Write, the quoted prompt is replaced by a **voice chip**:

- shape: `AppShapeTokens.Pill`, `AppSurface.card()` fill, 1dp `outlineVariant`, padding `SpacingMd` / `SpacingSm`
- `Icons.Filled.Mic` 18dp `onSurfaceVariant`
- `onboarding_voice_message_chip` ("Voice message · 0:07"), bodyMedium, onSurfaceVariant

See frame A_11.

---

## 3. Components

### 3.1 GoalComposer (Write): the hero input

**Reuse check (v1-surface rule):**

- `ChatInputRow` (`feature/aichat/impl/.../components/ChatInputRow.kt:138`) is the right **control set** but the wrong **shape** for this screen. It is a pill that grows from one line, built for a running conversation. Here the input IS the screen, and the owner's complaint is that it does not stand out.
- `AppTextField` (outlined M3) cannot host a toolbar inside its outline.
- So the **container** is new and the **controls are lifted** from `ChatInputRow`: the Mic⇄Send trailing control with its press-and-hold gesture becomes a designsystem component (`GistiMicSendButton`, §3.2). Both `ChatInputRow` and `GoalComposer` call it. One gesture, one recording look, one set of strings.

```
Surface(shape = AppShapeTokens.Group (24dp), color = AppSurface.card(),
        border = 1dp AppChatColors.controlOutline()  →  2dp colorScheme.primary while focused)
└─ Column(padding start SpacingLg, top SpacingMd, end SpacingXs, bottom SpacingXs)
   ├─ BasicTextField(bodyLarge, onSurface, minLines 3, maxLines 6, imeAction Send, capitalization Sentences)
   │    placeholder onboarding_try_type_placeholder (onSurfaceVariant)          (existing key)
   └─ Row(End, minHeight 48dp)
        └─ GistiMicSendButton(canSend = text.isNotBlank())
```

| State | What is visible |
|---|---|
| Empty, unfocused | 1dp outline, placeholder, **Mic** (36dp disc `AppSurface.recessed()` + `Icons.Outlined.Mic` onSurface) |
| Empty, focused (entry) | 2dp primary border, caret, Mic |
| Typed | Text, **Send** (36dp `primary` disc + `ArrowUpward` onPrimary). Crossfade 200ms, as ChatInputRow |
| Recording (mic held) | The text area is replaced in place, same height: a 10dp `colorScheme.error` dot (pulses unless reduced motion), the elapsed time `titleMedium` ("0:07"), then `KeyboardArrowUp` 18dp + `chat_voice_drag_cancel_hint` (bodyMedium, onSurfaceVariant). After the cancel threshold: `chat_voice_release_cancel_hint`. The disc turns `colorScheme.error` / `colorScheme.onError` and keeps **the same 36dp** size. Pass-1 finding: a bigger "recording" disc grew the card by 16dp and jumped the layout (A_10 pass 1). |
| Too short (< 1 s) | Supporting text under the card: `chat_voice_too_short` (bodyMedium, onSurfaceVariant, liveRegion polite). Clears on the next interaction. |
| IME Send on empty | Supporting text `onboarding_try_type_empty_hint` (existing behaviour). The on-screen send never exists while empty, because Mic sits in that slot. |
| Disabled (busy) | Text 38 % alpha, disc in the M3 disabled pair (as `GistiCallButton(enabled = false)`) |

- **Mic tap** (a tap, not a hold) shows `chat_voice_press_hold_hint` as a plain tooltip. That is ChatInputRow's own behaviour, lifted with it. The mic is never started without the user's hold.
- **IME:** on focus, the **whole card** is brought into view (`BringIntoViewRequester` on the card, requested from `onFocusChanged`), not just the caret line. Pass-1 finding: at 320×640 × 1.5 the caret was visible but the toolbar sat under the keyboard (A_27 before/after).

### 3.2 GistiMicSendButton and TalkComposerPlate

- **`GistiMicSendButton`** (new home: `core/designsystem/.../components/gisti/`) is lifted out of `ChatInputRow`:
  - the trailing Mic⇄Send crossfade
  - the `awaitEachGesture` hold / drag-up-to-cancel (80dp) logic
  - the TalkBack custom actions
  - the recording visuals

  Its API mirrors ChatInputRow's callbacks (`onSend`, `onVoiceRecordingStarted/Stopped/Cancelled`, `onDragCancelChanged`, `canSend`, `isRecording`, `isTranscribing`, `enabled`, plus a `style` parameter: `Disc` (Write: a recessed 36dp disc behind the mic) or `Bare` (chat: a bare 24dp glyph, as today)). `ChatInputRow` keeps its exact look and behaviour by passing `Bare`. **No behaviour change for the chat.** Its existing screenshot tests (`ChatInputCallScreenshotTest`) must stay byte-identical.
- **TalkComposerPlate (Talk):** reuse **`ChatInputRow` itself**. It depends only on androidx and designsystem (checked: no `feature`/`core` imports besides designsystem). Move it to `core/designsystem/.../components/gisti/` unchanged (package move, Chat screens re-import it), and add two optional parameters with backwards-compatible defaults:
  - `ChatInputLeading.None` (no Help, no Credits)
  - `attachVisible: Boolean = true` (Talk passes false: a "+" in onboarding Talk would offer a third meaning for files)

  Then:

  ```
  Box(fillMaxWidth, background = AppSurface.bottomChrome())
   └─ CompositionLocalProvider(LocalChatSurfaceTone provides BottomChrome)   // raised() → bottomChromeRaised(), as the Ask plate
       └─ ChatInputRow(leading = None, attachVisible = false,
                       placeholderOverride = null  → chat_input_placeholder "Message Gisti",
                       onCallClick = if (isCallAvailable) onCall else null, …)
          padding(h = ScreenPaddingHorizontal, v = SpacingMd)
  ```

  It looks and behaves exactly like the chat's own composer: `[field · Call · Mic⇄Send]`. That is the point: the next screen the user sees (the chat dock) has the same control in the same place. **No new strings.**

### 3.3 SamplePhoto and SamplePhotoCard

**Asset:**

- Source: `docs/designs/onboarding-steps-2026-11-17/assets/sample_note_{en,ru,hi}.png`, 1024×1536, from the main agent. All three share one composition. The text block is at ≈ x 110–680, y 230–880; the lower half of the sheet is empty; a pen sits bottom-right.
- Export: 720×1080 WebP, quality ≈ 80. Target ≤ 150 KB each (not measured).
- Location: `core/designsystem/src/commonMain/composeResources/drawable/onboarding_sample_note.webp` (EN default) + `drawable-ru/` + `drawable-hi/`. These are Compose Resources language qualifiers (not verified for drawables in this project; `@compose-expert` confirms with one preview per locale).
- The text in each photo matches `onboarding_sample_note_*` in that language (main agent's check). The drawn `SampleNote` composable is deleted once nothing uses it.

**Crops** are fractions of the source, so they do not depend on the export size. Implement them with `BitmapPainter(image, srcOffset, srcSize)` from `imageResource(...)`, not with `ContentScale` + alignment. A painter crop is exact on both platforms.

| Name | x | y | Used by |
|---|---|---|---|
| `Band` | 0–100 % | 9.8–61.8 % (aspect 1024 : 800) | SamplePhotoCard media, fontScale ≤ 1.3 |
| `BandTight` | 0–100 % | 12.4–58.0 % (aspect 1024 : 700) | SamplePhotoCard media, fontScale > 1.3 |
| `Text` | 7.8–70.3 % | 11.7–61.2 % | ResultCard thumb 44×52, Showcase Attach stage 46×62 (replaces the drawn mini-note), B's compact row |
| `Full` | 0–100 % | 0–100 % | Overlay "Reading" |

Pass 1 proved the point: a cover-crop of the full frame into the 44×52 thumb showed a mostly brown desk (A_06 before/after). The `Text` crop shows the handwriting.

**SamplePhotoCard** (`groupSegment(Single)`, `AppShapeTokens.Group`):

```
Column
├─ Box(fillMaxWidth, aspectRatio = crop aspect)         ← media, top corners clipped by the group shape
│   ├─ Image(SamplePhoto(Band), contentDescription = onboarding_sample_image_a11y)
│   └─ SampleBadge  top-start 12dp (SpacingMd)          (existing badge, fontScale-locked)
└─ Column(padding start/end SpacingLg, top SpacingMd, bottom SpacingLg)
    ├─ onboarding_sample_title   titleMedium
    ├─ onboarding_sample_body    bodyMedium onSurfaceVariant      ← hidden when fontScale > 1.3
    ├─ SpacingMd
    └─ AppButton(onboarding_sample_cta, icon AutoAwesome, fillMaxWidth)   ← the screen's only filled button
```

- **fontScale ladder:**
  - Above 1.3: `BandTight` + no body. This keeps the CTA inside the first 800dp at RU × 2.0 (A_22). The body's sense moves into the button's `onClickLabel`.
  - The CTA label may wrap to 2 lines at 2.0. It does not clip.
- **Dark theme:** the photo keeps its colours, with a `ColorFilter.colorMatrix` brightness 0.9 (named constant `SamplePhotoDarkDim`) so the oak does not glare against `#161412` (A_17).
- **Overlay "Reading":**
  - Light: a soft shadow on the photo, the one shadow on the screen. *No elevation token for it*; nearest is M3 level 3 = 6dp. It is a physical print.
  - Dark: no shadow, a 1dp `outlineVariant` ring instead (A_19).

**Scan animation without line geometry.** Per-line highlighting needs each line's position. The photos share a composition, but line positions differ by up to ≈ 3 % of the height between EN and RU, which is enough to miss a line. Instead:

- (a) four **corner brackets** snap in around the photo (document detected)
- (b) a **band** (vertical gradient `primary` 0 → 38 % → 0, 16 % of the photo height) sweeps only the **text block** (y 12 % → 62 %), which is constant across locales
- (c) a **counter** "Found N of 6 tasks" ticks as the band passes each sixth of the block

Timeline in §6.

### 3.4 SourceCardGrid (Attach): the four doors, redrawn

- **Reuse check:** `SourceTileRow` (`SourceRow.kt:278`, owner's pick for the Inbox, 2026-11-16) is the same four doors as discs + labels. It fits a strip under a heading. On a screen whose whole job is "which file?" it has no room for **what each door takes**. Without that answer the four pills read as "attach to the task", as the `SourceRowSection` KDoc warns.
- So: **extend, don't twin.** Add an optional `caption: String?` to `SourcePillEntry` and a third rendering `SourceCardGrid(captions: (AnalyzeInputKind) -> String, onSelect)` in `SourceRow.kt`. It is built over the same `sourceDoorEntries()`, so the glyphs, palette slots (Photo 2 peach · PDF 3 lilac · Link 1 blue · Voice 0 green), order and `onSelect` contract are shared with the pills and tiles. The host emits `ai_entry_tapped` as today.

```
Card: Surface(onClick, shape AppShapes.medium (16), color AppSurface.card(), border 1dp outlineVariant, minHeight 96dp)
  vertical (2 columns):   Column(padding SpacingMd, gap SpacingSm): Disc 40dp (paletteTint/paletteContent, glyph 20dp) · title titleSmall onSurface · caption bodySmall onSurfaceVariant
  horizontal (1 column, and expanded): Row(padding SpacingMd, gap SpacingMd, CenterVertically, minHeight 64dp): Disc 40 · Column(title, caption)
```

- **Ladder (measured, like `SourceTileRow`):**
  - 2 columns of vertical cards if the widest **title** fits one line at half width and no caption needs more than 2 lines.
  - Otherwise 1 column of horizontal rows.
  - Never truncated, never ellipsized.
  - RU × 2.0 at 360 → 1 column. EN/RU/HI × 1.0 → 2×2 (A_03, A_17, A_28).
- Grid gap `SpacingSm`. Each card is one merged `Role.Button` node: "Photo, Note, whiteboard, receipt".

### 3.5 Talk pieces

- **AiBubble:**
  - fill `AppChatColors.raised()`, 1dp `AppChatColors.contentOutline()`
  - shape: the chat's AI-bubble shape. The tail corner (top-start) is `AppShapeTokens.ChatBubbleTail` (8dp, a `Dp` token, not a shape). The other corners match the chat's AI bubble; reuse that bubble's shape value rather than a new radius.
  - padding `SpacingMd` / `SpacingMd`
  - the hint line is hidden at fontScale > 1.3: the composer's own Call and Mic icons carry it (A_23)
- **StarterBubble:**
  - outlined (1dp `AppChatColors.controlOutline()`), transparent fill
  - user-bubble shape (tail corner `AppShapeTokens.ChatBubbleTail` at bottom-end, the other corners as the chat's user bubble)
  - bodyMedium onSurface, padding `SpacingMd` horizontal / `SpacingSm` vertical, `minHeight 48dp` via `minimumInteractiveComponentSize`
  - `Role.Button`, End-aligned, wraps to 2 lines at 2.0 rather than clipping
  - Outlined, not filled: a filled user bubble would read as "already sent"
- **Starters:** `onboarding_talk_starter_1..3` ("Plan my week", "Help me pack for a trip", "What can you do?"). A tap sends that text to the chat (§5).

---

## 4. States (variant A, every screen)

| Screen | State | What is visible |
|---|---|---|
| Showcase | default / call off / reduced motion / fontScale > 1.3 | As 11-04 §2, minus the dock CTA, plus the "Show me an example" text button. The Attach stage now shows the photo `Text` crop instead of the drawn note (A_01). |
| Write | entry | Composer focused, IME open, chips visible above the IME (A_07) |
| | typed | Send disc; chips hidden (A_08) |
| | recording / too short / mic permission denied | §3.1. Denied → snackbar `onboarding_mic_denied` + the field keeps focus (type instead). |
| | AI generating (text / voice) | AI loader with the quote / the voice chip (A_11) |
| | AI error, text | Existing `AiErrorBox` under the composer, text kept, chips back (A_09): Try the sample · Save as a plain list |
| | AI error, voice | Same box with `onboarding_write_voice_error`; actions: Try the sample · `onboarding_write_type_instead` (focuses the field). There is no "Save as a plain list": there is no text to save. |
| | web, no credits | Box with `onboarding_try_ai_signin` (existing) |
| | chip creating | Controls disabled; the spinner rule of 11-04 §2 |
| Attach | default | Photo card + 4 cards (A_03). No loading or empty state: static content. |
| | own file tapped | Leaves to Analyze (unchanged contract) |
| | sample save failed | Snackbar `onboarding_welcome_create_error` (existing) |
| Talk | default, call on / off | Greeting with the call / chat hint; Call disc only when `isCallAvailable` (A_04, A_13) |
| | IME open | Scroll at end, starters above the composer (A_12, A_26) |
| | typed | Starters hidden; Send replaces Mic, Call hides (ChatInputRow's own rule) |
| | recording | ChatInputRow's own recording state (A_14) |
| | handing off | `Leaving` phase: controls disabled; no loader (navigation is immediate) |
| Overlay | reading / ready / reduced motion | §2.4, §6 (A_05, A_06, A_19, A_20, A_31) |

**Loading / error / empty / success** in one line each:

- **Loading:** only the overlay and the AI loader.
- **Error:**
  - AI: the box on Write.
  - Recording: the snackbar `chat_recording_failed`.
  - Sample write: a snackbar.
  - Chat and call errors belong to their own screens.
- **Empty:** not applicable (static screens).
- **Success:** navigation to the checklist detail, Analyze, or the chat/call.

---

## 5. Flow, side effects and the contract delta for `@feature-expert`

| Trigger | Result |
|---|---|
| Showcase tile | Go to that screen. Write requests focus **after** the step transition finishes (no IME animation during the slide). |
| "Show me an example" | `OnSampleClick(origin = Showcase)` → overlay → detail. Same latch and timeout as today. |
| Write send | AI generation from text (existing branch §4.3 of the 11-04 FEATURE_SPEC) |
| Write mic released | Record → `analyzeData(AnalyzeInputData.Audio(path))` in place (the type exists: `AnalyzeInputData.kt:70`) → AI loader (voice) → detail / voice error box |
| Write chip | Preset list (existing) |
| Attach CTA | Sample (existing) |
| Attach card | `navigateToAnalyzeWithInput(kind, ONBOARDING)` (existing) |
| Talk send / starter tap | `ShellLaunch.OpenChat(prefill = text, autoSend = true)` → Inbox with the dock open and the message **sent** (the user pressed Send; making them press it twice is the defect). Q2. |
| Talk mic released | `ShellLaunch.OpenChat(voiceClip = path)` → the chat runs its existing transcribe → turn pipeline. Q1. |
| Talk call | `ShellLaunch.StartVoiceCall` (existing) |
| ← / back on any branch | Showcase; `inputText` kept (shared by Write and Talk, so a goal typed on Write carries into Talk) |

**UiState / intents delta** (names are proposals):

- `OnboardingStep`: `Showcase, Write, Attach, Talk`. `index` is 1/2/2/2, `count` stays 2, `wire` is `showcase/write/attach/talk`. `TryIt` is gone.
- Removed: `pendingFocus`, `OnModeFocusConsumed`, `OnboardingMode.Default`, `OnShowcaseCtaClick`. Each screen is its own focus target; Write's autofocus is UI-local.
- `SampleOrigin.Showcase` added.
- `AiGenerating(input: GenerationInput)` with `GenerationInput.Text(prompt)` / `GenerationInput.Voice(durationMs)`.
- `OnboardingAiError.VoiceUnavailable` added (third box text).
- Recording: `voice: VoiceCapture? = null` (`elapsedMs`, `cancelArmed`, `screen`). Intents `OnVoiceRecordStart`, `OnVoiceRecordStop`, `OnVoiceRecordCancel`, `OnVoiceDragCancelChanged`.
  - The recorder is `AudioRecorderLauncher` (`core:filepicker:api`; `feature:onboarding` gains that dependency, `feature:analyze` already has it).
  - Below 1 s → `voiceTooShort` hint, nothing sent.
- `OnTalkStarterClick(index)`, `OnTalkSend` (the Talk send). Kept separate from `OnSendClick` because the destinations differ.
- `ShellLaunch.OpenChat` gains `autoSend: Boolean = false` and `voiceClip: String? = null`. App.kt glue: `OnPrefillInput` + send, or the chat's voice entry with that clip.
- Seeds (proposal): `typed_voice` (Write voice), `talk_text`, `talk_starter`, `talk_voice`. Retire `chat`; keep `call`.
- Analytics (proposal):
  - `onboarding_voice_recorded{screen, outcome = sent|cancelled|too_short|failed, duration_bucket}`
  - `onboarding_starter_tapped{index}`
  - `onboarding_mode_selected` loses `default`
  - `onboarding_sample_used{origin = showcase}`

**Microphone rule:** the permission prompt comes only from a hold on the mic, never on screen entry. On web the browser prompt does the same. Denied → snackbar with the reason + the field focused.

---

## 6. Motion

All values come from `AppMotion`. Reduced motion: no loops, every `spatial*` snaps, every `effects*` is 120ms.

- **Showcase → branch:** the existing step transition (shared X axis, `spatialDefaultTween`). Back mirrors it.
- **Write autofocus:** `focusRequester.requestFocus()` in a `LaunchedEffect` that waits for `transition.isRunning == false`.
- **Recording:** the dot pulses alpha 1 → 0.4 at 800ms, infinite, only while recording and not under reduced motion. The disc colour crossfades over `effectsFast`.
- **Overlay, ≤ 2.2 s total (11-04 had 2.0; +0.2 s for the brackets):**
  - 0–200ms: photo in (fade + scale 0.94 → 1, `spatialDefault`); brackets snap in (scale 1.08 → 1 + fade, `spatialFast`).
  - 200–1200ms: the band sweeps y 12 % → 62 %, `LinearEasing`. The counter N goes 1 → 6 at each sixth (liveRegion polite announces only the last value, via a debounce, to avoid six announcements).
  - 1200–1400ms: brackets fade; status crossfades to "Your list is ready".
  - 1400–1800ms: the photo shrinks into the ResultCard header thumbnail (shared element via `SharedTransitionLayout` if `@compose-expert` takes it; fallback: crossfade). Rows stagger 40ms.
  - 1800–2200ms: hold, then navigate. The "write not done by the end" rule and the 5 s timeout are as in 11-04.
  - Reduced motion: photo 300ms static → 120ms crossfade → list 600ms → navigate.
- **Starters hide/show on typing:** `expandVertically`/`shrinkVertically` + fade (`spatialFastTween`/`effectsDefaultTween`), as the chips.

---

## 7. Adaptive

| Window | Write | Attach | Talk |
|---|---|---|---|
| Compact | §2.1 | §2.2 | §2.3 |
| Medium (600–839) | Column max 560dp, centred | Same; cards stay 2×2 vertical | Column and plate max 560dp |
| Expanded (≥ 840, web on PC) | Column max 640dp, content block at the 1 : 2 split; chips wrap in one row (A_34) | Max 1040dp; headline `headlineMedium`; grid 5 : 6, photo card left, "Or use your own" + **2×2 horizontal cards** right, top-aligned (A_32) | Column max 560dp, top padding `SpacingXxl`; the plate stays full width (its band), the pill inside is capped at 640dp (A_33) |
| Height < 480 (landscape phone) | No autofocus (the IME would cover everything); single column, scroll | Single column, scroll | Plate stays docked; the feed scrolls |

- **Web:** Enter = send, Shift+Enter = new line (Write and Talk). The mic hold works with the mouse (pointer down / up), and the TalkBack custom actions are also exposed as keyboard: Space toggles the recording. There is no call entry on web (`isCallAvailable` false).

---

## 8. Accessibility

- Touch targets ≥ 48dp:
  - every disc sits in a 48dp `IconButton`
  - source cards are ≥ 96 / 64dp
  - starter bubbles use `minimumInteractiveComponentSize`
  - the Showcase text button gets `heightIn(min = 48dp)`
- Headings: each screen's headline, "Or start from an idea", "Or use your own".
- **Hold gestures are not accessible on their own.** `GistiMicSendButton` exposes custom actions "Start recording" / "Stop and send" / "Cancel recording" (lifted from ChatInputRow's existing `customActions`). The recording state announces `chat_recording_in_progress` once (liveRegion polite).
- Sample photo: `contentDescription = onboarding_sample_image_a11y` (existing), `Role.Image`; the badge text is inside it.
- Source card: one merged node "{title}, {caption}", `Role.Button`.
- Contrast:
  - card text on `AppSurface.card()`: roles from the scheme
  - the badge on the photo uses the card fill + onSurfaceVariant (its own background, not the photo)
  - the error dot is not the only signal (the timer and hint text are there)
  - the dark photo is dimmed, not inverted
- Focus order:
  - Write: back → progress → Skip → headline → subtitle → composer → mic/send → chips
  - Talk: … → greeting → starters → composer → Call → Mic

---

## 9. Strings

All live in `core/designsystem/src/commonMain/composeResources/values{,-ru,-hi}/strings.xml`. Apostrophes and quotes are written literally (`'` in strings.xml is the plain ASCII apostrophe; the prototype used typographic ones for display only). **Rule found in pass 1:** put a U+00A0 before every em dash, so the dash never starts a line (RU greeting at 412dp, A_18 pass 1). HI needs a native-speaker pass.

### New

| Key | EN | RU | HI |
|---|---|---|---|
| `onboarding_showcase_example` | Show me an example | Показать на примере | उदाहरण देखें |
| `onboarding_write_title` | What do you want to get done? | Что нужно сделать? | आपको क्या करना है? |
| `onboarding_write_subtitle` | Type a goal or hold the mic. Gisti breaks it into steps. | Напишите цель или зажмите микрофон — Gisti разложит её по шагам. | लक्ष्य लिखें या माइक दबाकर रखें — Gisti उसे क़दमों में बाँट देगा। |
| `onboarding_write_ideas_title` | Or start from an idea | Или начните с идеи | या किसी आइडिया से शुरू करें |
| `onboarding_write_voice_error` | Gisti couldn't turn your voice note into a list right now. Try the sample or type your goal. | Gisti сейчас не смог превратить голосовое в список. Попробуйте пример или напишите цель. | Gisti अभी आपके वॉइस नोट को सूची में नहीं बदल पाया। नमूना आज़माएँ या अपना लक्ष्य लिखें। |
| `onboarding_write_type_instead` | Type instead | Написать текстом | लिखकर बताएँ |
| `onboarding_mic_denied` | Microphone access is off. You can type your goal instead. | Доступ к микрофону выключен. Можно написать цель текстом. | माइक्रोफ़ोन की अनुमति बंद है। आप लक्ष्य लिखकर बता सकते हैं। |
| `onboarding_voice_message_chip` | Voice message · %1$s | Голосовое · %1$s | वॉइस मैसेज · %1$s |
| `onboarding_attach_title` | Turn a photo or file into a list | Превратите фото или файл в список | फ़ोटो या फ़ाइल को सूची में बदलें |
| `onboarding_attach_own_title` | Or use your own | Или свой файл | या अपनी फ़ाइल |
| `onboarding_source_photo_caption` | Note, whiteboard, receipt | Записка, доска, чек | नोट, बोर्ड, रसीद |
| `onboarding_source_pdf_caption` | Tickets, plans, docs | Билеты, планы, документы | टिकट, प्लान, दस्तावेज़ |
| `onboarding_source_link_caption` | Recipe or article | Рецепт или статья | रेसिपी या लेख |
| `onboarding_source_voice_caption` | Just say it | Просто скажите | बस बोल दें |
| `onboarding_talk_greeting` | Hi! Tell me what's on your plate — I'll turn it into tasks. | Привет! Расскажите, что у вас на уме, — я разложу это по задачам. | नमस्ते! बताइए क्या करना है — मैं उसे कामों में बदल दूँगा। |
| `onboarding_talk_greeting_hint_call` | Type, hold the mic for a voice message, or call me. | Напишите, зажмите микрофон для голосового или позвоните мне. | लिखें, वॉइस मैसेज के लिए माइक दबाकर रखें या मुझे कॉल करें। |
| `onboarding_talk_greeting_hint_chat` | Type or hold the mic for a voice message. | Напишите или зажмите микрофон для голосового. | लिखें या वॉइस मैसेज के लिए माइक दबाकर रखें। |
| `onboarding_talk_starters_title` | Try asking | Например | जैसे |
| `onboarding_talk_starter_1` | Plan my week | Спланируй мою неделю | मेरा हफ़्ता प्लान करो |
| `onboarding_talk_starter_2` | Help me pack for a trip | Помоги собраться в поездку | यात्रा की पैकिंग में मदद करो |
| `onboarding_talk_starter_3` | What can you do? | Что ты умеешь? | तुम क्या कर सकते हो? |
| `onboarding_sample_found` | Found %1$d of %2$d tasks | Нашёл %1$d из %2$d задач | %2$d में से %1$d काम मिले |

### Reused (unchanged)

`onboarding_try_type_placeholder`, `onboarding_try_type_empty_hint`, `onboarding_try_talk_title`, `onboarding_sample_title/_body/_cta/_badge/_image_a11y/_reading/_ready/_list_meta/_checklist_name/_item_1..6`, `onboarding_try_ai_error/_signin/_error_sample/_error_save`, `onboarding_try_generating_slow`, `onboarding_welcome_generating`, `onboarding_welcome_create_error`, `chat_input_placeholder`, `chat_record_voice`, `chat_voice_press_hold_hint`, `chat_voice_drag_cancel_hint`, `chat_voice_release_cancel_hint`, `chat_voice_too_short`, `chat_recording_in_progress`, `chat_recording_failed`, `nav_ask_pill_call_description`, `analyze_source_photo/_pdf/_link_short/_voice`.

### No longer used in A

Delete each after a grep shows zero uses:

- `onboarding_showcase_cta` (B still needs it)
- `onboarding_try_title`
- `onboarding_try_own_file_title`
- `onboarding_try_type_title`
- `onboarding_try_talk_caption_call/_chat/_typed`
- `onboarding_try_chat`, `onboarding_try_call`
- `onboarding_sample_note_*`, after the drawn `SampleNote` is gone. The photos carry that text, so the keys stay as the **source of truth for the photo text** until the owner says otherwise (Q5).

---

## 10. Проверка (one picture pass, step 5b)

Prototype: `prototype.html`. Query params:

- `v` = A | B | C
- `s` = s1 | write | attach | talk | ov1 | ov2 | gen
- `theme`, `lang`, `fs`, `w`, `h`
- `st` = default | typed | kb-typed | error | rec | long | voice
- `kb=1`, `call=0`

Rendered with Playwright Chromium at DPR 2. Every frame below was opened and looked at. Real photos from `assets/` were used (no placeholder needed).

**Frames:**

- All variants (360×800 light EN): `frames/{A,B,C}_01_showcase…`, `_02_write…`, `_03_attach…`, `_04_talk…`, `_05_overlay_reading…`. Contact sheets: `frames/sheet_A.png`, `sheet_B.png`, `sheet_C.png`.
- A only:
  - 06 overlay ready · 07 write+IME · 08 write typed+IME · 09 write AI error · 10 write recording · 11 AI loader voice · 12 talk+IME · 13 talk call off · 14 talk recording
  - 15–20 dark RU 412×915 (showcase, write, attach, talk, overlay reading, overlay ready)
  - 21–24 RU fontScale 2.0 (write, attach, talk, showcase)
  - 25 attach RU 320×640 · 26 talk+IME RU 320×640 · 27 write+IME RU 320×640 × 1.5
  - 28–29 HI (attach, talk) · 30 write with a 4-line goal · 31 overlay RU 320×640
  - 32–34 expanded 1280×800 (attach, talk dark, write)

**Found and fixed in this spec after pass 1:**

1. The accent orphan "a" on Showcase came back (NBSP lost in the prototype). Fixed by the existing NBSP rule (A_01).
2. RU greeting: the em dash started a line. New rule: NBSP before every em dash (§9).
3. Recording disc grew 36 → 48dp and jumped the card by 16dp. Fixed: same size, colour change only.
4. At 320×640 × 1.5 with the IME, the composer's toolbar sat under the keyboard. Fixed: `BringIntoView` of the whole card (A_27).
5. At 320×640 with the IME, the Talk starters were hidden under the composer. Fixed: scroll to the end on IME open (A_26).
6. RU × 2.0 Attach: the CTA fell below the first screen. Fixed: fontScale ladder (tight crop + no body) (A_22).
7. RU × 2.0 Talk: the greeting bubble was 9 lines. Fixed: hint line hidden above 1.3 (A_23).
8. Showcase docked "Show me an example" wrapped to 2 lines at 2.0. Fixed: inline text button, no dock (A_24).
9. ResultCard thumbnail showed the desk, not the note. Fixed: `Text` source crop (A_06).
10. A Write demo used to fill the empty band read as a second, fake input. Removed (§2.1).
11. Expanded Attach: vertical source cards left big empty boxes. Fixed: horizontal cards on expanded (A_32).

**Checked and fine:**

- The photo band shows all seven lines legibly at 320–412dp in EN/RU/HI.
- The dark photo reads well with the 0.9 dim.
- Source cards stay 2×2 in EN/RU/HI at 1.0.
- The Talk plate matches the chat composer (compare `ChatInputCallScreenshotTest.call_360_light.png`).

**Not verified by picture:**

- the real `GistiPresenceOrb`
- the animation timing
- the hold gesture
- the HI handwriting accuracy

These belong to `@compose-expert`'s cycle.

## 11. Эталон (raster)

`frames/sheet_A.png` (overview) plus `A_01`, `A_02`, `A_03`, `A_04`, `A_05`, `A_06`, `A_07`, `A_09`, `A_17`, `A_32`. HTML is not the reference; these PNGs are. (The `frames/` directory is gitignored and lives locally only.)

## 12. Screenshot matrix for `@compose-expert`

- **Sizes:** 320×640, 360×800, 412×915, 600×960, 1280×800, landscape 800×360.
- **Themes:** light, dark.
- **fontScale:** 1.0, 1.3, 1.5, 2.0.
- **Locales:** EN, RU, HI.
- **States:** §4 table, every row; overlay reading / ready.
- **Edge data:**
  - a 6-line goal in the composer (`maxLines 6`) plus the error box
  - RU "Сборы в поездку" chip
  - starter 2 in RU at 2.0 (wraps)
  - the HI greeting with the call hint
  - Attach at 320 × 2.0 RU (1-column cards)
  - Talk with the IME at 320×640
  - a 0:59 recording timer in RU ("Смахните вверх для отмены" next to the timer at 320)
  - a 60-character plain-list name
- **Regression:** `ChatInputCallScreenshotTest` and `SourceRowScreenshotTest` byte-identical after the moves.

## 13. Handoff

- **Order:**
  1. `@feature-expert` `Mode: SPEC`: contract delta §5, matrix for the three screens, `ShellLaunch` additions, the recorder dependency.
  2. `@compose-expert`: designsystem moves first (`ChatInputRow` → designsystem, `GistiMicSendButton` extraction, `SourceCardGrid`, `SamplePhoto` + assets), then the screens and the PNG matrix.
  3. `@feature-expert` `Mode: IMPLEMENT`.

  The `@android-platform-expert` hop is needed only if the drawable language qualifier misbehaves on wasm.
- **Files:**
  - `feature/onboarding/.../welcome/` (`WelcomeOnboardingScreen.kt`, `OnboardingContract.kt`, `components/{ShowcaseTiles,TryItBlocks,SampleNote,OnboardingOverlays}.kt`; split `TryItBlocks` into `WriteStep`, `AttachStep`, `TalkStep`)
  - `core/designsystem/.../components/gisti/{ChatInputRow (moved), GistiMicSendButton (new)}.kt`
  - `core/designsystem/.../components/SourceRow.kt`
  - `core/designsystem/src/commonMain/composeResources/drawable{,-ru,-hi}/onboarding_sample_note.webp`
  - the three `strings.xml`
- **Reuse:**
  - `GistiPromptChips`, `AiErrorBox`, `AiGeneratingLoader`, `GistiPresenceOrb`, `GistiCallButton`
  - `ChatInputRow` (moved), `sourceDoorEntries`
  - `AppButton`, `AppButtonText`, `groupSegment`
  - `AppSurface.card/recessed/bottomChrome`, `AppChatColors.*`, `GistiColors.palette*`
- **Pitfalls:**
  1. Moving `ChatInputRow` must not change the chat. Keep the defaults and re-run its screenshot tests before touching onboarding.
  2. Crop with `BitmapPainter(srcOffset, srcSize)`. A `ContentScale.Crop` + alignment crop drifts between a 2:3 source and a 4:3 box on different widths and lands on the desk.
  3. Write's autofocus must wait for the step transition. Focus during the slide makes the IME resize the window mid-animation, which costs frames on the wasm canvas.

## 14. Open questions for the owner

- **Q1. Voice message in Talk.** A held mic records a clip and hands it to the chat, which transcribes and answers. This needs a new `ShellLaunch.OpenChat(voiceClip)` path into the chat's voice pipeline (`@feature-expert`). If that is too big for this release, the fallback is no mic on Talk: Call carries voice where available, and Write keeps its in-place voice → list. Recommended: build it; it is the owner's explicit ask.
- **Q2. Talk send and starters spend AI credits immediately.** Before, the onboarding only prefilled the chat, so the user confirmed there. Now the user presses Send on our screen, and a second confirm would be the defect. Recommended: send. The owner should confirm, because of the small Free lifetime wallet.
- **Q3. "Show me an example" on Showcase** runs the sample straight from step 1 and never shows step 2. Recommended: yes. It is the one tap to a real result for a person who will not choose.
- **Q4. Write voice in place** (recorder in onboarding, `AnalyzeInputData.Audio`) vs handing off to the Analyze voice screen. In place keeps the user in onboarding and gives the same loader. Hand-off needs no new dependency. Recommended: in place.
- **Q5.** Keep `onboarding_sample_note_*` as the documented text of the photos, or delete them with the drawn note?

---

## AI loader v2 (2026-11-17)

Supersedes §2.5 ("the existing `AiGeneratingLoader` stays as it is") for the typed goal from Write. The voice chip of §2.5 is already gone (owner decision Q1: voice is dictation into the field, so the loader only ever shows typed text).

**Owner request, in short:** the loader is a blue circle that just hangs there and looks scary. Remove the circle and show nice changing steps like "gathering ideas…", "adding the magic…" while the list is made.

### L2.1 What changes

- **Removed:** `GistiPresenceOrb` in the loader, the `LoaderOrb` 96dp size, the quote-above-status order. `GistiPresenceOrb` stays everywhere else (Showcase, Talk, voice call).
- **Added:** a card of four steps with round checks, like the app's checklist rows. The steps run on a UI timer and tick off one by one. The last step stays in progress until the result arrives.
- **Unchanged:**
  - `OnboardingPhase.AiGenerating(prompt, isSlow)`, and the VM's 8 s `isSlow` flip
  - success: straight to the checklist detail
  - failure: back to Write with `AiErrorBox`
  - non-dismissible
  - the loader also shows during `Leaving(Typed)`

  **No contract change for `@feature-expert`.** The step clock is UI-local state.

### L2.2 Honesty rules (binding)

1. The server reports no progress, so the steps are a description of the work, not a measurement. No percentages, no "N of 4", no bar that fills.
2. **Step 4 never completes on a timer.** It stays in progress until the phase leaves `AiGenerating` / `Leaving(Typed)`. The person never sees "all done" while we still wait.
3. When the result arrives early (say, during step 2), the remaining steps are **not** ticked. Navigation lands on the real list, and that list is the payoff (see Q-L1 for an optional finish beat).
4. No haptics on the timer ticks: a buzz would sell a fake event as a real one.
5. The copy describes what the model does with a goal (reads it, gathers ideas, orders steps). It does not promise speed ("almost there", "a few seconds") or quality.

### L2.3 Structure (recommended variant)

```
AiGeneratingLoader(prompt, isSlow)                        background, statusBars + navigationBars padding
└─ BoxWithConstraints → verticalScroll Column, heightIn(min = viewport), widthIn(max = OverlayMaxWidth 560dp)
   padding horizontal ScreenPaddingHorizontal (16), vertical SpacingXl (24); Arrangement.Center, CenterHorizontally
   ├─ Text onboarding_welcome_generating     headlineSmall, onSurface, Center, heading()
   ├─ SpacingSm (8)
   ├─ Text onboarding_try_generating_prompt  bodyLarge, onSurfaceVariant, Center,
   │                                         maxLines 3 (fontScale ≥ 1.5 → 2), Ellipsis
   ├─ SpacingXl (24)                         section break: heading block → steps
   ├─ LoaderSteps card                       fillMaxWidth, widthIn(max = 400dp) (no token, see Tokens),
   │                                         groupSegment(GroupPosition.Single), padding vertical SpacingSm (8)
   │   └─ 4 × StepRow  (compact mode: only the active row, §L2.7)
   │        Row(verticalAlignment = Top), padding horizontal GroupRowPaddingHorizontal (16),
   │        vertical SpacingMd (12), min height 48dp
   │        ├─ glyph box: width RoundCheckboxBox (24), height = one bodyLarge line (24sp → dp, grows with fontScale),
   │        │   glyph centred → the glyph lines up with the FIRST text line when the label wraps
   │        ├─ GroupRowGap (12)
   │        └─ Text step label, bodyLarge, weight(1f)
   └─ slow caption (only when slow)          SpacingMd (12) above; bodyMedium, onSurfaceVariant, Center
```

No dividers between the steps: with hairlines the card reads as the finished checklist, and the steps are not its items.

### L2.4 Components and tokens

| Element | Component | Tokens | States |
|---|---|---|---|
| Headline | `Text` | `headlineSmall`, `onSurface` | static |
| Quote | `Text` (existing `onboarding_try_generating_prompt`) | `bodyLarge`, `onSurfaceVariant` | 3 / 2 lines |
| Steps card | `Modifier.groupSegment(GroupPosition.Single)` (as `ResultCard`) | `AppSurface.card()` fill (inside groupSegment), `AppShapeTokens.Group` 24dp | — |
| Done glyph | `AppRoundCheckbox(checked = true)` | `primary` fill, `onPrimary` check | ring → fill crossfades on its own `effectsDefaultAs()` |
| Active glyph | private `LoaderStepGlyph`: M3 `CircularProgressIndicator` (indeterminate), size `AppDensity.RoundCheckboxSize` 22dp, `strokeWidth` 2dp, `StrokeCap.Round`. No designsystem wrapper exists for an indeterminate ring; `AppProgressRing` is determinate. | color `primary`, trackColor `outlineVariant` | reduced motion → static ring (§L2.6) |
| Pending glyph | `AppRoundCheckbox(checked = false)` | 2dp `outline` ring | — |
| Done label | `Text` | `bodyLarge`, `onSurfaceVariant` | — |
| Active label | `Text` | `bodyLarge` + `FontWeight.SemiBold` (no token; nearest `titleMedium`, which also changes the line height and would jump the row), `onSurface` | — |
| Pending label | `Text` | `bodyLarge`, `onSurfaceVariant` | — |
| Slow caption | `Text` | `bodyMedium`, `onSurfaceVariant` | 3 lines over time (§L2.5) |

- **No-token values:**
  - card max width 400dp (`LoaderCardMaxWidth`, private const next to `OverlayMaxWidth`). Without the cap, a 528dp card on a tablet holds 20-character lines with half the card empty.
  - spinner stroke 2dp (the same as `AppRoundCheckbox`'s ring stroke)
- **Contrast** (computed from `Color.kt` values):

  | Pair | Light | Dark |
  |---|---|---|
  | `onSurfaceVariant` on card | 7.26 : 1 | 9.03 : 1 |
  | `onSurface` on card | 15.79 : 1 | 13.48 : 1 |
  | `outline` ring on card | 4.67 : 1 | 4.63 : 1 |
  | `primary` arc / fill on card | 5.66 : 1 | 9.38 : 1 |
  | `onPrimary` check on `primary` | — | 9.83 : 1 |
  | `onSurfaceVariant` caption on page | 6.44 : 1 | 10.11 : 1 |

  The `outlineVariant` spinner track is decorative (1.3 : 1). The moving `primary` arc carries the state, and so does the text weight.

### L2.5 Motion and timeline

The clock starts when the loader first composes (`LaunchedEffect(Unit)` + `delay`, the frame clock). It is UI-local, and it keeps running through `Leaving(Typed)`: the same call site, so `remember` survives.

| t (from Send) | What changes |
|---|---|
| 0 ms | Block fades in, `effectsDefaultTween` (220ms); step rows stagger 40ms each, fade only. Step 1 active. |
| 1200 ms | Step 1 → done, step 2 → active |
| 2600 ms | Step 2 → done, step 3 → active |
| 4200 ms | Step 3 → done, step 4 → active. **Stays active until the result.** |
| 8 s (VM `isSlow`) | Caption 1 `onboarding_try_generating_slow` fades in (`effectsDefaultTween`) |
| 16 s | Caption crossfades to caption 2 `onboarding_ai_slow_longer` |
| 26 s | Caption crossfades to caption 3 `onboarding_ai_slow_thanks` and stays |

- **One tick:**
  - the done glyph swaps from spinner to `AppRoundCheckbox(checked = true)`, crossfade `effectsFastTween`; the checkbox's own colour animation does the fill
  - the label weight and colour change at once
  - the next row's ring fades into the spinner, `effectsFastTween`
  - nothing moves position: rows have fixed slots
- **Long wait:** after 4.2 s the steps are done moving, so the spinner on step 4 is what shows the work is alive. The captions at 8 / 16 / 26 s refresh the screen so a long wait does not read as frozen. The captions do not loop: a repeating "Thanks for your patience" would read as a bot.
- **`isSlow` is sticky in the UI** (`var slowSeen`). `Leaving(Typed)` passes `isSlow = false` today, so without that the caption would vanish for the last frame before navigation.

### L2.6 Reduced motion (`LocalReducedMotion`)

- **No infinite animation.** The `CircularProgressIndicator` is not composed at all. The active glyph is a static 22dp ring with a 2dp `primary` border (an `AppRoundCheckbox`-sized ring in `primary` instead of `outline`), and the label is still SemiBold `onSurface`.
  - Rejected: a centre dot inside that ring, because it read as a radio button (L2_08 pass 1).
- **The ticks still happen** at the same times. They are state, not decoration, and they are the only thing that changes on the screen. Every crossfade is `tween(AppMotion.ReducedMotionMillis)` (120ms), with no stagger.
- The slow captions stay; they are the change on a long wait.

### L2.7 Adaptive: compact mode by measured fit

At 320×640 × 2.0 the full card is 834dp against a 592dp viewport, so the **active row sits below the fold**, and the visible part of the screen shows only ticked rows, which is the "all done, still waiting" lie (L2_04b, rejected).

- **Rule:** measure the full layout (headline + quote + 4 rows + the tallest slow caption) at the viewport width with unbounded height. If it exceeds `maxHeight`, use **compact** for the whole loader session. The decision is sticky, so it does not flip when a caption appears or the phase moves to `Leaving`.
  - Implement with a `SubcomposeLayout` or a measure-only pass. A font-scale threshold does not work. Measured (worst case: RU long prompt + caption 3):
    - 320×640 fails at 1.5
    - 360×640 fails at 1.8
    - 360×800 RU fails at 2.0
    - 412×915 fits at 2.0
- **Compact:**
  - the card shows **only the active row**
  - a step change crossfades that row's content, `AnimatedContent` with fade `effectsDefaultTween`; reduced motion → 120ms fade
  - the quote keeps 2 lines; at 1 line "«Собраться на…» lost its meaning (pass 1)
  - fits every measured case with room: at most 486dp at 320×640 × 2.0
- **Landscape phone** (800×360): falls under the same rule and goes compact.
- **Medium / expanded:** the column is capped at 560dp, the card at 400dp, and the block is vertically centred. Nothing else changes.

### L2.8 Accessibility

- **Announced on entry, once:** the headline ("Creating your checklist…"), via `liveRegion = Polite` on the headline. Its text never changes, so it speaks once. `heading()` too.
- **Not announced: the ticks.**
  - The steps card is ONE node: `clearAndSetSemantics { contentDescription = <active step label>; progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate }`, with no live region.
  - Swiping to it reads e.g. "Gathering ideas, in progress". The step changes are not pushed.
  - If the person keeps focus on the card, TalkBack may re-read the new label at most three times in 4 s. That is acceptable, and it is the only path.
  - The glyphs carry no semantics.
- **Announced: slow caption 1 only** (`liveRegion = Polite`, at 8 s). Captions 2 and 3 are not live regions: an announcement every 10 s while the person waits is noise.
- **Success:** no announcement here. The checklist detail announces its own title on arrival.
- **Error:** the existing `AiErrorBox` on Write.
- **Interactive elements:** none on the loader. Back behaviour is unchanged.
- **Large text:** see §L2.7 (compact by measured fit).
  - RU headline: U+2011 (non-breaking hyphen) in "чек‑лист". At 2.0 it otherwise split "чек-/лист…".
  - HI headline: U+00A0 before "है…". At 1.5 it otherwise left "है…" alone on line 2.
- **Contrast:** see §L2.4. Every text pair is ≥ 6.4 : 1 in both themes, and the rings are ≥ 4.6 : 1.

### L2.9 States

| State | What is visible |
|---|---|
| loading 0–4.2 s | headline, quote, card with 1–3 done / 1 active / the rest pending |
| loading > 4.2 s | steps 1–3 done, step 4 active (spinner) |
| slow ≥ 8 s | + caption 1 → 2 (16 s) → 3 (26 s) |
| success | navigation to the checklist (unchanged); remaining steps are not ticked |
| error | Write with `AiErrorBox` (unchanged); a retry starts the loader from step 1 |
| empty / disabled | n/a (Send with an empty field never opens the loader, §4) |
| retry of an already-created list (I6) | navigates at once, as today; the loader may show for a frame |

### L2.10 Strings

Every string goes in `core/designsystem/src/commonMain/composeResources/values{,-ru,-hi}/strings.xml`. Apostrophes are literal.

| Key | EN | RU | HI |
|---|---|---|---|
| `onboarding_ai_step_read` (new) | Reading your goal | Читаю вашу цель | आपका लक्ष्य पढ़ रहा हूँ |
| `onboarding_ai_step_ideas` (new) | Gathering ideas | Собираю идеи | आइडिया जुटा रहा हूँ |
| `onboarding_ai_step_order` (new) | Putting steps in order | Расставляю шаги по порядку | क़दमों को क्रम में लगा रहा हूँ |
| `onboarding_ai_step_magic` (new) | Adding a little magic | Добавляю немного магии | थोड़ा जादू जोड़ रहा हूँ |
| `onboarding_try_generating_slow` (reused) | Still working on it… | Ещё работаю… | अभी काम चल रहा है… |
| `onboarding_ai_slow_longer` (new) | Some lists take a bit longer | Бывает, нужно чуть больше времени | कुछ सूचियों में थोड़ा ज़्यादा समय लगता है |
| `onboarding_ai_slow_thanks` (new) | Thanks for your patience | Спасибо, что ждёте | इंतज़ार के लिए शुक्रिया |
| `onboarding_welcome_generating` (edit RU, HI) | unchanged | Создаём чек‑лист… (U+2011) | आपकी चेकलिस्ट बन रही है… (U+00A0 before है) |

- **Lengths:** EN step lines are 15–22 characters (limit about 28).
- **Voice:** RU is first person, like the existing "Ещё работаю…" and "Нашёл…". The headline stays "Создаём", as shipped.
- **Native HI review needed for all six new HI lines.** The riskiest are `step_order` (word choice for "order"), `step_ideas` ("आइडिया" is a loanword) and `slow_longer` (length and register). Gisti is masculine, as in the existing "दूँगा".
- `onboarding_welcome_generating` and `onboarding_try_generating_slow` are used only by this loader (grep 2026-11-17), so editing them changes nothing else.

### L2.11 Alternatives (not recommended)

- **B. One rotating line under `AppLinearProgressIndicator` (indeterminate).** It is calmer and smaller, but a single changing line reads as a text slideshow and previews nothing of the list.
- **C. A skeleton of the coming checklist** (`AppSkeletonLine` rows with round-check placeholders and a shimmer). This is the most literal preview, but it promises a row count we do not know, and a shimmer is again a "loading screen".

### L2.12 Проверка (one picture pass)

The prototype was a throwaway HTML page in the session scratchpad, deleted after the pass. Px = dp and rem = sp. It was rendered with Playwright Chromium at DPR 2, and **every frame below was opened and looked at**. Its colours are copied from `Color.kt`.

**Frames** (`frames/`, gitignored):

- **Steps:**
  - `L2_01_step2_light_en_360x800`: step 2 active
  - `L2_02_step4_light_en_360x800`: last step waiting
  - `L2_03_step3_dark_ru_412x915`
  - `L2_07_step2_light_hi_360x800`
- **Large text and compact:**
  - `L2_04_step4_light_ru_320x640_fs20` (compact)
  - `L2_04b_..._FULL_rejected` (the full card at 2.0: active row below the fold)
  - `L2_11_step2_compact_dark_en_320x640_fs20`
- **Slow state:**
  - `L2_05_slow1_long_dark_ru_360x800_fs15`: the old frame 66 case
  - `L2_06_slow2_light_en_360x800`
  - `L2_09_slow3_long_light_ru_320x640_fs20`
  - `L2_10_slow2_dark_hi_360x800_fs15`
- **Reduced motion:** `L2_08_reducedmotion_step4_light_en_360x800`
- **Contact sheet:** `L2_sheet.png`

**Found and fixed in this addendum:**

1. 320×640 × 2.0: the active row was below the fold, so the screen showed only ticked rows (L2_04b). Fixed by compact mode by measured fit (§L2.7). The fit was measured over 4 sizes × 5 font scales × 3 languages: a fixed font-scale threshold failed somewhere at every value.
2. RU headline split at the hyphen "чек-/лист…" at 2.0. Fixed with U+2011.
3. HI headline left "है…" alone on line 2 at 1.5. Fixed with U+00A0.
4. A wrapped step label had its glyph centred against 2–3 lines. The glyph now sits on the first line (glyph box = one line high, row aligned Top).
5. The reduced-motion glyph with a centre dot read as a radio button. Now a plain `primary` ring.
6. The compact quote at 1 line lost its meaning ("«Собраться на…»"). Now 2 lines; it still fits.

**Not verified by picture:**

- the real `CircularProgressIndicator` rendering at 22dp/2dp
- the timings
- TalkBack re-reading a focused node whose label changes
- wasm frame cost of the spinner

These belong to `@compose-expert`'s cycle; the last two need a device or `:9090`.

### L2.13 Эталон

`frames/L2_01`, `L2_02`, `L2_03`, `L2_04`, `L2_05`, `L2_08`, plus `L2_sheet.png` for the overview. HTML is not the reference; these PNGs are.

### L2.14 Screenshot matrix for `@compose-expert`

- **Frozen clock:** add an internal `elapsedMsOverride: Long?` (preview and test only) so a snapshot can pin step 1–4 and caption 1–3.
- **Cases:**
  - step 1, 2, 4 at 360×800 light EN / dark RU
  - slow captions 1 / 2 / 3
  - 320×640 at 1.0 / 1.5 / 2.0 in RU (1.5 and 2.0 must be compact)
  - landscape 800×360 (compact)
  - 600×960 (card capped at 400dp)
  - reduced motion step 4
  - HI at 1.5 (headline NBSP)
- **Edge data:**
  - the 4-line RU moving goal (quote ellipsis, 2 lines at ≥ 1.5)
  - a one-word goal
  - a goal with no spaces (`AAAAAAAA…`, the ellipsis must hold)
- **Baselines to update:** `onboarding_65_aiGenerating_*` and `onboarding_66_aiGeneratingSlowLongPrompt_*`. The orb is intentionally gone from both.

### L2.15 Handoff

- **To:** `@compose-expert` only. No ViewModel or contract change.
- **Files:**
  - `feature/onboarding/.../welcome/OnboardingOverlays.kt` (`AiGeneratingLoader` + private `LoaderSteps`, `StepRow`, `LoaderStepGlyph`; drop `LoaderOrb` and the `GistiPresenceOrb` import if unused)
  - the three `strings.xml`
  - the preview fixtures
- **Reuse:** `groupSegment(GroupPosition.Single)`, `AppRoundCheckbox`, `AppDensity.RoundCheckboxBox/RoundCheckboxSize/GroupRowPaddingHorizontal/GroupRowGap`, `AppMotion.*Tween`, `LocalReducedMotion`. Follow the `ResultCard` pattern in the same file.
- **Order:**
  1. strings
  2. the full card with the frozen clock + snapshots
  3. the compact measure
  4. the live clock, sticky `isSlow`
  5. the reduced-motion branch
- **Pitfalls:**
  1. The clock must not restart on recomposition or on the `AiGenerating` → `Leaving(Typed)` switch. Key the `LaunchedEffect` on `Unit` in the loader, not on `phase`.
  2. The compact decision is measured once and is sticky. Re-deciding per frame makes the card jump when the slow caption appears.
  3. Do not compose `CircularProgressIndicator` under reduced motion. Hiding it with alpha keeps the infinite transition running, which costs frames on the wasm canvas.

### L2.16 Open question for the owner

- **Q-L1. Finish beat.** When the result arrives, tick the remaining steps quickly (≤ 400ms, reduced motion 120ms) and then navigate, so the screen "lands" before the list. It is honest, because the work is really done. It needs a contract change: `finish` must wait for the loader's `onFinished`, like `SampleAnalyzingOverlay`. Every success is ~0.4 s slower.

  Recommended: **no** for now. The list itself is the payoff, and the loader stays a pure-UI change.
