---
title: Google Play "What's New" Text Generation
category: workflows
tags: [google-play, release-notes, update-text, marketing, store-listing]
module: composeApp
created: 2026-03-10
symptoms:
  - Need to write "What's New" text for a Google Play release
  - Adding a new feature to the update changelog
  - Release notes need updating after new version
---

# Google Play "What's New" Text Generation

How to generate and maintain the "What's New" section for Google Play Store listings.

## Constraints

- **Max 500 characters** (Google Play limit for "What's New")
- **Language**: English
- **Tone**: Friendly, benefit-focused, concise

## Template Structure

```
🆕 New in version X.YZ:

[feature entries — newest on top]

Thank you for using Gisti — your AI checklist assistant! 💙
```

### Fixed Parts (always present)

| Part | Text | Chars |
|------|------|-------|
| Header | `🆕 New in version X.YZ:\n\n` | ~27 |
| Footer | `\nThank you for using Gisti — your AI checklist assistant! 💙` | ~57 |
| **Available for features** | | **~416** |

## Feature Entry Format

Each feature is a single line:

```
{emoji} {Short title} — {benefit-focused description}
```

### Rules

1. **One emoji** per feature — pick the most relevant
2. **Short title** (2-4 words) — what the feature is
3. **Dash** `—` (em dash, not hyphen) separates title from description
4. **Description** — what the user can DO, not how it works technically
5. **No period** at the end
6. **Max ~140 chars** per entry (aim for 80-120)

### Emoji Guide

| Feature Type | Emoji Options |
|-------------|---------------|
| Major new feature | 🆕 ✨ 🚀 |
| Recurring/schedule | 🔁 ⏰ 📅 |
| UI improvement | ✨ 💅 🎨 |
| Organization/sort | 📌 📂 🗂️ |
| AI/smart feature | 🤖 🧠 ⚡ |
| Fix/stability | 🛠️ 🔧 💪 |
| Export/sharing | 📤 📄 🔗 |
| Premium/unlock | 👑 ⭐ 💎 |
| Performance | ⚡ 🏎️ |
| Notification | 🔔 📣 |

## Adding a New Feature

1. Write feature entry in the format above
2. **Insert at the top** of the feature list (newest first)
3. Check total character count (must be <= 500)
4. If over 500 chars — **remove the bottom (oldest) entry**
5. Update version number in header

## Examples

### Good Feature Entries

```
🔁 Recurring reminders — set checklists to repeat daily, weekly, or on a custom schedule so you never miss a routine

✨ Smoother checklists — delete completed items in one tap, auto-remove on check, and drag & drop to reorder

📌 Organize Your Checklist — tap ⋮ to separate done and to-do items, completed tasks move to the bottom

🤖 Smarter AI — improved checklist generation from photos, PDFs, and voice recordings

📤 PDF Export — share your checklists as beautifully formatted PDF documents

🔔 Reminders — set one-time reminders so you never forget an important checklist
```

### Bad Feature Entries (avoid)

```
❌ Added RecurringReminderReceiver with AlarmManager integration    ← too technical
❌ Bug fixes and improvements                                       ← too vague
❌ New feature: we added a cool new thing that lets you do stuff.   ← fluff + period
❌ 🔁🔔⏰ Recurring reminders are here!!!                           ← multiple emojis + exclamation
```

## Current "What's New" Text

```
🆕 New in version 1.11:

🔁 Recurring reminders — set checklists to repeat daily, weekly, or on a custom schedule so you never miss a routine

✨ Smoother checklists — delete completed items in one tap, auto-remove on check, and drag & drop to reorder

📌 Organize Your Checklist
Tap ⋮ to separate done and to-do items — completed tasks move to the bottom so you focus on what's left.

Thank you for using Gisti — your AI checklist assistant! 💙
```

## Prompt for Claude

When user asks to generate update text for a feature:

1. Research the feature (recent commits, UI strings, code changes)
2. Write 1-2 entry variants in the format above
3. Show the full updated "What's New" block with the new entry inserted at top
4. Verify total length <= 500 characters
5. If over limit, indicate which bottom entry to remove
