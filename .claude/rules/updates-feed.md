---
paths:
  - "**/feature/updatefeed/**"
---

# Updates Feed (`feature/updatefeed/`)

Version-grouped release feed shown from the drawer ("Updates"). Content is **bundled in-code** at `feature/updatefeed/.../data/UpdateFeedContent.kt` — no Remote Config override. Each post ties to a specific app version → editing the feed needs a code change + new APK release. Posts use **main-version** format only (`1.X`, never `1.X.Y`); patch versions fold to main at the repository layer. Store release notes and feature posts are de-duplicated.

Full feature playbook (content rules, icon whitelist, publication flow, anti-patterns): **`docs/guidelines/updates-feed.md`**. Releases are driven by the **`/create-release`** skill.

## Hard rules (break at your peril)

- **Every shipped product change earns a post** — features, big bug fixes, notable perf wins. Skip invisible internal work (refactors, deps, build infra) **and** localization / new-language additions (i18n plumbing is not a product feature).
- **Posts about important user-facing features SHOULD carry a CTA button.** Allowed deeplinks (resolved by `UpdateFeedDeepLinkHandler` → `AppNavigator`): `gisti://ai_chat`, `gisti://calendar`, `gisti://create?viewMode=weekly`, `gisti://widget_instruction`, `gisti://templates`, `gisti://analyze`, `gisti://create`, `gisti://home`. New host: extend `AppNavigator` interface + impl, extend the handler, cover with `UpdateFeedDeepLinkHandlerTest`, document in `docs/guidelines/updates-feed.md` §4. Premium gates live on the destination, not the handler.
- **Skip CTA** for visual polish, perf wins, bug fixes, or in-place behavior with no standalone destination — the post text alone is the notification.
- **CTA label must name the destination** ("Open AI Chat", "Open Calendar", "Add widget") — never generic ("Open", "Try it now"); generic produced measurable-zero click-through (PR `f56ec05`).
- `ReleaseCard` state MUST use `rememberSaveable` (not `remember`) — LazyColumn recycles items; plain `remember` loses expanded state on scroll.
- Card tap target MUST be on the outer `AppCard` modifier (not the inner header `Row`), else `CardPadding` eats the edges.
- Header↔body spacing MUST live **inside** the `AnimatedVisibility` content (`padding(top = SpacingMd)` on the inner column), not as outer `Arrangement.spacedBy` — else the collapse animation snaps 12dp at the end.
- New posts ship together with the version they describe — never reference a feature not yet in the released APK.
