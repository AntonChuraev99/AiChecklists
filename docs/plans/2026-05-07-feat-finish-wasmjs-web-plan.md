# Plan — Finish wasmJs Web Target

**Branch:** `add-web-wasmjs-target`
**Created:** 2026-05-07
**Status:** Phase 1–3, 5, 5b, 6, 7, 7b complete. Phase 4 + Phase 8 + manual deploy remaining.

This file picks up exactly where the prior session left off. Hand it to the next agent verbatim.

---

## Current state (already shipped on this branch)

### Build infrastructure
- Kotlin 2.3.20, Compose Multiplatform 1.9.3, kotlinx-datetime 0.7.1
- `androidx.room3:room-runtime:3.0.0-alpha04` + `androidx.sqlite:sqlite-bundled:2.7.0-alpha04` for unified Room across Android+iOS+wasmJs
- 22 modules with `wasmJs { browser() }` target
- KSP wired for all 4 targets (`kspAndroid`, `kspIosArm64`, `kspIosSimulatorArm64`, `kspWasmJs`)
- `nonWasmMain` source set deleted — Room 3.0 supports all targets
- WebWorkerSQLiteDriver via OPFS for wasmJs persistence
- Custom `DataStore<Preferences>` impl backed by `localStorage` + JSON serialization (single-blob format `{"key":{"t":"B|S|I|L|F|D","v":<val>}}`)
- COOP/COEP/CORP webpack headers wired in `composeApp/webpack.config.d/sqlite.js`
- Build-time `generateWasmInitJs` Gradle task — reads `local.properties` and substitutes Firebase web config into `init.js.template` → `init.js` (gitignored)
- Custom dev-server port 9090 (avoids conflict with other Compose Multiplatform projects on 8080–8082)

### Firebase JS SDK facade (init.js)
- ESM imports from `https://www.gstatic.com/firebasejs/12.11.0/...`
- `globalThis.__rcGetString / __rcGetBoolean / __rcGetNumber` synchronous getters
- `globalThis.__rcFetchPromise` — async Promise<Boolean> resolving when `fetchAndActivate` completes (or fails gracefully with defaultConfig)
- `globalThis.__functionsCall(name, dataJson)` — `httpsCallable` bridge (not yet wired from Kotlin side)
- Loads `composeApp.js` dynamically AFTER bridges are defined
- `RemoteConfigFactory.wasmJs.kt` uses these bridges instead of stub

### Server-side (committed but not deployed)
- `firebase-functions/main.py` has CORS headers added to `register_user` and `restore_credits_after_purchase` HTTP functions
- **NOT YET DEPLOYED** — see manual steps below

