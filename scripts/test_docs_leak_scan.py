"""Tests for the documentation leak gate.

WHY THIS EXISTS: `docs_leak_scan.py` is the only thing standing between a doc and a public,
permanently-indexed publication. It is a pile of regexes, and a regex that stops matching fails
*silently* — the scan goes green and reads exactly like "nothing to hide". So every pattern here
is pinned by a case that must BLOCK and, where it could over-fire, a case that must PASS.

The blocking cases are not invented. Each one is a line that a human reviewer pulled out of this
repo's own docs on 2026-08-18, in the pass that preceded un-ignoring `docs/`; several of them had
already slipped past an earlier version of the scanner. Redacted here to the shape that matters.

Run: pytest scripts/test_docs_leak_scan.py -q
"""
from __future__ import annotations

import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).parent))

from docs_leak_scan import numeric_lines, scan_text  # noqa: E402


def rules(text: str) -> set[str]:
    return {f[0].split(":")[0] for f in scan_text(text)}


def blocked(text: str) -> bool:
    return bool(scan_text(text))


# --------------------------------------------------------------- credentials

@pytest.mark.parametrize(
    "line, rule",
    [
        ("key `AIzaSyARBOxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx` leaked", "google-api-key"),
        # A TRUNCATED key. The first version of the gate required 35 chars and let this through.
        ("found 1 real secret (Firebase key `AIzaSyARBO…`, commit)", "google-api-key"),
        ("-----BEGIN RSA PRIVATE KEY-----", "private-key-block"),
        ('  "private_key": "xxx"', "private-key-json"),
        ("token ya29.a0AfH6SMBxxxxxxxxxxxxxxxxxxxxxx", "google-oauth-token"),
        ("export OPENAI=sk-abcdefghijklmnopqrstuvwx", "openai-key"),
        ("PAT ghp_abcdefghijklmnopqrstuvwxyz0123456789", "github-token"),
        ("bot 123456789:AAExxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx", "telegram-bot-token"),
        # Elided fingerprint — the shape docs actually use.
        ("the Play cert (`09:8F:1F:…:CF:19`, entry #6)", "signing-fingerprint"),
    ],
)
def test_credentials_block(line: str, rule: str) -> None:
    assert rule in rules(line), f"{rule} must fire on: {line}"


# ------------------------------------------------------------ business numbers

@pytest.mark.parametrize(
    "line",
    [
        "MRR is $4 and purchases run ~4/month",
        "purchase rate 0.48% (4 purchases / 838 new users, 30d)",
        "retention D7 ~1.6% and AI Analyze had 0 events",
        "login funnel 30d: 36 attempts / 6 success, failure rate ~42%",
        "crash-free users 100% -> 97.56%",
        "affecting 2 production users",
        "95 eligible -> 88 sent, no_token 74",
        "ARPU in low-tier markets is ~40-50% of Tier A",
        # No metric keyword at all — carried by "of users". This one leaked past v1.
        "~25-40% of first launches were failing to receive the value",
        "A/B works for ~96% of real users",
        "4763 CF invocations over 7 days",
        # Money and a version share the shape `1.69` / `1.18`; the sigil has to decide, or
        # treating versions as structural silently launders net revenue past the gate.
        "net $1.69/mo per subscriber after the store cut",
    ],
)
def test_business_numbers_block(line: str) -> None:
    assert blocked(line), f"must block: {line}"


