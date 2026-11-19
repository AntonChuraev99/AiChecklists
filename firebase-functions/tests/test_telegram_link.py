"""Telegram account linking — telegram_link.py + the two HTTP shells in main.py. OFFLINE.

No Firebase, no Telegram, no network: telegram_link takes `db` and `now` as parameters, so the
logic runs against the in-memory Firestore below. The fake keeps the two properties of a real
Firestore transaction that the link code depends on:
  * writes are buffered and applied only when the transaction function returns (an early
    `return` leaves NO trace — e.g. a refused token is not consumed);
  * a read after a write inside a transaction raises, exactly like the SDK
    ("Attempted read after write in a transaction").
`DocumentSnapshot.get(field)` raises KeyError on a missing field, as the real SDK does.

The derived-key vector is shared with the TypeScript worker and must never be edited.

Run from firebase-functions/:
    python -m pytest tests/test_telegram_link.py -q
"""

import copy
import logging
import os
import sys
from datetime import datetime, timedelta, timezone
from unittest.mock import MagicMock

import pytest
from flask import Flask
from google.cloud.firestore import DELETE_FIELD

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import telegram_link as tl  # noqa: E402

app = Flask(__name__)

BOT_TOKEN = "123456:TEST-TOKEN"
NOW = datetime(2026, 11, 19, 12, 0, 0, tzinfo=timezone.utc)
TG_A = {"telegram_user_id": 111, "chat_id": 111, "telegram_username": "alice",
        "telegram_first_name": "Alice", "language_code": "ru"}
TG_B = {"telegram_user_id": 222, "chat_id": 222, "telegram_username": "bob",
        "telegram_first_name": "Bob", "language_code": "en"}


# ---------------------------------------------------------------------------
# In-memory Firestore with transaction semantics
# ---------------------------------------------------------------------------

class ReadAfterWrite(AssertionError):
    pass


class FakeSnapshot:
    def __init__(self, ref, data):
        self.reference = ref
        self.id = ref.id
        self._data = copy.deepcopy(data) if data is not None else None
        self.exists = data is not None

    def to_dict(self):
        return copy.deepcopy(self._data) if self._data is not None else None

    def get(self, field_path):
        if self._data is None or field_path not in self._data:
            raise KeyError(field_path)  # the real SDK raises too
        return copy.deepcopy(self._data[field_path])


class FakeDocRef:
    def __init__(self, db, path):
        self._db = db
        self.path = path
        self.id = path.rsplit("/", 1)[-1]

    def get(self, transaction=None):
        if transaction is not None:
            transaction.check_read()
        return FakeSnapshot(self, self._db.store.get(self.path))

    def set(self, data):
        self._db.apply("set", self.path, data)

    def update(self, data):
        self._db.apply("update", self.path, data)

    def delete(self):
        self._db.apply("delete", self.path, None)


class FakeQuery:
    def __init__(self, db, path, filters=(), limit=None):
        self._db = db
        self._path = path
        self._filters = filters
        self._limit = limit

    def where(self, *, filter):  # noqa: A002 — mirrors the SDK keyword
        return FakeQuery(self._db, self._path, self._filters + (filter,), self._limit)

    def limit(self, n):
        return FakeQuery(self._db, self._path, self._filters, n)

    def get(self, transaction=None):
        if transaction is not None:
            transaction.check_read()
        prefix = self._path + "/"
        out = []
        for key in sorted(self._db.store):
            if not key.startswith(prefix) or "/" in key[len(prefix):]:
                continue
            data = self._db.store[key]
            if all(f.op_string == "==" and data.get(f.field_path) == f.value for f in self._filters):
                out.append(FakeSnapshot(FakeDocRef(self._db, key), data))
        return out[: self._limit] if self._limit else out


class FakeCollection(FakeQuery):
    def document(self, doc_id=None):
        if doc_id is None:  # auto-id, like the SDK
            self._db.auto_ids += 1
            doc_id = f"auto{self._db.auto_ids:04d}"
        return FakeDocRef(self._db, f"{self._path}/{doc_id}")

    def add(self, data):
        ref = self.document()
        ref.set(data)
        return None, ref


class FakeTransaction:
    def __init__(self, db):
        self._db = db
        self._writes = []

    def check_read(self):
        if self._writes:
            raise ReadAfterWrite("Attempted read after write in a transaction.")

    def set(self, ref, data):
        self._writes.append(("set", ref.path, data))

    def create(self, ref, data):
        self._writes.append(("create", ref.path, data))

    def update(self, ref, data):
        self._writes.append(("update", ref.path, data))

    def delete(self, ref):
        self._writes.append(("delete", ref.path, None))

    def commit(self):
        for op in self._writes:
            self._db.apply(*op)


class FakeDb:
    def __init__(self):
        self.store = {}
        self.auto_ids = 0

    def collection(self, name):
        return FakeCollection(self, name)

    def transaction(self):
        return FakeTransaction(self)

    def apply(self, op, path, data):
        if op == "create":
            if path in self.store:
                raise LookupError(f"create() on an existing document: {path}")  # SDK: Conflict
            op = "set"
        if op == "set":
            assert DELETE_FIELD not in data.values(), "DELETE_FIELD is only valid in update()"
            self.store[path] = copy.deepcopy(data)
        elif op == "update":
            if path not in self.store:
                raise LookupError(f"update() on a missing document: {path}")  # SDK: NotFound
            for key, value in data.items():
                if value is DELETE_FIELD:
                    self.store[path].pop(key, None)
                else:
                    self.store[path][key] = copy.deepcopy(value)
        elif op == "delete":
            self.store.pop(path, None)


def fake_transactional(fn):
    """Commit-on-return stand-in for google.cloud.firestore.transactional."""
    def run(transaction, *args, **kwargs):
        result = fn(transaction, *args, **kwargs)
        transaction.commit()
        return result
    return run


@pytest.fixture(autouse=True)
def _fake_transactions(monkeypatch):
    monkeypatch.setattr(tl, "transactional", fake_transactional)


@pytest.fixture
def db():
    return FakeDb()


def _account(db, credit_id, google_uid, **extra):
    db.store[f"users/{credit_id}"] = {"google_uid": google_uid, "ai_credits": 100, **extra}


def _link(db, tg_id):
    return db.store.get(f"telegram_links/{tg_id}")


def _credit(db, credit_id):
    return db.store.get(f"users/{credit_id}")


def _token_doc(db, token):
    return db.store.get(f"telegram_link_tokens/{tl.sha256_hex(token)}")


def _link_via_app(db, google_uid, credit_id, tg, now=NOW):
    """app → bot: create_token in the app, /start <token> in the bot."""
    token = tl.create_token(db, now, google_uid=google_uid, credit_user_id=credit_id).body["token"]
    return token, tl.consume_app_token(db, now, token, dict(tg))


# ===========================================================================
# Derived keys and tokens
# ===========================================================================

def test_derived_key_vector_is_shared_with_the_worker():
    # Same vector as the TS test — the two implementations must not drift. Do not edit.
    assert tl.derive_key(BOT_TOKEN, "gisti-telegram-internal-v1") == \
        "7iBbDTERoGTkAbeNNyKajDJfOFWoSO4V4bhvjPRMEjg"
    assert tl.derive_key(BOT_TOKEN, "gisti-telegram-webhook-v1") == \
        "0F_V7JjyEQfts-FqUHjJd6ceTdTdOC2jWK22GPZeawQ"
    assert tl.derive_key(BOT_TOKEN, tl.INTERNAL_KEY_LABEL) == \
        "7iBbDTERoGTkAbeNNyKajDJfOFWoSO4V4bhvjPRMEjg"
    assert tl.derive_key(BOT_TOKEN, tl.WEBHOOK_SECRET_LABEL) == \
        "0F_V7JjyEQfts-FqUHjJd6ceTdTdOC2jWK22GPZeawQ"


