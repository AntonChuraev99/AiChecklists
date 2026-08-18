---
title: SEO gallery deep-link — app-side `?g=create&template={slug}` create handler
date: 2026-07-14
status: resolved
resolved_date: 2026-07-14
resolution: "BUILT + shipped. Web handler (main.kt query-param → CreateChecklistFromGalleryTemplateUseCase reads Firestore gallery_templates → create as-is with notes) verified END-TO-END on :9090 (Paris: 18 items + notes, no AI credit) and prod (deep-link URL 200). Android App Links (intent-filter + MainActivity + assetlinks.json worker) code+build green — auto-verify activates on next Play release (v1.17.17+). Commits 8214ee90 + 64e5e701. Seed source = public Firestore (user decision), NOT AI/bundled. Solution doc: docs/solutions/gallery-deeplink-geo-schema-consolidation-2026-07-14.md"
blocking_reason: scope-boundary (Tier-1 gallery pilot shipped; app-side handler is a separate @wasmjs-expert task)
resume_trigger: "User says «доделай deep-link галереи / чтобы кнопка Use this checklist создавала чеклист / gallery CTA handler» OR when scaling the gallery and wanting the CTA to actually seed a checklist"
estimated_complexity: Standard
keywords: [seo-gallery, deep-link, wasmjs, create-checklist, utm, app.gisti-ai.com, query-param]
---

# SEO gallery deep-link create handler — Deferred

## Context

The Tier-1 SEO checklist gallery (live 2026-07-14, `gisti-ai.com/checklists/`) has a primary CTA on every detail page:

```
Use this checklist in Gisti → https://app.gisti-ai.com/?g=create&template={slug}&utm_source=gallery&utm_medium=organic&utm_campaign=checklist_detail
```

**Currently the `g=create&template={slug}` params are NOT handled app-side** — the link safely falls back to just opening the web app (no broken link, no error). The intended UX (click → app pre-creates/opens that exact checklist as a seed) is not wired.

## Task (when resumed)

Owner: `@wasmjs-expert` (+ `@compose-feature-expert` for the create flow) — the app is wasmJs Compose on `app.gisti-ai.com`.

1. On app load, read `location.search`; if `g=create` + `template=<slug>`, resolve the slug to seed content and route into the create/preview flow pre-filled.
2. Source of the seed: either (a) bundle the gallery `data/checklists/*.json` (or a slug→title map) so the app knows the items, or (b) have the CTA carry enough to trigger an AI create from the title. Decide — (a) is deterministic + free, (b) costs an AI credit.
3. Preserve UTM for attribution (gallery → install/signup funnel). Confirm Amplitude captures `utm_*`.
4. Strip/validate the slug (no injection); unknown slug → graceful open (current fallback).

## Verify

- Real path on `:9090` (`/web-dev-run`): open `app.gisti-ai.com/?g=create&template=5-day-paris-packing-list` → the Paris checklist seeds. Never debug via prod CI.
- `compile*` + `commonTest` do NOT cover the JS-interop `location.search` path — exercise the live flow.