### Tests (Playwright e2e in `e2e/`)
- 9 PASSED, 5 skipped (with documented justification), 0 failed
- Coverage: DataStore persistence, OPFS API, SharedArrayBuffer, init.js content, Firebase bridges, RC defaultConfig fallback, page reload, DOM inspection
- Skipped tests cover Compose canvas render (Playwright + Compose 1.9.3 26MB wasm bundle has a known wasm streaming truncation in both bundled Chromium and system Chrome via channel — user's regular Chrome renders fine, manually verified)

### What user has already verified manually in real Chrome
- ✅ App opens
- ✅ Onboarding renders
- ✅ Create checklist works
- ✅ Persistence between sessions (Room 3.0 OPFS confirmed)

### Known issues
- ❌ Onboarding completion flag not persisting — **FIXED in this session via custom DataStore localStorage impl**, needs user re-verification after they `git pull` and run dev-server
- ❌ Templates (47 from Remote Config) not loading on web — **REQUIRES `local.properties` keys** (manual step below)
- ❌ `register_user` CORS error — **REQUIRES Cloud Functions redeploy** (manual step below)

---

## Manual steps user must do before next dev session

### 1. Add Firebase Web config to `local.properties`
Get values from Firebase Console → Project Settings → Your apps → click the Web app `</>`. Add to `local.properties` at repo root:

```properties
FIREBASE_WEB_API_KEY=AIzaSy...
FIREBASE_WEB_AUTH_DOMAIN=aichecklists-40230.firebaseapp.com
FIREBASE_WEB_PROJECT_ID=aichecklists-40230
FIREBASE_WEB_STORAGE_BUCKET=aichecklists-40230.firebasestorage.app
FIREBASE_WEB_MESSAGING_SENDER_ID=27698629989
FIREBASE_WEB_APP_ID=1:27698629989:web:xxxxx
```

Defaults for `FIREBASE_WEB_PROJECT_ID` / `FIREBASE_WEB_MESSAGING_SENDER_ID` / `FIREBASE_WEB_AUTH_DOMAIN` / `FIREBASE_WEB_STORAGE_BUCKET` are pre-filled in `composeApp/build.gradle.kts` `generateWasmInitJs` task — only `FIREBASE_WEB_API_KEY` and `FIREBASE_WEB_APP_ID` are strictly required.

### 2. Deploy Cloud Functions
```bash
cd firebase-functions
firebase deploy --only functions:register_user,functions:restore_credits_after_purchase
```

This sends the new CORS-aware versions to production. Without this, web continues to fail anonymous user registration on first load (the app still works, but uses local-only anonymous deviceId).

### 3. Re-verify onboarding persistence
After `git pull` in the next session:
1. `./gradlew composeApp:wasmJsBrowserDevelopmentRun --continuous`
2. Open `http://localhost:9090/` in Chrome
3. Pass onboarding (select category → Skip)
4. Reload page
5. Expected: lands directly on Main, NOT onboarding again
6. If still landing on onboarding — DataStore localStorage write timing issue. Diagnostic: open DevTools → Application → Local Storage → `localhost:9090` → look for `gisti_user_datastore` key with `is_onboarding_passed` inside

---

## Phase 4 — AI Gemini + Sharing on web (NOT STARTED)

### Sub-task 4.1: AI Analyze (Gemini) on web

**Goal:** make the "Create via AI" flow work on web — user pastes text/uploads file, Gemini analyzes, returns structured checklist items.

**Strategy:**
- Generative AI Kotlin Multiplatform (`dev.shreyaspatil.generativeai:generativeai-google` 0.9.0-1.1.0) supports wasmJs target — verified.
- BUT direct Gemini REST calls from browser hit CORS (gemini-rest-api.googleapis.com doesn't allow `localhost:9090`). Must proxy through existing Cloud Function.
- **Action:** route web Gemini calls via `__functionsCall` to a new `proxy_gemini` Cloud Function (or reuse existing if any). Keeps API key server-side.

**Files likely affected:**
- `feature/analyze/src/wasmJsMain/.../GeminiAiAnalyzer.wasmJs.kt` — new actual that calls `__functionsCall("proxy_gemini", payload)` instead of direct REST
- OR `feature/analyze` adds wasmJs-specific binding in `platformModule()` that swaps `GeminiAiAnalyzer` for a `WebGeminiProxyAnalyzer`
- New Cloud Function: `firebase-functions/main.py` — `proxy_gemini(request)` that takes `prompt + parts` and returns Gemini response, validates user via Firebase Auth uid

**Acceptance:**
- User on web can paste text → AI generates 5–10 checklist items → renders as draft checklist
- User on web can upload a photo → AI extracts items
- API key never exposed to browser

### Sub-task 4.2: Sharing on web

**Goal:** export checklist as Plain Text (Web Share API) and PDF (`window.print()`).

**Files likely affected:**
- `feature/sharing/src/wasmJsMain/.../ShareLauncher.wasmJs.kt` — replace stub with real impl using `navigator.share({ title, text })` + clipboard fallback (`navigator.clipboard.writeText`)
- `feature/sharing/src/wasmJsMain/.../PdfGenerator.wasmJs.kt` — replace stub with HTML-rendered checklist + `window.print()` (uses browser's PDF print)
- Print CSS: hide nav/buttons, A4 page break rules, monochrome-friendly colors

**Acceptance:**
- "Share → Plain Text" on web → opens Web Share sheet (mobile browsers) or copies to clipboard with toast (desktop)
- "Share → PDF" on web → opens browser print dialog with cleanly-formatted checklist
- No fake/no-op stubs left in `wasmJsMain`

**Reference:** Swapface project at `C:\Users\Admin\StudioProjects\swapfaceandroid-web\` has production-ready `__uploadBytesToUrl`, `__uploadBlobUrlToUrl`, `__b64ToUint8`, `__uint8ToB64` helpers in `init.js` — useful for blob handling on Sharing flow.

---

## Phase 8 — Documentation (delegate to @doc-writer COMPLETE)

After Phase 4 done, run `@doc-writer COMPLETE` with hard scope guard. Persistent doc at `docs/solutions/features/wasmjs-target-2026-05-07.md`. Update `MEMORY.md` index. Update `docs/solutions/INDEX.md`. Update `~/.claude/stats/doc-writer.md`.

Pass to doc-writer:
- This plan as the active document
- `git diff --name-only <START_SHA>..HEAD -- ':(exclude)docs/*'` for changed-files context
- 5–7 lines key-findings (DataStore custom localStorage impl, Room 3.0 cross-target migration, Firebase JS SDK facade pattern, wasm streaming Playwright limitation, build-time secrets injection via Gradle template task)

Apply hard scope guard:
```
ЗАПРЕЩЕНО:
- git add / git commit / git push (никогда — только главный агент через /commit)
- ./gradlew build / assemble / compile / test
- Edit/Write файлов вне docs/, project memory, ~/.claude/stats/
Если задача требует выйти за это — return STATUS: REJECTED.
```

---

## Phase 9 — Production deploy to Firebase Hosting (NOT IN SCOPE OF NEXT SESSION)

After Phase 4 + Phase 8 are merged to master, separate session for hosting deploy:

```bash
./gradlew composeApp:wasmJsBrowserDistribution
firebase init hosting   # one-time, target = composeApp/build/dist/wasmJs/productionExecutable
firebase deploy --only hosting
```

Site will be at `https://aichecklists-40230.web.app/` (or custom domain). COOP/COEP/CORP headers configured via `firebase.json`:

```json
{
  "hosting": {
    "headers": [
      {
        "source": "**",
        "headers": [
          { "key": "Cross-Origin-Opener-Policy", "value": "same-origin" },
          { "key": "Cross-Origin-Embedder-Policy", "value": "require-corp" },
          { "key": "Cross-Origin-Resource-Policy", "value": "cross-origin" }
        ]
      }
    ]
  }
}
```

---

## Pitfalls noted during this session (warnings for future agent)

1. **`kotlinx-datetime` version must match Kotlin compiler stdlib version**: 0.7.x is built against Kotlin 2.3.20 stdlib. Mismatch causes "IrTypeAliasSymbolImpl already bound" linker error on wasmJs (wasmJs IR linker is stricter than JVM).
2. **DO NOT use `extraHTTPHeaders: { "Cache-Control": ... }` in `playwright.config.ts`** — that header gets sent to gstatic.com on Firebase ESM imports → CORS preflight fails → Firebase fully blocked.
3. **DO NOT set `Cross-Origin-Resource-Policy: same-origin`** on dev-server responses under COEP=`require-corp` — this blocks Compose's Skiko worker from fetching wasm. Use `cross-origin` instead.
4. **Don't use `--disable-cache`/`--disk-cache-size=0` Chrome flags in Playwright** — interferes with `WebAssembly.instantiateStreaming` for large bundles.
5. **`generateWasmInitJs` task must be config-cache-safe**: capture `rootProject.file("local.properties")` AT CONFIG TIME (outside `doLast`), not inside the task action. Otherwise Gradle config cache fails to serialize script object references.
6. **`wasmJsBrowserDevelopmentRun` requires `--continuous`** to keep webpack-dev-server alive. Without it, Gradle exits and dev-server with it.
7. **Compose 1.9.3 wasm streaming + Playwright = consistent truncation at 22081871 bytes**. Real Chrome works. Don't waste cycles trying to fix this in Playwright — skip render tests, use Compose UI Tests in commonTest for UI logic, manual Chrome verification for visual.
8. **Port conflicts** if user has Swapface project: that project takes 8080–8082. We pinned 9090 in `composeApp/build.gradle.kts` `commonWebpackConfig.devServer.port`.

---

## How to start next session

```
/work docs/plans/2026-05-07-feat-finish-wasmjs-web-plan.md
```

OR paste this file's contents to a fresh agent and tell them to start with Phase 4.1 (Gemini proxy) → Phase 4.2 (Sharing) → Phase 8 (doc-writer).

Verify before starting:
1. `git pull` on `add-web-wasmjs-target` branch
2. `local.properties` has Firebase Web config keys
3. Cloud Functions deployed (CORS update reached prod)
4. Onboarding persistence still works (after the DataStore fix from this session)