def test_internal_key_check():
    good = "7iBbDTERoGTkAbeNNyKajDJfOFWoSO4V4bhvjPRMEjg"
    assert tl.internal_key_matches(good, BOT_TOKEN)
    assert not tl.internal_key_matches(good[:-1] + "x", BOT_TOKEN)
    assert not tl.internal_key_matches(tl.derive_key(BOT_TOKEN, tl.WEBHOOK_SECRET_LABEL), BOT_TOKEN)
    assert not tl.internal_key_matches(None, BOT_TOKEN)
    assert not tl.internal_key_matches("", BOT_TOKEN)
    assert not tl.internal_key_matches("ключ", BOT_TOKEN)  # non-ASCII must not raise
    assert not tl.internal_key_matches(good, "")


def test_generated_token_fits_telegram_start_parameter():
    tokens = {tl.generate_token() for _ in range(50)}
    assert len(tokens) == 50
    for token in tokens:
        assert len(token) == 43 and tl.is_token_shaped(token)
        assert "=" not in token


# ===========================================================================
# Token storage
# ===========================================================================

def test_create_token_stores_only_the_hash(db):
    out = tl.create_token(db, NOW, google_uid="g1", credit_user_id="c1")
    assert out.status == 200
    token = out.body["token"]
    assert out.body["deep_link"] == f"https://t.me/gisti_app_bot?start={token}"
    assert out.body["expires_in_sec"] == 600

    assert list(db.store) == [f"telegram_link_tokens/{tl.sha256_hex(token)}"]
    doc = _token_doc(db, token)
    assert doc == {
        "direction": "app_to_bot", "google_uid": "g1", "credit_user_id": "c1",
        "created_at": NOW, "expires_at": NOW + timedelta(seconds=600), "used_at": None,
    }
    # The raw token appears nowhere — not as a key, not as a value.
    assert all(token not in key for key in db.store)
    assert all(token != v for d in db.store.values() for v in d.values())


def test_create_login_token_stores_telegram_fields_and_only_the_hash(db):
    out = tl.create_login_token(db, NOW, dict(TG_A))
    token = out.body["token"]
    assert out.body["url"] == f"https://app.gisti-ai.com/?tg_link={token}"
    doc = _token_doc(db, token)
    assert doc["direction"] == "bot_to_app"
    assert doc["telegram_user_id"] == 111 and doc["chat_id"] == 111
    assert doc["telegram_username"] == "alice" and doc["language_code"] == "ru"
    assert doc["used_at"] is None
    assert all(token not in key for key in db.store)


# ===========================================================================
# Consume / claim
# ===========================================================================

def test_consume_app_token_links_both_sides_in_one_go(db):
    _account(db, "c1", "g1")
    token, out = _link_via_app(db, "g1", "c1", TG_A)
    assert out.status == 200
    assert out.body == {"google_uid": "g1", "credit_user_id": "c1"}
    assert _link(db, 111) == {
        "telegram_user_id": 111, "chat_id": 111, "telegram_username": "alice",
        "telegram_first_name": "Alice", "language_code": "ru",
        "google_uid": "g1", "credit_user_id": "c1", "linked_at": NOW,
    }
    credit = _credit(db, "c1")
    assert credit["telegram_user_id"] == 111
    assert credit["telegram_username"] == "alice"
    assert credit["telegram_linked_at"] == NOW
    assert credit["ai_credits"] == 100  # untouched
    assert _token_doc(db, token)["used_at"] == NOW


def test_second_consume_of_the_same_token_is_410(db):
    _account(db, "c1", "g1")
    token, first = _link_via_app(db, "g1", "c1", TG_A)
    assert first.status == 200
    again = tl.consume_app_token(db, NOW + timedelta(seconds=5), token, dict(TG_B))
    assert again.status == 410 and again.body == {"error": "token_used"}
    assert _link(db, 222) is None  # the replay linked nothing


def test_expired_token_is_410_and_not_consumed(db):
    _account(db, "c1", "g1")
    token = tl.create_token(db, NOW, google_uid="g1", credit_user_id="c1").body["token"]
    at_expiry = tl.consume_app_token(db, NOW + timedelta(seconds=600), token, dict(TG_A))
    assert at_expiry.status == 410 and at_expiry.body == {"error": "token_expired"}
    later = tl.consume_app_token(db, NOW + timedelta(minutes=30), token, dict(TG_A))
    assert later.status == 410
    assert _token_doc(db, token)["used_at"] is None
    assert _link(db, 111) is None


def test_token_one_second_before_expiry_still_links(db):
    _account(db, "c1", "g1")
    token = tl.create_token(db, NOW, google_uid="g1", credit_user_id="c1").body["token"]
    out = tl.consume_app_token(db, NOW + timedelta(seconds=599), token, dict(TG_A))
    assert out.status == 200


def test_unknown_token_is_404(db):
    _account(db, "c1", "g1")
    unknown = tl.consume_app_token(db, NOW, tl.generate_token(), dict(TG_A))
    assert unknown.status == 404 and unknown.body == {"error": "unknown_token"}
    malformed = tl.consume_app_token(db, NOW, "short", dict(TG_A))
    assert malformed.status == 404
    claimed = tl.claim(db, NOW, tl.generate_token(), google_uid="g1", credit_user_id="c1")
    assert claimed.status == 404
    assert _link(db, 111) is None


def test_claim_refuses_an_app_to_bot_token_and_leaves_it_usable(db):
    _account(db, "c1", "g1")
    token = tl.create_token(db, NOW, google_uid="g1", credit_user_id="c1").body["token"]
    out = tl.claim(db, NOW, token, google_uid="g1", credit_user_id="c1")
    assert out.status == 404 and out.body == {"error": "unknown_token"}
    assert _token_doc(db, token)["used_at"] is None
    assert db.store.keys() == {f"telegram_link_tokens/{tl.sha256_hex(token)}", "users/c1"}
    # The rightful consumer can still spend it.
    assert tl.consume_app_token(db, NOW, token, dict(TG_A)).status == 200


def test_consume_app_token_refuses_a_bot_to_app_token_and_leaves_it_usable(db):
    _account(db, "c1", "g1")
    token = tl.create_login_token(db, NOW, dict(TG_A)).body["token"]
    out = tl.consume_app_token(db, NOW, token, dict(TG_A))
    assert out.status == 404 and out.body == {"error": "unknown_token"}
    assert _token_doc(db, token)["used_at"] is None
    assert _link(db, 111) is None
    assert tl.claim(db, NOW, token, google_uid="g1", credit_user_id="c1").status == 200


def test_claim_links_with_the_telegram_fields_from_the_token(db):
    _account(db, "c1", "g1")
    token = tl.create_login_token(db, NOW, dict(TG_A)).body["token"]
    out = tl.claim(db, NOW, token, google_uid="g1", credit_user_id="c1")
    assert out.status == 200 and out.body == {
        "linked": True, "telegram_username": "alice", "telegram_first_name": "Alice"}
    assert _link(db, 111)["google_uid"] == "g1"
    assert _link(db, 111)["chat_id"] == 111
    assert _credit(db, "c1")["telegram_user_id"] == 111
    assert tl.claim(db, NOW, token, google_uid="g1", credit_user_id="c1").status == 410


