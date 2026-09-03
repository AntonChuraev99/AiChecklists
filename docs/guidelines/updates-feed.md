# Updates Feed — Feature Playbook

_Last updated: 2026-05-18 (expanded CTA whitelist — important user-facing features now get a deeplink button by default; old "non-obvious only" rule retired)_

Authoritative guide for the in-app **Updates** screen (а.к.а. Update Feed). Read this before adding a new post, changing the card, or touching the release-notes source. Applies to any work in `feature/updatefeed/**`.

> TL;DR — every shipped product change earns a post (features, big bug fixes, notable perf wins). Any post that announces an **important user-facing feature** carries a CTA button that opens it directly. Cards group posts by **main version** (1.X, never 1.X.Y), always survive scroll without resetting state, and the whole card is tappable with no ripple.

---

## 1. What the feature is

A read-only feed of version-grouped release updates, accessed from the main drawer (item “Updates”). One card = one app version. Each card can expand to show:

1. Optional **store description** (Google Play release-notes text, emoji preserved).
2. One or more **feature posts** — short title + description + icon, optionally a single CTA button.

Goals:

- Close the loop between shipping a feature and the user noticing it.
- Give the user a single place to catch up on changes they missed.
- **Drive discovery of every important user-facing feature** by pairing the post with a one-tap CTA that opens the feature directly — even when the feature is also reachable from the drawer or bottom nav. Returning users skim Updates; they will not hunt through navigation.

Non-goals:

- **Not** a marketing funnel. Paywall promotion belongs in the premium banner above the feed, not in posts.
- **Not** a support channel. Feedback/contact belongs in the drawer.
- **Not** a dumping ground for invisible internal changes (refactors, library bumps, build infra). If the user cannot feel the change, it does not need a post.
- **Not** a place for localization / new-language announcements. Adding a translated locale (Russian, German, anything) is i18n plumbing — every user who already speaks the new language just sees the app working. Skip the post, skip the release-notes line.

---

## 2. Architecture at a glance

```
UpdateFeedContent.JSON (bundled in-code)
        │
        ▼
UpdateFeedRepositoryImpl ──► VersionReleaseGroup[]
        │                         (grouped by main-version)
        ▼
UpdateFeedViewModel  ──►  Success(releases, isPremium, formattedExpirationDate)
                          Empty / Loading
        │
        ▼
UpdateFeedScreen  (LazyColumn)
        │
        ├── PremiumBanner (top)
        └── items(keys = version) → ReleaseCard
                                     ├── header (Version X.Y + chevron)
                                     └── body (AnimatedVisibility)
                                          ├── storeDescription (optional)
                                          └── FeatureItem[] (optional)
                                                └── action buttons (optional)
```

Key files:

| File | Responsibility |
|---|---|
| `feature/updatefeed/data/UpdateFeedContent.kt` | **Source of truth** for posts + release notes (`UpdateFeedContent.JSON`). Bundled with the APK; no Remote Config override. |
| `feature/updatefeed/domain/model/UpdatePost.kt` | `UpdatePost`, `UpdatePostAction` domain models |
| `feature/updatefeed/domain/model/UpdateFeedConfig.kt` | JSON DTO (posts + releaseNotes map) |
| `feature/updatefeed/domain/model/VersionReleaseGroup.kt` | UI-level grouping by main-version |
| `feature/updatefeed/data/repository/UpdateFeedRepositoryImpl.kt` | Parse JSON, group by version, attach store notes, filter empty groups |
| `feature/updatefeed/domain/deeplink/UpdateFeedDeepLinkHandler.kt` | Resolve `gisti://...` deeplinks from action buttons (regex-based, KMP-safe) |
| `feature/updatefeed/presentation/UpdateFeedViewModel.kt` | Load, track premium status, log analytics, dispatch deeplinks |
| `feature/updatefeed/presentation/UpdateFeedScreen.kt` | Scaffold, premium banner, LazyColumn of cards |
| `feature/updatefeed/presentation/components/ReleaseCard.kt` | Collapsible version card |
| `feature/updatefeed/presentation/components/FeatureItem.kt` | Icon + title + description + actions row |

