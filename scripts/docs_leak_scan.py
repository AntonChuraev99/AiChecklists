#!/usr/bin/env python3
"""Leak gate for tracked documentation.

WHY THIS EXISTS: this repository is public. Until 2026-08-18 the whole of `docs/` was
gitignored, which made "can this doc be published?" a question nobody had to answer -- and made
it impossible to ship a task's documentation in the same MR as its code. The .gitignore now
whitelists the task-shaped doc directories instead, which moves the burden onto a gate: docs
may describe features and bugs, but no sales, analytics or account numbers may ride along, and
no credential ever may.

A regex gate cannot understand a sentence. It is deliberately built as a *first pass* that
fails loudly on the shapes we have actually leaked or nearly leaked before; a human read is
still required for anything new. What the gate guarantees is that the known shapes cannot pass
silently -- which is the failure mode regex is good at and people are bad at.

Usage:
    python scripts/docs_leak_scan.py --staged        # pre-commit / CI gate
    python scripts/docs_leak_scan.py docs            # audit a tree
    python scripts/docs_leak_scan.py docs --report   # per-directory summary
    python scripts/docs_leak_scan.py docs --list-clean
"""
from __future__ import annotations

import argparse
import re
import subprocess
import sys
from pathlib import Path

# ---------------------------------------------------------------- rules

# Credentials and tokens. Any hit blocks; no context needed, no exceptions.
SECRET_RULES = [
    # {6,} not {35}: the 2026-08-18 review found `AIzaSyARBO…` — a TRUNCATED key quoted inside a
    # grep command in a security post-mortem. A prefix is not usable on its own, but it is a
    # fragment of a real credential and it sailed past a full-length pattern.
    ("google-api-key", r"AIza[0-9A-Za-z_\-]{6,}"),
    # {3,} not {19}: real docs write fingerprints elided — `09:8F:1F:…:CF:19`. The elision is
    # what makes the full-length pattern useless here.
    ("signing-fingerprint", r"(?:[0-9A-Fa-f]{2}:){3,}[0-9A-Fa-f…\.]+"),
    ("private-key-block", r"-----BEGIN [A-Z ]*PRIVATE KEY-----"),
    ("private-key-json", r"[\"']private_key[\"']\s*:"),
    ("google-oauth-token", r"ya29\.[A-Za-z0-9_\-]{20,}"),
    ("openai-key", r"\bsk-[A-Za-z0-9]{20,}"),
    ("github-token", r"\bgh[pousr]_[A-Za-z0-9]{30,}"),
    ("telegram-bot-token", r"\b\d{8,11}:AA[A-Za-z0-9_\-]{30,}"),
    ("client-secret", r"client_secret[\"']?\s*[:=]\s*[\"']?[A-Za-z0-9_\-]{16,}"),
    ("slack-token", r"\bxox[abprs]-[A-Za-z0-9\-]{10,}"),
    ("aws-key", r"\bAKIA[0-9A-Z]{16}\b"),
]

# Business / analytics vocabulary. A hit only blocks when a NUMBER sits near it: a doc may say
# "conversion dropped after the regression", it may not say by how much.
METRIC_WORDS = r"""(?xi)
    ARPU | ARPPU | ARPDAU | \bLTV\b | \bMRR\b | \bARR\b | payback
  | выручк | revenue | конверси | conversion | \bCVR\b | \bCTR\b
  | \bCPA\b | \bCPI\b | \bCPM\b | \bROAS\b | \bDAU\b | \bWAU\b | \bMAU\b
  | retention | ретеншн | churn | отток
  | подписчик | subscriber | trial | триал
  | purchases? | покупк | продаж
  # `installs` plural only: the singular matches "install the APK", "installDebug",
  # "install-referrer" — 21 false blocks in one corpus pass, none of them a metric.
  | installs\b | установок | инсталл | impressions? | показов
  | uplift | refunds? | возврат
  | thumbs.?down | \bCSAT\b | \bNPS\b
  | активаци | activation | engagement | вовлеч
  # "…broke for ~25-40% of users" carries none of the words above. A share OF something the
  # product counts is the shape that actually leaked past the first version of this gate
  # (2026-08-18 review), so the vocabulary covers the countable nouns too, not just the
  # metric names an analyst would use.
  # PLURAL / COUNTING forms only. The first cut of this list used the singular — `user`,
  # `install`, `request`, `event` — and fired 574 times on "user id", "user-facing",
  # "installDebug", "HTTP request". A gate that noisy gets bypassed, which protects nothing.
  # A leak counts things: "2 users", "88 sent", "4763 invocations".
  | (?:real[- ])?users\b | пользовател | юзер | аудитори | audience
  | сессий | sessions\b | events\b | событий | launches\b | запусков
  | funnel | воронк | attempts\b | попыток
  | (?:success|failure|error|conversion|refusal)[ -]rate | доля\s+\w+ | share\s+of
  | eligible | \bsent\b | recipients | получател | invocations\b | вызовов
  | requests\b | запросов | traffic | трафик | crash-free
  | affected | затронут | impacted | thumbs
  # Margin is the one figure the review pass found still standing in a staged doc
  # ("~$0.0003 per AI request with 65-90% profit margin") — unit economics by another name.
  | margin | profit | маржа | маржинальн | прибыл | себестоимост
"""