def test_without_username_claim_and_status_fall_back_to_first_name(db):
    _account(db, "c1", "g1")
    token = tl.create_login_token(db, NOW, {**TG_A, "telegram_username": None}).body["token"]
    out = tl.claim(db, NOW, token, google_uid="g1", credit_user_id="c1")
    assert out.status == 200 and out.body == {
        "linked": True, "telegram_username": None, "telegram_first_name": "Alice"}
    assert tl.status(db, "g1").body == {
        "linked": True, "telegram_username": None, "telegram_first_name": "Alice",
        "linked_at_ms": int(NOW.timestamp() * 1000),
    }


def test_consume_when_the_credit_doc_is_gone_is_409_and_keeps_the_token(db):
    token = tl.create_token(db, NOW, google_uid="g1", credit_user_id="c1").body["token"]
    out = tl.consume_app_token(db, NOW, token, dict(TG_A))
    assert out.status == 409 and out.body == {"error": "no_account"}
    assert _token_doc(db, token)["used_at"] is None
    assert _link(db, 111) is None


def test_relinking_a_telegram_to_another_account_clears_the_old_account(db):
    _account(db, "cA", "gA")
    _account(db, "cB", "gB")
    assert _link_via_app(db, "gA", "cA", TG_A)[1].status == 200

    out = _link_via_app(db, "gB", "cB", TG_A, now=NOW + timedelta(minutes=1))[1]
    assert out.status == 200
    assert _link(db, 111)["google_uid"] == "gB"
    assert _link(db, 111)["credit_user_id"] == "cB"
    old = _credit(db, "cA")
    for name in tl.CREDIT_DOC_FIELDS:
        assert name not in old
    assert old["google_uid"] == "gA" and old["ai_credits"] == 100  # only the telegram fields go
    assert _credit(db, "cB")["telegram_user_id"] == 111
    assert len([k for k in db.store if k.startswith("telegram_links/")]) == 1


def test_second_telegram_on_the_same_account_replaces_the_first(db):
    _account(db, "c1", "g1")
    assert _link_via_app(db, "g1", "c1", TG_A)[1].status == 200

    out = _link_via_app(db, "g1", "c1", TG_B, now=NOW + timedelta(minutes=1))[1]
    assert out.status == 200
    assert _link(db, 111) is None
    assert _link(db, 222)["google_uid"] == "g1"
    credit = _credit(db, "c1")
    assert credit["telegram_user_id"] == 222 and credit["telegram_username"] == "bob"


def test_second_telegram_via_another_credit_doc_of_the_same_account(db):
    # Two credit docs carry the same google_uid; linking through the second must clear the first.
    _account(db, "c1", "g1")
    _account(db, "c2", "g1")
    assert _link_via_app(db, "g1", "c1", TG_A)[1].status == 200
    assert _link_via_app(db, "g1", "c2", TG_B)[1].status == 200
    assert _link(db, 111) is None
    assert "telegram_user_id" not in _credit(db, "c1")
    assert _credit(db, "c2")["telegram_user_id"] == 222


def test_relink_never_wipes_a_credit_doc_that_carries_another_telegram(db):
    # Stale link doc pointing at cA, while cA already carries a DIFFERENT telegram id.
    _account(db, "cA", "gA", telegram_user_id=999, telegram_username="carol")
    _account(db, "cB", "gB")
    db.store["telegram_links/111"] = {"telegram_user_id": 111, "google_uid": "gA",
                                      "credit_user_id": "cA", "linked_at": NOW}
    assert _link_via_app(db, "gB", "cB", TG_A)[1].status == 200
    assert _credit(db, "cA")["telegram_user_id"] == 999


def test_relinking_the_same_pair_is_idempotent(db):
    _account(db, "c1", "g1")
    assert _link_via_app(db, "g1", "c1", TG_A)[1].status == 200
    later = NOW + timedelta(minutes=5)
    assert _link_via_app(db, "g1", "c1", TG_A, now=later)[1].status == 200
    assert _link(db, 111)["linked_at"] == later
    assert _credit(db, "c1")["telegram_user_id"] == 111


# ===========================================================================
# Credit doc resolution
# ===========================================================================

def test_resolve_credit_doc_prefers_the_requested_match(db):
    _account(db, "c1", "g1")
    _account(db, "c2", "g1")
    _account(db, "other", "g2")
    assert tl.resolve_credit_user_id(db, "g1", "c2") == "c2"
    assert tl.resolve_credit_user_id(db, "g1", None) == "c1"          # first match
    assert tl.resolve_credit_user_id(db, "g1", "other") == "c1"       # not among the matches
    assert tl.resolve_credit_user_id(db, "g1", "missing") == "c1"
    assert tl.resolve_credit_user_id(db, "g1", "a/b") == "c1"         # never addresses a path
    assert tl.resolve_credit_user_id(db, "g-none", "c1") is None      # -> 409 no_account


# ===========================================================================
# Status / unlink
# ===========================================================================

def test_status_reports_the_link(db):
    _account(db, "c1", "g1")
    assert tl.status(db, "g1").body == {"linked": False}
    _link_via_app(db, "g1", "c1", TG_A)
    assert tl.status(db, "g1").body == {
        "linked": True, "telegram_username": "alice", "telegram_first_name": "Alice",
        "linked_at_ms": int(NOW.timestamp() * 1000),
    }


def test_app_unlink_removes_the_link_and_returns_the_chat_to_notify(db):
    _account(db, "c1", "g1")
    _link_via_app(db, "g1", "c1", TG_A)
    out = tl.unlink(db, "g1")
    assert out.status == 200 and out.body == {"linked": False}
    assert out.notify == ((111, "ru"),)
    assert _link(db, 111) is None
    for name in tl.CREDIT_DOC_FIELDS:
        assert name not in _credit(db, "c1")
    again = tl.unlink(db, "g1")
    assert again.status == 200 and again.notify == ()


def test_bot_unlink_removes_the_link(db):
    _account(db, "c1", "g1")
    _link_via_app(db, "g1", "c1", TG_A)
    out = tl.unlink_by_telegram(db, 111)
    assert out.status == 200 and out.body == {"ok": True}
    assert _link(db, 111) is None
    assert "telegram_user_id" not in _credit(db, "c1")
    assert tl.unlink_by_telegram(db, 111).body == {"ok": True}
    assert tl.status(db, "g1").body == {"linked": False}


def test_bot_unlink_tolerates_a_missing_credit_doc(db):
    db.store["telegram_links/111"] = {"telegram_user_id": 111, "google_uid": "g1",
                                      "credit_user_id": "gone", "linked_at": NOW}
    assert tl.unlink_by_telegram(db, 111).status == 200
    assert _link(db, 111) is None


# ===========================================================================
# Input parsing and the unlink notice
# ===========================================================================

def test_parse_telegram_fields():
    fields, err = tl.parse_telegram_fields({
        "telegram_user_id": 111, "chat_id": 111, "username": " alice ",
        "first_name": "Alice", "language_code": "ru",
    })
    assert err is None
    assert fields == {"telegram_user_id": 111, "chat_id": 111, "telegram_username": "alice",
                      "telegram_first_name": "Alice", "language_code": "ru"}
    for bad in ({"telegram_user_id": "111", "chat_id": 1}, {"telegram_user_id": True, "chat_id": 1},
                {"telegram_user_id": 0, "chat_id": 1}, {"telegram_user_id": 5, "chat_id": 0},
                {"telegram_user_id": 5}):
        assert tl.parse_telegram_fields(bad)[0] is None


