# Cutting a release slice out of a long-lived branch

**Date:** 2026-08-18 · **Branch:** `feat/ux-overhaul` → 1.20.0 (vc82) · **Status:** Done

> Объём: 620 слов — семь независимых ловушек, каждая найдена на этом релизе и каждая
> воспроизводится на следующем. Сжимать дальше можно только выбросив ловушку целиком.

Public repo: mechanisms only. Product metrics live in gitignored `docs/PRODUCT.md`; the branch log in
`docs/active/ux-overhaul-2026-08-13.md`.

## 1. `gradle … | tail` returns the exit code of `tail`

A failed build reads as success to anything checking `$?`. An 85/85 test failure was first reported
as a pass this way.

```bash
./gradlew test --console=plain 2>&1 | tail -50; echo "GRADLE_EXIT=${PIPESTATUS[0]}"
```

## 2. Green tests do not mean the artifact builds

`bundleRelease` died on `validateSigningRelease` — the gitignored keystore was absent from the
worktree. Every test and the debug APK had passed there; nothing on the test path touches signing.

**A worktree inherits tracked files and nothing else.** Same root cause, three symptoms:

| Missing | Symptom | Noticed |
|---|---|---|
| `.claude/rules/` | UI work runs without the project's UI rules | never |
| `docs/todos/` | "already tracked?" answers empty, work re-filed | never |
| `gisti-release.keystore` | `bundleRelease` fails | at the last step |

Only the third fails loudly. Check the others first: `ls .claude/rules/ | wc -l` against the main
checkout. `.worktreeinclude` has the same defect one level up — untracked, so a fresh clone has no
list at all.

## 3. 100% failure is a harness, not 85 regressions

All 85 instrumented tests failed with one identical `No compose hierarchies found in the app`. Real
regressions cluster where you edited and carry varied assertions. Two cheap checks:

1. `adb logcat -d -b crash` — persistent buffer; nothing from the app means it is not dying.
2. `grep -rl "no compose hierarchies" docs/todos/` — found the symptom recorded verbatim two months
   earlier, with the diagnosis attached.

Search deferred work **before** bisecting the diff. A known-broken harness goes in the report as a
stated gap — not as a blocker, not as silence.

## 4. A merge inside the diff range inflates static-analysis counts

Diffing from the previous session's HEAD put the trunk merge inside the range, so the pattern gate
reported 82 runtime warnings against weeks-old trunk code. `0 static HIGH` was the number that
mattered. Diff from the merge commit, or read counts as "code now in range".

## 5. Prose counters drift; no test reads prose

The `UpdateFeedContent` KDoc claimed 33 posts / 13 groups against an actual 36 / 14 — wrong for a
full release. The counters in the *test* file were right, because they are asserted. Recompute from
the artifact instead of incrementing. Same class as a `// mirrors X` comment on a constant.

## 6. Store note and feature post must not say the same thing

A release card renders the store note above its feature posts; when both describe one feature, it
renders twice. Rule already encoded here: a version whose note lines are covered by its posts
carries **no** `releaseNotes` key. 1.20 is such a version — the Play note ships to the store only.

## 7. `docs/*`, never `docs/`

Git does not descend into an excluded directory, so a bare `docs/` silently kills every later
`!docs/...`. Written as `docs/*`, per-file exceptions work — that is how a plan document rides in the
same MR as its code. Verify both directions:

```bash
git check-ignore -v docs/roadmap/other.md      # must print the ignoring rule
git status --porcelain docs/roadmap/allowed.md # must show the file
```

On a public repo each exception is a publication decision. Revenue, purchase counts, conversion and
pricing stay on the ignored side; the tracked file points at them by name.

## Checklist

1. `git status` first — a dead session leaves uncommitted work that `checkout`/`stash` destroys.
2. Compare `.claude/rules/` and `docs/todos/` against the main checkout.
3. Merge the trunk early, or ship without its crash fixes.
4. Build the release artifact, not only test tasks.
5. Read `PIPESTATUS`, not the wrapper's summary.
6. Have the diff reviewed by an agent that did not write it.
