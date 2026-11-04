# DESIGN_SPEC — Gisti web, React + SSR redesign (round 1, for owner comments)

- **Platform:** Web — React + Tailwind 4; public zone server-rendered (Node SSR), app zone SPA.
- **Method:** claude-design canvas, 10 artboards (`Main`, `Gallery`, `Template`, `TemplateMobile`, `Pricing`, `AppInbox`, `AppProject`, `AppChat`, `AppCreate`, `AppMobile`) + `gisti-tokens.css`.
- **Design system:** Gisti (`g-*` classes from the DS `components/bundle.css`), colour values from `core/designsystem/.../theme/Color.kt`.
- **Scope:** layout, components, tokens, states, keyboard and a11y, SEO page anatomy. Data layer (shared KMP library) is out of scope.

> **Scope change (owner, 2026-11-04):** the public pages (landing, gallery, category, template, `/hi/`)
> already exist, are designed and live — they are NOT redesigned. Sections 1 "Public", 3.1, 6.1–6.5 and 9
> are kept only as a record of round 1; the canvas now holds the app boards only. `Pricing` (6.6) stays
> as the in-app Upgrade screen.

## 0. Principles

1. **Built for the web, not ported from the phone.** Wide screens get a left sidebar, side panels, hover and focus states, keyboard shortcuts, real links and URL-addressable views. Mobile web (< 640) keeps the app patterns that genuinely fit (bottom plate with Ask pill + tabs).
2. **Two zones, one identity.** The public zone (indexable, SSR) and the app zone (SPA) share tokens, logo, type and components. Only the public zone may get a display face (D2).
3. **No feature is lost.** Every v2 surface (Inbox sections with counts, Calendar, Projects, fills, folders, completed section, item details, chat with in-bubble actions, Create with AI with 6 sources, paywall, sharing, MCP, Google sign-in) has a web home below. Overview (mobile tab) becomes the sidebar itself on wide screens.
4. **Content first, chrome quiet.** Depth comes from tone, not shadow. One brand blue. One AI-gradient element per view.

### 0.1 Palette source: code over the DS artifact

The DS artifact (`tokens.json`, synced 2026-10-16) still carries the pre-B cream palette (page `#FBFAF8`, white cards). The shipped app (`Color.kt`, redesign B) is warm sand: page `#F4EFE7`, card `#FFFDF9`, hairline `#E6DED1`, ink `#24211C`; dark page `#161412`, card `#221F1B`. `gisti-tokens.css` follows **Color.kt**, so Android and web look like one product. Token *names* follow the DS. The DS artifact needs a re-sync (separate task). Owner check: **D1**.

## 1. IA and URL map

### Public (apex `gisti-ai.com`, SSR, indexable; Hindi mirror under `/hi/…`)

| URL | Page | Notes |
|---|---|---|
| `/` · `/hi/` | Landing | H1 = slogan + "AI checklist app" in the eyebrow/title tag |
| `/checklists/` | Gallery hub | 15 categories, featured, search |
| `/checklists/{category}/` | Category | 3–9 templates each, intro, FAQ |
| `/checklists/{category}/{slug}/` | Template page | Keep the **trailing slash**, as in today's canonicals. Redirect the slash-less form with a 301 (never 307) |
| `/pricing/` · `/hi/pricing/` | Pricing | New page (today: `/#pricing`). Same component as the in-app upgrade dialog |
| `/mcp/` | Use in Claude | Exists; restyle only |
| `?q=` on `/checklists/` | Search results | `noindex,follow`. Filters the 81 templates server-side, so it works without JS |

Legal stays on `gisti-app.web.app/privacy-policy` and `/terms` (do not create `/privacy`).

### App (`app.gisti-ai.com`, SPA, `noindex`)