def test_unlink_message_language():
    assert tl.unlink_message("ru") == tl.UNLINK_MESSAGES["ru"]
    assert tl.unlink_message("pt-BR") == tl.UNLINK_MESSAGES["en"]
    assert tl.unlink_message("HI") == tl.UNLINK_MESSAGES["hi"]
    assert tl.unlink_message(None) == tl.UNLINK_MESSAGES["en"]


def test_notify_unlinked_posts_and_never_leaks_the_token_in_the_reason():
    calls = []

    def ok_post(url, json, timeout):
        calls.append((url, json, timeout))
        return MagicMock(status_code=200)

    assert tl.notify_unlinked(BOT_TOKEN, 111, "ru", ok_post) == (True, "ok")
    url, payload, timeout = calls[0]
    assert url == f"https://api.telegram.org/bot{BOT_TOKEN}/sendMessage"
    assert payload == {"chat_id": 111, "text": tl.UNLINK_MESSAGES["ru"]}
    assert timeout == 5

    def failing_post(url, json, timeout):
        raise ConnectionError(f"Max retries exceeded with url: {url}")

    delivered, reason = tl.notify_unlinked(BOT_TOKEN, 111, "en", failing_post)
    assert not delivered and reason == "ConnectionError"
    assert BOT_TOKEN not in reason

    blocked = MagicMock(status_code=403)
    blocked.json.return_value = {"description": "Forbidden: bot was blocked by the user"}
    delivered, reason = tl.notify_unlinked(BOT_TOKEN, 111, "en", lambda *a, **k: blocked)
    assert not delivered and reason.startswith("http 403")


# ===========================================================================
# HTTP shells in main.py
# ===========================================================================

@pytest.fixture
def main(monkeypatch, db):
    monkeypatch.setattr("firebase_admin._apps", {"[DEFAULT]": True})
    monkeypatch.setattr("firebase_admin.initialize_app", MagicMock())
    monkeypatch.setattr("firebase_admin.firestore.client", lambda: MagicMock())
    monkeypatch.setenv("GEMINI_API_KEY", "fake-key")
    monkeypatch.setenv("TELEGRAM_BOT_TOKEN", BOT_TOKEN)

    import importlib
    import main as main_mod
    importlib.reload(main_mod)

    monkeypatch.setattr(main_mod, "db", db)
    monkeypatch.setattr(main_mod, "_telegram_now", lambda: NOW)
    # Bearer <uid> -> that uid; anything else is an invalid token.
    monkeypatch.setattr(main_mod.firebase_auth, "verify_id_token", lambda tok: {"uid": tok})
    return main_mod


def _req(payload, headers=None, method="POST"):
    req = MagicMock()
    req.method = method
    req.get_json.return_value = payload
    req.headers = headers or {}
    return req


def _account_call(main, payload, uid="g1"):
    headers = {"Authorization": f"Bearer {uid}"} if uid else {}
    with app.test_request_context():
        resp = main.telegram_account(_req(payload, headers))
    return resp.status_code, resp.get_json()


def _internal_call(main, payload, key=tl.derive_key(BOT_TOKEN, tl.INTERNAL_KEY_LABEL)):
    headers = {"X-Gisti-Internal-Key": key} if key is not None else {}
    with app.test_request_context():
        resp = main.telegram_bot_internal(_req(payload, headers))
    return resp.status_code, resp.get_json()


def test_http_create_token_without_an_account_is_409(main):
    status, body = _account_call(main, {"action": "create_token"})
    assert status == 409 and body == {"success": False, "error": "no_account"}


def test_http_claim_without_an_account_is_409(main):
    token = tl.create_login_token(main.db, NOW, dict(TG_A)).body["token"]
    status, body = _account_call(main, {"action": "claim", "token": token})
    assert status == 409 and body["error"] == "no_account"
    assert _token_doc(main.db, token)["used_at"] is None


def test_http_account_requires_a_firebase_token(main):
    status, _ = _account_call(main, {"action": "status"}, uid=None)
    assert status == 401


def test_http_account_rejects_an_unknown_action(main):
    status, _ = _account_call(main, {"action": "delete_everything"})
    assert status == 400


def test_http_internal_rejects_a_wrong_or_missing_key(main):
    payload = {"action": "create_login_token", "telegram_user_id": 111, "chat_id": 111}
    for key in ("wrong", tl.derive_key(BOT_TOKEN, tl.WEBHOOK_SECRET_LABEL), "", None):
        status, body = _internal_call(main, payload, key=key)
        assert status == 401 and body["error"] == "unauthorized"
    assert not any(k.startswith("telegram_link_tokens/") for k in main.db.store)


def test_http_internal_without_bot_token_is_503(main, monkeypatch):
    monkeypatch.delenv("TELEGRAM_BOT_TOKEN")
    status, body = _internal_call(main, {"action": "unlink", "telegram_user_id": 111})
    assert status == 503 and body["error"] == "bot_not_configured"


def test_http_internal_validates_telegram_fields(main):
    status, _ = _internal_call(main, {"action": "create_login_token",
                                      "telegram_user_id": "111", "chat_id": 111})
    assert status == 400
    status, _ = _internal_call(main, {"action": "consume_app_token",
                                      "telegram_user_id": 111, "chat_id": 111})
    assert status == 400  # token missing


def test_http_full_round_trip_app_to_bot_then_unlink(main, monkeypatch):
    _account(main.db, "c1", "g1")
    status, body = _account_call(main, {"action": "create_token", "user_id": "c1"})
    assert status == 200 and body["success"] is True
    token = body["token"]
    assert body["deep_link"].endswith(token) and body["expires_in_sec"] == 600

    status, body = _internal_call(main, {
        "action": "consume_app_token", "token": token, "telegram_user_id": 111, "chat_id": 111,
        "username": "alice", "first_name": "Alice", "language_code": "ru",
    })
    assert status == 200 and body == {"success": True, "google_uid": "g1", "credit_user_id": "c1"}

    status, body = _account_call(main, {"action": "status"})
    assert status == 200 and body["linked"] is True and body["telegram_username"] == "alice"

    sent = []

    def fake_post(url, json, timeout):
        sent.append((url, json, timeout))
        return MagicMock(status_code=200)

    monkeypatch.setattr(main.http_requests, "post", fake_post)
    status, body = _account_call(main, {"action": "unlink"})
    assert status == 200 and body == {"success": True, "linked": False}
    assert sent == [(f"https://api.telegram.org/bot{BOT_TOKEN}/sendMessage",
                     {"chat_id": 111, "text": tl.UNLINK_MESSAGES["ru"]}, 5)]
    assert _account_call(main, {"action": "status"})[1] == {"success": True, "linked": False}


def test_http_bot_to_app_round_trip(main):
    _account(main.db, "c1", "g1")
    status, body = _internal_call(main, {"action": "create_login_token", "telegram_user_id": 111,
                                         "chat_id": 111, "first_name": "Alice"})
    assert status == 200
    token = body["token"]
    assert body["url"] == f"https://app.gisti-ai.com/?tg_link={token}"

    status, body = _account_call(main, {"action": "claim", "token": token})
    assert status == 200 and body == {"success": True, "linked": True,
                                      "telegram_username": None, "telegram_first_name": "Alice"}
    status, body = _account_call(main, {"action": "status"})
    assert body["telegram_username"] is None and body["telegram_first_name"] == "Alice"
    status, body = _account_call(main, {"action": "claim", "token": token})
    assert status == 410 and body["error"] == "token_used"

    status, body = _internal_call(main, {"action": "unlink", "telegram_user_id": 111})
    assert status == 200 and body == {"success": True, "ok": True}
    assert _link(main.db, 111) is None


