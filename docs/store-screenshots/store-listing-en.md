# Google Play Store Listing — EN

_Updated: 2026-07-02 · App version: 1.17.7 · ASO audit revision · **copy reviewed against the app 2026-08-03 (app now 1.18.7)**_

**This file is the source of truth for the Play Store listing.** Whenever the listing changes in Play Console, update this doc in the same task — and vice versa: any listing edit starts here, then gets pasted into the console.

> ⚠️ **Copy below is deliberately NOT edited (reviewed 2026-08-03).** This doc mirrors what is
> pasted into Play Console; rewriting it here without touching the console would just create the
> same drift in the opposite direction. Two gaps found against app 1.18.7 — **apply them to the
> console and this doc together, in one task:**
>
> 1. **Languages are stale.** Line ~50 says _"Works in English and Russian"_ — the app has shipped
>    **Hindi UI since 1.18.2**, the AI chat greets and replies in the user's language since 1.18.1,
>    and a separate Hindi listing exists (`store-listing-hi.md`). India is an explicit growth market
>    (`docs/pricing-strategy.md` Tier C), so this omission costs a targeted market.
> 2. **Nothing from 1.18 is reflected** — chat that remembers context across sessions and applies
>    reversible actions (the single headline feature of the 1.18 line), and the template library
>    grown **47 → 81** (1.18.3). "Ready-made templates" is unnumbered here, so it is not *wrong* —
>    but 81 is a stronger claim than the unstated 47 the Update Feed still advertises.
>
> Not changed on purpose: _"100 AI credits"_ (line ~104) is factually correct — `initial_ai_credits`
> is 100. Whether to keep advertising it is a **product** question, not a copy error: at
> `ai_action_cost=20` it buys 5 AI generations for the lifetime of a Free account
> (see [`docs/PRODUCT.md`](../PRODUCT.md) §2).

> ### Second drift pass — 2026-08-18, against the v2 shell (1.19.2 in prod, 1.20.0 going out)
>
> The v2 navigation shell became the default for every install in `b9e2db74` (2026-08-05), and that
> commit is an ancestor of the 1.19.2 release commit — so **the copy below already describes an app
> whose home screen no longer exists**. Four new gaps, to be applied to the console and to this doc
> in one session, together with the two from 2026-08-03 above:
>
> 3. **Screenshots 1, 2, 3 and 6 are stale** (frames read visually from `unnamed*.png` in this
>    folder). 1 (AI Chat) and 3 (Calendar) show the v1 hamburger drawer and the Today/Calendar top
>    tabs, both gone in v2. 2 shows Analyze as six full-width cards under "What would you like to
>    analyze?", which 1.20.0 replaces with a pill grid. 6 claims **47 templates** while the app
>    bundles **81** (`ls data/checklists/*.json` = 81). Frames 4, 5, 7 and 8 are detail surfaces and
>    survive the shell change untouched.
> 4. **The v2 vocabulary is missing from the indexed text.** The app now names three first-class
>    destinations — **Inbox**, **Projects**, **Overview** (`nav_tab_*` in `strings.xml`; tab set
>    confirmed in `App.kt:isV2TabRoute`) — and none of them appears in title, short or full
>    description. Play indexes the whole full description, so these are free slots that also close
>    the gap between store language and app language.
> 5. **"Today view"** survived the redesign but stopped being its own destination: it renders under
>    the Calendar tab. The line is not wrong, it just points at something reached differently now.
> 6. **Price string.** Full description says "$20/year"; the paywall renders **$19.99**
>    (screenshot 8). Align the text to the SKU.
>
> Checked and cleared 2026-08-18: this file is byte-identical to its committed blob
> (`git hash-object` = `git ls-files -s` = `e721a5cc`). The ` M` git kept reporting was a stale stat
> cache, cleared by `git update-index --refresh`. **That proves doc == git HEAD, not doc == console**
> — whether the 2026-07-02 copy below was ever pasted into Play Console remains unverified.

**Changes vs live listing (ASO audit 2026-07-02):**
- Short description: added `checklist` + `to-do list` keywords (the live version had neither — the second-highest-weight indexed field carried no primary keyword).
- Full description: live was 1395/4000 chars — expanded to ~3300 (Play indexes all 4000; unused chars are wasted keyword slots). Kept the live version's tone, added long-tail keywords (grocery list, packing list, photo to checklist, weekly planner, to-do list) + a keyword paragraph before the CTA.
- Title unchanged (29/30, already optimal).

Apply manually: Play Console → Store presence → Main store listing.

---

## App Title (30 char max)

```
Gisti: AI Checklist Assistant
```

