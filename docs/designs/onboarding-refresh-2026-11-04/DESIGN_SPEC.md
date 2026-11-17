# DESIGN_SPEC — Onboarding refresh (Showcase → Try it)

> Проектная спека, написана до кода: имена компонентов, событий и ключей, тайминги анимаций и проценты (альфа цветов) — план дизайна, а не замер (не проверено — реализация могла назвать иначе; источник истины — код `feature/onboarding/.../welcome/`).

- **Platform:** Android + Web (wasmJs), Compose Multiplatform, Material 3, redesign B "Soft & tactile"
- **Method:** native spec + throwaway HTML prototype (no Claude Design canvas; the prototype lives next to this file, gitignored)
- **Screens:** `Showcase` (step 1/2) → `Try it` (step 2/2) → finish in v2 Inbox / checklist detail / Analyze / chat / call
- **Replaces:** the four `ai_welcome` steps (`Welcome`, `Capture`, `Value`, `FirstChecklist`) in `WelcomeOnboardingScreen.kt`. The `slides` and `interactive` arms are deleted (owner decision 2026-11-04).
- **Task doc:** `docs/active/onboarding-refresh-2026-11-04.md`

## 0. Structure verdict

The chosen 2-screen structure stands. One refinement, already built into this spec: **tapping a Showcase tile does not reorder the Try-it screen.** The block order on Try-it is fixed (sample → own file → type → talk). The chosen mode only scrolls its block into view, rings it for 1.2 s, and (Write) focuses the field. A reordered screen per mode means four layouts to build, snapshot and keep in sync. It also means the person who comes back with ← sees the blocks in a different place than before. The fixed order costs nothing, because the screen fits one 360×800 window in EN (prototype frame 07).

Second refinement: **Try-it has no bottom "Create my first list" button.** The hero "Try with this note" button is the obvious way forward, and it always produces a real list. A button that does something with an empty field is exactly the pattern that sent most people to a tips-only list before. Skip stays in the top bar for people who just want in.

---

## 1. Structure

### 1.1 Shared chrome (both steps)

```
Column(fillMaxSize, background = colorScheme.background, statusBarsPadding, navigationBarsPadding, imePadding)
├─ OnboardingTopBar — Row(height 48dp, horizontal padding SpacingXs, CenterVertically)
│   ├─ start slot 48dp: IconButton(ArrowBack) on step 2, Spacer(48dp) on step 1
│   ├─ Box(weight 1f, contentAlignment = Center): StepProgress (2 segments)
│   └─ end slot (wrap): SkipButton  — "Skip" + chevron, minimumInteractiveComponentSize
├─ AnimatedContent(step) weight 1f
└─ (step 1 only, compact/medium) bottom dock with the CTA
```

- **StepProgress:** two 24×4dp segments, gap 6dp, `AppShapes.extraSmall` corners. Active = `primary`; inactive = `outline` at 28 % alpha. The whole row is one node with `contentDescription = "Step 1 of 2"` (`onboarding_progress_a11y`). It replaces the full-width 4-segment bar (`WelcomeProgressBar`, `WelcomeOnboardingScreen.kt:365`) and frees one 16dp row.
- **Progress in the centre slot is weighted, not absolutely centred.** At RU × fontScale 2.0 "Пропустить" takes ~170dp, and an absolutely centred progress would touch it (frame 21).
- **SkipButton:** today's Skip row is 36dp tall (`padding(SpacingSm)` around `bodyMedium`, `WelcomeOnboardingScreen.kt:339-357`). It must get `minimumInteractiveComponentSize()`. Visible on both steps (today it is hidden on the last step; brief: Skip visible on every screen).

### 1.2 Step 1 — Showcase

```
Column(fillMaxSize)
├─ OnboardingTopBar(step = 1)
├─ Column(weight 1f, verticalScroll)        ← scroll only when content > viewport
│   ├─ Spacer(weight 1f)   ┐ free space split 1 : 2 (above : below) when the content fits;
│   ├─ content (max width) │ when it does not fit both spacers are 0 and the column scrolls.
│   │   ├─ TwoToneHeadline (headlineLarge, start-aligned, heading())
│   │   ├─ 8dp
│   │   ├─ Subtitle (bodyLarge, onSurfaceVariant)
│   │   ├─ 24dp
│   │   └─ ShowcaseTiles: one grouped container, 3 segments (Write, Attach, Talk)
│   └─ Spacer(weight 2f)   ┘
└─ Dock: padding(start/end 16dp, top 12dp, bottom 16dp) → AppButton("Try it now", fillMaxWidth)
```

Implement the 1 : 2 split with `Modifier.verticalScroll` on a `Column` whose child has `heightIn(min = viewportHeight)`, `BoxWithConstraints` or a custom `Layout`. **Not** `Arrangement.Center`. Centring put the headline too low on 412×915, and pinning to the top left a dead band above the CTA (first prototype pass, frame 01 before the fix).

**ShowcaseTile segment** (`Row`, `CenterVertically`, padding start 12 / top 12 / bottom 12 / end 8dp, gap 12dp, `minHeight 88dp`):

```
[DemoStage 120×96dp | or IconDisc 40dp]  [Column(weight 1f): title titleMedium · caption bodyMedium onSurfaceVariant]  [chevron 24dp onSurfaceVariant]
```

| Tile | Title | Caption | Stage tint (palette slot) | Disc icon (fontScale ladder) |
|---|---|---|---|---|
| Write | `onboarding_showcase_write_title` | `…_write_caption` | 1 blue | `Icons.Outlined.EditNote` |
| Attach | `…_attach_title` | `…_attach_caption` | 2 peach (= Photo pill tint, `SourceRow.kt:714`) | `Icons.Outlined.PhotoCamera` |
| Talk | `…_talk_title` | `…_talk_caption_call` / `…_talk_caption_chat` (call off) | 3 lilac | `Icons.Outlined.Forum` |

### 1.3 Step 1 demo stages (decorative, fontScale locked to 1.0)

Each stage: `Box(size 120×96dp, clip AppShapes.medium, background GistiColors.paletteTint(n))`. Wrap its content in `CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 1f))` and `clearAndSetSemantics {}`. The tile's title and caption carry the meaning, and a mini-UI that grows with fontScale only overflows its 96dp box.