# Shapes that count as "a number worth hiding".
NUMBER = r"""(?x)
    \d+(?:[.,]\d+)?\s?%
  | \$\s?\d+(?:[.,]\d+)?
  | \d+(?:[.,]\d+)?\s?(?:USD|EUR|RUB|руб)
  | \b\d{2,}\b
"""

# Prices that are already public: the store price, the per-request model cost quoted in the
# public CLAUDE.md, the Apple developer fee.
PUBLIC_MONEY = re.compile(r"(?i)\$\s?(?:1[.,]99|0[.,]0002|99(?:/yr|\b))")

# Numerals that carry no business signal: dates, versions, HTTP codes, API levels, build codes.
#
# ⚠️ A PERCENTAGE IS NEVER STRUCTURAL. The first version of this file listed `\d+\.\d+` here as
# "version", which silently swallowed `1.6%` retention and `97.56%` crash-free — i.e. the gate
# read the most sensitive numbers in the repo as semver and passed them. Percentages are checked
# before this pattern is ever consulted (see scan_text).
STRUCTURAL_OK = re.compile(
    r"""(?xi)
      \b20\d\d-\d\d-\d\d\b
    | \bv?\d+\.\d+(?:\.\d+)?\b            # 1.18, 1.19.2, v2.0 — app and library versions
    | (?:HTTP|status|code|→|`)\s*\d{3}\b  # an HTTP code, only where it is marked as one
    | \bvc\d+\b | \bAPI\s\d+\b | \bAndroid\s\d+\b
    | \b\d+\s?(?:dp|sp|px|ms)\b
    | \#\d+\b                             # "blocker #1", issue / PR numbers
    """
)

# Words that make a number a measurement even without a % or $ sigil.
SPELLED_MEASURE = re.compile(r"(?i)\bper ?cent\b|\bpercent|\bпроцент|\bдолл|\bруб\b|\beuro")

PII_RULES = [
    # (?i) is load-bearing: `c:\users\admin\...` (all lowercase, as PowerShell scripts write it)
    # slipped past a case-sensitive `Users` while the same commit stripped the capitalised form
    # out of CLAUDE.md. Both slash directions and the Git-Bash `/c/Users/` form are covered.
    ("local-user-path", r"(?i)\b[a-z]:[\\/]+users[\\/]+(?!YOUR_USERNAME|<)[a-z0-9._-]+"),
    ("gitbash-user-path", r"(?i)/[a-z]/users/(?!YOUR_USERNAME|<)[a-z0-9._-]+"),
    (
        "private-ipv4",
        r"\b(?:192\.168\.\d{1,3}\.\d{1,3}"
        r"|10\.\d{1,3}\.\d{1,3}\.\d{1,3}"
        r"|172\.(?:1[6-9]|2\d|3[01])\.\d{1,3}\.\d{1,3})\b",
    ),
    # The lookbehind is load-bearing: without it every `feature/home/src/...` source path in
    # every solution doc reads as a leaked home directory. A real one is preceded by a drive
    # letter, a separator-free boundary or nothing at all, never by another path segment.
    ("home-user-path", r"(?<![\w/])/(?:home|Users)/(?!YOUR_USERNAME|<)[A-Za-z0-9._-]+/"),
]