@pytest.mark.parametrize(
    "line",
    [
        # Public product facts. A gate that fires on these is a gate people bypass.
        "Premium is $1.99/mo with a 3-day trial",
        "gemini-2.5-flash-lite costs ~$0.0002/req",
        "published after Android revenue covers the $99/yr Apple fee",
        # Technical numerals.
        "see ChecklistScreen.kt:142 and RemoteConfigKeys.kt:75-76",
        "Compose Multiplatform 1.9.3 has no runtime locale API",
        "132 tests green in :feature:checklist:testAndroidHostTest",
        "the handler returns 404 when the doc is absent",
        "fixed in vc81, shipped 2026-08-17, sha f61bcfa4",
        "the sheet is 336dp tall on API 34",
        "nothing from 1.18 is reflected — chat remembers context across sessions",
        "rating is blocker #1: copy drives clicks, not installs",
        # A solutions/INDEX.md row: a date plus keyword TAGS, no measurement anywhere. Matching
        # numbers inside a pre-sliced window cut `2026-08-13` down to `202`, which stopped
        # looking like a date and blocked 8 index rows at once.
        "| 2026-08-13 | bug-fix | install_attributed, days_since_install |"
        " [Ship the event](install-referrer-attribution-2026-08-13.md) |",
    ],
)
def test_public_and_technical_numbers_pass(line: str) -> None:
    assert not blocked(line), f"must NOT block: {line}"


# ---------------------------------------------------------------- identifiers

@pytest.mark.parametrize(
    "line, rule",
    [
        ("Amplitude project 786722, release builds", "amplitude-project-id"),
        ("PROJNUM `27698629989` · projid `aichecklists-40230`", "gcp-project-number"),
        ("Google froze project `gen-lang-client-0932760344`", "gcp-generated-project-id"),
        ("account id `2c9dfaadbca94b44f59f13dbf70519d7`", "cloudflare-account-id"),
        ("bucket `pubsite_prod_9052706413621097736`", "play-publisher-bucket"),
        ("measurement id G-KP9BD9ETD8 never reaches prod", "ga4-measurement-id"),
        ("routine `trig_01AATyYnNCK4XsuyAcgdwYHs`", "cloud-routine-id"),
        ("Play edit id 03035428521595647825", "play-edit-id"),
        ("https://classify-chat-intent-thawefgb2q-uc.a.run.app", "cloudrun-gen2-url"),
        ("see https://app.amplitude.com/analytics/chart/abc", "amplitude-link"),
        ("report at https://claude.ai/code/artifact/7d659450", "private-artifact-link"),
        ("ads account 359-150-8046", "ads-account-id"),
    ],
)
def test_identifiers_block(line: str, rule: str) -> None:
    assert rule in rules(line), f"{rule} must fire on: {line}"


def test_public_project_id_passes() -> None:
    # Semi-public by design (CLAUDE.md): defended by App Check, not by secrecy.
    assert not blocked("Firebase project id `aichecklists-40230` is safe to commit")


# ----------------------------------------------------------------------- PII

@pytest.mark.parametrize(
    "line, rule",
    [
        (r"plan at `C:\Users\Admin\.claude\plans\x.md`", "local-user-path"),
        ("device at 192.168.1.106 on the LAN", "private-ipv4"),
        ("logged in as a.churaev@swapify.dev instead", "third-party-email"),
    ],
)
def test_pii_blocks(line: str, rule: str) -> None:
    assert rule in rules(line), f"{rule} must fire on: {line}"


@pytest.mark.parametrize(
    "line",
    [
        # The owner's own address is the public author on every commit in this repo.
        "author churaevanton@gmail.com",
        "write to support@gisti-ai.com",
        "use your-sa@project.iam.gserviceaccount.com as a template",
        # `feature/home/...` is a source path, not a home directory. This over-fired in v1 and
        # would have blocked a third of the solution docs.
        "- `feature/home/src/commonMain/kotlin/.../MainScreen.kt`",
        "see /home/ci/runner only in CI logs".replace("/home/ci/runner", "feature/home/presentation"),
    ],
)
def test_safe_identities_pass(line: str) -> None:
    assert not blocked(line), f"must NOT block: {line}"


# ------------------------------------------------------------- prompts are IP

def test_prompt_constant_blocks() -> None:
    assert "server-prompt-quote" in rules("must be added to both FEATURE_CATALOG_RU and _EN")


def test_reviewed_marker_clears_a_line() -> None:
    line = "both FEATURE_CATALOG_RU and _EN <!-- docs-leak-scan: reviewed — names only -->"
    assert not blocked(line)


