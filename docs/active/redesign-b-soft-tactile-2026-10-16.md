# Redesign B "Soft & tactile" — Inbox, Checklist detail, AI chat

**Статус:** In Progress
**Дата старта:** 2026-10-16
**Start SHA:** 8d8931e48c76ef9f2d3290c3ecff13ee1dab830c
**Project:** Checklists
**Тип:** feature
**Затронутые модули:** core/designsystem, feature/home, feature/aichat, composeApp

## Цель (продуктовая)

Refresh the look of the three screens users spend most time in (v2 Inbox, checklist detail, AI chat) without losing any existing function. The owner picked direction B "Soft & tactile" out of three directions on the Claude Design canvas: warm paper background, rounded section groups, round checkboxes, an "Ask Gisti or add a task" pill in the bottom plate instead of the centre AI FAB, a header card with a progress ring on the checklist, a lighter chat without per-reply labels.

Design sources (claude.ai, private): directions canvas `https://claude.ai/artifact/LXZngnEKuQh3PsoPhSHTpM`, current design system `https://claude.ai/artifact/8AJjnzfSnq7QXoSPpEyWre` (also synced to the claude.ai/design project "Gisti", folder `gisti-ds/` (не проверено — path inside the claude.ai/design project, not in this repo)).

## Технический план

1. `@design-expert` — Compose DESIGN_SPEC for B (token deltas, components mapped to existing v1 composables, function-preservation table, state needs).
2. Tokens + design-system components (`core/designsystem`) → `@compose-expert`, Roborazzi before/after.
3. State changes the spec needs (remaining/done counts, Completed group, chat input credits) → `@feature-expert`.
4. Screens one by one — Inbox → Detail → Chat — `@compose-expert`, screenshot cycle after each.
5. Validation: `:androidApp:assembleDebug`, `testAndroidHostTest`, wasmJs compile + `/web-dev-run` check; `/task-gate`.

Found along the way, fixed in this task: `.claude/rules/designsystem.md` describes an outdated AppCard (12dp, 2dp shadow, white bg); `ChecklistListCard` hardcodes "No items yet"; WCAG failures — white label on AI gradient 3.1:1, star 2:1, light avatar monograms 2.2–2.7:1, `success` small text 3.4:1 (источник: contrast check by the design-system export agent).

## Лог итераций

### Итерация 1 — 2026-10-16 — main: current screens rendered via `StoreListingFramesScreenshotTest` raw pass (a*), design system exported to Claude Design, 3 directions on canvas → owner chose B
### Итерация 2 — 2026-10-16 — design-expert: Compose DESIGN_SPEC for B (tokens, 11 components, screens, 56-row function-preservation table) → owner overrode 2 spec calls: Inbox section counts ON (reopens "no debt counter"), Ask pill keeps "or add a task" + mic (needs a "start chat in voice" entry); `separateCompleted` defaults to true for new checklists only
### Итерация 3 — 2026-10-16 — compose-expert: step (a) tokens (warm palette, contrast fixes) + 8 new DS components + AskGistiBar ChromePill w/ mic → `:core:designsystem:testAndroidHostTest` (182 tests) + `:androidApp:assembleDebug` + `:composeApp:compileKotlinWasmJs` green; 61 DS goldens re-recorded. Deviations from spec forced by screenshots: header-card ring drops under the title at narrow width, Ask-pill label 2 lines (RU clipped at 1). Open: warm pills on the cool bottom chrome — check on device
### Итерация 4 — 2026-10-16 — feature-expert: pill mic → chat dock opens already recording (tap to start, tap the dock mic to finish — reuses `ChatInputRow`'s tap-while-recording = finish), `voiceEntryDecision` + cancel-on-dock-gone / on-background as pure functions in `VoiceEntry.kt`, 7 tests → `:feature:aichat:impl` 572 tests green. No Channel: the recorder lives in App.kt above the overlay, a direct call from the tap handler is already one-shot and keeps the start inside the user gesture
### Итерация 5 — 2026-10-16 — compose-expert: step (b) bottom plate (Ask pill + mic above 4 equal tabs, old AI-circle metrics removed), Inbox (in-screen header, project chips instead of pager dots, grouped sections with counts, `TaskRow` Grouped mode) + main wired `onOpenChatVoice = openChatInVoice` in App.kt → `:feature:home` + `:composeApp` host tests (1024) + `:androidApp:assembleDebug` + wasmJs green. Open: RU fs1.5 tab labels break mid-word at 360dp; `AddTaskRow` restyle also reaches Calendar
### Итерация 6 — 2026-10-16 — feature-expert: `separateCompleted = true` for new checklists via explicit `NEW_CHECKLIST_SEPARATE_COMPLETED` at 11 creation sites; model/Room/sync/parser defaults stay `false` (an absent field must not flip old lists — pinned by `SeparateCompletedLegacyDecodeTest`) → feature/* tests green; composeApp tests pending on detail compile. Left for owner: interactive onboarding toggle (3 tests pin `false`), MCP `mcp-server/src/mutate.ts:87` writes `false`