EMAIL = re.compile(r"\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b")
# The owner's own address is already the public author on every commit here; support and
# placeholder addresses are meant to be read. Everything else is somebody else's identity.
EMAIL_OK = re.compile(
    r"(?i)^(?:"
    r"churaevanton@gmail\.com"
    r"|support@gisti(?:-ai)?\.(?:app|com)"
    r"|noreply@\S+"
    r"|your[-.][^@]*@\S+"
    r"|[^@]*@(?:example\.(?:com|org)"
    r"|your-?project\S*"
    r"|\S*\.iam\.gserviceaccount\.com"
    r"|appspot\.gserviceaccount\.\S+"
    r"|developer\.gserviceaccount\.com)"
    r")$"
)

# Private dashboards. The URL itself is the leak: it names an account, and often an experiment.
LINK_RULES = [
    ("amplitude-link", r"https?://(?:app|analytics)\.amplitude\.com/\S+"),
    ("revenuecat-link", r"https?://app\.revenuecat\.com/\S+"),
    ("firebase-console-link", r"https?://console\.firebase\.google\.com/\S+"),
    ("gcp-console-link", r"https?://console\.cloud\.google\.com/\S+"),
    ("play-console-link", r"https?://play\.google\.com/console/\S+"),
    ("ads-account-id", r"\b\d{3}-\d{3}-\d{4}\b"),
    ("private-artifact-link", r"https?://claude\.ai/(?:code/artifact|public/artifacts)/\S+"),
    # Cloud Run gen-2 service URLs. The Cloud Functions base URL is semi-public by design, but
    # the per-service gen-2 host is a deploy artifact the client never uses.
    ("cloudrun-gen2-url", r"https?://[a-z0-9-]+-[a-z0-9]{8,}-[a-z]{2}\.a\.run\.app"),
]

# Identifiers that name an internal account or project. Each one was found in a doc the metric
# rules had already cleared during the 2026-08-18 review pass.
IDENT_RULES = [
    ("amplitude-project-id", r"(?i)amplitude[^\n]{0,60}?\b\d{6}\b|\b\d{6}\b[^\n]{0,30}?amplitude"),
    ("gcp-project-number", r"(?i)(?:PROJNUM|project[ _-]?number)\D{0,12}\d{9,13}\b"),
    ("gcp-generated-project-id", r"\bgen-lang-client-\d{8,}\b"),
    ("cloudflare-account-id", r"\b[0-9a-f]{32}\b"),
    ("play-publisher-bucket", r"\bpubsite_prod_\d+\b"),
    ("ga4-measurement-id", r"\bG-[A-Z0-9]{8,}\b"),
    ("cloud-routine-id", r"\btrig_[A-Za-z0-9]{16,}\b"),
    ("play-edit-id", r"\b\d{19,21}\b"),
]

# Server AI prompts are the product's IP and live in gitignored prompts_private.py (CLAUDE.md,
# "Repository Visibility"). Quoting one into a doc republishes what the .gitignore protects.
# Matching on the constant NAME is deliberately broad — mentioning a prompt is usually fine, so
# clear the line with the reviewed-marker once you have checked no prompt body follows it.
#
# The name list is enumerated from `firebase-functions/prompts_private.py`, NOT invented: the
# 2026-08-18 review found the full bodies of GENERATE_CHECKLIST_PROMPT and FILL_CHECKLIST_PROMPT
# quoted into two docs that had passed both the scanner and a human read, because the first cut
# of this rule only listed the chat-related names. Adding a prompt constant there means adding
# it here.
PROMPT_RULES = [
    ("server-prompt-quote",
     r"\b(?:FEATURE_CATALOG(?:_RU|_EN)?|CLASSIFY_CHAT_INTENT[A-Z_]*"
     r"|CHAT_COMPLETION_PROMPT[A-Z_]*|CHAT_AGENT[A-Z_]*"
     r"|GENERATE_CHECKLIST_PROMPT[A-Z_]*|FILL_CHECKLIST_PROMPT[A-Z_]*"
     r"|ANALYZE_[A-Z_]*PROMPT|SYSTEM_PROMPT[A-Z_]*|[A-Z_]{4,}_PROMPT_TEMPLATE)\b"),
]