def test_http_unlink_notice_failure_does_not_change_the_answer(main, monkeypatch, caplog):
    _account(main.db, "c1", "g1")
    _link_via_app(main.db, "g1", "c1", TG_A)

    def broken_post(url, json, timeout):
        raise ConnectionError(f"HTTPSConnectionPool: Max retries exceeded with url: {url}")

    monkeypatch.setattr(main.http_requests, "post", broken_post)
    with caplog.at_level(logging.WARNING, logger="gisti"):
        status, body = _account_call(main, {"action": "unlink"})
    assert status == 200 and body == {"success": True, "linked": False}
    assert any("unlink notice not delivered" in r.getMessage() for r in caplog.records)
    assert all(BOT_TOKEN not in r.getMessage() for r in caplog.records)
    assert all(r.exc_info is None for r in caplog.records)  # a traceback would carry the URL


def test_http_internal_error_is_a_logged_static_500(main, monkeypatch, caplog):
    def boom(*a, **k):
        raise RuntimeError("firestore exploded: secret internals")

    monkeypatch.setattr(main.telegram_link, "status", boom)
    with caplog.at_level(logging.ERROR, logger="gisti"):
        status, body = _account_call(main, {"action": "status"})
    assert status == 500
    assert "secret internals" not in str(body)
    assert any(r.exc_info for r in caplog.records)


# ===========================================================================
# Phase 3 — Telegram Stars subscription
# ===========================================================================

CAP = 300
EXP_1 = NOW + timedelta(days=30)
EXP_2 = NOW + timedelta(days=60)


def _payment(charge_id="ch-1", expiration=EXP_1, tg_id=111, **overrides):
    raw = {
        "telegram_user_id": tg_id,
        "telegram_payment_charge_id": charge_id,
        "currency": "XTR",
        "total_amount": 100,
        "invoice_payload": "premium-30d",
        "is_recurring": True,
        "is_first_recurring": charge_id == "ch-1",
        "subscription_expiration_date": int(expiration.timestamp()) if expiration else None,
        **overrides,
    }
    payment, err = tl.parse_payment(raw)
    assert err is None, err
    return payment


def _linked(db):
    _account(db, "c1", "g1", ai_credits=20, is_premium=False)
    assert _link_via_app(db, "g1", "c1", TG_A)[1].status == 200


def _payment_doc(db, charge_id):
    return db.store.get(f"telegram_payments/{charge_id}")


def _audit_rows(db):
    return [v for k, v in db.store.items() if k.startswith("premium_events_log/")]


def _store(answer):
    return lambda credit_user_id: answer


def test_apply_payment_grants_premium_like_the_rc_grant_path(db):
    _linked(db)
    out = tl.apply_payment(db, NOW, _payment(), credits_cap=CAP)
    assert out.status == 200
    assert out.body == {"applied": True, "status": "applied",
                        "telegram_premium_until_ms": int(EXP_1.timestamp() * 1000),
                        "duplicate": False}
    credit = _credit(db, "c1")
    assert credit["is_premium"] is True
    assert credit["ai_credits"] == CAP  # set to the cap, exactly like the RC grant path
    assert credit["telegram_premium_until"] == EXP_1
    assert credit["telegram_subscription"] == {"charge_id": "ch-1", "state": "active",
                                               "updated_at": NOW}
    assert credit["premium_activated_at"] == NOW.isoformat()
    assert credit["credits_restored_at"] == NOW.isoformat()
    pay = _payment_doc(db, "ch-1")
    assert pay["status"] == "applied" and pay["credit_user_id"] == "c1"
    assert pay["google_uid"] == "g1" and pay["currency"] == "XTR" and pay["total_amount"] == 100
    [row] = _audit_rows(db)
    assert row["source"] == "telegram_stars" and row["user_id"] == "c1"
    assert row["new_state"] == {"is_premium": True, "ai_credits": CAP}


def test_apply_payment_twice_applies_once(db):
    _linked(db)
    first = tl.apply_payment(db, NOW, _payment(), credits_cap=CAP)
    db.store["users/c1"]["ai_credits"] = 7  # spent after the payment
    again = tl.apply_payment(db, NOW + timedelta(minutes=1), _payment(), credits_cap=CAP)
    assert again.status == 200
    assert again.body == {**first.body, "duplicate": True}
    assert _credit(db, "c1")["ai_credits"] == 7  # not granted a second time
    assert len(_audit_rows(db)) == 1


def test_renewal_extends_until_and_never_shortens_it(db):
    _linked(db)
    tl.apply_payment(db, NOW, _payment("ch-1", EXP_1), credits_cap=CAP)
    renewal = tl.apply_payment(db, NOW + timedelta(days=29), _payment("ch-2", EXP_2), credits_cap=CAP)
    assert renewal.body["telegram_premium_until_ms"] == int(EXP_2.timestamp() * 1000)
    assert _credit(db, "c1")["telegram_premium_until"] == EXP_2
    assert _credit(db, "c1")["telegram_subscription"]["charge_id"] == "ch-2"
    # A late / out-of-order event with an earlier expiration keeps the later date, and does not
    # become the "latest charge" that a refund would end premium for.
    tl.apply_payment(db, NOW + timedelta(days=30), _payment("ch-0", EXP_1), credits_cap=CAP)
    assert _credit(db, "c1")["telegram_premium_until"] == EXP_2
    assert _credit(db, "c1")["telegram_subscription"]["charge_id"] == "ch-2"
    tl.revoke_payment(db, NOW + timedelta(days=31), "ch-0", store_premium=_store(False))
    assert _credit(db, "c1")["telegram_premium_until"] == EXP_2
    assert _credit(db, "c1")["is_premium"] is True


def test_payment_without_expiration_grants_thirty_days(db):
    _linked(db)
    tl.apply_payment(db, NOW, _payment(expiration=None), credits_cap=CAP)
    assert _credit(db, "c1")["telegram_premium_until"] == NOW + timedelta(days=30)


def test_payment_from_an_unlinked_telegram_is_unassigned(db):
    _account(db, "c1", "g1", ai_credits=20, is_premium=False)
    out = tl.apply_payment(db, NOW, _payment(), credits_cap=CAP)
    assert out.body == {"applied": False, "status": "unassigned",
                        "telegram_premium_until_ms": None, "duplicate": False}
    pay = _payment_doc(db, "ch-1")
    assert pay["status"] == "unassigned"
    assert pay["credit_user_id"] is None and pay["google_uid"] is None
    assert _credit(db, "c1") == {"google_uid": "g1", "ai_credits": 20, "is_premium": False}
    assert _audit_rows(db) == []
    assert tl.apply_payment(db, NOW, _payment(), credits_cap=CAP).body["duplicate"] is True


def test_non_xtr_payment_is_rejected():
    payment, err = tl.parse_payment({
        "telegram_user_id": 111, "telegram_payment_charge_id": "ch-1",
        "currency": "USD", "total_amount": 100,
    })
    assert payment is None and "XTR" in err


