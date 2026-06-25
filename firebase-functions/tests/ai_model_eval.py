"""
AI model evaluation harness — run the same chat scenarios across MULTIPLE Gemini
models and compare quality (Tier 2 of the AI test system).

This is the server-side companion to the offline client harness
(feature/aichat/impl/src/commonTest/.../presentation/AiChatScenariosTest.kt). The
offline harness is free & deterministic; THIS one hits the DEPLOYED Cloud Functions
and spends real AI credits, so it is **opt-in and cost-gated**:

  * Default run is a DRY RUN: it prints the plan (scenarios × models = N Gemini
    calls, estimated credits) and EXITS. Nothing is sent.
  * You must pass `--yes` to actually fire requests.
  * Only a tiny default subset runs unless you pass `--full`.
  * It is NOT a `test_*.py` file, so pytest will not auto-collect / auto-run it.

It works by sending the gated `model_override` + `test_secret` fields the CF added
in resolve_model(). For the override to take effect the deployed CF must have
MODEL_OVERRIDE_TEST_SECRET set and you must export the SAME value locally:

    export MODEL_OVERRIDE_TEST_SECRET=...      # must match the CF env
    python firebase-functions/tests/ai_model_eval.py                 # dry run (safe)
    python firebase-functions/tests/ai_model_eval.py --yes           # tiny subset, 2 models
    python firebase-functions/tests/ai_model_eval.py --yes --full \
        --models gemini-2.5-flash-lite,gemini-2.5-flash,gemini-2.5-pro

If MODEL_OVERRIDE_TEST_SECRET is unset (locally or on the CF) the override is
disabled and every "model" column will actually be the endpoint's prod default —
the harness warns you about this up front so a run isn't silently meaningless.

LLM output is non-deterministic: assertions check STRUCTURE (intent label / response
type / non-dead-end), never exact wording. A model "passes" a scenario when the
structural expectation holds; the final table is models × pass-rate.
"""

import argparse
import json
import os
import sys
import urllib.request
import urllib.error

BASE = "https://us-central1-aichecklists-40230.cloudfunctions.net"

# Models allowed for comparison — must be a subset of MODEL_OVERRIDE_ALLOWLIST in main.py.
DEFAULT_MODELS = ["gemini-2.5-flash-lite", "gemini-2.5-flash"]

# Credit cost per call, mirrors the CHAT_* costs in main.py — used only for the
# pre-run estimate so you know what a run will spend before you approve it.
COST_BY_ENDPOINT = {"classify": 1, "agent": 3, "completion": 3}

TEST_SECRET = os.environ.get("MODEL_OVERRIDE_TEST_SECRET", "")

ADD_TOOL_NAMES = {"add_item", "add_items", "create_checklist"}


# ── HTTP plumbing (mirrors test_chat_agent.py conventions) ──────────────────────

def _post(path, payload, timeout=120):
    req = urllib.request.Request(
        BASE + path,
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"},
    )
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return r.status, r.read().decode("utf-8")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8")
    except Exception as e:  # noqa: BLE001 — surface network errors as a failed scenario, don't crash the sweep
        return -1, f"{type(e).__name__}: {e}"


def _new_user():
    status, body = _post("/register_user", {
        "device_id": "ai-model-eval",
        "app_version": "1.16.0",
        "platform": "test",
    })
    if status != 200:
        raise RuntimeError(f"register_user failed: {status} {body}")
    return json.loads(body)["user_id"]


def _with_model(payload, model):
    """Attach the gated override fields. Harmless if the CF has the override disabled."""
    p = dict(payload)
    p["model_override"] = model
    p["test_secret"] = TEST_SECRET
    return p


# ── Scenario model ──────────────────────────────────────────────────────────────

