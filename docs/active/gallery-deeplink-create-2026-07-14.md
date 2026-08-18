# Gallery deep-link → create-from-template — Design & Contract

**Date:** 2026-07-14 · **Status:** Partially Done
**Start SHA:** 30e0d761 (2026-07-14 feat(seo): add checklist gallery + indexing foundation)
**Complexity × Impact:** Complex × High · cross-platform (commonMain + androidMain + wasmJsMain + web infra)

## Locked decisions (user, 2026-07-14)

1. **Seed source = Firestore** (not AI, not bundled). Templates copied to a public `gallery_templates/{slug}` collection; app reads it and creates the checklist **as-is** (deterministic, **no AI credit**).
2. **Deep-link id = slug**, carried on the CTA already: `https://app.gisti-ai.com/?g=create&template={slug}&utm_*`. Same slug is the Firestore doc id and the gallery URL slug.
3. **Open-in-app = claim `app.gisti-ai.com` (App Links).** SEO gallery pages live on `gisti-ai.com` (landing) → NOT claimed → stay in the browser (dwell-time/index safe). Only the app-host CTA opens the native app when installed.
4. **Create as-is preserves notes.** Gallery items carry per-item notes; these must survive into the created checklist.

## Firestore foundation — DONE

- Collection `gallery_templates/{slug}` seeded (50 docs) via `landing-src/checklists/seed-firestore-templates.mjs` (SA JWT + REST, idempotent; re-run to sync as the gallery grows). Source: `landing-src/checklists/gallery-templates.seed.json`.
- **Doc shape:** `{ slug, category, title, ordered:bool, items:[ {text, note?} ] }`.
- **Rules (deployed):** `match /gallery_templates/{templateId} { allow read: if true; allow write: if false; }` — public read, no client write, top-level (NOT under `users/{uid}`). See `firestore.rules`.

## Data contract (commonMain)

```kotlin
@Serializable data class GalleryTemplateSyncData(
    val slug: String, val category: String = "", val title: String,
    val ordered: Boolean = false, val items: List<GalleryTemplateItemData> = emptyList())
@Serializable data class GalleryTemplateItemData(val text: String, val note: String? = null)
```
Place next to `ChecklistSyncData` (`feature/checklist/.../data/sync/`).

## Firestore read — new method on `FirestoreSyncDataSource`

```kotlin
suspend fun fetchGalleryTemplate(slug: String): AppResult<GalleryTemplateSyncData?>   // null = not found
```
- **Android** (`AndroidFirestoreSyncDataSource`): `FirebaseFirestore.getInstance().collection("gallery_templates").document(slug).get().await()` → map fields → model. Mirror `fetchAllChecklists`.
- **Web** (`WasmFirestoreSyncDataSource`): reuse the already-wired `jsFirestoreGetDoc("gallery_templates", slug)` (`__firestoreGetDoc` in `init.js.template:1070`, `{ok,data}` envelope) → decode. **No JS change needed.**
- **iOS** (`IosFirestoreSyncDataSource`): stub `Error(...)` like its siblings.

## UseCase (feature/create domain) — mirror `CreateWeeklyChecklistUseCase`

```kotlin
class CreateChecklistFromGalleryTemplateUseCase(fetch, checklistRepository) {
  sealed interface Result { data class Created(val checklistId: Long); object NotFound; data class Error(val cause) }
  suspend operator fun invoke(slug: String): Result
}
```
Logic: `fetchGalleryTemplate(slug)` → null → `NotFound`; else build `Checklist(name=title, items=items.map { ChecklistItem(text = it.text, checked = false) })` (respect `ordered` = list order) → `checklistRepository.addChecklist(...)` → **then preserve notes**: `addChecklist` auto-creates the default fill with `note=null`; load it (`getDefaultFillByChecklistId`), set each fill item's note by **index** (or `templateItemId`), `updateFill(...)`. Return `Created(id)`. Register in `CreateFeatureModule`. NB `addChecklist` already creates the default fill — do NOT also `addFill`. (recurring: [[checklist-detail-optimistic-state-sync]])

## Web entry (wasmJsMain)

`main.kt:14` — parse `kotlinx.browser.window.location.search` for `g=create` + `template=<slug>` at boot; stash. Convention: ViewModel/handler side-effect → `App.kt` `LaunchedEffect` → invoke UseCase → `appNavigator.navigateToChecklistDetail(id, clearBackStack = true)`. Preserve UTM (verify Amplitude captures). Unknown slug/error → graceful land on main + **snackbar** (visible feedback, no silent skip). One-shot guard so recompose doesn't re-fire.

## Android entry (androidApp + androidMain)

