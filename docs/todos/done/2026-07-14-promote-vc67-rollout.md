---
title: Promote vc67 (1.17.17) rollout to 100% after health check
date: 2026-07-14
status: resolved  # 2026-07-28: moot — vc67 long superseded in production by vc76 (1.18.5). Nothing left to promote.
blocking_reason: ""
resume_trigger: "N/A — resolved 2026-07-28 as moot."
estimated_complexity: Trivial
keywords: [play-rollout, vc67, 1.17.17, staged-rollout, crashlytics, promote, google-play, deep-link]
---

# Promote vc67 (1.17.17) to 100% — Resolved (moot)

## Resolution — 2026-07-28

**Closed as moot, not as done.** The decision this task was waiting on can no longer be made: vc67 was
overtaken by later releases and is nowhere near the production track any more. Play API read on
2026-07-28 (`edits.insert → tracks.list → edits.delete`, no commit):

```
production  1.18.5 (76)   status=completed   (100%)
beta        1.5 (6)
alpha/internal 1.17.10 (60)
```

So there is no staged 20% left to widen — production moved 67 → 70 → 74 → 75 → 76 in the meantime.
The *underlying* concern (the Android gallery deep-link path was build-verified only, never device-tested)
did **not** evaporate with the rollout question, and it has since produced a real finding: the
`gallery_deeplink_opened` counter double-counts arrivals because the event is emitted before the suspend
point in a restartable collector. That is tracked separately in the 2026-07-28 healthcheck fixes, not here.

## Context

1.17.17 (vc67) shipped **2026-07-14** to production **staged 20%** — carries the gallery deep-link
(`?g=create&template={slug}` → create-from-Firestore-template) + Android App Links intent-filter for
`app.gisti-ai.com` (assetlinks.json already live). vc66 (1.17.16) holds the un-rolled 80% as the
`completed` base.

⚠️ App is **below Play's volume/privacy threshold** → Play's numeric crash%/ANR% rate is
**unavailable**; rollout decisions rest on **Crashlytics raw counts + Play error reports**, not
measured rates. (project memory `play-gcs-stats-read-as-owner-not-sa`, release-rollout precedents)

The Android deep-link path (MainActivity `intent.data` parsing → `PendingGalleryDeepLink` → UseCase)
was **build-verified only** — the web equivalent is runtime-verified (:9090 + prod), Android was not
device-tested. 20% is the conservative canary for exactly this.

## Task (when resumed, ~2026-07-15/16)

1. **Crashlytics** (Firebase MCP `crashlytics_list_events` / console) — NEW crashes on **vc67**
   over the staged window, especially: MainActivity intent parsing, App Links VIEW-intent path,
   `CreateChecklistFromGalleryTemplateUseCase` (Firestore read / fill-note patch). Compare vs vc66 baseline.
2. **Play Console → Production** — vitals / error reports for vc67.
3. **App Links** — `adb shell pm get-app-links com.antonchuraev.aichecklists` on a device with vc67 →
   `app.gisti-ai.com` should be `verified`. Tap-test the deep-link (cold + warm) → creates exactly one checklist.
4. Clean → **promote vc67 to 100%** via `@google-play-console-expert` (**promote-only**: keep vc67
   `completed`, drop the vc66 base — NOT a `tracks.update` collapse). Crashes → halt + diagnose (route to bug slice).

## Verify

- Play Console Production shows **vc67 @ 100%**, vc66 superseded.