class Scenario:
    """One server-side expectation. `check(resp_dict) -> (ok, reason)`."""

    def __init__(self, sid, endpoint, locale, payload, check, core=False):
        self.sid = sid
        self.endpoint = endpoint        # "classify" | "agent" | "completion"
        self.locale = locale
        self.payload = payload          # endpoint-specific body (sans user_id/model)
        self.check = check
        self.core = core                # part of the tiny default subset

    def run(self, user_id, model):
        if self.endpoint == "classify":
            body = _with_model({"user_id": user_id, "locale": self.locale, **self.payload}, model)
            status, raw = _post("/classify_chat_intent", body)
        elif self.endpoint == "agent":
            body = _with_model({"user_id": user_id, "locale": self.locale,
                                "timezone_offset_minutes": 0, **self.payload}, model)
            status, raw = _post("/chat_agent", body)
        elif self.endpoint == "completion":
            body = _with_model({"user_id": user_id, "locale": self.locale,
                                "timezone_offset_minutes": 0, **self.payload}, model)
            status, raw = _post("/chat_completion", body)
        else:
            return False, f"unknown endpoint {self.endpoint}"

        if status != 200:
            return False, f"HTTP {status}: {raw[:160]}"
        try:
            data = json.loads(raw)
        except Exception:
            return False, f"non-JSON: {raw[:160]}"
        return self.check(data)


# ── Structural checks ───────────────────────────────────────────────────────────

def _intent_is(*allowed):
    def chk(d):
        got = d.get("intent")
        return (got in allowed), f"intent={got}, want one of {allowed}"
    return chk


def _agent_emits_add(d):
    if d.get("type") != "tool_calls":
        return False, f"type={d.get('type')}, want tool_calls"
    names = {c.get("name") for c in d.get("tool_calls", [])}
    return (bool(names & ADD_TOOL_NAMES)), f"tools={names}, want an add/create tool"


def _agent_final_nonempty(d):
    if d.get("type") != "final":
        return False, f"type={d.get('type')}, want final"
    return (bool((d.get("content") or "").strip())), "final content empty"


def _agent_no_dead_end(d):
    """Bug-B guard: a 'suggest items' turn must DELIVER — tool_calls, options, or
    substantive final content — never a bare preamble that names nothing."""
    t = d.get("type")
    if t == "tool_calls":
        return True, "ok (tool_calls)"
    if t == "options":
        return (len(d.get("options") or []) >= 2), f"options={d.get('options')}"
    if t == "final":
        c = (d.get("content") or "").strip()
        return (len(c) >= 40), f"final too short to be substantive: {len(c)} chars"
    return False, f"unexpected type={t}"


def _completion_nonempty(d):
    return (bool((d.get("content") or "").strip())), "content empty"


# ── The scenarios (server-relevant mirror of the 30-case set) ───────────────────

SCENARIOS = [
    # classify (Layer 2) — EN
    Scenario("S1_classify_add_en", "classify", "en",
             {"text": "add milk to shopping"}, _intent_is("create_item"), core=True),
    Scenario("S2_classify_reminder_en", "classify", "en",
             {"text": "remind me to buy milk tomorrow at 9", "timezone_offset_minutes": 0},
             _intent_is("set_reminder")),
    Scenario("S3_classify_move_en", "classify", "en",
             {"text": "move all reminders from monday to tuesday"},
             _intent_is("move_reminders")),
    Scenario("S4_classify_create_en", "classify", "en",
             {"text": "create a trip checklist"}, _intent_is("create_checklist")),
    # classify — RU mirrors (~50%)
    Scenario("S5_classify_add_ru", "classify", "ru",
             {"text": "добавь молоко в покупки"}, _intent_is("create_item")),
    Scenario("S6_classify_reminder_ru", "classify", "ru",
             {"text": "напомни купить молоко завтра в 9", "timezone_offset_minutes": 180},
             _intent_is("set_reminder")),

    # agent (Layer 3) — EN
    Scenario("S7_agent_add_en", "agent", "en",
             {"transcript": [{"role": "user", "text": "add milk and bread to shopping"}],
              "checklists_summary": [{"name": "Shopping", "totalItems": 0, "doneItems": 0}]},
             _agent_emits_add, core=True),
    Scenario("S8_agent_qa_en", "agent", "en",
             {"transcript": [{"role": "user", "text": "thanks!"}]},
             _agent_final_nonempty),
    Scenario("S9_agent_suggest_en", "agent", "en",
             {"supports_options": True,
              "transcript": [{"role": "user", "text": "suggest some items to add to my trip list"}],
              "checklists_summary": [{"name": "Trip", "totalItems": 0, "doneItems": 0}]},
             _agent_no_dead_end),
    Scenario("S10_agent_no_deadend_en", "agent", "en",
             {"supports_options": True,
              "transcript": [{"role": "user", "text": "give me ideas to add to groceries"}],
              "checklists_summary": [{"name": "Groceries", "totalItems": 0, "doneItems": 0}]},
             _agent_no_dead_end),
    # agent — RU mirror
    Scenario("S11_agent_add_ru", "agent", "ru",
             {"transcript": [{"role": "user", "text": "добавь молоко и хлеб в покупки"}],
              "checklists_summary": [{"name": "Покупки", "totalItems": 0, "doneItems": 0}]},
             _agent_emits_add),

    # completion (Layer 3 free-form) — EN + RU
    Scenario("S12_completion_help_en", "completion", "en",
             {"messages": [{"role": "user", "content": "what can you do?"}]},
             _completion_nonempty, core=True),
    Scenario("S13_completion_plan_ru", "completion", "ru",
             {"messages": [{"role": "user", "content": "спланируй мою неделю"}]},
             _completion_nonempty),
]


