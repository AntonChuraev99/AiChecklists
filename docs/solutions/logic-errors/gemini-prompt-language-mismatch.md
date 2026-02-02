---
title: "AI-generated checklists not matching input language"
type: logic_error
category: ai-integration
severity: high
date_discovered: "2026-02-02"
date_solved: "2026-02-02"
modules:
  - firebase-functions/main.py
components:
  - GENERATE_CHECKLIST_PROMPT
  - FILL_CHECKLIST_PROMPT
tags:
  - ai
  - language-detection
  - gemini
  - multilingual
  - cloud-functions
  - prompt-engineering
keywords:
  - language detection
  - checklist language mismatch
  - random language output
  - multilingual support
  - gemini language preference
  - cross-lingual semantic matching
related_docs:
  - docs/solutions/features/ai-integration-pattern.md
  - docs/plans/2026-02-02-feat-ai-checklist-language-matching-plan.md
---

# AI-Generated Checklists Not Matching Input Language

## Problem

Users uploading content in Spanish, German, Russian, or other languages received checklists in random/wrong languages (often English). The AI ignored the input language and generated output inconsistently.

### Symptoms

- Spanish text input → English checklist
- German PDF → mixed language output
- Russian voice recording → English or random language
- "Fill via AI" generated notes in different language than input
- Cross-lingual fills not working (Russian checklist + English input → wrong language notes)

### Reproduction

1. Open app → Create Checklist → Create via AI
2. Enter text in any non-English language: "Список покупок: молоко, хлеб, яйца"
3. Generate checklist
4. **Expected**: Russian checklist with Russian items
5. **Actual**: English or random language checklist

## Root Cause

Firebase Cloud Functions prompts (`GENERATE_CHECKLIST_PROMPT` and `FILL_CHECKLIST_PROMPT` in `main.py`) had **no explicit language instructions**. Without guidance, Gemini's language detection was inconsistent and often defaulted to English.

```python
# BEFORE - No language instruction
GENERATE_CHECKLIST_PROMPT = """You are an AI assistant that creates checklists...

USER PROMPT:
{user_prompt}

USER DATA:
{user_data}
...
"""
```

## Solution

Add explicit `LANGUAGE:` instruction block to both prompts with clear rules for language detection and fallbacks.

### Step 1: Update `GENERATE_CHECKLIST_PROMPT`

**File**: `firebase-functions/main.py` (lines 481-486)

```python
GENERATE_CHECKLIST_PROMPT = """You are an AI assistant that creates checklists based on user requirements.

LANGUAGE: Detect the language of USER DATA. ALL output (checklist_name, items, summary) MUST be in that detected language.
- If USER DATA has mixed languages, use the DOMINANT language (>50% of content)
- If USER DATA is empty or non-textual, use the language of USER PROMPT
- If both are non-textual, use English as fallback

USER PROMPT:
{user_prompt}

USER DATA:
{user_data}
...
"""
```

### Step 2: Update `FILL_CHECKLIST_PROMPT`

**File**: `firebase-functions/main.py` (lines 320-322)

```python
FILL_CHECKLIST_PROMPT = """You are an AI assistant that helps fill checklists based on provided data.

LANGUAGE: Detect the language of USER DATA. The "note" and "summary" fields MUST be in that detected language.
- Match checklist items SEMANTICALLY across languages (e.g., Russian item "Проверить окна" matches English input "windows look good")
- If USER DATA has no text, use the language of checklist items

The user has a checklist with the following items that need to be filled:
{checklist_items}
...
"""
```

### Step 3: Deploy

```bash
cd firebase-functions
./deploy.ps1
```

**Important**: Deploy from the correct git branch! First deployment failed because it was run from `master` instead of the feature branch with changes.

## Key Insights

### 1. Gemini Has Built-In Language Detection
No external libraries needed. Gemini 2.0 Flash automatically detects 100+ languages from text, OCR, and audio transcription.

### 2. Explicit Instructions Override Default Behavior
LLMs need explicit guidance when behavior differs from typical patterns. Adding a `LANGUAGE:` block with clear rules made Gemini prioritize language matching over its default English bias.

### 3. Structured Instructions Are More Reliable
Bulleted rules (using `-`) are more reliably followed than prose instructions. Example:
```
- If USER DATA has mixed languages, use the DOMINANT language (>50% of content)
```

### 4. Dominance Rule Handles Mixed Language
Real-world input often has mixed languages (Russian text + English terms). Using >50% threshold works better than language detection libraries which fail on mixed content.

### 5. Semantic Matching Enables Cross-Lingual Fill
Users create checklists in one language, then fill from content in another. Instructing Gemini to match **semantically** (not just lexically) makes this work automatically.

### 6. Server-Only = Zero Friction
- No client code changes
- No app store release required
- Works immediately for all users
- Lower risk than client-side changes

## Prevention

### For Future Prompt Changes

1. **Always include explicit language handling** in AI prompts that process user content
2. **Define fallback chain**: Primary source → Secondary source → English
3. **Test with multiple languages** before deploying prompt changes
4. **Deploy from correct branch** - verify `git branch --show-current` before `firebase deploy`

### Testing Checklist

| Input | Expected Output |
|-------|-----------------|
| Russian text | Russian checklist |
| Spanish text | Spanish checklist |
| German PDF | German checklist |
| Japanese photo (OCR) | Japanese checklist |
| Mixed Russian + English | Russian (dominant) |
| Image without text | English (fallback) |
| Russian checklist + English fill | English notes |

## Files Changed

| File | Lines | Change |
|------|-------|--------|
| `firebase-functions/main.py` | 320-322 | Added LANGUAGE instruction to FILL_CHECKLIST_PROMPT |
| `firebase-functions/main.py` | 481-486 | Added LANGUAGE instruction to GENERATE_CHECKLIST_PROMPT |

**Total**: 1 file, 8 lines added

**Commit**: `0d0cff7` - "feat(analyze): add language detection to AI prompts"

## Related Documentation

- [AI Integration Pattern](../features/ai-integration-pattern.md) - Complete architecture guide
- [Language Matching Plan](../../plans/2026-02-02-feat-ai-checklist-language-matching-plan.md) - Original implementation plan
