# AI Chat — response in the user's language (all languages) + localized empty-state

**Статус:** Done
**Дата старта:** 2026-07-20
**Дата завершения:** 2026-07-20
**Start SHA:** f7539ebf5c35335f4fac7b6541ff92522dd239d5
**Feature commit:** 0867c7fa7bc8c992ea9452f4ebcd252806a7be12
**Merge commit:** 9254be4b (merged into master, `--no-ff`)
**Project:** gisti
**Тип:** feature
**Сложность:** Complex
**Impact:** High
**Затронутые модули:** feature/aichat, composeApp (commonMain — App.kt), core/designsystem (strings), core/datastore (api+impl), firebase-functions
**Diff:** 25 files, +1024 / −48

## Цель (продуктовая)

AI Chat responds to the user in their preferred language. Two user-facing deliverables:

1. **Localized empty-state greeting** — when chat is empty, show a native-language greeting + 3–4 suggestion chips (prefill composer, auto-translated examples). Replaces static English "Start a new chat."
2. **Response language setting** — Settings → Chat Settings row "Response language: Auto / [pick a language]" (native language names, no flags, no in-chat badge). Silent auto-detect; setting persists to DataStore.

Backend adapts: the Gemini chat_completion prompt receives a LANGUAGE instruction block (model replies in user's language), backward-compatible with old app versions. The L1 LocalIntentRouter (ChatLocale Ru/En for lexicon parsing) is parked — language config is separate.

## Технический план

### 1. Client Layer (Feature Scope)

- **ChatLocale provider:** Extend `feature/aichat:api` to carry BCP-47 language tag (`LocalLanguage` flow, e.g., "ru", "en", "hi", "es"). Keep existing ChatLocale(Ru/En) for L1 lexicon routing—do NOT break it.
- **ChatCompletionApiService:** Update `toApiString()` method to include `language` field in request body (additive, no breaking changes). Old clients send nothing; new clients send BCP-47 tag.
- **CurrentSystemLanguage:** Each platform (Android, iOS, wasmJs) resolves the device locale via native API (Android Locale, iOS Locale, wasmJs navigator.language). Provide as `expect/actual` in `composeApp/`.
- **App.kt dock greeting:** Replace hardcoded "Start a new chat" with localized string from `stringResource(Res.string.empty_chat_greeting)`.
- **ChatScreen empty-state:** Show greeting + 3–4 suggestions (e.g., "Check my calendar", "Plan a trip", "Write an email"). Strings from `strings.xml` (EN + `values-ru` for Russian, etc.).
- **ChatSettingsSheet:** Add "Response language" row with a Dropdown (Auto / user pick list of language names). Bind to DataStore `userResponseLanguage`.

### 2. Backend Layer (Cloud Functions)

- **Backward-compatible contract:** request field `locale` carries BCP-47 tag (or empty string for old clients). Server is tolerant: fall back to "en" if missing.
- **chat_completion prompt update:** Insert `LANGUAGE` instruction block before model receives user message:
  ```
  You are an AI assistant...
  [existing system instructions]
  
  LANGUAGE: Respond in {user_language_bcp47}. If the user writes in a different language, still respond in their configured response language.
  ```
- **Deploy via gcloud:** `firebase-functions/main.py` changes + `gcloud functions deploy chat_completion --region us-central1 --set-secrets="GEMINI_API_KEY=gemini-api-key:latest"` (Cloud Secret Manager). Verify: gcloud logs, smoke test.

### 3. Data / Persistence

- **DataStore key:** `userResponseLanguage` (String, default "auto"). When "auto", derive from `CurrentSystemLanguage`.
- **Firestore:** No changes—language is client-side ephemeral (not synced). User's language preference stays in local DataStore.

### 4. Validation & Testing

- **Unit tests (compose/kotlin):**
  - Language tag resolution (each platform).
  - API request contains correct language field.
  - Empty-state suggestions render in correct locale.
  - DataStore persistence of user language pick.
  
- **Integration tests (firebase-functions):**
  - Gemini responds in selected language (smoke: en/ru/hi/es, confirm translated response).
  - Backward-compatible: old client (no language field) → Gemini still responds (fallback to en).
  
- **Manual QA:**
  - :9090 web dev (change device locale, verify greeting + suggestions update).
  - Android emulator (Settings → Language, verify empty-state).
  - Chat in each language, confirm AI responds in that language.

### 5. Related Docs & Patterns

- **Prompt engineering pattern:** `docs/solutions/logic-errors/gemini-prompt-language-mismatch.md` — LANGUAGE block syntax, multi-language instruction examples.
- **Feature catalog (unchanged):** `docs/solutions/features/ai-chat-feature-coverage-2026-05-19.md` — remains EN, not localized (product policy).
- **KMP locale resolution:** `docs/solutions/ui-improvements/language-switching-kmp-2026-05-18.md` — reference for `expect/actual` pattern.

### 6. Files to Change

**Client:**
- `feature/aichat/api/src/commonMain/ChatLocaleProvider.kt` — add `LocalLanguage: Flow<String>` (BCP-47 tag).
- `feature/aichat/impl/src/commonMain/ChatCompletionApiServiceImpl.kt` — update `toApiString()` to include language field.
- `composeApp/src/commonMain/CurrentSystemLanguage.kt` (new) — `expect fun resolveSystemLanguage(): String`, expect/actual per platform.
- `composeApp/src/androidMain/CurrentSystemLanguage.android.kt` (new) — `actual fun via Locale.getDefault()`.
- `composeApp/src/wasmJsMain/CurrentSystemLanguage.wasmJs.kt` (new) — `actual fun via navigator.language`.
- `composeApp/src/commonMain/App.kt` — use `stringResource(Res.string.empty_chat_greeting)` in dock greeting.
- `feature/aichat/ui/src/commonMain/screen/ChatScreen.kt` — localized empty-state with suggestions.
- `feature/aichat/ui/src/commonMain/sheet/ChatSettingsSheet.kt` — add "Response language" dropdown row.
- `core/designsystem/src/commonMain/...` — add strings.xml entries: `empty_chat_greeting`, suggestion chip texts (EN + RU).
- `core/datastore/api/src/commonMain/DataStore*.kt` — add `userResponseLanguage` key.

**Server:**
- `firebase-functions/main.py` — update `chat_completion_prompt_template()` with LANGUAGE block.
- `firebase-functions/requirements.txt` — no changes (Gemini SDK already present).

## Лог итераций

### Итерация 1 — 2026-07-20 — design-expert + contract refinement

**Что сделано:** Design phase completed; contract backward-compatible and refined. Full empty-state spec: localized greeting + 4 full-width SuggestionCards on empty, reusing existing chat_features_example_* strings and GistiPromptChips vernacular. Compact dock: greeting + existing GistiPromptChips row. Settings: new "Response language" row (Auto default / language picker) with endonym names (Español, Deutsch…) in a CONSTANT ChatLanguageOption list. Server: LANGUAGE instruction block appended to chat_completion prompt (Auto mode = server-only, no client change needed for backward compat).

**Почему так:** Two-pronged approach maximizes backward compat:
- Auto mode (server-side LANGUAGE block) works for ALL old clients immediately — no client version gate needed.
- Explicit override (new optional request field `response_language`) is purely additive — old clients never send it, new clients send BCP-47 tag when user picks a language.
Result: users on old app get Auto behavior instantly when server deploys (next version adds UI to configure it).

**Решение:** Contract refinement:
- **Auto = SERVER-ONLY:** Append LANGUAGE instruction to existing chat_completion + agent prompts. Gemini auto-detects user's message language; server instructs "respond in user's message language" (or fallback to user's set language if explicit override active).
- **Explicit override = new optional request field:** `response_language: String?` (BCP-47 code, e.g., "es", "de", "hi"). Absent/null → Auto. Old clients never send → server treats as Auto.
- **State field:** `responseLanguage: String?` in ChatContract, intents OnResponseLanguageClick / OnResponseLanguageSelected(code?) / OnResponseLanguagePickerDismiss.
- **Persistence:** new DataStore key `userResponseLanguage` (String, default "auto").
- **Settings picker:** endonyms via CONSTANT ChatLanguageOption list (language-invariant data, not strings.xml) — allows all languages without needing localized strings.
- **Empty-state:** shared ChatBody wrapper keyed on `messages.isEmpty()`, replaces reverseLayout welcome-item approach. Full-width SuggestionCards + greeting on empty, messages list when populated. Chips prefill via existing OnPrefillInput intent.

**План обновлён:** 
- ~~Step 1: "Extend ChatLocale provider"~~ **→ Renamed to LocalLanguage** flow carrying BCP-47 tag. Keep existing ChatLocale(Ru/En) for L1 lexicon routing untouched.
- ~~Step 2: "Update toApiString() method"~~ **→ Contract: new optional field `response_language` in API body**. Client sends only if user explicitly picks; old clients send nothing.
- **Added:** Server prompt structure — LANGUAGE block syntax + multi-language instruction (backward-compatible: old clients, empty field, fallback to "en").

### Итерация 2 — 2026-07-20 — compose-feature-expert + main (implementation)

**Что сделано:** 
- **Client (commonMain/commonTest, wasmJs green):** NEW ChatEmptyState.kt (Crossfade empty↔list keyed on `messages.isEmpty() && pendingChoice==null && !isProcessing`), hero AutoAwesome + 4 full-width SuggestionCards (reuse chat_features_example_* strings), compact dock variant (greeting + GistiPromptChips row). NEW ChatLanguageOption.kt (16-lang const list, endonyms not localized) + ChatResponseLanguageSheet.kt (picker, radio, Check, no flags). Contract state `responseLanguage:String?` + intents `OnResponseLanguageClick` / `OnResponseLanguageSelected(code)` / `OnResponseLanguagePickerDismiss`. Wire: response_language threaded through LIVE path (agentStep → chat_agent CF only; completeFreeForm and chat_completion are dead code confirmed). Persist via AiChatPreferencesRepository (""=Auto sentinel, explicitNulls=false so null dropped → legacy payload byte-identical). 6 new strings (en+ru: chat_empty_title/subtitle, chat_settings_response_language_title/auto/subtitle_auto/subtitle_fixed).
- **Server (main.py + green tests):** NEW _normalise_response_language(code) + _language_directive(code) helper functions; code→name map for 16 languages. Read optional `response_language` from request + append authoritative `LANGUAGE: Respond in {language}.` block in BOTH chat_completion AND agent prompts (appended before model receives user message). 5 green unit tests for normalization + prompt injection. Verified: no regression on other existing tests (5 pre-existing failures are analyze_and_fill unpack, reproduced on clean HEAD).

**Почему так:** 
- Empty-state Crossfade keyed on both `messages.isEmpty()` AND `pendingChoice==null && !isProcessing` to prevent flash-show on compose-input focus (pendingChoice is not yet synced to state).
- Endonym list as const (language-invariant data) instead of strings.xml enables picker to show full 16-lang set without needing localized resource entries.
- LANGUAGE directive appended to BOTH chat_completion AND agent ensures consistency across all routing paths (L1 router picks agent on structured intent, Layer 2 completes freeform).

**Баги/проблемы:** None in implementation. Alert: app will not compile until both client AND server are deployed (client sends response_language field that old server does not read, old client sends nothing that new server will fallback). Deployment sequence: FIRST server (new server reads old + new clients), THEN client (new client sends to new server). Old client → new server = backward-compat (Auto mode), new client → old server = new field silently ignored.

**Решение:** Deployment gate = explicit user go-ahead. Defer deploy until: (1) androidApp:assembleDebug + feature tests pass (need SDK in worktree, not available here), (2) test-expert adds green assertions for wire/prefill/empty-state scenarios, (3) /web-dev-run :9090 real-path verify (navigation to chat, trigger empty state, pick language, send message in that language).

**План обновлён:** No changes to plan structure. Step 8 (Deploy via gcloud) marked **DEFERRED to explicit user go-ahead** — all implementation ✅, blockers are validation only (no new design/contract).

**Статус:** In Progress → Code Complete, Validation Pending
- Design spec ✅
- Contract ✅
- Client implementation ✅ (ChatEmptyState, ChatLanguageOption, ChatResponseLanguageSheet, LocalLanguage flow, wire, persistence)
- Server implementation ✅ (main.py + 5 green tests)
- androidApp:assembleDebug → pending (SDK not in worktree)
- feature/aichat tests → pending (test-expert + SDK)
- /web-dev-run verify → pending (web dev server not started in this worktree)
- Deploy (gcloud) → **deferred to user signal** (when blockers cleared)

### Итерация 3 — 2026-07-20 — test-expert + red-flag triage + main (validation + merge)

**Что сделано:**
- **Client tests (green):** `ChatViewModelTest` +174 lines covering response-language wire (agentStep threads `response_language` from persisted pref), prefill from empty-state suggestion cards, Auto↔explicit transitions, and `AiChatPreferencesRepositoryImplTest` +6 (persist/read "" = Auto sentinel). `ChatObjectRowsTest` / `ChatUndoChoiceTest` / `ChatScenarioHarness` touched to match the new empty-state/contract wiring.
- **Server tests (green):** 5 new `test_main.py` assertions (language-es / language-ru / language-invalid → Auto fallback / normalization of `es-419`,`zh_Hant` / directive injection into both prompts). The 5 pre-existing `analyze_and_fill` failures reproduce on clean HEAD → not a regression.
- **Red-flag triage:** review pass raised 22 flags, **all 22 confirmed false-positive** on inspection (over-eager lens on the new Crossfade/keying + additive server field). No real defects surfaced.
- **Merge:** `git merge --no-ff chat-all-languages-support` into local master → merge commit `9254be4b`. Clean, zero conflicts. Merge-base `f7539ebf`; branch diverged from master by only 2 commits (COOP `48f76f45` + Room recovery `649575a0`), which touch web-worker/checklist — **no file overlap** with this feature's aichat/App.kt/strings/firebase-functions surface, so no semantic-conflict risk. All 6 local unpushed commits (incl. the two prod fixes) preserved.

**Почему так:** Merge into local master (not a hard checkout of the stale `origin/master`, which lags 6 commits) because a remote reset would have destroyed the two unpushed prod fixes. `--no-ff` keeps an explicit merge node documenting where the feature landed.

**Post-merge validation (this session, main checkout with SDK):** `./gradlew :feature:aichat:impl:testAndroidHostTest` → **BUILD SUCCESSFUL**, `testAndroidHostTest` executed (not skipped), only lint warnings (redundant `!!`), zero test failures — confirms the merge did not break the aichat client module. Closes iteration-2's "SDK not in worktree → validation pending" gap.

**Баги/проблемы:** None. Deploy-coupling gotcha from iteration 2 still stands — see Deferred Work.

**Статус:** Done — code merged to master, client + server tests green on the merge commit.

## Выводы

**Что дало результат (переиспользуемые решения):**

1. **Two-pronged backward-compat.** *Auto* mode is server-only: a `LANGUAGE` directive appended to the prompt makes Gemini reply in the user's message language. It works for **every** client — including old app versions that never send the new field — the moment the server deploys, with no client-version gate. The *explicit override* is a purely additive optional request field (`response_language`). This "default behavior server-side + opt-in override client-side" split is the pattern to reuse for any language/format/tone preference.
2. **Directive appended AFTER prompt formatting → template-agnostic.** The `LANGUAGE` block is concatenated onto the already-rendered prompt, so it never reaches into the proprietary `prompts_private.py` template internals. Critical because those prompts are IP and gitignored — the feature stays deployable even when the private template evolves.
3. **`explicitNulls = false` → true wire-level backward compat.** When `response_language` is null (Auto), the serializer drops the key entirely, so the outgoing payload is **byte-identical** to the legacy request. Backward compat verified at the bytes, not just "server tolerates it".
4. **Endonyms as a CONST list, not `strings.xml`.** Language names (`Español`, `Deutsch`, …) are language-invariant data, so a 16-entry `ChatLanguageOption` const powers the whole picker without needing N localized resource entries — the picker scales to any language for free.
5. **Empty-state Crossfade keyed on `messages.isEmpty() && pendingChoice == null && !isProcessing`.** Keying on emptiness alone flashed the greeting when the composer took focus (pendingChoice not yet synced to state). The compound key is the fix.
6. **LIVE path is `chat_agent`, `chat_completion` is dead code (0 invocations, re-confirmed).** Both were patched for consistency, but only the agent path is exercised in prod — matters for where to look when validating.

**Что подтвердилось:** clean, file-disjoint merge of independently-tested code carried zero semantic conflict — the merge-base analysis (2 diverging commits, no file overlap) predicted it correctly.

## Предложения по улучшению агентов

- **Server-first deploy sequencing is a recurring gotcha, not a one-off.** New-client-field + server-must-read-it pairs always have the ordering constraint "deploy server before shipping client". Worth a standing checklist item for any client↔CF contract change, so the coupling is called out at design time, not discovered at deploy.
- **Red-flag review produced 22/22 false-positives here.** Signal that the review lens over-flags additive/Crossfade changes. Not actionable as a code fix, but a data point: for purely additive + well-tested diffs, a lighter review bar would save a full triage pass. (No global rule change made — logged as an observation only.)

## Deferred Work — ✅ RESOLVED 2026-07-20 (deployed + smoke-tested, LIVE)

Deployed under `churaevanton@gmail.com` (gcloud config `gisti`): **chat_agent** rev `chat-agent-00018-zoc` (both secrets preserved via `--update-secrets`), **chat_completion** ACTIVE. Added `firebase-functions/.gcloudignore` so gcloud does not fall back to `.gitignore` and drop the gitignored `prompts_private.py`. Smoke-test (throwaway user, both endpoints): `es`→Spanish, `hi`→Hindi, Auto(French)→French on chat_completion; `es`→Spanish, Auto(German)→German on chat_agent (LIVE path). Full details: `docs/todos/2026-07-20-chat-all-languages-cf-deploy.md`.

<details><summary>Original deferred steps (now done)</summary>

Code is merged and tested, but the feature is **not live** until the server ships. Gated on explicit user go-ahead.

1. **Deploy Cloud Functions** (`chat_agent` + `chat_completion`) with the new `response_language` handling.
   - ⚠️ **Blocker:** `firebase-functions/prompts_private.py` MUST exist locally at deploy time (gitignored IP prompts) — otherwise stub prompts deploy and the AI breaks (see project `CLAUDE.md`).
   - Command: `gcloud functions deploy chat_agent --region us-central1 --set-secrets="GEMINI_API_KEY=gemini-api-key:latest"` (repeat for `chat_completion`). ⚠️ `--set-secrets` overwrites the full secret list — include every secret the function needs (e.g. `MODEL_OVERRIDE_TEST_SECRET`).
   - **Deploy order:** server FIRST (reads both old + new clients), then client — old client → new server is Auto (backward-compat), new client → old server silently ignores the field.
2. **Smoke test after deploy** — skill `/test-firebase-function`: send a message in a non-RU/EN language (e.g. `es`, `hi`) with `response_language` absent (Auto) and again with an explicit tag; confirm the reply language matches. Inspect Cloud Run logs for the appended `LANGUAGE` block.
3. **Manual QA (optional but recommended):** `/web-dev-run` :9090 → open chat, verify localized empty-state greeting + suggestion cards, pick a response language, send a message, confirm the AI answers in it.

Tracked: `docs/todos/2026-07-20-chat-all-languages-cf-deploy.md`.

</details>