def test_revoking_the_latest_payment_ends_telegram_premium_now(db):
    _linked(db)
    tl.apply_payment(db, NOW, _payment(), credits_cap=CAP)
    later = NOW + timedelta(days=3)
    out = tl.revoke_payment(db, later, "ch-1", store_premium=_store(False))
    assert out.body == {"revoked": True, "status": "refunded", "duplicate": False,
                        "is_premium": False,
                        "telegram_premium_until_ms": int(later.timestamp() * 1000)}
    credit = _credit(db, "c1")
    assert credit["telegram_premium_until"] == later
    assert credit["is_premium"] is False
    assert credit["telegram_subscription"]["state"] == "canceled"
    assert not tl.telegram_premium_active(credit, later)
    assert _payment_doc(db, "ch-1")["status"] == "refunded"
    again = tl.revoke_payment(db, later, "ch-1", store_premium=_store(False))
    assert again.body["duplicate"] is True and again.body["is_premium"] is False


def test_revoke_keeps_premium_for_a_store_subscriber(db):
    _linked(db)
    tl.apply_payment(db, NOW, _payment(), credits_cap=CAP)
    out = tl.revoke_payment(db, NOW, "ch-1", store_premium=_store(True))
    assert out.body["is_premium"] is True
    assert _credit(db, "c1")["is_premium"] is True


def test_revoke_with_revenuecat_unreachable_does_not_demote(db):
    _linked(db)
    tl.apply_payment(db, NOW, _payment(), credits_cap=CAP)
    out = tl.revoke_payment(db, NOW, "ch-1", store_premium=_store(None))
    assert out.body["is_premium"] is True  # left for the daily refill to settle
    assert not tl.telegram_premium_active(_credit(db, "c1"), NOW)


def test_revoking_an_older_charge_keeps_the_newer_period(db):
    _linked(db)
    tl.apply_payment(db, NOW, _payment("ch-1", EXP_1), credits_cap=CAP)
    tl.apply_payment(db, NOW, _payment("ch-2", EXP_2), credits_cap=CAP)
    out = tl.revoke_payment(db, NOW, "ch-1", store_premium=_store(False))
    assert out.body["is_premium"] is True
    assert _credit(db, "c1")["telegram_premium_until"] == EXP_2
    assert _credit(db, "c1")["telegram_subscription"]["state"] == "active"


def test_revoke_unknown_payment_is_404(db):
    out = tl.revoke_payment(db, NOW, "nope", store_premium=_store(False))
    assert out.status == 404 and out.body == {"error": "unknown_payment"}


def test_premium_status_none_telegram_store_and_unlinked(db):
    assert tl.premium_status(db, NOW, 111, store_premium=_store(False), price_stars=100).body == {
        "linked": False, "is_premium": False, "source": None,
        "telegram_premium_until_ms": None, "subscription_state": None, "price_stars": 100,
    }
    _linked(db)
    none = tl.premium_status(db, NOW, 111, store_premium=_store(False), price_stars=100).body
    assert none["linked"] is True and none["is_premium"] is False and none["source"] is None

    tl.apply_payment(db, NOW, _payment(), credits_cap=CAP)
    tg = tl.premium_status(db, NOW, 111, store_premium=_store(False), price_stars=150).body
    assert tg == {"linked": True, "is_premium": True, "source": "telegram",
                  "telegram_premium_until_ms": int(EXP_1.timestamp() * 1000),
                  "subscription_state": "active", "price_stars": 150}
    # RevenueCat also pays: the store wins, so the bot never sells a second subscription.
    store = tl.premium_status(db, NOW, 111, store_premium=_store(True), price_stars=100).body
    assert store["source"] == "store" and store["is_premium"] is True
    # After the Telegram period, with no store subscription, nothing is active.
    after = tl.premium_status(db, EXP_1 + timedelta(seconds=1), 111,
                              store_premium=_store(False), price_stars=100).body
    assert after["is_premium"] is False and after["source"] is None


def test_premium_status_store_only_and_revenuecat_unreachable(db):
    _account(db, "c1", "g1", is_premium=True)
    _link_via_app(db, "g1", "c1", TG_A)
    assert tl.premium_status(db, NOW, 111, store_premium=_store(True),
                             price_stars=100).body["source"] == "store"
    # Unreachable RevenueCat + a premium doc Telegram does not explain -> treated as store.
    assert tl.premium_status(db, NOW, 111, store_premium=_store(None),
                             price_stars=100).body["source"] == "store"


def test_subscription_update_records_state_without_touching_premium(db):
    _linked(db)
    tl.apply_payment(db, NOW, _payment(), credits_cap=CAP)
    out = tl.subscription_update(db, NOW + timedelta(days=1), 111, "canceled")
    assert out.body == {"updated": True, "subscription_state": "canceled"}
    credit = _credit(db, "c1")
    assert credit["telegram_subscription"] == {"charge_id": "ch-1", "state": "canceled",
                                               "updated_at": NOW + timedelta(days=1)}
    assert credit["is_premium"] is True and credit["telegram_premium_until"] == EXP_1
    assert tl.subscription_update(db, NOW, 999, "failed").body == {
        "updated": False, "subscription_state": None}


def test_price_stars_default():
    assert tl.price_stars_or_default(150) == 150
    for bad in (None, 0, -5, "100", True):
        assert tl.price_stars_or_default(bad) == 100


# ---- main.py: verify_premium / RevenueCat revoke / daily refill ----

def _store_not_verified(main, monkeypatch):
    monkeypatch.setattr(main, "verify_premium_from_firestore", lambda uid: main.NOT_VERIFIED)
    monkeypatch.setattr(main, "verify_premium_with_revenuecat", lambda uid: main.NOT_VERIFIED)


def test_verify_premium_honours_telegram_premium_until(main, monkeypatch):
    _store_not_verified(main, monkeypatch)
    real_now = datetime.now(timezone.utc)
    _account(main.db, "c1", "g1", telegram_premium_until=real_now + timedelta(days=5))
    assert main.verify_premium("c1") == main.VERIFIED
    main.db.store["users/c1"]["telegram_premium_until"] = real_now - timedelta(seconds=1)
    assert main.verify_premium("c1") == main.NOT_VERIFIED
    _account(main.db, "c2", "g2")  # no Telegram premium at all: the store answer, unchanged
    assert main.verify_premium("c2") == main.NOT_VERIFIED
    monkeypatch.setattr(main, "verify_premium_with_revenuecat", lambda uid: main.UNAVAILABLE)
    assert main.verify_premium("c2") == main.NOT_VERIFIED


def test_rc_revoke_keeps_premium_while_telegram_is_active(main):
    real_now = datetime.now(timezone.utc)
    _account(main.db, "c1", "g1", is_premium=True, ai_credits=300,
             telegram_premium_until=real_now + timedelta(days=5))
    main._handle_rc_event_payload({"type": "EXPIRATION", "app_user_id": "c1"}, event_id="e1")
    assert main.db.store["users/c1"]["is_premium"] is True
    [row] = _audit_rows(main.db)
    assert row["new_state"]["is_premium"] is True and row["state_changed"] is False

    main.db.store["users/c1"]["telegram_premium_until"] = real_now - timedelta(days=1)
    main._handle_rc_event_payload({"type": "EXPIRATION", "app_user_id": "c1"}, event_id="e2")
    assert main.db.store["users/c1"]["is_premium"] is False


def test_daily_refill_keeps_a_telegram_buyer_and_expires_a_lapsed_one(main, monkeypatch):
    _store_not_verified(main, monkeypatch)
    real_now = datetime.now(timezone.utc)
    _account(main.db, "active", "g1", is_premium=True, ai_credits=10,
             telegram_premium_until=real_now + timedelta(days=5))
    _account(main.db, "lapsed", "g2", is_premium=True, ai_credits=10,
             telegram_premium_until=real_now - timedelta(days=1))
    with app.test_request_context():
        resp = main.refill_premium_credits(_req({}))
    body = resp.get_json()
    assert body["users_updated"] == 1 and body["users_expired"] == 1
    assert main.db.store["users/active"]["is_premium"] is True
    assert main.db.store["users/active"]["ai_credits"] == body["credits_cap"]
    assert main.db.store["users/lapsed"]["is_premium"] is False