No Remote Config override. The feed is bundled with the APK because every post is tied to a specific app version — editing the feed without releasing a new build would either reference unreleased features or rewrite the past, both of which are confusing. Content changes ship through the normal release pipeline only.

---

## 3. Data model

```kotlin
data class UpdatePost(
    val id: String,                       // stable, lowercase_snake; never reuse
    val version: String,                  // main-version "1.6" … "1.11" — NEVER patch (1.11.2)
    val title: String,                    // ≤ 40 chars, sentence case, no trailing period
    val description: String,              // 1–2 sentences, ≤ 160 chars, plain text
    val publishedAtMillis: Long,          // epoch ms; used for post order within version
    val iconName: String? = null,         // one of the whitelisted names, see §5
    val actions: List<UpdatePostAction> = emptyList(),  // 0 or 1 element — never more in practice
)

data class UpdatePostAction(
    val label: String,                    // verb-first, 2–3 words, Title Case ("Show me how")
    val deepLink: String,                 // gisti://<host>[?query]
)
```

`UpdateFeedConfig` wraps `posts` + `releaseNotes: Map<mainVersion, ReleaseNote>` where each note is `notes: String` + `publishedAtMillis: Long`.

### Version convention (hard rule)

- **Main-version** (user-facing): `1.6`, `1.7` … `1.12`. No patch digit.
- **Patch-version** (internal, e.g. `1.11.2`) must fold into the main-version group at the repository layer. Never land a post with `version = "1.11.2"` in the JSON — it creates a phantom release card.
- Release notes use main-version keys only.
- Why: Google Play Console release notes are keyed per-user-visible release; matching that avoids sync drift and avoids showing the user three "1.11.x" cards for what feels like one release.

---

## 4. Content rules

### Title

- Describes **what you can now do**, not internal change: "Add the home screen widget" — not "Widget support shipped".
- ≤ 40 characters; one line on compact devices without wrapping.
- Sentence case. No trailing period. No emoji in titles.

### Description

- 1–2 sentences, ≤ 160 characters.
- Plain text only (no markdown). Emoji allowed sparingly in descriptions but **not** in store release-notes text for posts that duplicate a notes line — see §6.
- State the benefit first, mechanic second: "Pin any checklist to your home screen and tick items without opening the app."
- Avoid jargon ("AI-powered", "leverage", "seamless"). Plain English.

### Icons

Whitelist lives in `FeatureItem.iconForName` — add new icons there first, then reference by name. Current whitelist and their intended use:

| Name | Intended meaning |
|---|---|
| `AutoAwesome` | AI / smart features / generic “magical” |
| `Bolt` | Speed, quick actions, instant input |
| `Star` | Premium catalogs (templates), starred/important items |
| `Campaign` | User feedback, CSAT, announcements |
| `Notifications` | Reminders, alerts |
| `Widgets` | Home-screen widget |
| `Replay` | Recurring, schedule |
| `DragIndicator` | Drag & drop, reorder |
| `PlaylistAddCheck` | Checklist item operations |
| `Tune` | Config, selection, fine-tuning |
| `Celebration` | Onboarding completion, milestones |
| `Folder` | Folders, nested checklists, grouping items |
| `Article` | Fallback when nothing else fits |

If in doubt, use `Article`. Don’t invent icon names that aren’t mapped — they silently fall back.

### Actions / CTA buttons

> Default: **any post announcing an important user-facing feature SHOULD carry a CTA button** that opens the feature directly. The Updates Feed is the only place returning users skim to learn what's new; the CTA closes the discovery loop in one tap. Posts about pure visual polish, small UX tweaks, or bug fixes — skip the CTA, the text alone is the notification.

**Bar for including a CTA:** is the post about a flagship surface, a new screen, a new top-level mode, or a notable new capability the user will want to try once? If yes — add the CTA.

**Bar for skipping a CTA:** is the change a visual tweak, a perf win, a bug fix, or an in-place behavior that has no standalone destination? If yes — skip; the post itself is the notification.