# Words naming something the product counts. Next to these a SINGLE digit is already a leak
# ("affecting 2 production users"), whereas next to "1 credit" or "2 layers" it is prose — hence
# the narrower list rather than lowering the global threshold to one digit.
# Kept deliberately in step with the METRIC vocabulary: when METRIC matched `subscriber` inside
# "subscribers" but this list only held the plural, `9 subscribers cancelled` scored as
# not-counted and its single digit was ignored. Same drift hid `affecting 2 real users`, which
# is why the caller uses .search() rather than .fullmatch() on the matched phrase.
AUDIENCE_WORDS = re.compile(
    r"(?i)users|пользовател|юзер|подписчик|subscribers?|purchases?|покупк|installs?"
    r"|инсталл|аудитори|audience|затронут|affected|impacted"
)
SINGLE_DIGIT = re.compile(r"\b\d\b")

METRIC_RE = re.compile(METRIC_WORDS)
NUMBER_RE = re.compile(NUMBER)
SECRET_RES = [(n, re.compile(p)) for n, p in SECRET_RULES]
PII_RES = [(n, re.compile(p)) for n, p in PII_RULES]
LINK_RES = [(n, re.compile(p)) for n, p in LINK_RULES + IDENT_RULES + PROMPT_RULES]

WINDOW = 70      # chars each side of a metric word that count as "near"
CTX_RADIUS = 14  # chars each side of a number consulted for a spelled-out unit
ALLOW_MARKER = "docs-leak-scan: reviewed"  # per-line opt-out, for a line a human has cleared


def scan_text(text):
    """Return [(rule, lineno, severity, excerpt)] for one document."""
    out = []
    for i, line in enumerate(text.splitlines(), 1):
        excerpt = line.strip()[:150]

        # Credentials are checked BEFORE the reviewed-marker and are never waived by it. The
        # marker exists so a human can clear a false positive on a metric or an identifier; it
        # must not be able to sign off on a key, or "reviewed" becomes a way to publish one.
        for name, rx in SECRET_RES:
            if rx.search(line):
                out.append((name, i, "CRITICAL", excerpt))

        if ALLOW_MARKER in line:
            continue

        for name, rx in LINK_RES:
            if rx.search(line):
                out.append((name, i, "BLOCK", excerpt))
        for name, rx in PII_RES:
            if rx.search(line):
                out.append((name, i, "BLOCK", excerpt))

        for m in EMAIL.finditer(line):
            if not EMAIL_OK.match(m.group(0)):
                out.append(("third-party-email", i, "BLOCK", excerpt))
                break

        # Numbers are matched against the WHOLE line and filtered by distance, never against a
        # pre-sliced window. Slicing cut `2026-08-13` into `202`, which no longer looked like a
        # date, so every INDEX row with a date and the word "install" blocked (found 2026-08-18).
        numbers = list(NUMBER_RE.finditer(line))
        singles = list(SINGLE_DIGIT.finditer(line))
        # Spans that ARE a structural token. A number is exempt only when it lies inside one —
        # never merely because a date or a version happens to sit nearby. The window form let
        # `2026-08-13: 47 purchases` and `vc81 shipped 4763 invocations` through, and this repo
        # date- and version-prefixes almost every doc line, so that was the common case.
        structural = [mm.span() for mm in STRUCTURAL_OK.finditer(line)]
        public_money = [mm.span() for mm in PUBLIC_MONEY.finditer(line)]

        def inside(span, spans):
            return any(lo <= span[0] and span[1] <= hi for lo, hi in spans)

        for m in METRIC_RE.finditer(line):
            word = m.group(0).strip()
            counted = AUDIENCE_WORDS.search(word) is not None

            hit = False
            for num in numbers + (singles if counted else []):
                if num.start() > m.end() + WINDOW or num.end() < m.start() - WINDOW:
                    continue
                token, span = num.group(0), num.span()
                if inside(span, public_money):
                    continue
                # A percentage or a sum of money is a measurement, full stop. Version numbers and
                # money share a shape (`1.18` vs `$1.69`), so the sigil decides — and a spelled-out
                # "percent" counts too, or `crash-free 97.56 percent` walks straight through.
                near = line[max(0, span[0] - CTX_RADIUS):span[1] + CTX_RADIUS]
                measured = "%" in token or "$" in token or SPELLED_MEASURE.search(near)
                if not measured and inside(span, structural):
                    continue
                out.append(("metric-number:" + word.lower(), i, "BLOCK", excerpt))
                hit = True
                break
            if hit:
                break
    return out