# ---- HTTP: the new internal actions ----

def _http_link(main):
    _account(main.db, "c1", "g1", ai_credits=20, is_premium=False)
    assert _link_via_app(main.db, "g1", "c1", TG_A)[1].status == 200


def _http_payment(**overrides):
    return {"action": "apply_payment", "telegram_user_id": 111,
            "telegram_payment_charge_id": "ch-1", "currency": "XTR", "total_amount": 100,
            "is_recurring": True, "subscription_expiration_date": int(EXP_1.timestamp()),
            **overrides}


def test_http_apply_payment_and_premium_status(main, monkeypatch):
    _store_not_verified(main, monkeypatch)
    _http_link(main)
    main.db.store["remote_config/current"] = {"telegram_premium_price_stars": 150}

    status, body = _internal_call(main, _http_payment())
    assert status == 200 and body["applied"] is True and body["status"] == "applied"
    status, again = _internal_call(main, _http_payment())
    assert status == 200 and again["duplicate"] is True

    status, body = _internal_call(main, {"action": "premium_status", "telegram_user_id": 111})
    assert status == 200
    assert body == {"success": True, "linked": True, "is_premium": True, "source": "telegram",
                    "telegram_premium_until_ms": int(EXP_1.timestamp() * 1000),
                    "subscription_state": "active", "price_stars": 150}


def test_http_non_xtr_payment_is_400_and_records_nothing(main):
    _http_link(main)
    status, body = _internal_call(main, _http_payment(currency="USD"))
    assert status == 400 and "XTR" in body["error"]
    assert _payment_doc(main.db, "ch-1") is None


def test_http_unassigned_payment_logs_an_error(main, caplog):
    with caplog.at_level(logging.ERROR, logger="gisti"):
        status, body = _internal_call(main, _http_payment())
    assert status == 200 and body["applied"] is False and body["status"] == "unassigned"
    assert any(r.levelno == logging.ERROR and "unassigned" in r.getMessage() for r in caplog.records)


def test_http_subscription_update_and_revoke(main, monkeypatch):
    _store_not_verified(main, monkeypatch)
    _http_link(main)
    _internal_call(main, _http_payment())
    status, body = _internal_call(main, {"action": "subscription_update",
                                         "telegram_user_id": 111, "state": "canceled"})
    assert status == 200 and body["updated"] is True
    status, _ = _internal_call(main, {"action": "subscription_update",
                                      "telegram_user_id": 111, "state": "paused"})
    assert status == 400

    status, body = _internal_call(main, {"action": "revoke_payment",
                                         "telegram_payment_charge_id": "ch-1"})
    assert status == 200 and body["revoked"] is True and body["is_premium"] is False
    status, body = _internal_call(main, {"action": "revoke_payment",
                                         "telegram_payment_charge_id": "missing"})
    assert status == 404 and body["error"] == "unknown_payment"
    status, _ = _internal_call(main, {"action": "revoke_payment",
                                      "telegram_payment_charge_id": "a/b"})
    assert status == 400


def test_http_new_actions_require_the_internal_key(main):
    for payload in (_http_payment(), {"action": "premium_status", "telegram_user_id": 111},
                    {"action": "revoke_payment", "telegram_payment_charge_id": "ch-1"}):
        status, _ = _internal_call(main, payload, key="wrong")
        assert status == 401
    assert _payment_doc(main.db, "ch-1") is None


# ===========================================================================
# Link-time application of unassigned payments (paid while unlinked)
# ===========================================================================

def _unassigned(db, charge_id, expiration, at=NOW, tg_id=111):
    """A payment that arrived while `tg_id` had no link — recorded via the real apply_payment."""
    out = tl.apply_payment(db, at, _payment(charge_id, expiration, tg_id=tg_id), credits_cap=CAP)
    assert out.body["status"] == "unassigned"


def test_relink_via_app_applies_a_renewal_paid_while_unlinked(db):
    _linked(db)
    tl.apply_payment(db, NOW, _payment("ch-1", EXP_1), credits_cap=CAP)
    tl.unlink(db, "g1")
    renewed_at = NOW + timedelta(days=29)
    _unassigned(db, "ch-2", EXP_2, at=renewed_at)  # the subscription kept renewing
    db.store["users/c1"]["ai_credits"] = 3  # spent meanwhile
    relink_at = renewed_at + timedelta(hours=1)

    _token, out = _link_via_app_with_cap(db, relink_at)
    assert out.status == 200
    assert out.body == {"google_uid": "g1", "credit_user_id": "c1", "premium_applied": 1}
    credit = _credit(db, "c1")
    assert credit["telegram_premium_until"] == EXP_2
    assert credit["is_premium"] is True and credit["ai_credits"] == CAP
    assert credit["telegram_subscription"] == {"charge_id": "ch-2", "state": "active",
                                               "updated_at": relink_at}
    assert credit["premium_activated_at"] == relink_at.isoformat()
    assert credit["credits_restored_at"] == relink_at.isoformat()
    assert credit["telegram_user_id"] == 111 and credit["telegram_linked_at"] == relink_at
    pay = _payment_doc(db, "ch-2")
    assert pay["status"] == "applied" and pay["credit_user_id"] == "c1" and pay["google_uid"] == "g1"
    assert pay["telegram_premium_until"] == EXP_2
    assert pay["applied_at"] == relink_at and pay["applied_via"] == "link"
    linked_rows = [r for r in _audit_rows(db) if r["event_type"] == "telegram_stars_payment_linked"]
    [row] = linked_rows
    assert row["user_id"] == "c1" and row["telegram_payment_charge_id"] == "ch-2"
    assert row["applied_charge_ids"] == ["ch-2"]
    assert row["expiration_at_ms"] == int(EXP_2.timestamp() * 1000)
    assert row["previous_state"] == {"is_premium": True, "ai_credits": 3}
    assert row["new_state"] == {"is_premium": True, "ai_credits": CAP}
    assert row["source"] == "telegram_stars" and row["price"] == 100 and row["currency"] == "XTR"
    # The link-applied charge is now the one a refund ends premium for.
    tl.revoke_payment(db, relink_at, "ch-2", store_premium=_store(False))
    assert _credit(db, "c1")["telegram_premium_until"] == relink_at
    assert _credit(db, "c1")["is_premium"] is False


def _link_via_app_with_cap(db, now, tg=TG_A, cap=CAP):
    token = tl.create_token(db, now, google_uid="g1", credit_user_id="c1").body["token"]
    return token, tl.consume_app_token(db, now, token, dict(tg), credits_cap=cap)


