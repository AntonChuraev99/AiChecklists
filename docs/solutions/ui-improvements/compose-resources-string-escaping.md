---
title: "Compose Resources: Don't Use Backslash Escaping in strings.xml"
date: 2026-02-23
category: ui-improvements
tags:
  - compose-multiplatform
  - strings
  - localization
  - compose-resources
  - escaping
module: core/designsystem
symptoms: |
  Quotes and apostrophes in strings.xml display with visible backslashes in the UI:
  - `\"Open Settings\"` instead of `"Open Settings"`
  - `Don\'t` instead of `Don't`
---

## Problem

Strings in `strings.xml` displayed backslash-escaped quotes and apostrophes literally in the UI:

```
Tap \"Open Settings\" below    ← backslashes visible!
Find \"Alarms & reminders\"
Don\'t show again
```

## Root Cause

**Compose Multiplatform Resources handles string escaping differently from Android Resources.**

| Syntax in strings.xml | Android Resources | Compose Resources |
|------------------------|-------------------|-------------------|
| `\"` (backslash + quote) | Displays `"` (backslash stripped) | Displays `\"` (backslash shown literally!) |
| `\'` (backslash + apostrophe) | Displays `'` (backslash stripped) | Displays `\'` (backslash shown literally!) |
| `"` (bare double quote) | Build warning / undefined | Displays `"` (works correctly) |
| `'` (bare apostrophe) | Build error in older versions | Displays `'` (works correctly) |

In Android native resources, `\"` and `\'` are the standard way to include these characters. The resource parser strips the backslash and renders just the character.

In Compose Multiplatform Resources (JetBrains), the backslash is **not** stripped — it is displayed as part of the text.

## Solution

Use bare (unescaped) quotes and apostrophes in Compose Resources `strings.xml`:

```xml
<!-- CORRECT for Compose Resources -->
<string name="step1">Tap "Open Settings" below</string>
<string name="dont_show">Don't show again</string>

<!-- WRONG for Compose Resources — backslashes will show in UI -->
<string name="step1">Tap \"Open Settings\" below</string>
<string name="dont_show">Don\'t show again</string>
```

The only character that still needs XML entity escaping is `&` → `&amp;`:

```xml
<string name="step2">Find "Alarms &amp; reminders"</string>
```

## Affected File

`core/designsystem/src/commonMain/composeResources/values/strings.xml`

## Key Takeaway

When writing strings for Compose Multiplatform, do **not** carry over Android escaping habits (`\"`, `\'`). Compose Resources treats the content as plain text where backslash has no special meaning.