| Route | View | Right slot / overlay |
|---|---|---|
| `/inbox` | Inbox (Overdue / Today / Upcoming / Anytime, counts) | — |
| `/today` | Today across all lists | — |
| `/calendar?view=week\|month&date=YYYY-MM-DD` | Calendar (tasks with `reminderAt`) | — |
| `/projects` | All projects grid | — |
| `/p/{listId}` · `/p/{listId}/fill/{fillId}` | Project detail (template vs fill) | — |
| `…/t/{taskId}` (suffix on any list view) | Same view | Item details panel open |
| `/templates` · `/templates/{slug}` | In-app templates, preview | `?use=1` creates the list right away |
| `/chat` · `/chat/{threadId}` | Full-screen chat (compact); on ≥ 1024 it redirects to the last view with the chat panel open | Chat panel |
| `/create?source=photo\|pdf\|file\|link\|text\|voice` | Create with AI dialog over the current view | Modal dialog |
| `/upgrade` | Paywall dialog (pricing component) | Modal dialog |
| `/settings` · `/settings/account` · `/settings/mcp` · `/settings/notifications` | Settings | — |
| `/updates` | Updates feed | — |
| `/signin` | Google sign-in | — |

Compatibility: the gallery deep link `/?g=create&template={slug}&utm_*` keeps working and redirects to `/templates/{slug}?use=1` without losing the `utm_*` params.

## 2. Breakpoints and grid

| Name | Width | Public | App |
|---|---|---|---|
| compact | < 640 | 1 column, gutter 16, burger menu | Top bar + bottom plate (Ask pill + 4 tabs), details and chat as full-height sheets |
| medium | 640–1023 | 8 columns, gutter 24, burger menu | Rail 72 + main. Right slot = overlay drawer 400 with scrim |
| expanded | 1024–1439 | 12 columns, container 1200, gutter 24, side margins 32 | Sidebar 248 + main + docked right slot 400 (no scrim) | <!-- docs-leak-scan: reviewed — layout px, not a metric -->
| wide | ≥ 1440 | as expanded, container stays 1200 | Right slot 440. Main content stays capped at 760 |

- Grid: `grid-template-columns: repeat(12, minmax(0,1fr))`, column gap 24 (`space-xl`), row gap 16–24.
- Text measure: body copy ≤ 68ch. A wide screen widens the layout, never the line length.
- Inside the app, main content is capped at 760 and centred in the main column (Todoist-like), so 1440 shows list + panel without stretched rows.

## 3. Shells

### 3.1 Public shell
- **Header 64**, page tone. A 1px `outline-variant` hairline appears once scrolled. Sticky, `scroll-padding-top: 80`.
  Left: logo (launcher asset, 32) + "Gisti" wordmark. Centre: Templates · How it works · Pricing · Use in Claude.
  Right: language switch `EN | हिन्दी` (links with `hreflang`, `aria-current` on the active one) · Log in (text button) · **Open web app** (primary).
  Below 1024 the centre links and Log in fold into a disclosure panel (`<button aria-expanded>`; native `<details>` as the no-JS fallback). The language switch and the primary button stay in the bar.
- **Footer**, 4 columns: brand + one-line description with the disambiguation ("Gisti is an AI checklist app — not GitHub Gist") · Product · Popular templates (6 categories) · Legal. Under them: language switch, ©, and the trademark line "Google Play and the Google Play logo are trademarks of Google LLC."

### 3.2 App shell (expanded / wide)
```
Row (flex-wrap)
├ Sidebar 248  nav-chrome tone (= bottom-chrome sand), full height, own scroll
│  ├ logo + workspace/account menu (avatar, name, chevron)
│  ├ nav: Inbox (count) · Today (count) · Calendar · Templates      — 40px rows, radius-sm, active = primary-container + primary text
│  ├ "Projects" header + [+] (new list) → project rows: avatar tile 24 (pastel tint + monogram) · name · open count
│  ├ spacer
│  ├ Updates (dot when unread) · Settings
│  └ plan card (Free: "3 of 5 lists" meter · Upgrade link) — Premium: hidden
├ Main (flex 999 1 560, min-width 0)
│  ├ Top bar 56: search field "Search or ask…  ⌘K" (max 480) · spacer · credits chip · Ask Gisti button (SparkleTile = the view's gradient) · New list [+]
│  └ content ≤ 760 centred
└ Right slot 400 (440 ≥ 1440): surface-card, 1px docked seam on its left edge, own scroll
     holds ONE of: Item details | Chat. Details opened from a chat reply stack over the chat with a Back control.
```
- The sidebar uses the bottom-chrome tone on purpose. It is the web counterpart of the mobile bottom plate (the "navigation chrome"), so both platforms share one chrome colour. Owner check: **D3**.
- **Chat on desktop = docked right panel, with ⌘K as the quick entry.** Why not a palette-only chat: replies carry multi-step proposals with action chips, and their result shows up in the list (a new project in the sidebar, a reminder chip on a row). The user has to see the conversation and the list side by side. A palette closes on blur and loses that. ⌘K stays the fast door: it searches tasks, lists and templates, and its last row is "Ask Gisti: ‹typed text›", which sends the message into the panel.
- The panel's open state is per viewer (localStorage), so it reopens where the user left it.