def test_claim_applies_a_live_unassigned_payment(db):
    _account(db, "c1", "g1", ai_credits=20, is_premium=False)
    _unassigned(db, "ch-1", EXP_1)
    claim_at = NOW + timedelta(hours=2)
    token = tl.create_login_token(db, claim_at, dict(TG_A)).body["token"]

    out = tl.claim(db, claim_at, token, google_uid="g1", credit_user_id="c1", credits_cap=CAP)
    assert out.status == 200 and out.body == {
        "linked": True, "telegram_username": "alice", "telegram_first_name": "Alice",
        "premium_applied": 1,
    }
    credit = _credit(db, "c1")
    assert credit["telegram_premium_until"] == EXP_1
    assert credit["is_premium"] is True and credit["ai_credits"] == CAP
    assert credit["telegram_subscription"]["charge_id"] == "ch-1"
    pay = _payment_doc(db, "ch-1")
    assert pay["status"] == "applied" and pay["credit_user_id"] == "c1"
    assert pay["applied_via"] == "link" and pay["telegram_premium_until"] == EXP_1
    [row] = _audit_rows(db)
    assert row["event_type"] == "telegram_stars_payment_linked"
    assert row["previous_state"] == {"is_premium": False, "ai_credits": 20}


def test_link_applies_only_payments_whose_period_is_still_running(db):
    _account(db, "c1", "g1", ai_credits=20, is_premium=False)
    # Telegram's own expiration date has passed: exactly at the link instant counts as ended.
    _unassigned(db, "ch-exp", NOW, at=NOW - timedelta(days=30))
    # No expiration date: created_at + 30 days decides — one ended, one still running.
    _unassigned(db, "ch-old", None, at=NOW - timedelta(days=31))
    _unassigned(db, "ch-live", None, at=NOW - timedelta(days=10))

    _token, out = _link_via_app_with_cap(db, NOW)
    assert out.body["premium_applied"] == 1
    assert _payment_doc(db, "ch-live")["status"] == "applied"
    assert _payment_doc(db, "ch-live")["telegram_premium_until"] == NOW + timedelta(days=20)
    assert _credit(db, "c1")["telegram_premium_until"] == NOW + timedelta(days=20)
    for expired in ("ch-exp", "ch-old"):
        pay = _payment_doc(db, expired)
        assert pay["status"] == "unassigned"
        assert pay["credit_user_id"] is None and pay["google_uid"] is None
        assert "applied_at" not in pay and "telegram_premium_until" not in pay


def test_expired_unassigned_payment_is_not_applied(db):
    _account(db, "c1", "g1", ai_credits=20, is_premium=False)
    _unassigned(db, "ch-1", EXP_1)
    before = copy.deepcopy(_payment_doc(db, "ch-1"))

    _token, out = _link_via_app_with_cap(db, EXP_1 + timedelta(minutes=1))
    assert out.status == 200
    assert out.body == {"google_uid": "g1", "credit_user_id": "c1"}  # no premium_applied
    assert _payment_doc(db, "ch-1") == before
    credit = _credit(db, "c1")
    assert credit["is_premium"] is False and credit["ai_credits"] == 20
    assert "telegram_premium_until" not in credit and "telegram_subscription" not in credit
    assert _audit_rows(db) == []


def test_link_never_shortens_a_later_premium_nor_moves_the_marker_back(db):
    later = EXP_2 + timedelta(days=5)
    marker = {"charge_id": "ch-9", "state": "active", "updated_at": NOW - timedelta(days=3)}
    _account(db, "c1", "g1", ai_credits=20, is_premium=True,
             telegram_premium_until=later, telegram_subscription=dict(marker))
    _unassigned(db, "ch-1", EXP_1)

    _token, out = _link_via_app_with_cap(db, NOW + timedelta(hours=1))
    assert out.body["premium_applied"] == 1
    credit = _credit(db, "c1")
    assert credit["telegram_premium_until"] == later
    assert credit["telegram_subscription"] == marker
    assert credit["ai_credits"] == CAP and credit["is_premium"] is True
    # The payment doc records ITS own period, not the credit doc's.
    assert _payment_doc(db, "ch-1")["telegram_premium_until"] == EXP_1
    [row] = _audit_rows(db)
    assert row["expiration_at_ms"] == int(later.timestamp() * 1000)


def test_link_applies_every_live_payment_and_marks_the_latest_ending_one(db):
    _account(db, "c1", "g1", ai_credits=20, is_premium=False)
    _unassigned(db, "ch-b", EXP_2)  # arrived first, ends last
    _unassigned(db, "ch-a", EXP_1)

    _token, out = _link_via_app_with_cap(db, NOW + timedelta(hours=1))
    assert out.body["premium_applied"] == 2
    credit = _credit(db, "c1")
    assert credit["telegram_premium_until"] == EXP_2
    assert credit["telegram_subscription"]["charge_id"] == "ch-b"
    assert _payment_doc(db, "ch-a")["telegram_premium_until"] == EXP_1
    assert _payment_doc(db, "ch-b")["telegram_premium_until"] == EXP_2
    [row] = _audit_rows(db)
    assert row["telegram_payment_charge_id"] == "ch-b"
    assert sorted(row["applied_charge_ids"]) == ["ch-a", "ch-b"]


def test_link_leaves_another_telegram_users_payment_alone(db):
    _account(db, "c1", "g1", ai_credits=20, is_premium=False)
    _unassigned(db, "ch-bob", EXP_1, tg_id=222)
    before = copy.deepcopy(_payment_doc(db, "ch-bob"))

    _token, out = _link_via_app_with_cap(db, NOW + timedelta(hours=1))  # links TG_A (111)
    assert out.body == {"google_uid": "g1", "credit_user_id": "c1"}
    assert _payment_doc(db, "ch-bob") == before
    assert _credit(db, "c1")["is_premium"] is False
    assert _audit_rows(db) == []


def test_link_without_credits_cap_refuses_instead_of_skipping_a_live_payment(db):
    _account(db, "c1", "g1", ai_credits=20, is_premium=False)
    _unassigned(db, "ch-1", EXP_1)
    token = tl.create_token(db, NOW, google_uid="g1", credit_user_id="c1").body["token"]
    with pytest.raises(ValueError, match="credits_cap"):
        tl.consume_app_token(db, NOW, token, dict(TG_A))
    # Nothing committed: the token is still usable and the payment still waits.
    assert _token_doc(db, token)["used_at"] is None
    assert _link(db, 111) is None
    assert _payment_doc(db, "ch-1")["status"] == "unassigned"


def test_http_consume_app_token_applies_with_the_remote_config_cap(main):
    main.db.store["remote_config/current"] = {"premium_daily_credits_cap": 777}
    _account(main.db, "c1", "g1", ai_credits=20, is_premium=False)
    status, body = _internal_call(main, _http_payment())
    assert status == 200 and body["status"] == "unassigned"
    token = tl.create_token(main.db, NOW, google_uid="g1", credit_user_id="c1").body["token"]

    status, body = _internal_call(main, {"action": "consume_app_token", "token": token,
                                         "telegram_user_id": 111, "chat_id": 111})
    assert status == 200 and body == {"success": True, "google_uid": "g1", "credit_user_id": "c1",
                                      "premium_applied": 1}
    assert main.db.store["users/c1"]["ai_credits"] == 777
    assert _payment_doc(main.db, "ch-1")["status"] == "applied"


def test_http_claim_applies_with_the_remote_config_cap(main):
    main.db.store["remote_config/current"] = {"premium_daily_credits_cap": 555}
    _account(main.db, "c1", "g1", ai_credits=20, is_premium=False)
    _internal_call(main, _http_payment())
    status, body = _internal_call(main, {"action": "create_login_token", "telegram_user_id": 111,
                                         "chat_id": 111, "first_name": "Alice"})
    token = body["token"]

    status, body = _account_call(main, {"action": "claim", "token": token})
    assert status == 200 and body["linked"] is True and body["premium_applied"] == 1
    assert main.db.store["users/c1"]["ai_credits"] == 555
    assert main.db.store["users/c1"]["telegram_premium_until"] == EXP_1