# ---------------------------------------------------------------- review aid

# A number that carries no business signal. Used only by --numeric-lines, whose job is to hand
# a human the lines actually worth reading rather than the whole 7.8 MB tree.
BORING_NUMBER = re.compile(
    r"""(?xi)
      ^\s*\d+[.)]\s                      # ordered-list marker
    | \b20\d\d-\d\d-\d\d\b               # ISO date
    # Semver BEFORE short-date: `1.9.3` matches the date shape as `1.9`, whose optional tail
    # wants 2-4 digits and so refuses `.3`, leaving a stray `.3` behind. Alternation is
    # first-match-wins, so the more specific pattern has to come first.
    | \bv?\d+\.\d+(?:\.\d+)?\b           # version / semver
    | \b\d{1,2}[-./]\d{1,2}(?:[-./]\d{2,4})?\b   # short date
    | \bvc\d+\b
    | :\d+(?:-\d+)?\b                    # File.kt:123 / :75-76
    | \b\d+\s?(?:px|dp|sp|ms|s|ко?д|KB|MB|GB)\b
    | \bAPI\s\d+\b | \bAndroid\s\d+\b | \bSDK\s\d+\b
    | \b(?:200|201|204|30[12478]|4\d\d|5\d\d)\b
    | \#\d+\b                            # issue / PR number — ESCAPED: bare `#` in a (?x)
                                         # pattern starts a comment and blanks the alternative,
                                         # which makes the whole regex match empty everywhere.
    | \b[0-9a-f]{7,40}\b                 # git sha
    """
)
ANY_NUMBER = re.compile(r"\d")


def numeric_lines(text):
    """Lines whose numbers are not obviously structural — the human-review surface."""
    out = []
    in_code = False
    for i, line in enumerate(text.splitlines(), 1):
        stripped = line.strip()
        if stripped.startswith("```"):
            in_code = not in_code
            continue
        if in_code or not ANY_NUMBER.search(line):
            continue
        # strip the boring shapes; if digits survive, a human should look at the line
        residue = BORING_NUMBER.sub(" ", line)
        if ANY_NUMBER.search(residue):
            out.append((i, stripped[:220]))
    return out


def decode(raw, origin):
    """Decode strictly. Mojibake matches no pattern, so errors='replace' would scan CLEAN —
    a cp1251 or utf-16 copy of a Russian metrics doc passed the first version of this gate."""
    try:
        return scan_text(raw.decode("utf-8"))
    except UnicodeDecodeError as exc:
        return [("non-utf8", 0, "CRITICAL", "%s: %s" % (origin, exc))]


def scan_file(path):
    try:
        raw = path.read_bytes()
    except OSError as exc:  # an unreadable file is a finding, never a silent pass
        return [("unreadable", 0, "CRITICAL", str(exc))]
    return decode(raw, path.as_posix())


def scan_staged(path):
    """Scan the STAGED blob, not the working tree.

    The two differ exactly when it matters: stage a doc holding a key, then overwrite the
    working copy with clean text without re-staging, and a worktree-reading gate reports OK
    while the credential goes into the commit. Reading `:path` closes that.
    """
    proc = subprocess.run(["git", "show", ":" + path], capture_output=True)
    if proc.returncode != 0:
        return [("unreadable-index", 0, "CRITICAL",
                 proc.stderr.decode("utf-8", "replace").strip()[:150])]
    return decode(proc.stdout, path)