- **Write:** mini input pill at top (inset 8dp, height 22dp, `AppShapeTokens.Pill`, fill `AppChatColors.raised()`, 1dp `AppChatColors.controlOutline()`, text 10.5sp `onSurface` = `onboarding_demo_write_prompt`, trailing 16dp `primary` disc with ArrowUpward 11dp `onPrimary`). Mini list card at the bottom (inset 8dp, `AppSurface.card()`, `AppShapes.small`, padding 4/6dp): 3 rows of 16dp, each a 9dp ring (`outline`, 1.2dp) + 10sp text `onboarding_demo_write_item_1..3`.
- **Attach:** the sample note (§3) at 46×60dp, rotated −4°, left 8 / top 10. Then ArrowForward 16dp `paletteContent(2)`. Then a mini list card (left 68, right 6, top 14) with 4 rows: ring + `AppSkeletonLine`-style bar (5dp, `onSurfaceVariant` 35 %), widths 22/16/20/14dp.
- **Talk:** user bubble at top end (`AppChatColors.userBubble()` / `onUserBubble()`, 10.5sp `onboarding_demo_talk_user`, corner 12dp with the `AppShapeTokens.ChatBubbleTail` bottom-end). Bottom start: `GistiPresenceOrb(mode = Speak, level = { 0f }, size = 18.dp)`, then the AI bubble (`AppChatColors.raised()` + `contentOutline()`, text `onboarding_demo_talk_reply` + Check icon 11dp `GistiColors.success`). This is the call screen's own presence mark, so the tile reads as "this is Gisti talking". No ✓ glyph in the string: U+2713 is not guaranteed in the Skiko fallback fonts on wasm.
- **Expanded layout:** the stage fills the tile width at 5 : 4. Draw the same 120×96 composition with `graphicsLayer { scaleX = scaleY = width / 120dp }` (one composition, no second layout to maintain).

### 1.4 Step 2 — Try it

```
Column(fillMaxSize)
├─ OnboardingTopBar(step = 2)
└─ Column(weight 1f, verticalScroll(state), padding(horizontal 16dp, top 8dp, bottom 16dp), max width)
    ├─ Headline "Make your first list" (headlineSmall, heading())
    ├─ 16dp
    ├─ A. SampleCard        (groupSegment(Single), padding 16dp)
    │    ├─ Row(gap 12dp, CenterVertically)            ← Column (thumb above text) when fontScale > 1.3
    │    │   ├─ SampleNoteThumb 88×104dp (recessed tile, AppShapes.medium; note 70×88dp rotated −3°; "Sample" badge)
    │    │   └─ Column(weight 1f): title titleMedium "No file at hand?" · body bodyMedium onSurfaceVariant
    │    ├─ 12dp
    │    └─ AppButton("Try with this note", icon = Icons.Filled.AutoAwesome, fillMaxWidth)   ← the ONLY filled button on the screen
    ├─ 24dp
    ├─ B. SourceRowSection(title = "Use your own file", style = Page)  ← existing component, unchanged
    ├─ 24dp
    ├─ C. TypeBlock
    │    ├─ titleSmall "Type what's on your mind" (heading()), 8dp
    │    ├─ AppTextField(singleLine = false, maxLines = 4, placeholder, trailingIcon = SendButton, imeAction = Send)
    │    ├─ [AiErrorBox]  — only in the AI-failure state (§2)
    │    ├─ [empty-send hint as supportingText] — only after a send tap on an empty field
    │    └─ StarterChips: GistiPromptChips(WelcomeStarterTemplate chips), 4dp above; hidden while the field has text
    ├─ 24dp
    └─ D. TalkCard        (groupSegment(Single), padding 16dp)
         ├─ Row(gap 12dp, Top): GistiPresenceOrb(Speak, level {0f}, size 40dp → node 52dp, offset −6dp so the disc aligns to the text) · Column(title titleMedium, caption bodyMedium onSurfaceVariant)
         ├─ 12dp
         └─ Buttons: [Chat] [Call] — AppButtonSecondary ×2, gap 8dp; measured stack (see §6)
              Chat icon Icons.Outlined.ChatBubbleOutline; Call icon Icons.Filled.GraphicEq
              (the SAME glyph as the Ask-pill call button, AskGistiBar.kt:307, so the user recognises it later)
              Call absent → a single full-width Chat button.
```

**SendButton** (trailing slot of the field, 48dp target): a 36dp disc. With text: `primary` fill + ArrowUpward `onPrimary`. Empty: `AppSurface.recessed()` fill + `onSurfaceVariant` icon at 60 % alpha. It **stays clickable when empty** (rule "every tap answers"). An empty tap focuses the field and shows `onboarding_try_type_empty_hint` as the field's supporting text. That text clears on the next keystroke.

**StarterChips:** reuse `GistiPromptChips` (`GistiPromptChips.kt:164`). `WelcomeStarterTemplate` is already an enum. Emoji per chip: GROCERIES 🛒, TRIP 🧳, WORKOUT 🏋️, TODO ✅. The row bleeds edge to edge: place it **without** the column's horizontal padding (the component applies `ScreenPaddingHorizontal` as `contentPadding`). This replaces the private `StarterChipsRow` / `StarterChip` and their layout-modifier bleed hack (`WelcomeOnboardingScreen.kt:894-972`). **A chip tap now creates and opens the preset list in one tap.** Today a tap selects and a second CTA tap creates; the CTA is gone.

### 1.5 Sample analysing overlay (replaces the whole onboarding chrome, like `GeneratingChecklistLoader`)

```
Box(fillMaxSize, background, statusBarsPadding, navigationBarsPadding)
 phase "reading":  Column(Center): SampleNote large (width = min(280dp, windowWidth − 64dp), aspect 1 : 1.1, rotated −2°, text bodyLarge-ish) + "Sample" badge → 24dp → status titleMedium "Reading the note…"
 phase "ready":    Column(Center, padding 16dp): status titleMedium "Your list is ready" → 16dp → ResultCard (groupSegment rows):
                     header row: note thumb 44×52dp · title titleMedium (= checklist name) · meta bodySmall onSurfaceVariant "From the sample note"
                     6 rows: AppRoundCheckbox (unchecked, non-interactive) + item text bodyLarge, inset dividers
```

The ResultCard is a picture of the list that is being saved, not a live list. Navigation lands on the real checklist detail right after, so the user sees the actual screen they will use.

---

## 2. States

### Step 1

| State | What is visible |
|---|---|
| Default (entry) | Headline, subtitle, 3 tiles playing their demos in turn (§7), CTA, Skip. Back on step 1 = OS back (exits on the Android root, as today). |
| Call unavailable (`voice_call_enabled` off, or web/iOS) | Talk caption switches to `…_talk_caption_chat`. The Talk demo stays (the orb and bubbles show chat just as well). |
| fontScale > 1.3 | Stages are replaced by the 40dp `paletteTint(n)` / `paletteContent(n)` disc with the tile icon. Divider inset 64dp. |
| Reduced motion | Every stage shows its final frame (poster) at once. No transitions are created. |

