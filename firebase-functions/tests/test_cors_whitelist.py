"""Unit tests for the CORS origin whitelist (cors.py).

Run from firebase-functions/:
    python -m pytest tests/test_cors_whitelist.py -q
or without pytest:
    python tests/test_cors_whitelist.py

No Firebase credentials needed — cors.py is dependency-free by design.
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from cors import origin_allowed


def test_apex_allowed():
    assert origin_allowed("https://gisti-ai.com")


def test_www_alias_allowed():
    assert origin_allowed("https://www.gisti-ai.com")


def test_localhost_dev_allowed():
    assert origin_allowed("http://localhost:9090")


def test_legacy_workers_dev_allowed():
    assert origin_allowed("https://checklists.gisti.workers.dev")


def test_preview_deploy_allowed():
    assert origin_allowed("https://abc12345-checklists.gisti.workers.dev")


def test_http_workers_dev_rejected():
    # Suffix rule must require https.
    assert not origin_allowed("http://checklists.gisti.workers.dev")


def test_foreign_origin_rejected():
    assert not origin_allowed("https://evil.example")


def test_suffix_lookalike_rejected():
    # Misses the dot before gisti — a different workers.dev subdomain.
    assert not origin_allowed("https://evil-gisti.workers.dev")


def test_other_localhost_port_rejected():
    assert not origin_allowed("http://localhost:3000")


def test_empty_origin_rejected():
    assert not origin_allowed("")


if __name__ == "__main__":
    failures = 0
    for name, fn in sorted(globals().items()):
        if name.startswith("test_") and callable(fn):
            try:
                fn()
                print(f"PASS {name}")
            except AssertionError:
                failures += 1
                print(f"FAIL {name}")
    sys.exit(1 if failures else 0)