### Итерация 7 — 2026-10-16 — compose-expert: step (c) detail v2 Standard (header card + ring, grouped rows, Ask row, Completed section, "Add a task" → QuickCaptureDock plate; FAB + inline add removed; `FolderCard` grouped) → host tests + `:androidApp:assembleDebug` + wasmJs green, 14 frames. Open: BACK/scrim close clears the draft (`closeItemCreateMode` wipes `pendingItemInput`) → @feature-expert; `androidTest/.../FolderFlowTest.smartAddItem` must tap "Add a task" first (not updated); keyboard lift unproven on JVM
### Итерация 8 — 2026-10-16 — feature-expert: detail capture plate lost the draft on BACK/scrim — root: `closeItemCreateMode()` (the only handler of `OnDockItemCreateClosed`) cleared `pendingItemInput`, and `openItemCreateMode()` cleared it too. Fix: new intent `OnItemCreateDismissed` keeps text/chips/attachments; v1 dock collapse still clears. Red `itemCreate_dismissKeepsDraft_reopenRestoresIt` → green, `:feature:home` tests green
### Итерация 9 — 2026-10-16 — test-expert: `FolderFlowTest.smartAddItem` input steps follow the button→plate contract, assertions untouched → compile pending (chat step in flight)
### Итерация 10 — 2026-10-16 — compose-expert: step (d) chat — no per-reply label, avatar on the last reply of a run, feedback row on the latest reply (tap reveals older), credits chip inside the input (opens pricing sheet + Get more), header without credits, bottom-chrome input plate; `showSenderLabel` → `showAvatar`; pricing copy no longer promises free on-device commands (L1 is disconnected) → aichat + composeApp host tests + `:androidApp:assembleDebug` + wasmJs green. Open: RU placeholder truncates at 320dp; Deep Thinking subtitle still mentions "local router"

### Итерация 11 — 2026-10-16 — compose-expert: polish — compact `AppDueChip` in grouped task rows, bottom tab labels single-line with auto-shrink (the RU mid-word break was pre-existing in the HEAD golden) → host tests + `:androidApp:assembleDebug` + wasmJs green
### Итерация 12 — 2026-10-16 — task-gate review (Standards + Spec): staged attachments leaked to another Weekly day after dismiss (reset branch of `openItemCreateMode` never cleared `itemCreatePendingAttachments`) → @feature-expert; `ChatHeader` "Open menu" literal → @compose-expert
### Итерация 13 — 2026-10-16 — feature-expert: owner decision — interactive onboarding also defaults to separate completed (`InteractiveOnboardingScreenContract` + toggle reset use `NEW_CHECKLIST_SEPARATE_COMPLETED`; MCP untouched by owner choice) → `:feature:onboarding` 71/71 green. Debug APK installed on Pixel 9 for owner review; web :9090 check deferred by owner (wasm dev compile hit OutOfMemoryError while shared Gradle daemons held memory)
### Итерация 14 — 2026-10-17 — design-expert: pass 2 — the Add-task capture sheet (`QuickCaptureDock`: Inbox, detail/project, Calendar) and the AI chat cards / empty state / input; 3 directions per surface on a canvas (claude.ai artifact `U7BDsRtkE6pMDcVULMgdN5` (не проверено — artifact id lives on claude.ai, not in the repo)), spec `docs/designs/redesign-b-2026-10-16/DESIGN_SPEC_B_pass2.md` → recommends dock B "composer card" and chat B "actions inside the reply"; awaiting owner pick
### Итерация 15 — 2026-10-17 — compose-expert: dock B "composer card" (owner AFK — main took the recommended variant, which changes no owner-approved decision and no state): new `QuickCaptureDock` slots `attributes`/`attachments`, `AddItemFieldStyle.Embedded`, soft chips via `chipFill()`, warm source pills; detail chip set unchanged; planner keeps the keyboard → DS/aichat verify green, `:androidApp:assembleDebug` + wasmJs green. Two `DueRailScreenshotTest` geometry asserts re-pointed for the new order (rail under the field; Done on `card()`) — spec change, needs owner OK. Found: 52 goldens in `:feature:home`/`:composeApp` stale since `24ef642d` (old page bg) → main re-recorded
### Итерация 16 — 2026-10-17 — owner: dock B ok, the two re-pointed asserts ok, chat B; Cancel → main's call (same row, ghost chip); planner hides the keyboard
### Итерация 17 — 2026-10-17 — feature-expert: `ChatChoice.kind: ChoiceKind?` + `ChoiceOption.seedId: Long?` (checklist id → `GistiColors.avatarContent`), pure `ToolCall.choiceKind()` mapper, filled in 4 builders → `:feature:aichat:impl` 591 tests green
### Итерация 18 — 2026-10-17 — compose-expert: `QuickCaptureDock(inputSuspended)` — planner open clears focus + hides the keyboard, closing returns focus; Inbox/Calendar pass `plannerExpanded`, detail has no planner → `InboxCapturePlannerFocusTest` green (focus, not keyboard — Robolectric has no IME), three modules verify green