### Step 2

Mode comes from step 1: `Default` (CTA) · `Write` · `Attach` · `Talk` (tile). Keep it in `UiState` and clear it after it is consumed once, so ← → does not replay it.

| State | What is visible |
|---|---|
| Default | Blocks A–D. Scroll at top. The sample button is the only filled control. |
| Mode Write | Scroll so block C is at the top of the viewport. The field gets a 2dp `primary` focus ring (its own focused border). Focus requested → the IME opens. TalkBack focus moves to the field. |
| Mode Attach | Scroll at top (A and B are already first). Blocks A and B get the highlight ring (2dp `primary`, shape = their own shape) and fade it out after 1.2 s (`AppMotion.effectsSlow`). |
| Mode Talk | Scroll block D into view (bottom-aligned with 16dp margin). The ring on D for 1.2 s. TalkBack focus → D's title. |
| Call hidden | D: one full-width Chat button; caption `onboarding_try_talk_caption_chat`. |
| Text entered | Send disc filled. Chips collapse (`AnimatedVisibility`, `expandVertically` / `shrinkVertically` with `AppMotion.spatialFastTween()`, a fade with `effectsDefaultTween()`). D's caption switches to `onboarding_try_talk_caption_typed` ("The chat starts with your text."). The Chat label stays "Chat": the label "Discuss in chat" wrapped to two lines beside a one-line "Call" at 360dp (prototype pass 1, frame 12). |
| Empty send tap | Supporting text under the field: `onboarding_try_type_empty_hint`. The field is focused. |
| Sample running | Overlay §1.5. Back is swallowed (≤ 2 s), Skip hidden. |
| Chip / plain-list creating | All controls disabled; the tapped chip is kept visually as is. A DB write is instant; if it takes > 300 ms show the `AppButton(loading)`-style spinner in the sample button's place. No overlay. |
| Typed → AI generating | A full-screen loader replaces the chrome (re-skin of `GeneratingChecklistLoader`, `WelcomeOnboardingScreen.kt:277`): `GistiPresenceOrb(Think, size 96dp)`, then the user's text in quotes (bodyLarge, onSurfaceVariant, max 3 lines, ellipsis), then `onboarding_welcome_generating`. After 8 s add `onboarding_try_generating_slow` (bodyMedium, onSurfaceVariant, liveRegion polite). Non-dismissible, as today. |
| Typed → AI failed (server / 402 / timeout / offline) | Back on step 2, text kept, scroll at block C. **AiErrorBox** under the field: `AppSurface.recessed()` fill, `AppShapes.medium`, padding 12 / 12 / 4 / 12. Row: `Icons.Outlined.SyncProblem` 20dp `colorScheme.error` + Column(`onboarding_try_ai_error` bodyMedium onSurface, a FlowRow of two `AppButtonText`: `…_ai_error_sample` → runs the sample, `…_ai_error_save` → saves a plain list). liveRegion polite. Not `errorContainer`: blue text buttons on pink make three palettes in one box (`AppButton.kt:100-107`). The box disappears on the next edit of the field. |
| Typed → sign-in needed (web, no credits until Google sign-in) | The same box with `onboarding_try_ai_signin`. The two actions stay. A real sign-in action is a product decision, see open question Q3. |
| Own file chosen | No state here. Onboarding completes, then the app navigates to Analyze with that source pre-selected. Analyze owns its own loading and errors. |
| Sample save failed | Return to step 2 + snackbar `onboarding_welcome_create_error` (existing). |

The plain list from "Save as a plain list": name = the typed text, trimmed, cut at 60 characters on a word boundary + "…". Items = the four `onboarding_welcome_default_item_*` tips. Then it opens. Never a dead end: every failure offers two working ways forward, and neither needs AI.

---

## 3. The sample

**Decision: one sample, a photo of a handwritten "brain-dump" note, drawn in Compose.** Confirmed over the alternatives:

- **Not two samples.** A choice before a demo is one more decision on the step that should cost one tap. The other formats are shown right below it by the four material pills.
- **Not a receipt or a PDF page.** A receipt is already a list, so turning it into one proves nothing. A PDF page needs a long block of prose, which is unreadable in an 88dp thumbnail. A messy note shows the actual skill: an abbreviation ("re: Sunday"), a crossed-out word ("~~gym~~ yoga"), a time, a deadline and a list of groceries all become clean, separate tasks.
- **Photo** is the most-used analysis source, so it is the format worth rehearsing.
- **Drawn, not bitmapped.** Zero bytes, localised like any other string, sharp at every size, follows the theme.

**SampleNote composable** (`feature/onboarding`, internal): parameters are the width and a `scale` for text size. Body:
- Paper: `GistiColors.paletteTint(5)` (amber), corners `AppShapes.extraSmall`, a 1dp ring of `paletteContent(5)` at 18 % alpha.
- Ruled lines every line-height: `paletteContent(5)` at 16 % alpha.
- Ink: `paletteContent(5)`, `FontStyle.Italic`. Line 1 is the title: SemiBold, underlined (1.5dp, offset 2dp). Line 7: the word `onboarding_sample_note_struck` struck through (ink, 1.5dp), then a space and `onboarding_sample_note_7`.
- Contrast: light `#7E5800` on `#FFF8E1` and dark `#E6C76E` on `#332B16` are both well above 4.5 : 1 (palette values from `GistiColors.kt:101-134`).
- No handwriting font: a font covering Latin + Cyrillic + Devanagari would weigh more than the whole feature. Italic, a slight rotation and the ruled paper carry the "handwritten" read (prototype frames 15 and 20).
- **"Sample" badge:** read-only data badge at bottom-start. `AppSurface.card()` fill, `onSurfaceVariant` `labelSmall`, `AppShapes.extraSmall`. FontScale locked like the stage: at fontScale 2.0 it clipped to "Приме…" before the lock (frame 21, pass 1).

**Note text → precomputed checklist** (strings in §9; the checklist is created with `separateCompleted = true`):

| # | Note line (EN) | Checklist item (EN) |
|---|---|---|
| title | this week!! | **This week** (checklist name) |
| 1 | call mom re: Sunday | Call Mom about Sunday |
| 2 | milk, eggs, bread | Buy milk, eggs and bread |
| 3 | dentist Tue 3pm | Dentist on Tuesday at 3 PM |
| 4 | pay electricity before 15th | Pay the electricity bill by the 15th |
| 5 | library books! | Return the library books |
| 6 | ~~gym~~ yoga Thu | Yoga class on Thursday |

