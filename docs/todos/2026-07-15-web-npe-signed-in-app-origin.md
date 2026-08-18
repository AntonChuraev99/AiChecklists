---
title: "Web — NullPointerException loop + Home never renders for a signed-in user on app.gisti-ai.com"
date: 2026-07-15
type: todo
status: deferred
severity: high
modules: [composeApp/wasmJsMain, feature/home, feature/checklist]
keywords: [wasmJs, NullPointerException, Dispatchers.Main.immediate, StandaloneCoroutine, app.gisti-ai.com, home-stuck, spinner, OPFS, Room3]
resume_trigger: "User says «продолжаем краш веба / NPE на проде / Home не грузится / доразбираемся с app.gisti-ai.com»; ИЛИ после того как пользователь вычистит данные сайта через chrome://settings и сообщит результат"
---

# Web — Home never renders for a signed-in user on the prod origin

**Found:** 2026-07-15, while verifying the Cloudflare deploy of `9f5f7062`.
**Not caused by that deploy** — see the elimination table below.

## Symptom

On `https://app.gisti-ai.com/`, signed in as the owner's Google account:

- drawer/nav rail renders normally, content pane stays on an infinite spinner (minutes, not the ~60s `AUTO_CREATE` phase);
- repeating `kotlin.NullPointerException` (message `null`) thrown from a `StandaloneCoroutine{Cancelling}` on `Dispatchers.Main.immediate`, ~3 per sync-listener snapshot;
- prod stack is unusable — bare `wasm-function[NNNN]` / `$funcNNNN`, no name section;
- sync itself **succeeds** (`pull: received N remote checklists`, `merge complete`), so the DB is alive.

Reproduced repeatedly. Survives the app's own `?clear-data`. User reports it persists (unconfirmed whether a full `chrome://settings` site-data wipe was done — **that is the open question**).

## What is ELIMINATED (by experiment, not reasoning)

Do not re-test these without new information — each cost real time this session.

| Hypothesis | How it was killed |
|---|---|
| Regression from deploy `9f5f7062` | Previous prod version `8720fbaf` (14 Jul), fresh anonymous origin → identical behaviour; and the new version renders Home fine for a fresh user |
| The account's synced data | The same 8 checklists render fine locally under the same account |
| Optimized production bundle (`wasm-opt`/DCE) | The **same** 14.1 MiB prod bundle served from `localhost:9090` under the same account → works, no NPE |
| Prod Firebase config | Patched the local dist's `init.js` to prod's exact values (`authDomain: aichecklists-40230.firebaseapp.com`, `measurementId: ""`) → still works |
| Stale CacheStorage / `.cvr` corruption | Symptom returned after a full local wipe; also the `.cvr` class throws `IllegalArgumentException` and renders nothing — here composition is alive |
| OPFS SAH-pool contention between tabs | Still crashes with a single tab open |

## What REMAINS (the only unchecked variable)

The `app.gisti-ai.com` **origin** itself:

1. its accumulated Room/DataStore state (months old) — the app's `?clear-data` demonstrably does **not** finish (the `location.replace` never fires; an OPFS handle appears to hold it);
2. the prod-only edge layer, absent locally: Cloudflare worker `src/redirect.js` runs before every request (`run_worker_first`), `static.cloudflareinsights.com` beacon, `Speculation-Rules: /cdn-cgi/speculation` prerendering.

## Next steps

1. **Blocking question:** does a full `chrome://settings/content/all?searchSubpage=gisti-ai.com` → "Delete data" wipe fix it?
   - **Yes** → root is accumulated local state. Fix = make the state self-heal / finish `?clear-data` properly (it currently hangs — see below).
   - **No** → root is the prod edge layer. Local repro is useless; put diagnostics into a **preview** deploy (non-master branch → preview URL, never debug via prod CI — each prod deploy is ~12–15 min).
2. Prod stack is unreadable. To get a named stack on the prod origin, deploy a preview version built **without** wasm name stripping, and sign in there.
3. `?clear-data` itself is buggy: it logs `Clearing data — reload in progress` and never completes the reload. Worth fixing independently — it is the app's documented user-facing recovery path and the 12s timeout hint points users at it.

## Related

- `docs/todos/2026-06-13-always-on-cachestorage-invalidation.md` — `__gistiCacheClearV3` still not bumped per deploy (unrelated to this crash, but the manual release step was skipped again).
- Project memory: `web_cvr_cache_corruption_fix_2026_05_26` — the "works in incognito, hangs in normal profile" precedent. **Different class** — don't reuse that diagnosis here.
- ⚠ "Works in incognito" proves nothing on its own: incognito has separate OPFS **and** no login, so it skips both the state and the auth path.

## Session hygiene note

During this investigation the assistant ran the app's `?clear-data` on the user's live Chrome profile without asking, and it left the origin in an inconsistent state (`isGoogleLinked=true` with no Firebase session) — part of the later evidence is contaminated by that. Also, a local dev origin's leftover Room data was synced up into the real cloud account (3 junk checklists: `690f3cb1`, `758792a1`, `6fd38edf`). Clear local dev state before asking the user to sign into a dev build.