def _selected(full):
    return SCENARIOS if full else [s for s in SCENARIOS if s.core]


def _estimate(scenarios, models):
    calls = len(scenarios) * len(models)
    credits = sum(COST_BY_ENDPOINT.get(s.endpoint, 3) for s in scenarios) * len(models)
    return calls, credits


def main():
    ap = argparse.ArgumentParser(description="Multi-model Gemini eval (cost-gated).")
    ap.add_argument("--yes", action="store_true", help="actually fire requests (otherwise dry-run)")
    ap.add_argument("--full", action="store_true", help="run all scenarios (default: tiny core subset)")
    ap.add_argument("--models", default=",".join(DEFAULT_MODELS),
                    help="comma-separated model ids (must be in the CF allowlist)")
    args = ap.parse_args()

    models = [m.strip() for m in args.models.split(",") if m.strip()]
    scenarios = _selected(args.full)
    calls, credits = _estimate(scenarios, models)

    print("=" * 64)
    print("AI MODEL EVAL - Tier 2 (hits deployed CF, spends real credits)")
    print("=" * 64)
    print(f"Models     : {models}")
    print(f"Scenarios  : {len(scenarios)} ({'full' if args.full else 'core subset'})")
    print(f"Gemini calls: {calls}   (~{credits} AI credits across fresh throwaway users)")
    if not TEST_SECRET:
        print("\n[WARN] MODEL_OVERRIDE_TEST_SECRET is NOT set locally.")
        print("    The override will be IGNORED by the CF -> every 'model' column will be the")
        print("    endpoint's PROD default, not the requested model. Set the secret (matching")
        print("    the deployed CF env) to actually compare models.")

    if not args.yes:
        print("\nDRY RUN. Re-run with --yes to spend the credits above. Nothing sent.")
        return 0

    print("\nRunning...\n")
    # results[model][sid] = (ok, reason)
    results = {m: {} for m in models}
    for model in models:
        # Fresh user per model so credit budgets don't collide across the sweep.
        try:
            uid = _new_user()
        except Exception as e:  # noqa: BLE001
            print(f"[{model}] register failed: {e}")
            for s in scenarios:
                results[model][s.sid] = (False, "no user")
            continue
        for s in scenarios:
            ok, reason = s.run(uid, model)
            results[model][s.sid] = (ok, reason)
            mark = "PASS" if ok else "FAIL"
            print(f"[{model}] {s.sid:28s} {mark}  {'' if ok else reason}")

    # ── Summary table ──
    print("\n" + "=" * 64)
    print("SUMMARY (pass-rate per model)")
    print("=" * 64)
    header = "scenario".ljust(30) + "".join(m.split("gemini-")[-1].ljust(14) for m in models)
    print(header)
    for s in scenarios:
        row = s.sid.ljust(30)
        for m in models:
            ok = results[m][s.sid][0]
            row += ("ok" if ok else "FAIL").ljust(14)
        print(row)
    print("-" * len(header))
    total = len(scenarios)
    score = "score".ljust(30)
    for m in models:
        passed = sum(1 for s in scenarios if results[m][s.sid][0])
        score += f"{passed}/{total}".ljust(14)
    print(score)
    return 0


if __name__ == "__main__":
    sys.exit(main())