Whitelisted deeplink hosts (resolved by `UpdateFeedDeepLinkHandler` → `AppNavigator`):

| Deep link target | Use when | Notes |
|---|---|---|
| `gisti://ai_chat` | Post announces AI Chat assistant or a major AI Chat upgrade | Opens AI Chat screen via `navigateToAiChat()` → `AppNavRoute.AiChat` |
| `gisti://calendar` | Post announces Calendar / Agenda / cross-checklist time view | Opens Calendar via `navigateToCalendar()` → `AppNavRoute.Calendar`. Premium gate is enforced by the screen itself, not by the handler |
| `gisti://create?viewMode=weekly` | Post announces weekly checklist mode | Routed through `AppNavEvent.CreateWeeklyChecklistRequested` → `CreateWeeklyChecklistUseCase`, so the premium gate (`canCreateWeeklyChecklist`) is enforced before navigation |
| `gisti://widget_instruction` | Post announces a widget-related change | Opens the in-app widget instruction overlay; the widget itself must be added from the launcher |
| `gisti://templates` | Post announces a templates expansion (new packs, redesign) | Opens templates picker |
| `gisti://analyze` | Post announces a Create-via-AI improvement (new input modes etc.) | Opens analyze entry |
| `gisti://create` | Post announces a Create-from-scratch flow change | Opens Create screen |
| `gisti://home` | Post announces a main-screen change that the user must see to notice | Opens main screen |
| `gisti://update_feed` | ❌ Never (you're already here — looks like a dead button) | — |
| `gisti://paywall?source=…` | ❌ Never inside a post (paywall promotion lives in the premium banner above the feed) | — |

**Adding a new whitelisted host:** add the case in `UpdateFeedDeepLinkHandler.kt`, make sure `AppNavigator` exposes the matching `navigateTo*()` (extend the interface + `AppNavigatorImpl` if missing), cover with a unit test in `UpdateFeedDeepLinkHandlerTest`, then document it in this table alongside the post that uses it. If the target has a business gate (premium, role-based), enforce it at the screen / use case layer — the handler stays a thin router.

**Label rules:**

- Verb-first, 2–3 words: "Open AI Chat", "Open Calendar", "Add widget", "Show me how".
- Title Case.
- Never repeat the post title in the label.
- One action per post — two CTAs in one card dilute the signal and have never produced a better UX in this app. If the post has two CTA-worthy destinations, split it into two posts.

---

## 5. Versioning & release flow

New feature shipping in v1.X:

1. Decide: is this user-facing? Default answer is **yes — write a post** for every shipped product change: new features, notable improvements, major bug fixes, big performance wins. Skip invisible internal work (refactors, dependency bumps, build infra, telemetry rewires) **and** localization changes (new languages, translation passes — those just make the app work for someone who couldn't read it before; not a product feature for anyone else). If the change has a standalone destination, also attach a CTA (§4).
2. Draft the `UpdatePost` entry:
   - `version = "1.X"` (main-version only).
   - `id = lowercase_snake_v1` — never reuse an existing id even across versions.
   - `publishedAtMillis` = UTC epoch ms of the intended publish date.
3. Append to the `posts` array in `UpdateFeedContent.JSON` (`feature/updatefeed/.../data/UpdateFeedContent.kt`). The JSON is one line by design — keep it that way so diffs stay review-able.
4. If Google Play release notes for this version contain lines that would duplicate the post description, **remove those lines from `releaseNotes[version].notes`**. De-duplication is enforced in `UpdateFeedRepositoryImplTest`; tests will fail if the same line appears twice across the feed.
5. Update post-count and version-count assertions in `UpdateFeedRepositoryImplTest` (e.g. `returnsNineReleaseGroups`, `totalPostCountIsNineteen`) so they match the new content.
6. Run `./gradlew :feature:updatefeed:testDebugUnitTest` — expect green.
7. Open a PR; the new content ships with the APK that includes it. Verify on a test device: force-stop, install, open the app, land on Updates.

Deprecated / never-shipped content:

- Do not hide posts by removing their `version` from `releaseNotes`. Remove the post itself.
- A version with no posts **and** no `releaseNotes` entry is filtered out in the repository and does not render. Safe to leave a version unreferenced once nothing cites it.

---

## 6. UI & motion rules (ReleaseCard)

The card is a small piece of UI with surprisingly many ways to go wrong. All of the following are mandatory — the code currently respects them and regressions have caused visible jank in past sessions.

### 6.1 Collapse state survives scroll

`ReleaseCard` lives inside `LazyColumn`, which dispose-and-recomposes items as they leave the viewport. Plain `remember { mutableStateOf(...) }` is wiped on dispose, and next time the item comes back the default re-applies — i.e., the card "forgets" it was collapsed.

**Do:**

```kotlin
var expanded by rememberSaveable { mutableStateOf(true) }
```

`LazyColumn` with a stable `key = { it.version }` wraps each item in a `SaveableStateProvider(key)`. `rememberSaveable` then stores the flag per-key through the list’s `SaveableStateHolder`, so scroll-recycling no longer resets state. As a bonus, it also survives process death.

**Don’t:**

```kotlin
var expanded by remember { mutableStateOf(true) }   // ❌ resets on scroll
```

### 6.2 Tap target spans the entire card, no ripple

Anchor `clickable` on the **outer `AppCard` modifier**, not on an inner `Row`. An inner `Row.clickable` sits **inside** `CardPadding`, so the outer rim of the card stops registering taps — a fat-finger usability bug that’s easy to miss in development.

Per product decision, the card has **no ripple**. The chevron rotation carries the affordance by itself.

**Do:**

```kotlin
val interactionSource = remember { MutableInteractionSource() }

AppCard(
    modifier = Modifier
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = { expanded = !expanded },
        )
        .semantics { contentDescription = headerDescription },
) { /* header + body */ }
```

**Don’t:**

```kotlin
Row(
    Modifier
        .fillMaxWidth()
        .clickable { expanded = !expanded }   // ❌ obscured by CardPadding on the edges
) { … }
```

If you genuinely want ripple feedback in a future redesign, prefer passing `onClick = … ` into `AppCard` itself (it uses the Material3 clickable `Card` overload) — that keeps the tap target full-size **and** adds a proper ripple. Don’t hand-roll both.

### 6.3 Collapse animation doesn’t snap

The naive layout

```kotlin
Column(verticalArrangement = Arrangement.spacedBy(SpacingMd)) {
    Row(header)
    AnimatedVisibility(visible = expanded, exit = shrinkVertically() + fadeOut()) {
        Column { body }
    }
}
```

**jumps** by `SpacingMd` at the tail of the exit animation. The `spacedBy` value is part of the outer `Column`’s measurement — it persists as long as both children exist in the composition, then vanishes abruptly once `AnimatedVisibility` removes its child. Visually you see the body shrink smoothly to 0, then the card body hop up 12dp in a single frame.

Fix: move the gap **into** the animated body.

```kotlin
Column {   // no spacedBy on the outer column
    Row(header)
    AnimatedVisibility(visible = expanded, …) {
        Column(
            modifier = Modifier.padding(top = SpacingMd),   // ⭐ padding lives inside the animation
            verticalArrangement = Arrangement.spacedBy(SpacingMd),
        ) {
            // body items
        }
    }
}
```

Now `shrinkVertically` measures the 12dp padding together with the body, so total height animates continuously from `full` to `0`. No snap.

### 6.4 Tokens only

- Colours: `MaterialTheme.colorScheme.onSurface` / `.onSurfaceVariant` / `.outlineVariant`. Never `Color(0xFF…)`.
- Typography: `titleMedium` for "Version X.Y", `bodyMedium` for body text, `titleSmall` for feature titles. Never raw `fontSize`.
- Spacing: `AppDimens.SpacingXs / SpacingSm / SpacingMd / SpacingLg`. Never ad-hoc `.dp`.
- Card corners and elevation are owned by `AppCard`. Don’t override in `ReleaseCard`.

### 6.5 Accessibility

- Whole-card `contentDescription` rebuilds on every expand flip: `"Version X.Y release notes, expanded, tap to collapse"` (or "collapsed, tap to expand"). TalkBack reads state + action together.
- Chevron has its own `contentDescription` ("Collapse" / "Expand") for node-level hit-testing by accessibility tools.
- Tap target is the full card — comfortably > 48dp.

---

## 7. Testing

Mandatory tests (all in `commonTest`, no platform deps):

- `UpdateFeedRepositoryImplTest` — JSON parsing, version grouping, de-duplication invariants, store-notes attachment, min-version invariants (19 posts, 9 groups as of v1.14; update counts when content changes).
- `UpdateFeedViewModelTest` — state transitions for Loading/Empty/Success, action-click → deeplink dispatch, premium banner click routing.
- `UpdateFeedDeepLinkHandlerTest` — every deeplink host listed as supported must parse; unknown hosts must return `false` and log.

Do **not** add snapshot tests for `ReleaseCard` — the expanded/collapsed state is animated and stable snapshots are flaky. Animation correctness is verified by hand.

---

## 8. Analytics

| Event | Params | When |
|---|---|---|
| `screen_view` | `screen_name = "update_feed"` | `LaunchedEffect(Unit)` on screen entry |
| `update_feed_action_click` | `post_id`, `label`, `deep_link` | User taps an action button on any post |

Premium banner click is **not** tracked here — it feeds into the existing `paywall_source` chain via `navigator.navigateToPaywall(source = "update_feed")`.

---

## 9. Anti-patterns — things not to do again

- **Don't write a generic CTA label** ("Open", "Go", "Try it now"). Labels must name the destination: "Open AI Chat", "Open Calendar", "Add widget", "Show me how". Generic labels were tried in PR `f56ec05` and produced visual noise with measurable-zero click-through; the fix was specificity, not removing CTAs. Today the default is to ship a CTA for any important user-facing feature (see §4) — what stayed wrong is the wording.
- **Don't pair a CTA with a description that contradicts it.** If the description says "open it from the menu", the user reads that and skips the button. Describe the value of the feature; let the CTA carry the navigation.
- **Don't stack two CTAs on one post.** Pick the strongest destination, or split into two posts. Two CTAs in one card dilute the signal — confirmed across multiple iterations.
- **Don't add a CTA host without wiring it through `AppNavigator`.** The handler must call a typed `navigateTo*()` method; ad-hoc `navController.navigate(...)` outside the navigator breaks the contract that every business gate (premium, role) is enforced in one place.
- **Don't use `remember` for collapsed state.** Scroll-recycling erases it. See §6.1.
- **Don’t put `clickable` on the inner header `Row`.** Edge zones inside `CardPadding` stop responding to taps. See §6.2.
- **Don’t leave outer `spacedBy` around an `AnimatedVisibility` collapse target.** Visible 12dp snap at the end of the exit animation. See §6.3.
- **Don’t ship a post with a patch-version (`1.11.2`) in the JSON.** The repository will fold it, but tests won’t catch a _new_ phantom group — fold at source.
- **Don’t reach for Firebase Remote Config to "hotfix" the feed.** The feed is intentionally bundled with the APK; there is no RC override. If a post is wrong, ship a new release.
- **Don’t use `gisti://update_feed` as a placeholder deeplink** — it loops to the current screen and looks like a dead button.
- **Don’t re-use a post `id`.** Even if the old one is gone. Something downstream (potentially analytics aggregation) relies on id-uniqueness across feed history.

---

## 10. Open items

- Feedback deeplink: no `gisti://feedback` host exists yet. CSAT post (`csat_v1`) currently ships without a CTA; when a feedback host lands, we can re-introduce the button. Revisit §4 Actions table then.
- Post pinning: no concept of a "pinned" top post today. If promotional pinning is ever required, implement in repository ordering, not in post shape.
- Read/unread state: all cards are treated as unread each session. If we add a badge count on the drawer item, introduce a `lastSeenVersion` pref and diff against the latest group.
