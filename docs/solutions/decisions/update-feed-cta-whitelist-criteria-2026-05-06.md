---
title: "Update Feed CTA Whitelist: When CTAs Open Features vs Navigate Direct"
date: 2026-05-06
type: decision
modules: [feature/updatefeed, core/navigation, feature/create]
keywords: [update-feed, cta, navigation, whitelist, feature-discovery, non-obvious, affordance]
project: gisti-checklists
---

# Update Feed CTA Whitelist: When CTAs Open Features vs Navigate Direct

## Context

Update Feed displays curated release notes and feature announcements. Some posts include Call-To-Action buttons:

```
📱 Widget feature
[Create widget]  ← CTA button
```

**Question:** When is it OK for an Update Feed CTA to navigate directly, vs when must it go through a feature-create flow?

## Decision

**Hard rule:** CTA buttons in Update Feed are **whitelisted to specific, non-obvious entry points ONLY.**

- ✅ **Allowed:** `widget_instruction` (Widget tutorial overlay), `create_weekly_checklist` (Create Checklist with weekly preset)
- ❌ **Not allowed:** `create_checklist` (general creation), `analyze` (AI analysis), `paywall` (subscriptions), `settings`, `home`, or any bottom-nav destination

### Rationale

**The problem:** Update Feed is a "what's new" feature. If every post leads to a direct action (go to home, create, paywall), it becomes **redundant with bottom nav**.

Users can already:
- Tap "Home" icon → see all checklists
- Tap "Create" icon → create manually
- Tap hamburger → see settings, paywall, etc.

Adding CTAs that duplicate bottom nav = **affordance clutter**. Users wonder "why is Create in the Update post instead of the nav?"

**The exception:** CTAs that open **specialized, non-obvious workflows**:
- `widget_instruction` — Widget is Android-specific, not obvious to new users, requires tutorial (in-app overlay is justified)
- `create_weekly_checklist` — Weekly is a **preset variation** of Create, not available as a plain icon. CTA is a discovery vector, justifies specialized entry point (Create screen with viewMode=weekly pre-selected)

**The rule:** If the CTA target is reachable via bottom nav or drawer in <1 tap, **do not add a CTA**. Use native affordances.

## Criteria for Adding New CTA

Before proposing a new CTA to Update Feed, check:

1. **Non-obvious target?** Is the feature hard to discover via nav/drawer?
   - ✅ Widget (Android-specific, not a standard icon)
   - ✅ Weekly (preset of existing feature, not a separate destination)
   - ❌ Home, Analyze, Create, Settings (already nav items)

2. **Specialized entry state?** Does the CTA initialize the feature with context/preset?
   - ✅ "Create weekly" (viewMode=WEEKLY passed to screen)
   - ✅ "Learn about widgets" (overlay, not a screen)
   - ❌ "Go to settings" (no state passed, just a nav tab)

3. **Adds friction vs removes friction?** Does the CTA reduce steps, or add complexity?
   - ✅ Widget tutorial (opens in-app, no external link) → +1 CTA, -0 steps
   - ✅ Create weekly (skips template selection) → +1 CTA, -2 steps
   - ❌ Settings (already 1 tap from drawer) → +1 CTA, +1 step vs existing

4. **Unique to Update Feed?** Is there a better place for this action?
   - ✅ Widget (Update Feed is discovery channel for feature launch)
   - ❌ "Restore purchase" (belongs in Paywall/Settings, not news feed)

## Implementation Pattern

When criteria are met, follow the **qualified CTA pattern** (see `docs/solutions/architecture/qualified-cta-use-case-pattern-2026-05-06.md`):

1. Extract domain logic into a **use case** (in appropriate feature module)
2. Emit typed **AppNavEvent** from AppNavigator
3. Handle event in **App.kt** collector
4. Update **UpdateFeedDeepLinkHandler** to parse and dispatch

**Deeplink format:** `gisti://<action>?<params>`
- `gisti://widget_instruction` (no params)
- `gisti://create?viewMode=weekly`

## Adding a New Whitelisted CTA

Checklist (before merging):

- [ ] New CTA meets all 4 criteria above (document why in commit)
- [ ] Use case implemented (domain logic, sealed Result, test coverage)
- [ ] UpdateFeedDeepLinkHandler updated (parse deeplink, emit event)
- [ ] UpdateFeedContent.kt updated (add post with action to weekly_mode_v1.json or next RC key)
- [ ] `docs/guidelines/updates-feed.md` updated (add to whitelist, update anti-patterns section)
- [ ] CLAUDE.md project section updated (add to "Hard rules" for Update Feed)

## Related Files

- `docs/guidelines/updates-feed.md` (anti-patterns section)
- `docs/solutions/architecture/qualified-cta-use-case-pattern-2026-05-06.md` (implementation)
- `feature/updatefeed/.../domain/deeplink/UpdateFeedDeepLinkHandler.kt`
- `feature/create/.../domain/usecase/CreateWeeklyChecklistUseCase.kt`
- `composeApp/.../App.kt` (event collector)

## Examples of Rejected CTA Proposals

**"Restore purchases" button in Update Feed (rejected, 2026-05-06)**
- Rationale: Available in Paywall (drawer → Paywall) and Settings. Adding CTA = redundancy.
- Resolution: Link goes in Settings, not Update Feed.

**"Try free trial" button (rejected, rationale in paywall-3-variants-2026-04-28)**
- Rationale: Bottom nav exposes paywall via drawer. Duplicate CTA adds noise, doesn't discover.
- Resolution: Paywall shown on engagement triggers (checklist limit, reminder limit), not via feed.

**"Learn about weekly mode" CTA (approved, 2026-05-06)**
- Rationale: Weekly is a preset of Create. Not a separate destination. Qualifies as "specialized entry state".
- Implementation: CreateWeeklyChecklistUseCase (opens Create screen with viewMode=weekly).