- **Manifest** `androidApp/src/main/AndroidManifest.xml` (MainActivity block): add `<intent-filter android:autoVerify="true">` VIEW/DEFAULT/BROWSABLE + `<data android:scheme="https" android:host="app.gisti-ai.com"/>`. (Query can't be matched in a filter → claim host; app checks `g=create` in code.)
- **MainActivity** (`composeApp/src/androidMain/.../MainActivity.kt`): read `intent.data?.getQueryParameter("g"/"template")` in `onCreate` (cold, stash-and-consume like `pendingProcessText`) + `onNewIntent` (warm) → same UseCase → `navigateToChecklistDetail`.
- **applicationId = `com.antonchuraev.aichecklists`** (assetlinks `package_name`; NOT namespace `.app`).

## assetlinks.json (web infra — app worker)

Serve `https://app.gisti-ai.com/.well-known/assetlinks.json` from the `checklists` worker `src/redirect.js` (`run_worker_first:true` → survives wasm rebuilds). Shape:
```json
[{"relation":["delegate_permission/common.handle_all_urls"],
 "target":{"namespace":"android_app","package_name":"com.antonchuraev.aichecklists",
  "sha256_cert_fingerprints":["<PLAY_APP_SIGNING_SHA256>"]}}]
```
⚠ **BLOCKER:** SHA-256 = **Play App Signing key certificate** (Play Console → Release → Setup → App integrity), because AAB upload = Google re-signs. Local keystore SHA is only the upload key (add as a 2nd fingerprint to also verify local `installRelease`).

## Execution order (DELIVERED)

1. ✅ Firestore: rules + seed (done).
2. ✅ **commonMain foundation** (@compose-feature-expert): model + interface method + UseCase + DI + iOS stub + App.kt side-effect wiring.
3. ✅ **Parallel** — @android-platform-expert (Android impl + App Links + MainActivity) ∥ @wasmjs-expert (Web impl + main.kt entry).
4. ✅ assetlinks worker + get Play SHA (2 fingerprints: Play app-signing + upload key) + deploy; **verified web on :9090 END-TO-END** (Paris checklist: 18 items + all notes auto-created, no AI credit).
5. ✅ **GEO Phase 4** — JSON-LD consolidation: @graph with WebPage/Organization/WebSite/ItemList/HowTo/FAQ cross-references + honest datePublished/dateModified per-file + byline. Deployed `gisti-landing` v`99c852db`, IndexNow 200/63 URLs.
6. ✅ **Prod verification** — assetlinks.json 200 (2 fingerprints); deep-link URL 200; web gallery live at `gisti-ai.com/checklists/`; Firestore templates seeded 50 docs.

## Лог итераций

### Итерация 1–7 — 2026-07-14 — specialist chain (compose+android+wasmjs experts + design-expert)

**Что сделано:** 
- GalleryTemplateSyncData model (commonMain) + Firestore rules + 50-doc seed (public-read, top-level, idempotent).
- FirestoreSyncDataSource.fetchGalleryTemplate() — Android (Firebase), Web (jsFirestoreGetDoc), iOS stub.
- CreateChecklistFromGalleryTemplateUseCase — preserves per-item notes via index-based fill-item patching (anti-regression: notes live on fillItem, not item; silent loss if addChecklist alone).
- Android App Links: intent-filter + MainActivity cold/warm intent parsing; assetlinks.json via worker src/redirect.js (2 fingerprints: Play app-signing SHA + upload key).
- Web entry: main.kt query-param parsing (`g=create&template=<slug>`) → side-effect LaunchedEffect → UseCase → navigate + graceful error snackbar (unknown slug).
- **GEO Phase 4:** JSON-LD @graph consolidation (WebPage/Organization/ItemList/HowTo cross-refs by @id) + per-file datePublished/dateModified + Gisti-curated byline; IndexNow sync; 61 pages LIVE.
- **Verification:** web :9090 END-TO-END (Paris checklist, 18 items, notes intact); assetlinks 200; prod URLs 200; Firestore seed idempotent.

**Почему так:**
- Firestore for gallery = deterministic, no AI credit, immutable (user decision at seed-time, not generation-time).
- Deep-link slug = common ID (URL/Firestore/query-param) — simplifies implementation + future analytics.
- Notes preservation via addChecklist + fillItem patch = maintains data integrity (notes not silently dropped); mirrors existing fill-creation pattern.
- App Links auto-verify only on NEXT Play release (Play rewrites AndroidManifest; app stores SHA statically, can't update without Play sign-off). Current release ships the intent-filter code but assetlinks verification activates only after Play ships it.
- GEO consolidation = anti-abuse defense (Google scaled-content warnings hit templated galleries; one @graph + per-page info-gain + canonical + byline reduces false-positive hits).

**Статус итерации:** Partially Done (features shipped, Android Play release deferred).

## Выводы

### Features Shipped
**Web + GEO:** SEO gallery LIVE at `gisti-ai.com/checklists/` (61 pages: 50 checklists across 10 categories + hubs + index). JSON-LD consolidated into single @graph (WebPage/Organization/ItemList/HowTo/FAQ cross-referenced by @id). Honest datePublished/dateModified per-file (NOT build-stamped). Byline "Curated by the Gisti team". IndexNow submitted 200/63 URLs. Sitemap + robots.txt deployed Phase 0.

**Deep-link:** Public Firestore `gallery_templates/{slug}` seeded 50 docs (idempotent seed script). Web entry (main.kt query parsing) ✅. Android foundation + assetlinks.json ✅. Web verified END-TO-END on :9090 (Paris → 18-item checklist, all notes, no AI credit).

### Critical Anti-Regression Won
1. **Notes preservation pattern:** addChecklist creates default fill with `note=null` automatically. Without explicit fillItem note-patching by index, notes silently dropped. UseCase does patch explicitly (mirrors [[checklist-detail-optimistic-state-sync]] pattern: load default fill → patch notes → update).
2. **assetlinks two-fingerprint pattern:** Play App Signing SHA (from `androidpublisher.generatedapks.list` API) + upload key SHA (from Play Console "Release → Setup"). Only one of the two would fail verification if not included.
3. **Worker persistence:** assetlinks.json served from `src/redirect.js` (`run_worker_first:true`) — survives wasmJs rebuilds. Hostage mistake: assetlinks in a bundled static route gets clobbered on each build.
4. **GEO abuse defense:** Templated galleries hit Google "scaled content" warnings; defense is NOT more schema/filler (FAQ killed by Google, HowTo deprecated for many use-cases), but honest per-page dateModified + information-gain per template (category + notes + task count differentiation). One shared @graph (not per-page) + internal cross-refs reduce validator false-positives on small template variations.

### Deferred → Next phase
- **Android Play release** (1.17.17+): carries intent-filter code + AndroidManifest to Play servers; Play rebuilds APK → assetlinks auto-verification activates. App Links deep-link then becomes live on devices with app installed (web browsers stay in browser, never App Links-claimed because SEO pages on root domain).
- **MCP registry + directory submissions:** playbook in `docs/marketing/mcp-directory-submissions-2026-07-14.md` (interactive GitHub OAuth for registry; manual form fills for OpenAI, Anthropic, Claude Marketplace directories). Requires human review/approval on each directory (low priority, scale = 1 per dir, not blocking).
- **Gallery scale:** current 50 checklists. As more templates added, watch for Google "scaled content abuse" false-positives (use Search Console Coverage → Manual Action → reason). Risk is low (each template has `category + title + ordered + items = unique structuring`), but if scaling to 500+, log per-template info-gain metrics (item count, category cardinality, description length) and adjust byline/schema.

### Commits
- `8214ee90` — commonMain foundation (Firestore read, UseCase, DI, App.kt wiring, Android/Web entry stubs)
- `64e5e701` — GEO Phase 4, assetlinks, web :9090 verified, templates seeded

### Files changed (84 total)
- Landing generator + 61 pages (`landing-src/checklists/generate.mjs`, `landing-src/checklists/data/checklists/*.json`)
- Firestore (`firestore.rules`, `seed-firestore-templates.mjs`, `gallery-templates.seed.json`)
- Server JSON (`mcp-server/server.json`)
- commonMain model/UseCase/holder/App.kt/DI/strings (`core/common/api`, `feature/create`, `composeResources/strings.xml`)
- Android/wasmJs Firestore impls, MainActivity, AndroidManifest, test fakes
- Web assetlinks redirect handler (`src/redirect.js`), iOS stub

## Предложения по улучшению агентов

- [ ] **compose-feature-expert:** Document the note-preservation pattern for create-from-template flows (UseCase fetches → patching fillItem notes by index, not relying on ViewModel side-effect). Current pattern (addChecklist + patch) mirrors optimistic-state-sync, but no explicit guidance when template items carry custom fields beyond `text`.
- [ ] **android-platform-expert:** App Links deep-link verification depends on Play Console build (Play app-signing + assetlinks.json both required). Document the two-SHA pattern (Play app-signing vs. upload key) + asset-links timing (intent-filter code ships before Play re-signs, so auto-verify happens on NEXT release, not current).
- [ ] **wasmjs-expert:** init.js `jsFirestoreGetDoc` reuse pattern works well for gallery templates. Document the `{ok, data}` envelope + null-check pattern for optional-resource reads (templates, etc.).
