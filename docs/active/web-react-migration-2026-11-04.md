# Web — React migration and web-native redesign

**Статус:** In Progress
Started 2026-11-04 · branch `worktree-web-react-redesign`

## Goal

Move the Gisti web APP (`app.gisti-ai.com`) from the Compose wasmJs canvas (Skiko) to a
client-rendered React front end. The app UI is redesigned to web layout standards, not ported
screen-for-screen from Android.

What the migration buys now that SEO is out: web-native layout (sidebar, side panels, hover and
keyboard), a real DOM (text selection, browser find, accessibility, autofill, extensions) and a
start-up without downloading and booting the wasm runtime.

**Out of scope (owner, 2026-11-04):** the public pages — landing, `/checklists/` gallery,
category and template pages, `/hi/` mirror, `/mcp` — already exist, are designed and work.
They are not redesigned. **No SEO work in this migration**: template SEO is already served by
the static gallery; the app stays `noindex`. So server-side rendering is NOT a requirement — the
app is a client-rendered React SPA; Node is build tooling unless phase 2 finds another reason
for a server.

## Decisions (owner, 2026-11-04)

1. **Stack:** JS + React, client-rendered SPA (no SSR); Node.js as tooling.
2. **The data layer stays SHARED with the Android app.** It is extracted into a Kotlin
   Multiplatform library (domain models, repositories, sync, business rules, limits) and
   compiled to a JS package; the React site imports that package instead of re-implementing
   the data layer in TypeScript. One source of truth for rules such as free limits, fills,
   reminders and sync, so web and Android cannot drift.
3. **Design first** — done: the app design below is approved.

## Approved design (owner, 2026-11-04)

Spec: [`docs/designs/web-react-2026-11-04/DESIGN_SPEC.md`](../designs/web-react-2026-11-04/DESIGN_SPEC.md)
(app sections are binding; public-page sections are a round-1 record only). The prototype lives
on a private claude.ai design canvas — its link is in the project memory note
`web-react-redesign-2026-11-04`, not in this public repo. Its `.dc.html` boards and
`gisti-tokens.css` are the visual reference for implementation.

Approved screens:

| Screen | Route | Essentials |
|---|---|---|
| Inbox | `/inbox` | Left sidebar nav (Inbox, Today, Calendar, Projects, Templates, Settings); top bar with ⌘K search, credits chip, Create with AI, Ask Gisti; sections Overdue / Today / Upcoming / Anytime with open-task counters; rows show read-only indicators only |
| Project + task details | `/p/{id}/t/{taskId}` | Folders, inline "Add task", completed section separated and collapsed, fill switcher; right side panel = full v1 `ItemDetailsSheet` fields (note, reminder + repeat, priority/star, attachments, move to project, delete) + "Ask Gisti about this task" |
| AI chat | docked right panel | The list updates beside the chat while it acts; actions live inside the reply bubble (Cancel as a ghost chip in the same row) |
| Create with AI | `/create?source=…` | Dialog with six sources as vertical tabs (Photo, PDF, Text, Link, Voice, File); credit cost visible; the Create button is the view's only AI-gradient element |
| Upgrade / paywall | `/upgrade` | Free vs Premium, Premium marked by a primary border (no gradient); product title, duration, price, trial terms, Terms/Privacy, Restore purchases |
| Mobile web (<640px) | — | Same as Android redesign B: warm-sand bottom plate with the Ask pill (attach, label, mic, call) over Inbox · Calendar · Projects · Overview |

Design rules carried into code:
- Palette values from the shipped `Color.kt` (warm sand), token NAMES from the Gisti design system.
  The Gisti DS artifact still holds the older cream/white values and needs a re-sync.
- Generate the web tokens from `core/designsystem` rather than copying them by hand, so web and
  Android cannot drift again.
- App zone font: system-ui stack (+ Noto Sans Devanagari), as in the DS.

Open design decisions — the owner approved the screens but has not answered these; the designer's
recommendation is the default, to be confirmed at the start of implementation:

| # | Question | Default |
|---|---|---|
| D1 | Palette: shipped sand vs DS cream/white | Sand; re-sync the DS artifact |
| D3 | Sidebar tone: chrome sand vs page tone + hairline | Chrome sand |
| D4 | Desktop chat: docked panel + ⌘K / palette only / full `/chat` page | Docked panel + ⌘K |
| D5 | Paying on the web: web billing (RevenueCat Web Billing or Stripe) vs Android-only | Web billing; price parity → `@product-expert` |
| D6 | Chat send key | Enter sends, Shift+Enter new line |
| D7 | White label on the AI-gradient button (contrast) | 18px Bold label + darker gradient start |

## Phases

| Phase | Content | Status |
|---|---|---|
| 1. Design | DESIGN_SPEC + canvas prototype of the app, owner review | done (approved 2026-11-04) |
| 2. Architecture | Shared data library: module boundary, JS export surface, target (`js` IR vs `wasmJs`), persistence and Firebase on the web, hosting | next session |
| 3. Implementation | React SPA screen by screen from the approved design, on top of the shared library | pending |
| 4. Cutover | Replace the wasmJs app on `app.gisti-ai.com` | pending |

## Open questions for phase 2 (to verify against primary sources, not from memory)

- Kotlin target for the shared library: `js(IR)` with `@JsExport` + generated `.d.ts`, or
  `wasmJs`. How suspend functions and `Flow` cross into JS (Promise / callback adapters).
- Local persistence on the web: today Room 3.0 over SQLite OPFS in a Web Worker (wasmJs) —
  is that reachable from a `js` target, or does the web keep its own storage adapter behind
  the shared repository interfaces.
- Firebase on the web: Firebase JS SDK is already behind expect/actual — keep it on the
  Kotlin side of the library or inject it from the React app.
- Hosting: the SPA build served by the existing `checklists` worker (Static Assets) on
  `app.gisti-ai.com`; Firebase Auth `/__/auth/*` proxy, COOP header and `authDomain` stay as today.
- Deep links into the app (`app.gisti-ai.com` App Link from the gallery "Use this template") keep
  working with the new router.

## Iterations

- 2026-11-04 — worktree created; knowledge scan done; design canvas started with the Gisti
  design system.
- 2026-11-04 — design-expert: round 1 → `docs/designs/web-react-2026-11-04/DESIGN_SPEC.md` + 10 canvas
  boards (5 public, 5 app). Palette taken from shipped `Color.kt` (warm sand), not from the stale
  Gisti DS artifact (cream/white). 8 open decisions sent to the owner.
- 2026-11-04 — owner: public pages already exist and work → removed Landing / Gallery / Template
  boards from the canvas; Pricing kept as the in-app Upgrade screen. Scope = the app only; no SEO,
  no SSR.
- 2026-11-04 — owner approved the six app screens. Next session: phase 2 (architecture), then
  implementation.