# -------------------------------------------------------------- code fencing

# ------------------------------------------------- exemptions must not launder a real number
#
# Every case below PASSED an earlier version of the gate. They share one shape: a structural
# token (a date, a version, a build code, an issue number, an HTTP-looking literal) sitting near
# a business number and vouching for it. This repo date- and version-prefixes nearly every doc
# line, so this was the common case, not the corner case.

@pytest.mark.parametrize(
    "line",
    [
        "| 2026-08-13 | 47 purchases recorded |",
        "version 1.9.3 saw 88 sent",
        "vc81 shipped 4763 invocations",
        "#1 blocker: 250 users affected",
        "404 purchases in total",
        "200 users signed up last week",
        "we saw 500 installs from the campaign",
        # No sigil at all — the unit is spelled out.
        "crash-free 97.56 percent",
        "конверсия 12 процентов после фикса",
        # Plural/singular drift between the two vocabularies let these through.
        "affecting 2 real users",
        "9 subscribers cancelled this week",
        # Unit economics by another name.
        "~$0.0003 per AI request with 65-90% profit margin at current pricing",
    ],
)
def test_structural_neighbours_do_not_launder(line: str) -> None:
    assert blocked(line), f"must block: {line}"


def test_http_code_still_exempt_when_marked_as_one() -> None:
    assert not blocked("the endpoint returns HTTP 404 for a missing doc")
    assert not blocked("`503` from Gemini is transient, not an incident")


# ------------------------------------------ the reviewed-marker may not sign off a credential

def test_marker_cannot_waive_a_credential() -> None:
    line = "key AIzaSyABCDEFGHIJKLMNOP <!-- docs-leak-scan: reviewed -->"
    assert "google-api-key" in rules(line), "a marker must never clear a secret"
    line = "-----BEGIN RSA PRIVATE KEY----- <!-- docs-leak-scan: reviewed -->"
    assert blocked(line)


# ------------------------------------------------------- rules with no blocking case till now
#
# A rule-deletion mutation matrix found 8 rules that could be removed with the whole suite still
# green — i.e. the suite did not actually protect them. One blocking case each, so a well-meaning
# edit cannot disarm them silently.

@pytest.mark.parametrize(
    "line, rule",
    [
        ("AKIAIOSFODNN7EXAMPLE in the deploy script", "aws-key"),
        ('client_secret: "abcdefghijklmnop1234"', "client-secret"),
        ("xoxb-1234567890-abcdefghijkl", "slack-token"),
        ("logs under /home/buildbot/workspace/out", "home-user-path"),
        ("chart at https://app.revenuecat.com/charts/x", "revenuecat-link"),
        ("https://console.firebase.google.com/project/x/config", "firebase-console-link"),
        ("https://console.cloud.google.com/run/detail/x", "gcp-console-link"),
        ("https://play.google.com/console/u/0/developers/x", "play-console-link"),
        (r"$mainRoot = 'c:\users\admin\studioprojects\checklists'", "local-user-path"),
        ("body of GENERATE_CHECKLIST_PROMPT follows", "server-prompt-quote"),
        ("FILL_CHECKLIST_PROMPT is quoted below", "server-prompt-quote"),
    ],
)
def test_previously_unpinned_rules_block(line: str, rule: str) -> None:
    assert rule in rules(line), f"{rule} must fire on: {line}"


def test_numeric_lines_skips_code_blocks() -> None:
    text = "intro\n```\nval limit = 9999\n```\nprose with 4763 invocations\n"
    got = [t for _, t in numeric_lines(text)]
    assert any("4763" in t for t in got)
    assert not any("9999" in t for t in got), "fenced code is not review surface"


def test_numeric_lines_drops_structural_numbers() -> None:
    text = "shipped 2026-08-17 in vc81, see File.kt:142, version 1.9.3, HTTP 404\n"
    assert numeric_lines(text) == []