def staged_docs():
    """Staged Markdown paths, NUL-separated.

    `-z` is not a nicety: without it git C-quotes any non-ASCII path (`"\\320\\276…"`), the
    quoted string does not exist on disk, and a `.exists()` filter drops it in silence. The
    docs in this repo are Russian; a Cyrillic filename is a matter of time.
    """
    proc = subprocess.run(
        ["git", "diff", "--cached", "--name-only", "-z", "--diff-filter=ACMR"],
        capture_output=True, check=True,
    )
    out = proc.stdout.decode("utf-8", "replace")
    return [p for p in out.split("\0") if p.strip().endswith(".md")]


def collect(paths):
    targets = []
    for raw in paths:
        p = Path(raw)
        if p.is_dir():
            targets.extend(sorted(p.rglob("*.md")))
        elif p.suffix == ".md":
            targets.append(p)
    return targets


def main():
    # Findings quote Russian doc lines; the Windows console defaults to cp1251 and would raise
    # UnicodeEncodeError on the first excerpt, turning a leak report into a crash.
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    except (AttributeError, OSError):
        pass

    ap = argparse.ArgumentParser(description="Leak gate for tracked documentation.")
    ap.add_argument("paths", nargs="*", help="files or directories to scan")
    ap.add_argument("--staged", action="store_true", help="scan staged *.md instead")
    ap.add_argument("--report", action="store_true", help="per-directory summary, no detail")
    ap.add_argument("--list-clean", action="store_true", help="print clean files, one per line")
    ap.add_argument("--list-blocked", action="store_true", help="print blocked files only")
    ap.add_argument("--numeric-lines", action="store_true",
                    help="dump non-structural numeric lines of CLEAN files, for human review")
    args = ap.parse_args()

    if args.staged:
        targets = [Path(p) for p in staged_docs()]
        reader = lambda p: scan_staged(p.as_posix())  # noqa: E731 — index, not worktree
    else:
        targets = collect(args.paths or ["docs"])
        reader = scan_file
        # An explicit argument that matches nothing is a typo, not a clean bill of health.
        if args.paths and not targets:
            print("nothing to scan — no *.md matched: " + " ".join(args.paths))
            return 1

    dirty = {}
    clean = []
    for path in targets:
        found = reader(path)
        if found:
            dirty[path] = found
        else:
            clean.append(path)

    if args.list_clean:
        for p in clean:
            print(p.as_posix())
        return 0

    if args.list_blocked:
        for p in sorted(dirty):
            print(p.as_posix())
        return 0

    if args.numeric_lines:
        shown = 0
        for p in clean:
            lines = numeric_lines(p.read_text(encoding="utf-8", errors="replace"))
            if not lines:
                continue
            shown += 1
            print("\n### " + p.as_posix())
            for no, txt in lines:
                print("L%-5d %s" % (no, txt))
        print("\n--- %d/%d clean file(s) carry numeric lines" % (shown, len(clean)))
        return 0

    if args.report:
        buckets = {}
        for p in targets:
            b = buckets.setdefault(p.parent.as_posix(), [0, 0])
            b[0] += 1
            b[1] += 1 if p in dirty else 0
        for key in sorted(buckets):
            total, hit = buckets[key]
            print("%-52s total=%-4d blocked=%-4d clean=%d" % (key, total, hit, total - hit))
        print("\nTOTAL scanned=%d  blocked=%d  clean=%d" % (len(targets), len(dirty), len(clean)))
        return 0

    for path in sorted(dirty):
        print("\n" + path.as_posix())
        for rule, line, sev, excerpt in dirty[path][:12]:
            print("  %-8s L%-5d %-34s %s" % (sev, line, rule, excerpt))
        if len(dirty[path]) > 12:
            print("  ... %d more" % (len(dirty[path]) - 12))

    if dirty:
        print("\n%d file(s) blocked, %d clean." % (len(dirty), len(clean)))
        print("Docs may describe features and bugs. Sales/analytics numbers, private dashboard")
        print("links, third-party identities and credentials stay out of the public repo.")
        print("Cleared a line by hand? Append a comment containing: " + ALLOW_MARKER)
        return 1

    print("OK -- %d file(s) clean." % len(clean))
    return 0


if __name__ == "__main__":
    sys.exit(main())