### 3.3 App shell (medium / compact)
- **Medium:** rail 72 (icon + 12px label, tooltip with shortcut) holding Ask (sparkle) · Inbox · Today · Calendar · Projects · Templates · then Updates · Settings at the bottom. The right slot becomes an overlay drawer 400 over a 32% scrim; Esc and a scrim click close it.
- **Compact (< 640):** top bar 56 (title, search icon, avatar) + **bottom plate** in warm sand `bottom-chrome` (#E4DCD0 / #221F1B), 28px top corners, no extra shadow: Ask pill (`+` attach · "Ask Gisti or add a task" · mic · call) above 4 tabs **Inbox · Calendar · Projects · Overview** (the v2 set). Details and Create open as full-height sheets, and chat opens as a full-screen sheet without a top hairline (ADR 2026-10-18). The plate pads `env(safe-area-inset-bottom)`.

## 4. Tokens (`gisti-tokens.css`)

- Names follow the DS (`--surface`, `--surface-card`, `--primary`, `--space-*`, `--radius-*`, `--font-sans`…), so `bundle.css` works unchanged. Values come from Color.kt (see 0.1).
- **Theme switch:** `:root` = light; `[data-theme="dark"]` = dark; `@media (prefers-color-scheme: dark)` applies dark to `:root:not([data-theme="light"])`. The app adds a Settings choice: System / Light / Dark.
- **Web additions (new tokens):** `--container-max 1200`, `--content-max 760`, `--measure 68ch`, `--sidebar-w 248`, `--rail-w 72`, `--panel-w 400`, `--panel-w-wide 440`, `--header-h 64`, `--topbar-h 56`; state layers `--state-hover` (on-surface 6%), `--state-pressed` (10%), `--state-selected` (= primary-container); `--focus-ring` (2px primary, offset 2); `--ease-spatial`, `--ease-slow`, `--dur-fast/default/slow`; `--nav-chrome` (= bottom-chrome); `--user-bubble` / `--on-user-bubble`; `--font-display` (D2).
- **Tailwind 4 seed:** `@theme inline { --color-surface: var(--surface); --color-primary: var(--primary); … --radius-md: var(--radius-md); --spacing: 4px; }`. Components use semantic classes (`bg-surface-card`, `text-on-surface-variant`, `rounded-button`) and never raw hex.
- Spacing stays compact: element gaps 8–12, 24+ only for section breaks. Marketing sections use `space-xxxl` (48) and a new **`--space-section` 96** (no DS token exists; the nearest is `space-xxxl` 48. Proposed as a web-only token for public section rhythm).

## 5. Component inventory

**Reused from the DS (`g-*` → React):** `g-btn` (+primary/secondary/destructive/text) → `Button` · `g-card` → `Card` · `g-field` → `TextField` · `g-switch` → `Switch` · `g-avatar` → `ListAvatar` · `g-list-card` + `g-progress` → `ListCard`, `Progress` · `g-today` → `TodaySummary` · `g-sparkle` + `g-ai-grad` → `SparkleTile` · `g-ask` / `g-dock-pill` → `AskPill` · `g-chip` / `g-chip-sel-*` → `PromptChip`, `SelectChip` · `g-grad-btn` → `GradientButton` · `g-dock` → `BottomPlate` · `g-empty` → `EmptyState`.

**New web components:**

| Component | Use | Notes |
|---|---|---|
| `SiteHeader`, `SiteFooter`, `LangSwitch` | public | links with `hreflang`; switch keeps the same page |
| `Breadcrumbs` | public | `<nav aria-label="Breadcrumb"><ol>`, mirrors BreadcrumbList |
| `TemplateCard`, `CategoryTile` | gallery | whole card is one `<a>`; icon tile uses avatar tints by category |
| `ChecklistPreview` | template page | real `<ul>`; each `<li>` = `<label><input type=checkbox>` + text + optional note; ticks saved in the browser |
| `UseTemplateAside` | template page | primary CTA, "Tailor with AI" field, Play badge, facts line |
| `FaqList` | public | native `<details>` |
| `PlayBadge` | public | official PNG **stored in the repo** (today's page hotlinks `play.google.com` — fix). Fixed height 56 (desktop) / 48 (mobile), width by ratio; no recolour, crop, rounding or shadow; the clear space built into the PNG is kept. Source: play.google.com/intl/en_us/badges/ (re-check the guidelines before ship) |
| `GoogleSignInButton` | `/signin`, header | official Google Identity button, per developers.google.com/identity/branding-guidelines; never a home-made "Continue with Google" |
| `PlanCard`, `PlanTable` | pricing + upgrade dialog | one component, two hosts |
| `AppSidebar`, `AppRail`, `AppTopBar`, `BottomPlate` | app shell | per breakpoint |
| `CommandPalette` | ⌘K | combobox + listbox; groups Tasks / Lists / Templates / Ask Gisti |
| `SectionHeader` | Inbox | title + open-task count chip (owner decision 1) |
| `TaskRow` | lists | round checkbox (own 40px target) + title button that opens details; read-only meta: priority bar 3px, reminder chip, repeat glyph, list chip, star, attachment count |
| `InlineAddTask` | lists | "+ Add task" row → inline composer (Enter adds and stays open, Esc closes) |
| `FolderGroup`, `CompletedSection` | project | disclosure buttons, `aria-expanded` |
| `FillSwitcher` | project | menu button: template · fills list · "Start a new fill" |
| `SidePanel` | details / chat | `role="complementary"` when docked, `role="dialog"` with focus trap when overlaid |
| `ItemDetails` | panel | lifts v1 ItemDetailsSheet field-for-field (§6.8) |
| `ChatThread`, `ChatBubble`, `ActionChips`, `ChatComposer` | chat | the actions live inside the reply bubble; the escape option is a ghost chip in the same row |
| `CreateWithAiDialog`, `SourceList` | create | vertical source tabs on ≥ 1024, pills on compact |
| `Toast` | global | bottom-left on desktop (clear of the right panel), above the plate on compact; carries Undo |

## 6. Per-screen anatomy

### 6.1 Landing `/` (board `Main`)
1. Header.
2. **Hero** (12 columns, 6 | 6). Left: eyebrow "AI checklist app for Android and the web" → H1 "Turn anything into a checklist" → lead "Just tell Gisti…" → [Open web app] (primary) + Play badge → facts line "Free to start · No card needed · Not affiliated with GitHub Gist". Right: a live HTML mock of the product (not a screenshot): Ask pill with a typed request → the reply bubble with the generated list and its action chips. The mock's SparkleTile is the section's one gradient element.
3. **Six ways in**: Photo · PDF · File · Link · Text · Voice. Six cards on a 6-column grid (3×2 on medium, 2×3 on compact), icon + name + one-line example.
4. **How it works**: 3 steps — Tell Gisti → Review the list → Tick it off and get reminded.
5. **Feature bento** (12-column grid, mixed spans): the AI chat that does the work (reply with chips) · Inbox with Today/Upcoming · Reminders and repeats · Ready-made templates · Android and web in sync · Use in Claude (MCP).
6. **Template teaser**: category chips (links) + 6 template cards → "Browse all 81 templates".
7. **Pricing summary**: Free vs Premium $1.99/mo → link to `/pricing/`.
8. **FAQ** (what Gisti is, the GitHub Gist question, free plan, iPhone (use the web app), languages, Claude).
9. Closing CTA band + footer.

### 6.2 Gallery hub `/checklists/` (board `Gallery`)
Breadcrumbs → H1 "Free checklist templates you can tailor with AI" → lead → **search** (`<form role="search" method="get">`, works without JS) → **15 category tiles** (5×3; 3 columns on medium, 2 on compact; icon tile + name + count) → **Featured templates** (3 columns of `TemplateCard`: title, card description, item count, category) → **"Can't find yours? Just tell Gisti"** card (the page's one gradient element: a gradient button that opens the app's chat with the typed text) → FAQ → footer. JSON-LD: `CollectionPage` + `ItemList` of categories + `BreadcrumbList`.

### 6.3 Category `/checklists/{category}/` (no board; same parts)
Breadcrumbs → H1 "{Category} checklists" (e.g. "Travel and packing checklists") → intro ≤ 68ch → template grid (all templates in the category) → "Other categories" chips → category FAQ. JSON-LD `ItemList` of templates.

### 6.4 Template page `/checklists/{cat}/{slug}/` (board `Template`, 1440)
```
Breadcrumbs: Home › Checklists › Travel & Packing › Business Trip Checklist
Grid 12: article (cols 1–8)  |  aside (cols 9–12, sticky top = header + 24 on ≥ 1024)
article:
  H1 (display-small 30/38 → display-medium 36/44 at ≥ 1024) · intro (body-large, ≤ 68ch, internal link to the category)
  byline: "Curated by the Gisti team · Updated July 2026" + facts row (18 items · Travel & Packing)
  toolbar: progress "0 of 18 ticked" · [Copy list] [Print] [Share]  (text buttons with icons)
  ChecklistPreview (real <ul>, notes under items, ticks saved in this browser)
  "How to use this checklist" — 3 numbered steps (HowTo)
  FAQ (3 <details>, first one open)
aside: UseTemplateAside card — "Use this template" (primary, block) · "Tailor it with AI" field + gradient button
       (the page's one gradient element) · Play badge · facts line · "More in Travel & Packing" (4 links)
Related checklists (4 TemplateCards, full width)
```
- 768–1023: the aside moves under the H1 as a horizontal card. < 768: see 6.5.
- The CTA link to the app keeps `rel="nofollow"` (the app is disallowed in robots.txt).

### 6.5 Template page, phone (board `TemplateMobile`, 390)
Compact header (logo, `हिन्दी`, Open) → back link "‹ Travel & Packing" (the full trail stays in JSON-LD and a visually hidden `<ol>`) → H1 → intro → byline → toolbar (Copy, Share) → list → Tailor-with-AI card → Play badge → How to use → FAQ → Related as a link list → footer. A **sticky bottom bar** (`surface-docked` + `docked-seam`, not the app's sand chrome, because this is a web page, not app chrome) holds "Use this template" full width and pads the safe area.

### 6.6 Pricing `/pricing/` + in-app `/upgrade` (board `Pricing`)
H1 "One small price. Unlimited checklists." → a 2-card plan row: **Free** (5 lists, 5 fills each, 10 recurring reminders, 1 weekly list, 3 attachments per task, limited AI credits) | **Premium** $1.99/month (unlimited lists, fills, reminders and attachments, Calendar, Weekly) with a primary border, not a gradient → CTA "Start 3-day free trial" → compliance block → comparison table → "How Gisti compares" (Gisti Premium $1.99 · Todoist Pro $5 · TickTick Premium $3 per month, as published by each vendor; **re-check and date-stamp before launch**) → billing FAQ.
- **Required on the paywall** (`~/.claude/references/store-economics-and-paywall-compliance.md`): product title "Gisti Premium"; duration "1 month, renews automatically"; price and price per period; trial terms "3 days free, then $1.99/month — cancel before the trial ends and you won't be charged"; working Privacy Policy and Terms links; **Restore purchases**; account deletion reachable in Settings → Account. The trial is a store-side offer, never an in-app timer.
- The price shown is the US list price, followed by "Local prices may differ — you'll see yours at checkout." Geo-tiered pricing must not be contradicted by a hard-coded $1.99.
- In-app host (`/upgrade`): same PlanCard and table in a 720 dialog, no header or footer, close ✕, Restore purchases, and the current-limit context line ("You've used 5 of 5 lists"). <!-- docs-leak-scan: reviewed — dialog width in px and the published Free limit, not sales data -->
- Web checkout path: **D5**.

### 6.7 App — Inbox (board `AppInbox`)
Shell §3.2, no right panel. Content ≤ 760:
H1 "Inbox" + subtitle "11 open tasks" + [Display] menu (sort, group, show completed) → `InlineAddTask` ("Add a task", hint `Q`) → four **sections**, each `SectionHeader` (title + count chip) over ONE grouped plate (radius 24, inset dividers, the B group anatomy): Overdue (header text in `error`) · Today · Upcoming · Anytime. Rows are `TaskRow` with read-only meta only. Hover = `--state-hover`. Clicking a row opens the details panel; the checkbox completes the task with an Undo toast.

### 6.8 App — Project + item details (board `AppProject`)
- Header: avatar tile 40 + H1 list name + progress (`g-progress`, "7 of 18 done") + `FillSwitcher` ("Delhi · Nov 11 ▾") + [Share] (Text / PDF menu) + [⋯] (rename, colour, separate completed, delete).
- Body: folders as disclosure groups (Documents · Tech · Clothes), each a grouped plate. `InlineAddTask` at the end of each folder and of the list. **Completed (7)** is a separate collapsed section (separateCompleted = true by default).
- **Item details panel** (400, docked, URL `…/t/{taskId}`), mirroring v1 ItemDetailsSheet field-for-field: header (list breadcrumb "Business Trip › Tech" · ✕ with Esc hint) → round checkbox + title (inline-editable, title-large) → Note (multi-line field) → **Reminder** row (date + time, mono-time) → **Repeat** row ("Doesn't repeat" ▾) → **Priority** (star toggle "Starred" + priority bar colour) → **Attachments** ("2 of 3 · Free" counter, thumbnails 64, [+ Add], drop zone on desktop; at the limit: "Upgrade for unlimited") → **Move to project** (list picker) → divider → "Ask Gisti about this task" (text button, opens chat with context) → **Delete task** (destructive text button, confirm with Undo toast). Footer meta: "Created Nov 2 · Edited 2 h ago" (`text-faint`).
- The selected row gets the `--state-selected` fill and a 2px primary leading edge.

### 6.9 App — AI chat (board `AppChat`)
Right panel 440 (wide): header (SparkleTile = gradient element · "Gisti" · thread menu · New chat · ✕) → thread (`aria-live="polite"` on new assistant turns) → **user bubble** `#D6E4F7` / dark `primary-container` → **assistant bubble** (raised card + `outline-variant`): type tile + prompt, object rows (list preview, "+9 more"), divider, then **ActionChips in the bubble**: Primary "Create checklist" · Default "Add to Inbox instead" · Default "Edit list" · Ghost "Cancel" in the same row (tap = execute; the chosen chip spins, the others dim to 38%). Post-action turn: "Done — …" + chips Undo (ghost) · Open checklist. Feedback row (thumbs) on the latest answer → composer: credits chip · input pill "Message Gisti" (focus = 2px primary) · attach · mic · call (primary circle) · send. `⌘↵` sends; `↵` sends when the input is single-line (D6).
The main column stays live: the created list appears in the sidebar with a "New" tag.

### 6.10 App — Create with AI (board `AppCreate`)
Modal dialog 760 (radius-xl 28, `surface-modal`, 32% scrim), title "Create with AI" + subtitle → **two columns**: left `SourceList` 220 (vertical tabs: Photo · PDF · File · Link · Text · Voice, icon + label + format hint, `role="tablist" aria-orientation="vertical"`) | right editor for the chosen source (Link: URL field + "What should Gisti focus on?" optional field; Photo/PDF/File: drop zone + Browse; Text: textarea; Voice: record button + live transcript) → destination ("New checklist" | "Add to Inbox" segmented) → cost line "Uses 20 AI credits · 80 left" → footer: Cancel (text) · **Create checklist** (gradient button = the one gradient element). < 1024: one column, the six sources become pills (2 rows of 3).

### 6.11 Mobile app (board `AppMobile`, 390×844)
Top bar (Inbox, search, avatar) → capture row → sections with counts → bottom plate in sand: Ask pill (`+`, label, mic, call) above Inbox · Calendar · Projects · Overview (active = primary pill). It mirrors Android redesign B one-to-one, so a user moving between phone app and mobile web sees one product.

## 7. States

| Surface | Loading | Empty | Error | Offline |
|---|---|---|---|---|
| Public pages | SSR, no loading state; images reserve their size (no layout shift) | — | 404 page: search + categories | static, cached by the CDN |
| Gallery search | instant (server filter) | "No templates match ‘…’" + "Just tell Gisti" card | — | — |
| Template ticks | — | "0 of 18 ticked" | storage blocked → ticks still work, nothing is saved, no warning shown | works |
| Inbox / lists | skeleton rows in the plate shape (3 per section) | Inbox zero: EmptyState "You're all caught up" + Add task + "Ask Gisti to plan your day" | inline `error-container` block + Retry | top banner "You're offline — changes will sync when you're back"; edits queue |
| Item details | panel skeleton | — | "Couldn't save" toast + Retry; the field keeps its value | edits queue |
| Chat | typing bubble | greeting + 4 starter rows | in-bubble Retry · out of credits → in-bubble "Become Pro" (primary) | composer disabled with the reason shown |
| Create with AI | button spinner + "Reading your link…" status (`aria-live`) | — | inline field error (bad URL, unsupported file, too large) | button disabled + reason |
| Upgrade | price skeleton (never a fake price) | — | "Couldn't load prices" + Retry | disabled with the reason |
| Free limits | — | — | at a limit: inline notice at the point of action + Upgrade link (never a silent no-op) | — |

## 8. Keyboard and a11y

| Key | Action |
|---|---|
| ⌘K / Ctrl K | Command palette (search + "Ask Gisti: …") |
| Q | Quick add task in the current list |
| A | Open / focus the Ask Gisti panel |
| G then I / T / C / P | Go to Inbox / Today / Calendar / Projects |
| J K or ↑ ↓ | Move between tasks |
| Enter | Open task details |
| X | Complete the focused task (Undo toast) |
| Esc | Close the topmost panel, dialog or palette |
| ? | Shortcut sheet |

- Single-key shortcuts only fire when focus is not in a text field, and Settings has a switch to turn them off (WCAG 2.1.4).
- Landmarks: `header`, `nav` (sidebar labelled "Main"), `main`, `aside` (panel). A skip link "Skip to content" comes first.
- Focus: opening the panel or a dialog moves focus to its heading; closing returns focus to the row or button that opened it. Overlay drawers and dialogs trap focus; the docked panel does not.
- The `TaskRow` checkbox is a real checkbox labelled with the task title; the title is a button "Open details: ‹title›". Meta chips are text, not controls.
- Contrast: text 4.5:1 and non-text 3:1 in both themes. Known gap carried from the DS: the white label on the AI gradient is 3.12:1. **Web fix:** the gradient button label is set at 18px Bold (counts as large text, 3:1 floor), and the gradient start for button fills is pinned to `primary` `#1565C0` → `ai-end` (D7).
- Touch targets ≥ 48 on compact; on pointer devices ≥ 32 visual with ≥ 40 hit area (WCAG 2.2 target size 24 minimum is met).
- `prefers-reduced-motion`: panels and dialogs appear without the slide; fades drop to 120 ms.

## 9. SEO page anatomy (public)

- **Head:** `<title>` = `metaTitle` from the JSON ("Business Trip Packing Checklist (Work Travel) | Gisti"); description = `metaDescription`; canonical with a trailing slash; `hreflang` en / hi / x-default; OG/Twitter; `theme-color` follows the theme.
- **Body order (crawlable without JS):** breadcrumbs → H1 (the long-tail query, e.g. "Business Trip Checklist") → intro with an internal link to the category → the list as a real `<ul>` with notes → HowTo steps → FAQ → related links. One H1 per page; H2 per block.
- **JSON-LD `@graph`:** WebPage · BreadcrumbList · ItemList (the items) · FAQPage · HowTo · WebSite · Organization (`sameAs`: Play listing, web app). Note: Google no longer shows HowTo rich results and limits FAQ rich results to a few site types. Keep both blocks for AI answer engines (GEO) and parity, but don't count on SERP features from them.
- **Brand disambiguation:** the visible copy says "Gisti — the AI checklist app" in the hero, the template aside and the footer, and the FAQ answers "Is Gisti related to GitHub Gist? No."
- **Language:** `/hi/` pages have `lang="hi"`, Devanagari H1, and `--font-sans` with Noto Sans Devanagari early in the stack.
- **Performance:** public pages ship no framework JS for reading. Hydrated islands only: ticks, copy/share, the tailor field, the menu. LCP element = the H1 text.

## 10. Motion
Panel open: translateX(16px → 0) + fade, 320 ms `cubic-bezier(0.2,0,0,1)`. Dialog: scale 0.96 → 1 + fade, 220 ms. Row complete: checkbox fill 140 ms, then the row collapses 320 ms after a 600 ms hold (time for Undo). Toast: slide up 220 ms. Effects never bounce. Reduced motion: no translate or scale.

## 11. Open decisions for the owner

- **D1 Palette:** web uses the shipped sand palette (Color.kt) / the older cream-white palette from the DS artifact. → **Sand** (one product across Android and web; re-sync the DS artifact after).
- **D2 Display face (public zone only):** system-ui as in the app / **Eczar** 600–800 (display serif, Latin + Devanagari, Google Fonts) for H1–H2 / Poppins 600 (geometric, Latin + Devanagari). → **Eczar for H1/H2 on public pages**, with system-ui everywhere else. SSR HTML removes the reflow argument behind "no bundled font", the face gives the marketing pages a voice the app doesn't need, and Devanagari is covered. Toggle `eczarDisplay` on the `Main` board.
- **D3 Sidebar tone:** chrome sand (same as the mobile bottom plate) / page tone with a hairline. → **Chrome sand.**
- **D4 Chat entry on desktop:** docked right panel + ⌘K door / command palette only / full page `/chat`. → **Docked panel + ⌘K.**
- **D5 Web checkout:** web billing (RevenueCat Web Billing or Stripe) so the upgrade completes in the browser / "Upgrade in the Android app" only (QR + Play link). → **Web billing**: web users who never install the Android app otherwise have no way to pay. Needs `@product-expert` on price parity.
- **D6 Enter in chat:** Enter sends, Shift+Enter adds a new line / ⌘Enter sends. → **Enter sends** (chat convention); Create-with-AI text fields use ⌘Enter.
- **D7 Gradient button contrast:** 18px Bold label (large text) / darker gradient start `#1565C0` / both. → **Both.**
- **D8 App location:** keep `app.gisti-ai.com` / move it to `gisti-ai.com/app`. → **Keep the subdomain** (robots, auth domain and cookies already set; no SEO value in the app).

## 12. Verification, reference, screenshot matrix, handoff

- **Verification:** not rendered in round 1. The brief forbids rendering and screenshots (the main agent publishes the canvas). The owner's canvas review is the first visual check. Before round 2, render every board at 390 / 768 / 1024 / 1440 + dark.
- **Reference frame:** none yet in raster form. The canvas boards are the reference; export PNGs after owner sign-off.
- **Screenshot matrix for the code expert (`screenshot-driven-ui`, Playwright):** widths 360, 390, 768, 1024, 1280, 1440, 1920 · light + dark · browser zoom 200% · `lang=hi`. Fragile data to test: the longest template title ("New Employee Onboarding Checklist", the hi version of "Wedding Planning Checklist"), a template with 20+ items and long notes, a project with 0 tasks and one with 200, an Inbox section with 99+ open tasks (count chip width), a 60-character task title in the row and the panel, 3 of 3 attachments, a chat reply with 6 chips plus Cancel, Create with AI with a 300-character URL, a 4-digit credits number, the sidebar with 30 projects (scroll).
- **Handoff → `@react-ui-expert`:** order: (1) `gisti-tokens.css` → Tailwind 4 `@theme` + theme switch; (2) public shell + Template page (the SEO core, all 81 × 2 languages from `data/checklists/*.json`); (3) Gallery hub + category; (4) Landing + Pricing; (5) app shell (sidebar/rail/plate) + Inbox; (6) project + details panel; (7) chat panel + ⌘K; (8) Create with AI + upgrade dialog. Behaviour and state (routes, panel stack, offline queue, credits) → `@feature-expert`-equivalent for web / main agent; billing → D5 first.
- **Pitfalls:** (a) the details panel and the chat share ONE right slot — opening a second docked panel squeezes main below 560 on 1280; (b) the Play badge must be a file in the repo, not a hotlink; (c) keep trailing-slash canonicals byte-identical to today, or indexed URLs reset.