## Выводы

- Done: design system exported to Claude Design (artifact + claude.ai/design project "Gisti", folder gisti-ds (не проверено — lives in the claude.ai/design project, not in this repo)), three directions on a canvas, B implemented in Compose for tokens, DS components, bottom plate, Inbox, detail, chat. Decision record: `docs/decisions/2026-10-16-redesign-b-soft-tactile.md`; specs in `docs/designs/redesign-b-2026-10-16/`.
- Found: the store raw pass `StoreListingFramesScreenshotTest.a*` renders real screens with realistic data — the cheapest before/after of the whole app.
- Owner decisions closed: interactive onboarding defaults to separate completed (done, uncommitted — see handoff); MCP keeps `false` by choice; tab-label auto-shrink accepted.
- Owner review on device (2026-10-17): likes the new main screen (Inbox). Detail and chat not commented yet.
- Pass 2 (2026-10-17): Add-task capture sheet = composer card (B), planner hides the keyboard; AI chat = actions inside the reply (B), Cancel as a ghost chip in the same row (main's call, owner delegated), list choice as rows with the checklist's avatar colour, memory as a switch, empty state as one group of 4 prompts. Canvas: claude.ai artifact `U7BDsRtkE6pMDcVULMgdN5` (не проверено — lives on claude.ai, not in the repo); spec `docs/designs/redesign-b-2026-10-16/DESIGN_SPEC_B_pass2.md`.
- Found: pass 1 (`24ef642d`) changed the palette but left 52 goldens in `:feature:home`/`:composeApp` unrecorded — verify had been red since; a token change invalidates every snapshot of the module, not only the redesigned screens.
- Open (verification): web :9090 not run — owner deferred it (wasm dev compile OOM while shared Gradle daemons held memory); device checks still open — keyboard lift of the capture plate, chat bubble tap, mic permission flow, list scroll under the taller bottom plate on Calendar.

## Handoff — следующая сессия (2026-10-17)

State: worktree `.claude/worktrees/redesign-b-soft-tactile`, branch `worktree-redesign-b-soft-tactile`; commit `24ef642d` holds the redesign. Uncommitted on top: the interactive-onboarding default (`InteractiveOnboardingScreenContract.kt`, `InteractiveOnboardingViewModel.kt`, `InteractiveOnboardingViewModelTest.kt`) and this doc. Debug APK with all of it is installed on the owner's Pixel 9.

Next, in order:
1. Collect the owner's feedback on detail and chat from the device; fix through the same specialists (compose-expert for layout, feature-expert for state).
2. Free memory (owner closes heavy apps, or agrees to `./gradlew --stop`), then run the web dev server (`./gradlew composeApp:wasmJsBrowserDevelopmentRun --continuous`, :9090) and walk Inbox → detail → chat, incl. the pill mic in the browser permission prompt.
3. Re-run `/task-gate` lite on the increment since `24ef642d` (onboarding + any fixes); commit through `/commit`.
4. After owner confirmation: Step 6 — merge `origin/master` into the branch, re-validate, push, PR, squash-merge. ⚠️ merge to `master` auto-deploys the web to prod (CI); the Android release is separate (`/create-release`, `/build-release-aab`).
5. On merge: status `Done`, move this file to `docs/archive/`.
- Analytics: older replies' feedback row is now tap-to-reveal, so `ai_chat_feedback` volume may drop without a defect; the credits chip opens the pricing sheet, the paywall source series continues through "Get more".

## Предложения по улучшению агентов

- **@feature-expert:** when a fix splits one exit path into "dismiss keeps state" vs "close clears state", audit EVERY reset branch for every field the old close cleared — here the reopen-for-another-day branch kept staged attachments (caught only by the gate review).
- **@design-expert:** good pattern worth keeping — it surfaced its deviations from the owner-approved mock as an explicit list ("two calls change what the owner approved") instead of silently diverging; that made the owner override cheap.
- **@compose-expert:** a theme-token change re-records the WHOLE module's goldens, not only the redesigned screens' — pass 1 left 52 stale goldens red because verify was run per touched class. Brief line: "token change → `recordRoborazzi` + `verifyRoborazzi` on every module that renders the token".
- **@compose-expert:** when a measured constraint forces a spec deviation (20dp disc / `labelMedium` to keep four source pills in one row; one-line RU placeholder to avoid a mid-word break), it reported it — good; the brief should also ask to put such deviations into the spec file itself, so the Spec reviewer does not re-raise them as findings.
- **Parallel specialists in one worktree:** three runs hit transient compile failures from another agent's half-written file and burned retries; briefs for parallel agents should say "if a module outside your zone fails to compile, wait and re-run once, do not edit it" (all of them complied, but each rediscovered it).
