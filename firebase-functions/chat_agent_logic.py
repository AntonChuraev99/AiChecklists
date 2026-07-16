"""Pure transcript-scanning logic for chat_agent (unit-testable without firebase_admin).

Mirrors the cors.py / generated_items.py / credits_logic.py pattern: the money-critical
"where does the current turn begin" decision lives here as a side-effect-free function, while
main.py keeps the request validation and Firestore/Gemini plumbing around it.

A TURN = one user message + the model/tool ping-pong that answers it. A ROUND = one step of
that ping-pong. The server charges per turn (on its first round) and caps rounds per turn — so
both numbers must be measured against the CURRENT turn, never against the whole transcript.

Why this is not the same as "scan everything": today's store clients rebuild the transcript seed
from message TEXT, so a tool entry can only ever appear after the last user message and the two
readings coincide. Once the client persists the real transcript (Stage 3), past turns arrive
with their tool entries attached — and "any tool anywhere" would read a brand-new turn as
"already mid-turn": no charge (every turn free forever) and a round cap tripped by history.
"""


def current_turn_start_index(transcript: list) -> int:
    """Index of the first entry BELONGING to the current turn's agentic ping-pong.

    That is one past the last `role == "user"` entry. Entries before it answer earlier
    user messages and must not be counted against this turn.

    Returns 0 when the transcript holds no user entry at all (degenerate shape): scanning
    the whole array then reproduces the pre-Stage-3 semantics exactly, so this can never
    turn a mid-turn continuation into a fresh charge.
    """
    for i in range(len(transcript) - 1, -1, -1):
        entry = transcript[i]
        if isinstance(entry, dict) and (entry.get("role") or "").strip().lower() == "user":
            return i + 1
    return 0


def scan_current_turn(transcript: list) -> tuple[bool, int]:
    """Measure the CURRENT turn only.

    Returns (is_first_round, agent_round_count):
        is_first_round    -> no tool result has come back yet for THIS turn, so this
                             invocation is the one that must reserve the turn's cost.
        agent_round_count -> tool-call rounds already spent on THIS turn, checked against
                             CHAT_AGENT_MAX_ROUNDS.

    Only model turns carrying `tool_calls` are agentic rounds. Assistant prose seeded as
    conversation history (`role="model"` with text and no tool_calls) is context, not a round.
    """
    has_tool_turn = False
    agent_round_count = 0
    for entry in transcript[current_turn_start_index(transcript):]:
        if not isinstance(entry, dict):
            continue
        role = (entry.get("role") or "").strip().lower()
        if role == "tool":
            has_tool_turn = True
        elif role == "model" and entry.get("tool_calls"):
            agent_round_count += 1
    return (not has_tool_turn, agent_round_count)