The items carry no due dates or reminders in v1, see Q2. The sample path makes **no network call and spends no credits**. It works offline and while AI is down.

---

## 4. Flow / navigation (side effects the ViewModel owns)

Every exit runs `completeOnboardingUseCase` **before** navigating (existing contract).

| Trigger | Result |
|---|---|
| Step 1 tile (Write/Attach/Talk) | Step 2 with that mode (§2). |
| Step 1 CTA "Try it now" | Step 2, mode Default. |
| Step 2 ← / system back | Step 1. Typed text kept (`UiState`). |
| Skip (either step) | Complete → v2 Inbox; the existing "tips into Inbox" seeding (PR #69) runs. |
| "Try with this note" / error action "Try the sample" | Overlay → save the precomputed checklist (localised name + 6 items, `separateCompleted = true`) → complete → **open its checklist detail**. Back from detail → Inbox. |
| Material pill (Photo/PDF/Link/Voice) | Complete → `navigateToAnalyzeWithInput(kind, AiEntrySource.ONBOARDING)` (the value is added together with this send site). Back from Analyze → Inbox, never back into onboarding. Camera/mic permission is asked by Analyze after this explicit tap, never on screen entry. |
| Send with text | AI generation (existing typed branch) → success: complete → open the created checklist. Failure: §2 error state. |
| Starter chip | Create the preset list (no AI, existing items) → complete → open it. |
| Chat | Complete → v2 Inbox with the chat dock open. With typed text: the composer is **prefilled, not sent** (sending spends credits; the user confirms). Without text: the chat's normal empty state. |
| Call | Complete → v2 Inbox → start the voice call (the same entry as the Ask-pill call button). The mic permission prompt comes from the call flow after this explicit tap. Shown only when `voice_call_enabled` && the platform supports calls. |
| "Save as a plain list" | Create (§2) → complete → open it. |

**Analytics hooks the UI needs** (names are proposals; `@feature-expert` finalises them against `AnalyticsEvents.kt`): `onboarding_step_viewed{step_index}`, `onboarding_tile_tapped{mode}`, `onboarding_sample_started` / `_completed`, `onboarding_own_source_tapped{kind}`, `onboarding_typed_sent`, `onboarding_chip_tapped{key}`, `onboarding_chat_tapped{prefilled}`, `onboarding_call_tapped`, `onboarding_ai_error_shown{kind}`, `onboarding_fallback_action{sample|plain_list}`, plus `checklist_created` with `ChecklistSource.ONBOARDING_{SAMPLE,CHIP,AI,PLAIN}` and a `seed` on Skip completion.

---

## 5. Components

### Reused (file:line)

| Element | Component | Where |
|---|---|---|
| Primary CTA, sample button | `AppButton` (default `AppShapeTokens.Button`; **drop** the `shape = shapes.large` override at `WelcomeOnboardingScreen.kt:416`) | `core/designsystem/.../components/AppButton.kt:53` |
| Chat / Call buttons | `AppButtonSecondary` | `AppButton.kt:124` |
| Error actions | `AppButtonText` | `AppButton.kt:171` |
| Tile group, sample card, talk card, result card | `Modifier.groupSegment(GroupPosition)` + `groupPositionOf` | `components/AppGroupedSection.kt:78`, `:34` |
| Own-file row | `SourceRowSection(style = Page)` | `components/SourceRow.kt:123` |
| Starter chips | `GistiPromptChips` | `components/gisti/GistiPromptChips.kt:164` |
| Type field | `AppTextField` (`trailingIcon`, `keyboardOptions`) | `components/AppTextField.kt:28` |
| Result rows | `AppRoundCheckbox` | `components/AppRoundCheckbox.kt:43` |
| Back handling | `PlatformBackHandler` | `components/PlatformBackHandler.kt:20` |
| Presence mark (Talk tile, Talk card, AI loader) | `GistiPresenceOrb` + `OrbMode` — **moved** (see below) | today `feature/voicecall/.../components/GistiPresenceOrb.kt:103` |
| Colours | `GistiColors.paletteTint/paletteContent` (`GistiColors.kt:159/167`), `aiGradient` (`:40`), `success`; `AppSurface.card/recessed` (`Elevation.kt:89/103`); `AppChatColors.raised/controlOutline/contentOutline/userBubble` (`ChatColors.kt:111-260`) | theme/ |
| Motion | `AppMotion.*`, `LocalReducedMotion` | `theme/Motion.kt:57`, `:176` |
| Call a11y label (if a description is needed beyond the visible "Call") | `nav_ask_pill_call_description` | strings.xml |

### New

| Component | Home | Why there |
|---|---|---|
| `GistiPresenceOrb` (moved as-is, with `OrbMode` and the `HaloExtent` constant) | `core/designsystem/.../components/gisti/GistiPresenceOrb.kt` | Onboarding cannot depend on `feature:voicecall` (that pulls the call engine into onboarding). The orb only reads designsystem tokens already. `feature:voicecall` re-imports it, and `VoiceCallDefaults.HaloExtent` points at the moved constant. |
| `GistiColors.aiHeadlineBrush` | `theme/GistiColors.kt` | The accent run of a headline. Light = `aiGradient`. Dark = `SolidColor(colorScheme.primary)`: the dark gradient (`#2A6FD6`→`#5562C4`) on the `#161412` page is only ~3.3–3.8 : 1 and reads dim next to the `#EEE8E0` lead (prototype frame 02 uses `primary`). |
| `OnboardingTopBar`, `StepProgress` | `feature/onboarding/.../welcome/components/` | Onboarding-only chrome. |
| `ShowcaseTiles` + `ShowcaseTile` + `DemoStage{Write,Attach,Talk}` | `feature/onboarding/.../welcome/components/` | Onboarding-only. |
| `SampleNote`, `SampleNoteThumb` | `feature/onboarding/.../welcome/components/` | Used by the Attach stage, the sample card and the overlay. |
| `SampleCard`, `TypeBlock` (+ `AiErrorBox`), `TalkCard` | `feature/onboarding/.../welcome/components/` | Step 2 blocks. |
| `SampleAnalyzingOverlay`, re-skinned `GeneratingChecklistLoader` | `feature/onboarding/.../welcome/` | Overlays. |

### Deleted from `WelcomeOnboardingScreen.kt`

`BrandBlue/BrandIndigo/BrandPurple/StarGold/BrandGradient` (:122-129), `HeroGradientBadge`, `GlassIconTile`, `TestimonialCard`, `ChatBubbleUser/Assistant`, `MultimodalStartCard`, `MultimodalThumbnailStack`, `StarterChipsRow/StarterChip`, `WelcomeFooter` + `WelcomeCtaState`, `WelcomeProgressBar`, `StaggerItem`, the `Welcome/Capture/Value` step composables. `TwoToneHeadline` stays, but reads `GistiColors.aiHeadlineBrush` instead of the hardcoded gradient.

---

## 6. Tokens

- **Colour (roles only):** page `colorScheme.background`. Cards and groups `AppSurface.card()` + `outlineVariant` hairline (via `groupSegment`). Thumbnail well `AppSurface.recessed()`. Headline `onSurface` + accent `GistiColors.aiHeadlineBrush`. Body text `onSurfaceVariant`. Highlight ring `primary`. Stage fills `GistiColors.paletteTint(1/2/3)`, disc icons `paletteContent(1/2/3)`. Note `paletteTint(5)` / `paletteContent(5)`. Error icon `colorScheme.error`. **No `Color(0x…)` anywhere in the feature.**
- **Type:** headline step 1 `headlineLarge` (expanded: `displaySmall`). Headline step 2 `headlineSmall`. Tile/card titles `titleMedium`. Section titles `titleSmall` (the `SourceRowSection` Page style, so the three section headings match). Body `bodyMedium`. Subtitle `bodyLarge`. Badge `labelSmall`. Demo text 10–10.5sp inside fontScale-locked stages: *no token*. These are illustration pixels, not UI text, and the nearest token `labelSmall` (11sp) does not fit the 96dp stage.
- **Shape:** groups and cards `AppShapeTokens.Group` (24dp, via `groupSegment`). Stage and thumbnail well `AppShapes.medium` (16). Field `AppShapes.small` (12, inside `AppTextField`). Buttons `AppShapeTokens.Button`. Note paper and badge `AppShapes.extraSmall` (6). Chips and pills `AppShapeTokens.Pill`.
- **Spacing:** screen padding `AppDimens.ScreenPaddingHorizontal`. Block gaps `SpacingXl` (section breaks). Inside blocks `SpacingSm`/`SpacingMd`. Card padding `SpacingLg`. Divider inset in the tile group = 12 + stage 120 + 12 = **144dp** (64dp with the 40dp disc). *No token*: it is derived from the stage width; pass it to `groupSegment(dividerInsetStart = …)` as a named private constant next to the stage size.
- **Fixed sizes without a token** (named private constants in the feature, like `VoiceCallDefaults`): stage 120×96dp; thumbnail well 88×104dp; talk orb 40dp; send disc 36dp; progress segment 24×4dp.

---

## 7. Motion

All springs and tweens come from `AppMotion`. With `LocalReducedMotion` on: no infinite or looping animation is created (gate **before** `rememberInfiniteTransition` / the clock), every `spatial*` snaps, and every `effects*` becomes 120ms.

- **Step change:** `slideInHorizontally { it / 4 }` + `fadeIn(effectsDefaultTween())`, exit mirrored, duration `spatialDefaultTween()` (320ms). Reduced: a 120ms crossfade. Replaces the hardcoded `tween(300)` full-width slide (`WelcomeOnboardingScreen.kt:203-223`).
- **Demo stages (step 1):** one clock per stage driven by `LaunchedEffect`. Start offsets 300ms / 1200ms / 2100ms (Write, Attach, Talk). Each demo runs ~2.4s, the whole set holds 3s, and the set plays **twice, then rests on the final frame**. That saves the Skiko frame budget on wasm and stops the motion from competing with reading. Paused when step 1 is not visible.
  - Write: placeholder → `onboarding_demo_write_prompt` types in over 800ms (fixed per-character steps, `LinearEasing`); the send disc scales 1 → 0.9 → 1 (`spatialFast`); 3 rows enter with 120ms stagger (`spatialFast`, translateY 6dp + fade).
  - Attach: a scan band (gradient `primary` 0 → 40 % → 0, 14dp tall) sweeps the note over 600ms; the note shifts left 6dp and rows enter with 120ms stagger.
  - Talk: the user bubble enters (fade + 6dp rise); orb `Think` for 800ms; the reply bubble enters; orb `Speak` (level from a fixed 1s envelope) then `Listen0`.
  - Poster frame (reduced motion, and after the 2 cycles) = the end state of each demo, as in prototype frame 01.
- **Mode ring on step 2:** a 2dp `primary` border animates alpha 0 → 1 (`effectsDefault`), holds 1.2s, then → 0 (`effectsSlow`). Scroll to the block with `animateScrollTo` (`spatialDefaultTween`). Reduced: the scroll snaps and the ring shows for 1.2s without a fade-in.
- **Sample overlay (≤ 2.0s total):**
  - 0–200ms: note in (fade + scale 0.92 → 1, `spatialDefault`).
  - 200–1100ms: the scan band (15 % of the note's height) moves top → bottom, linear. Each line gets a highlighter fill (`primary` 22 %, left → right 120ms) as the band's centre passes it.
  - 1100–1300ms: the note fades and moves up; status crossfades "Reading the note…" → "Your list is ready".
  - 1300–1700ms: the ResultCard rises 24dp + fade (`spatialDefault`); rows stagger 40ms each.
  - 1700–2000ms: hold, then navigate.
  - If the DB write is not done by 2.0s, keep the final frame until it is (timeout 5s → error, §2).
  - Reduced: note 300ms static → 120ms crossfade → list 600ms → navigate.
  - Haptic `Confirm` (Android) on "ready".
- **Chips collapse/expand on typing:** `expandVertically` / `shrinkVertically` with `spatialFastTween` (a tween, not a spring: layout animation over a measured row, `Motion.kt:24`).

---

## 8. Accessibility

- **Hit zones ≥ 48dp:** back and Skip (the fix in §1.1), tiles (≥ 88dp), material pills (component), chips (component, 48dp target), send (48dp slot), every button (`heightIn(48)`).
- **Step 1:**
  - The headline is one node with `heading()`; the two-tone split is one `AnnotatedString`, not two Texts.
  - Each tile is one merged `Role.Button` node: "{title}, {caption}", `onClickLabel` = `onboarding_showcase_cta` ("Try it now").
  - Stages and discs use `clearAndSetSemantics {}`.
  - Focus order: back/progress/Skip → headline → subtitle → tiles → CTA.
- **Step 2:**
  - The headline and the three section titles are `heading()`.
  - Sample thumbnail: `contentDescription = onboarding_sample_image_a11y`, `Role.Image`; the badge is inside it.
  - Field: the hint reads the placeholder; send has `contentDescription = onboarding_try_send_a11y`.
  - The AiErrorBox and the empty-send hint are `liveRegion = Polite`.
  - Mode Write/Talk moves accessibility focus to the field / D's title (`FocusRequester` + `semantics { focused }` request).
- **Overlay:** the status text is `liveRegion = Polite`; the note has the same image description; the ResultCard is a heading (list name) plus plain rows. The non-interactive checkboxes are `clearAndSetSemantics` inside one row node.
- **Contrast:**
  - Accent headline light: `#1A73D9`→`#4F5BB8` on `#F4EFE7` is ≥ 3 : 1 (large text, 28sp Bold).
  - Accent dark: `primary` `#90CAF9` on `#161412`, ~10 : 1.
  - Note ink ≥ 4.5 : 1 in both themes (§3).
  - Inactive progress: decorative; state is also in its `contentDescription`.
- **Colour is never the only channel:** the mode ring comes with a scroll and focus; the send state comes with a different icon alpha and the hint; errors carry an icon and text.

---

## 9. Adaptive

| Window | Step 1 | Step 2 |
|---|---|---|
| Compact (< 600dp) | As §1.2; CTA in the bottom dock | As §1.4, single column |
| Medium (600–839dp) | Same, content max width 560dp centred; the dock CTA matches the content width | Single column, max width 560dp centred |
| Expanded (≥ 840dp, incl. web on a PC) | Content max width 1040dp, vertically placed with the 1 : 2 split. Headline `displaySmall`. Tiles = 3 equal columns (`Row`, gap 16dp), each `groupSegment(Single)` with the stage on top (full width, 5 : 4, scaled composition) and title/caption + chevron below. CTA **inside the content**, centred, width 320dp; no dock. (Pass 1 also had a "Try it now →" link on every tile; removed, as it duplicated the CTA, frame 19.) | Max width 1040dp. Headline full width, then a 5 : 7 grid: left = SampleCard with the large note (well height 300dp, note 220×260dp), right = B, C, D stacked with 24dp gaps. Top-aligned; the page scrolls if short. |
| Height-compact (landscape phone, < 480dp) | Stages hidden (disc ladder); the content scrolls; the dock stays | Single column, scroll |

- **Web:** Enter in the field = send, Shift+Enter = new line. Hover and focus indication come from `clickable`/M3. Tab order = the focus order in §8. The call entry is never shown on web.
- **Dark theme:** frames 02, 04, 08, 12, 17, 20 checked; no change beyond the `aiHeadlineBrush` rule.
- **fontScale ladder (up to 2.0):**
  1. Step 1 tiles: stage → disc above 1.3.
  2. Sample card: Row → Column (thumb above text) above 1.3.
  3. Talk buttons: two abreast only if both labels fit **one** line at half width (measure like `SourcePillGrid`), else stacked full width.
  4. SourceRow: its own measured ladder (4 → 2 → 1).
  5. Demo stages, note thumbnail and badge are locked at fontScale 1.0 (decorative, semantics cleared).
  6. Headlines are not capped; the content scrolls and the step 1 CTA stays pinned.

---

## 10. Strings

All in `core/designsystem/src/commonMain/composeResources/values{,-ru,-hi}/strings.xml`. Literal apostrophes; ` ` is the only escape used. **HI copy needs a native-speaker pass before release** (machine-quality, natural register aimed at but not verified).

### New

| Key | EN | RU | HI |
|---|---|---|---|
| `onboarding_progress_a11y` | Step %1$d of %2$d | Шаг %1$d из %2$d | %2$d में से चरण %1$d |
| `onboarding_showcase_title_lead` | Turn anything into | Превратите что угодно | किसी भी चीज़ को |
| `onboarding_showcase_title_accent` | a checklist | в чек-лист | चेकलिस्ट में बदलें |
| `onboarding_showcase_subtitle` | Write it, snap it, or just talk. Gisti sorts it out. | Напишите, сфотографируйте или просто скажите — Gisti разложит по пунктам. | लिखें, फ़ोटो लें या बस बोलें — Gisti उसे सूची में बदल देगा। |
| `onboarding_showcase_write_title` | Write a goal | Напишите цель | लक्ष्य लिखें |
| `onboarding_showcase_write_caption` | Get a step-by-step list | Получите план по шагам | क़दम-दर-क़दम सूची पाएँ |
| `onboarding_showcase_attach_title` | Photo or file | Фото или файл | फ़ोटो या फ़ाइल |
| `onboarding_showcase_attach_caption` | Snap, PDF, link or voice — into a list | Снимок, PDF, ссылка или голос — в список | फ़ोटो, PDF, लिंक या आवाज़ — सूची में |
| `onboarding_showcase_talk_title` | Talk it through | Обсудите с Gisti | Gisti से बात करें |
| `onboarding_showcase_talk_caption_call` | Chat or call Gisti about your plans | Чат или звонок — про ваши дела | चैट या कॉल — अपने कामों के बारे में |
| `onboarding_showcase_talk_caption_chat` | Chat with Gisti about your plans | Пишите в чат про ваши дела | चैट में अपने कामों के बारे में बात करें |
| `onboarding_showcase_cta` | Try it now | Попробовать | आज़माएँ |
| `onboarding_demo_write_prompt` | Beach trip | Пляж | बीच ट्रिप |
| `onboarding_demo_write_item_1` | Sunscreen | SPF-крем | सनस्क्रीन |
| `onboarding_demo_write_item_2` | Towels | Полотенца | तौलिए |
| `onboarding_demo_write_item_3` | Snacks | Перекус | स्नैक्स |
| `onboarding_demo_talk_user` | Move dentist to Fri? | Стоматолога на пт? | डेंटिस्ट शुक्र को? |
| `onboarding_demo_talk_reply` | Done | Готово | हो गया |
| `onboarding_try_title` | Make your first list | Соберите первый список | अपनी पहली सूची बनाएँ |
| `onboarding_sample_title` | No file at hand? | Нет файла под рукой? | पास में कोई फ़ाइल नहीं? |
| `onboarding_sample_body` | Watch Gisti turn this note into a list. | Посмотрите, как Gisti превратит эту записку в список. | देखें, Gisti इस नोट को सूची में कैसे बदलता है। |
| `onboarding_sample_cta` | Try with this note | Попробовать на записке | इस नोट से आज़माएँ |
| `onboarding_sample_badge` | Sample | Пример | नमूना |
| `onboarding_sample_image_a11y` | Sample photo: a handwritten note with six to-dos | Пример фото: записка от руки с шестью делами | नमूना फ़ोटो: हाथ से लिखा नोट, जिसमें छह काम हैं |
| `onboarding_sample_note_title` | this week!! | на неделе!! | इस हफ़्ते!! |
| `onboarding_sample_note_1` | call mom re: Sunday | маме позвонить про вс | माँ को फ़ोन — रविवार |
| `onboarding_sample_note_2` | milk, eggs, bread | молоко, яйца, хлеб | दूध, दही, ब्रेड |
| `onboarding_sample_note_3` | dentist Tue 3pm | зубной вт 15:00 | डेंटिस्ट मंगल 3 बजे |
| `onboarding_sample_note_4` | pay electricity before 15th | свет оплатить до 15-го | बिजली बिल 15 से पहले |
| `onboarding_sample_note_5` | library books! | книги в библиотеку! | लाइब्रेरी की किताबें! |
| `onboarding_sample_note_struck` | gym | зал | जिम |
| `onboarding_sample_note_6` | yoga Thu | йога чт | योगा गुरु |
| `onboarding_sample_reading` | Reading the note… | Читаю записку… | नोट पढ़ रहा हूँ… |
| `onboarding_sample_ready` | Your list is ready | Список готов | आपकी सूची तैयार है |
| `onboarding_sample_list_meta` | From the sample note | Из записки-примера | नमूना नोट से |
| `onboarding_sample_checklist_name` | This week | На этой неделе | इस हफ़्ते |
| `onboarding_sample_item_1` | Call Mom about Sunday | Позвонить маме насчёт воскресенья | रविवार के बारे में माँ को फ़ोन करें |
| `onboarding_sample_item_2` | Buy milk, eggs and bread | Купить молоко, яйца и хлеб | दूध, दही और ब्रेड ख़रीदें |
| `onboarding_sample_item_3` | Dentist on Tuesday at 3 PM | Стоматолог во вторник в 15:00 | मंगलवार दोपहर 3 बजे डेंटिस्ट |
| `onboarding_sample_item_4` | Pay the electricity bill by the 15th | Оплатить электричество до 15-го | 15 तारीख़ से पहले बिजली का बिल भरें |
| `onboarding_sample_item_5` | Return the library books | Вернуть книги в библиотеку | लाइब्रेरी की किताबें लौटाएँ |
| `onboarding_sample_item_6` | Yoga class on Thursday | Йога в четверг | गुरुवार को योगा क्लास |
| `onboarding_try_own_file_title` | Use your own file | Свой файл | अपनी फ़ाइल |
| `onboarding_try_type_title` | Type what's on your mind | Напишите, что нужно | जो चाहिए, लिखें |
| `onboarding_try_type_placeholder` | e.g. Pack for a beach trip | Например: собраться на море | जैसे: छुट्टियों की पैकिंग |
| `onboarding_try_type_empty_hint` | Type a goal first — or pick an idea below | Сначала напишите цель — или выберите идею ниже | पहले लक्ष्य लिखें — या नीचे कोई आइडिया चुनें |
| `onboarding_try_send_a11y` | Make a list with AI | Создать список с ИИ | AI से सूची बनाएँ |
| `onboarding_try_talk_title` | Talk it through with Gisti | Обсудите с Gisti | Gisti से बात करें |
| `onboarding_try_talk_caption_call` | Plan your day out loud or in chat. Gisti adds the tasks. | Спланируйте день голосом или в чате — Gisti сам добавит задачи. | आवाज़ से या चैट में दिन की योजना बनाएँ — Gisti काम ख़ुद जोड़ देगा। |
| `onboarding_try_talk_caption_chat` | Plan your day in chat. Gisti adds the tasks. | Спланируйте день в чате — Gisti сам добавит задачи. | चैट में दिन की योजना बनाएँ — Gisti काम ख़ुद जोड़ देगा। |
| `onboarding_try_talk_caption_typed` | The chat starts with your text. | Чат начнётся с вашего текста. | चैट आपके टेक्स्ट से शुरू होगी। |
| `onboarding_try_chat` | Chat | Чат | चैट |
| `onboarding_try_call` | Call | Звонок | कॉल |
| `onboarding_try_ai_error` | Gisti can't reach the AI right now. Try the sample or save your text as a list. | Gisti сейчас не может подключиться к ИИ. Попробуйте пример или сохраните текст списком. | Gisti अभी AI से नहीं जुड़ पा रहा। नमूना आज़माएँ या अपना टेक्स्ट सूची के रूप में सेव करें। |
| `onboarding_try_ai_signin` | Sign in with Google to use AI here. Meanwhile, try the sample or save your text as a list. | Чтобы пользоваться ИИ здесь, войдите через Google. А пока попробуйте пример или сохраните текст списком. | यहाँ AI के लिए Google से साइन इन करें। तब तक नमूना आज़माएँ या टेक्स्ट को सूची के रूप में सेव करें। |
| `onboarding_try_ai_error_sample` | Try the sample | Попробовать пример | नमूना आज़माएँ |
| `onboarding_try_ai_error_save` | Save as a plain list | Сохранить как список | सादी सूची सेव करें |
| `onboarding_try_generating_slow` | Still working on it… | Ещё работаю… | अभी काम चल रहा है… |

**Kept and reused:** `onboarding_skip`, `back`, `onboarding_welcome_generating`, `onboarding_welcome_create_error`, `onboarding_welcome_default_checklist_name`, `onboarding_welcome_chip_*`, `onboarding_welcome_checklist_*`, `onboarding_welcome_items_*`, `onboarding_welcome_default_item_1..4`, `analyze_source_*` (via SourceRow), `nav_ask_pill_call_description`.

### Deleted (EN + RU + HI)

Delete each key only after a grep shows zero use outside the files deleted in this task. Keys marked ⚠ had possible outside users at spec time.

- **Slides:** `onboarding_page1..4_title`, `onboarding_page1..4_description`, `onboarding_get_started`, ⚠ `onboarding_continue`.
- **Interactive:** every `onboarding_interactive_*` **except** ⚠ `onboarding_interactive_settings` (check); `onboarding_preview_all_checked`.
- **Discover-more:** `onboarding_discover_title/subtitle`, `_reminder_title/subtitle`, `_widget_title/subtitle`, `_share_title/subtitle`, `_reminder_sheet_title`, `_reminder_tonight/daily/weekly/custom/done`, ⚠ `_maybe_later`, ⚠ `_completed`. **Keep** `onboarding_discover_widget_sheet_title`, `_widget_step1..3`, `_widget_done`: `feature/updatefeed/.../WidgetInstructionOverlay.kt:50-67` uses them.
- **Old welcome steps:** `onboarding_welcome_continue`, `_create_cta`, `_title_lead/_accent`, `_testimonial_quote/_author`, `_capture_title_lead/_accent`, `_capture_lead`, `_chat_bubble_user/_done`, `_value_title_lead/_accent`, `_value_lead`, `_first_title_lead/_accent`, `_first_lead`, `_first_input_placeholder`, `_more_ways_title/_sub`.

---

## 11. Проверка (one-time picture check, design-expert step 5b)

The HTML prototype was rendered with Playwright (Chromium, deviceScaleFactor 2) into 22 frames plus contact sheets, and every frame was opened and looked at:

- Light and dark.
- EN, RU and HI.
- 360×800 and 412×915.
- fontScale 1.5 and 2.0.
- Expanded at 1280×800.
- All step 2 states: default, talk, nocall, error, typed, write.
- The overlay in both phases.

Prototype: `docs/designs/onboarding-refresh-2026-11-04/prototype.html` (query params `s`, `theme`, `lang`, `fs`, `w`, `h`, `st`). Frames: `docs/designs/onboarding-refresh-2026-11-04/frames/*.png` (gitignored).

Found and fixed in this spec after pass 1:

1. **The accent orphan:** "Turn anything into **a** / **checklist**" (EN), "…угодно **в** / **чек-лист**" (RU), HI likewise. Fixed: NBSPs inside the accent string, so the accent wraps as one unit (frames 01, 03, 18).
2. **Dead band above the step 1 CTA** (~120dp at 360×800, ~240dp at 412×915) with the content pinned to the top. Fixed: free space split 1 : 2 (§1.2).
3. **Sample card at RU × fontScale 2.0:** the text column was squeezed to ~180dp (seven lines) and the "Sample" badge clipped. Fixed: Column layout above fontScale 1.3, badge fontScale-locked (frame 21).
4. **"Discuss in chat" wrapped** to two lines next to a one-line "Call" at 360dp. Fixed: the label stays "Chat"; the typed state changes the caption instead (frame 12).
5. **Expanded step 1 repeated "Try it now →" on each tile** plus the CTA. Fixed: chevrons on the tiles, one centred CTA (frame 19).
6. Talk orb without halos read as a flat blue dot. Fixed: `OrbMode.Speak` with level 0 shows the static halos (frames 07, 22).

Checked and fine:

- Step 2 fits one 360×800 window in EN (frame 07).
- SourceRow drops to 2×2 at RU × 1.5 by its own ladder (frame 13).
- Error box and typed state in light and dark (frames 11, 12).
- The overlay ResultCard fits 6 rows at 360×800 (frame 16).

**Not verified by picture:** the IME-open layout (the prototype has no keyboard), motion timing, and the real `GistiPresenceOrb` rendering. Those belong to the code expert's cycle.

## 12. Эталон (raster)

`docs/designs/onboarding-refresh-2026-11-04/frames/` — the canonical frames are 01 (step 1), 07 (step 2), 11 (error), 15/16 (overlay), 19/20 (expanded). Contact sheets: `frames/sheet_01..04.png` (не проверено — каталог `frames/` gitignored, лежит только локально).

## 13. Screenshot matrix the code expert must shoot and look at

Tooling: `android preview render` / Roborazzi.

- **Sizes:** 320×640, 360×800, 412×915, 600×960 (medium), 1280×800 (expanded / web). Landscape 800×360 for step 1.
- **Themes:** light, dark.
- **fontScale:** 1.0, 1.3, 1.5, 2.0 (1.3 and 1.5 sit on the two ladder thresholds).
- **Locales:** EN, RU, HI (HI is the tallest script; RU has the longest words).
- **States:** step 1 default / call off / reduced-motion poster; step 2 default / mode Write / mode Attach / mode Talk / call hidden / typed / empty-send hint / AI error / sign-in error / chip creating; overlay reading / ready; AI loader with the longest typed text.
- **Edge data where the layout can break:**
  - The typed text at 4 lines (the field grows) plus the error box.
  - The longest chip label (RU "Сборы в поездку", HI "यात्रा की पैकिंग").
  - RU × 2.0 on 320dp, where Skip, progress and back meet in the top bar.
  - The ResultCard at 320×640 RU (six rows + header must scroll, never clip).
  - A plain-list title of exactly 60 characters (detail screen top bar).

## 14. Handoff

- **Order:** `@feature-expert` `Mode: SPEC` (state matrix for §2, `UiState`/actions, routes, analytics) → `@compose-expert` (screens and components from this spec, previews per state, the PNG matrix §13) → `@feature-expert` `Mode: IMPLEMENT`. The orb move and the `aiHeadlineBrush` token are designsystem edits for `@compose-expert` (layout only, no logic).
- **Files:** `feature/onboarding/src/commonMain/.../presentation/welcome/` (`WelcomeOnboardingScreen.kt`, `...ScreenContract.kt`, new `components/`); `core/designsystem/.../components/gisti/GistiPresenceOrb.kt` (moved); `core/designsystem/.../theme/GistiColors.kt`; the three `strings.xml`.
- **Build order:**
  1. Strings.
  2. Orb move + token.
  3. Top bar and progress.
  4. Step 1 static (poster frames).
  5. Step 2 static, all states.
  6. Overlay static (both phases).
  7. Motion.
  8. Adaptive variants.
  9. Matrix §13.
- **Pitfalls:**
  1. `GistiPromptChips` must sit outside the column's horizontal padding, or the inset doubles and the edge bleed is lost (its KDoc).
  2. The demo stages need the fontScale lock **and** `clearAndSetSemantics`. Without the first they overflow at 1.3; without the second TalkBack reads "Sunscreen Towels Snacks" on every tile.
  3. Do not create the demo clocks or the orb's infinite transitions when `LocalReducedMotion` is on, and stop them after two cycles. A running transition costs frames on the wasm canvas even off-screen.

## 15. Open questions for the owner

- **Q1. One sample or two?** Recommended: **one** (the handwritten note). A second sample (e.g. a PDF page) adds a choice before the first result. The four material pills right below already show the other formats.
- **Q2. Should the sample list carry real dates** ("Dentist" due next Tuesday 15:00, "electricity" due the 15th)? It shows date extraction, which is a strong point. But it creates reminders, which can trigger the notification permission and use the recurring-reminder allowance on day 0. Recommended: **no dates in v1**; revisit once the onboarding baseline is measured.
- **Q3. A sign-in action in the web "no credits" box?** Recommended: **yes**, if a one-tap Google sign-in entry already exists on web. Otherwise keep the two AI-free actions and do sign-in later.
- **Q4. Call entry while AI is down.** The call also needs the AI backend. With `voice_call_enabled` off in release this is moot today. When the flag is turned on, the call's own error screens apply (`voice_call_err_server_*`). Recommended: no extra onboarding handling.
