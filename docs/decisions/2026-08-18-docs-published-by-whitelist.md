# Docs are published by directory whitelist, numbers never

> Объём: 717 слов — ADR несёт два нормативных перечня (15 разрешённых каталогов и список
> навсегда закрытых путей), которые обязаны читаться дословно, плюс шесть последствий, каждое
> из которых меняет чьё-то поведение: две зоны остаются закрытыми по разным причинам, снимки
> healthcheck уходят из git насовсем, а два дефекта самого гейта описаны как предупреждение
> следующему, кто станет его править. Сжатие ниже потолка выбрасывает либо перечень, либо
> причину направления whitelist — и то и другое и есть содержание решения.

**Date:** 2026-08-18 · **Status:** Accepted
**Supersedes:** the blanket `docs/*` ignore added when the repo went public (2026-06-16)

## Context

`docs/` was gitignored wholesale, which conflated two things: **what a doc describes** (feature,
bug, decision) and **what it measures** (revenue, conversion, retention, ad spend). Only the
second is sensitive. Ignoring both meant a task's documentation could never ride in the same MR
as its code — reviewers saw a diff with no rationale. Worktrees made it worse: a fresh worktree
started with 2 files under `docs/todos/` against 92 in the main checkout.

## Decision

`docs/` is tracked by **whitelist**, `.md` only, with a leak gate on top.

1. **Whitelist, not blacklist.** Un-ignored by name: `active` `archive` `backlog` `brainstorms`
   `completed` `decisions` `designs` `guidelines` `plans` `redesign` `reference` `roadmap`
   `solutions` `todos` `work`. The direction matters more than the list: this repo is public, a
   push is instant and irreversible, so a blacklist **fails open** (one forgotten line publishes
   an ARPU table) while a whitelist **fails closed** (a doc misses the MR — a nuisance).
2. **Text only.** Binaries stay ignored at any depth: the old tree was 311 MB of video and
   screenshots, and a binary committed once stays in history at full weight forever.
3. **Never public:** `docs/marketing/`, `docs/reports/`, `docs/healthcheck/`, `docs/PRODUCT.md`,
   `docs/product-features.md`, `docs/unit-economics.md`, `docs/pricing-strategy.md`.
4. **A gate, because a whitelist is not a review.** `scripts/docs_leak_scan.py` blocks business
   and analytics numbers, private dashboard links, third-party identities and credentials. Local
   pre-commit for speed; `.github/workflows/docs-leak-guard.yml` is the binding one, because
   `.git/hooks` is unversioned, absent on a fresh clone and skipped by `--no-verify`. CI scans
   everything **tracked**, not the diff — a clean doc can be made dirty by a later edit.
5. **`.claude/rules/` and `.claude/hooks/` become tracked.** `.claude/` was ignored, so worktrees
   started with zero rule files and the largest UI branch ran without the rules `CLAUDE.md`
   points at.

The gate deliberately ignores public product facts (store price, published free limits, project
id, domains) and technical numerals (versions, line numbers, test counts, dp/ms, HTTP codes,
shas). A gate that fires on `ChecklistScreen.kt:142` is one people learn to bypass.

## Consequences

- **Numbers now live elsewhere.** A doc says *"the funnel regressed after the RC change"* and
  points at `docs/PRODUCT.md` for the figure. Intended trade.
- **The scanner cannot read a sentence.** "Loses a third here" carries no keyword and passes.
  Whitelisting a directory means the scanner watches it, not that anyone reviewed it.
- **`docs/healthcheck/` snapshots leave git entirely.** 18 were untracked here (tracked since
  2026-08-06, pure prod metrics), and **new ones are ignored too** — `/healthcheck` keeps its
  baseline in the working copy only, so the diff history now lives in one machine and a reinstall
  loses it. That is a real capability being retired, not an oversight: the snapshots are exactly
  the numbers this policy exists to keep out. Untracking also does not remove the old ones from
  history.
- **`.claude/skills/` stays ignored** until `ai-chat-feedback-fixer/processed-feedback.md` is
  redacted: it is a verbatim log of real users' chat messages. A privacy problem, not a numbers
  one. `.claude/hooks/` stays ignored too — it hardcodes a machine-local checkout path, and the
  scanner cannot see it there because it reads only `*.md`.
- **The gate was reviewed adversarially before it shipped, and it did not survive intact.** Two
  independent reviewers found that the first `.gitignore` was a blacklist in disguise
  (`!docs/**/*.md` re-included everything, making the directory list decorative) and that
  `--staged` read the working tree instead of the staged blob — stage a secret, clean the
  worktree copy, and the gate passed it. Both are fixed and pinned by tests. The lesson is in the
  shape: a gate's failure mode is silence, so it needs a case proving it can fail.

## Rejected

- **Blacklist the sensitive files** (what was asked for) — fails open; on a public repo the first
  miss is permanent.
- **A new `docs/public/`** — safe, but strands 500+ existing docs outside every MR, which is the
  problem being solved.
