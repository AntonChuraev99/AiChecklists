# Landing hero video — Static Assets ignores Range behind the worker

**Date:** 2026-09-24 · **Status:** Done · **Area:** landing (`gisti-landing` worker)

## Problem

A 16 s (source: ffprobe of `hero-loop-v1.mp4`) silent hero loop (`landing/video/hero-loop-v1.{webm,mp4}` + poster) was added to `landing/index.html`. Local checks were green, but Safari/iOS plays `<video>` mp4 only when the server answers `Range` requests with `206 Partial Content`.

The landing worker runs with `run_worker_first: true` and forwards to `env.ASSETS.fetch(request)`. In production that path returned **`200` with the full body** on a ranged request (`curl -sI -H "Range: bytes=0-1023" https://gisti-ai.com/og-image.png` → `HTTP/1.1 200 OK`, no `Content-Range`). `wrangler dev --local` behaved the same. A worker that only rewrote `Cache-Control` would have shipped a video that never plays on iPhone.

## Fix

- `landing-range.js`: `parseRange()` (single range: `a-b`, `a-`, `-n`; end clamped; multi-range / malformed → full 200; start ≥ size or `-0` → 416) and `serveVideo()`. It fetches the asset without `Range`/`If-Range`, slices the body and answers `206` with `Content-Range`, `Content-Length`, `Accept-Ranges: bytes`, or `416 bytes */size`. A ranged HEAD is fetched upstream as GET, because the asset store's HEAD has no `Content-Length`. Any non-200 upstream answer (404, 304) passes through without the long cache.
- `landing-worker.js`: `/video/*` → `serveVideo()`, after the redirect logic; all other paths unchanged.
- Cache: `public, max-age=31536000, immutable`, so file names are versioned (`-v1`). A new cut ships as `-v2`; never overwrite `-v1`, since `If-Range` is ignored.
- Tests: `node --test landing-range.test.mjs` (9 cases, no deps).

## Reuse

Any byte-range media (video/audio) served through `gisti-landing` goes through `serveVideo()`. For files much larger than a few MB, slicing the whole buffer per request stops being acceptable; switch to R2 (which supports `range` natively).

Source video project (Remotion): sibling folder `gisti-store-assets/promo-video-2026-09-24/` (не проверено — outside the repo, private asset folder).