29/30 chars — unchanged.

---

## Short Description (80 char max)

```
AI checklist & to-do list — chat to add tasks, set reminders, plan your week
```

76/80 chars. Keywords: `ai checklist`, `to-do list`, `tasks`, `reminders`, `plan`.

---

## Full Description (4000 char max)

```
Gisti is an AI checklist and to-do list app with a built-in chat assistant. Type "add eggs to grocery list" or "remind me tomorrow at 6pm" — done. No menus, no forms. Your lists, tasks, and reminders organize themselves.

💬 AI CHAT ASSISTANT

Talk to Gisti like a friend:
• "Add milk to my shopping list" — item added instantly
• "Remind me to call mom tomorrow at 6pm" — reminder set
• "Create a packing list for Paris" — full checklist built in seconds
• "What's on my list?" — search across all your lists
• "Push everything to next week" — bulk reschedule

Text or voice input. Works in English and Russian. Zero learning curve — just say what you need.

📸 TURN ANYTHING INTO A CHECKLIST

Point, snap, done:
• 📷 Photo — receipt, whiteboard, label, handwritten note
• 📄 PDF — contract, syllabus, report
• 🎙 Voice — speak your ideas, get a structured to-do list
• 🔗 Link — extract action items from any article
• ✍️ Text — paste messy notes, AI organizes them into a clean list

AI detects the language and builds a clear checklist in seconds. No manual typing.

🔔 REMINDERS & DAILY PLANNER

• Per-item reminders — every task gets its own alert
• Recurring schedules — daily, weekly, monthly, or custom
• Smart dates in chat — say "tomorrow 9pm", it just works
• Today view — a daily planner with everything due now
• Calendar view — all your checklists and reminders on one grid

Never forget a task again. Gisti remembers, so you don't have to.

📅 WEEKLY PLANNER

Turn any checklist into a Mon–Sun weekly planner. Perfect for routines, meal prep, workout plans, or work sprints. See your week, check off tasks day by day.

⚡ ORGANIZE YOUR WAY

▸ Ready-made templates — grocery list, travel, study, fitness, cleaning, work
▸ Folders — group related items inside any checklist
▸ Fill via AI — drop a photo into an existing list, AI checks off matching items
▸ Photo attachments on any task
▸ Drag-to-reorder, priority stars, swipe-to-delete
▸ Auto-delete completed items
▸ Share as PDF or plain text
▸ Home screen widget — check off items without opening the app
▸ Dark theme & Material You dynamic colors

🌐 PHONE + BROWSER

Gisti runs on Android and in any browser at gisti-ai.com — same checklists, same AI, same data. Sign in with Google to sync across devices.

💡 HOW PEOPLE USE GISTI

🛒 "Add eggs, milk, bread" — grocery shopping list ready in 2 seconds
🏠 Snap a photo of an apartment — inspection checklist built by AI
📚 Import a syllabus PDF — track every assignment
👔 Record a meeting — AI extracts action items automatically
📅 "Laundry every Monday 8am" — recurring reminder set once, forever

📦 FREE & PREMIUM

FREE TO START:
• 100 AI credits
• Ready-made templates
• Reminders and lists

PREMIUM — 3-day free trial:
• $1.99/month or $20/year
• Unlimited checklists, fills & reminders
• 300 AI credits refilled daily
• Calendar view & weekly planner

🔒 PRIVACY FIRST

Your checklists stay on your device. Cloud sync is optional via Google Sign-In. We don't sell your data.

Whether you need a simple to-do list, a task planner with reminders, a grocery list maker, a weekly planner for routines, or an AI checklist maker that turns photos and PDFs into actionable lists — Gisti does it all in one chat.

Stop managing lists manually. Just tell Gisti what you need.

Questions? churaevanton@gmail.com
```

---

## Keyword coverage (post-edit check)

| Keyword | Where | Count |
|---|---|---|
| checklist / checklists | title, short, full | ~11 in full (natural contexts) |
| to-do list | short, full | 3 |
| ai checklist | title, full (1st sentence, within first 167 chars) | ✓ |
| grocery list / shopping list | full | 4 |
| reminders | short, full | 6+ |
| weekly planner / daily planner | full | 4 |
| packing list, task planner, checklist maker | full (long-tail) | 1 each |

## Follow-ups

- Rating is blocker #1 (stars hidden on the listing): copy improvements drive clicks, not installs. Review prompt after a success moment + reply to every review (replies are indexed).
- RU locale listing = a separate indexed surface; the app is already bilingual (do only on explicit request — project rule).
- A/B via Play Store Experiments is premature: not enough impressions at 500+ installs. Ship copy edits directly.
