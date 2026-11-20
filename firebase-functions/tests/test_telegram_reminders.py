"""Telegram reminders — telegram_reminders.py, the link/unlink hooks, main.py shells. OFFLINE.

Same approach as tests/test_telegram_link.py: an in-memory Firestore whose transactions buffer
writes until the function returns and refuse a read after a write. This fake also does range
queries (`<=`), order_by, slash paths for sub-collections and set(merge=True), and counts reads,
so "returns before any Firestore access" is asserted, not assumed.

Run from the repo root:
    python -m pytest firebase-functions/tests/test_telegram_reminders.py -q -p no:cacheprovider
"""

import copy
import json
import logging
import os
import sys
from datetime import datetime, timedelta, timezone
from types import SimpleNamespace
from unittest.mock import MagicMock

import pytest
from flask import Flask
from google.cloud.firestore import DELETE_FIELD

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import reminder_schedule as rs  # noqa: E402
import telegram_link as tl  # noqa: E402
import telegram_reminders as trm  # noqa: E402

app = Flask(__name__)

BOT_TOKEN = "123456:TEST-TOKEN"
NOW = datetime(2026, 11, 20, 8, 0, tzinfo=timezone.utc)  # 09:00 in Berlin (CET)
NOW_MS = rs.to_ms(NOW)
MIN = 60_000
HOUR = 60 * MIN
DAY = 24 * HOUR


# ---------------------------------------------------------------------------
# In-memory Firestore
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


class FakeDocRef:
    def __init__(self, db, path):
        self._db = db
        self.path = path
        self.id = path.rsplit("/", 1)[-1]

    def get(self, transaction=None):
        self._db.note_read(transaction)
        return FakeSnapshot(self, self._db.store.get(self.path))

    def set(self, data, merge=False):
        self._db.apply("set", self.path, data, merge)

    def update(self, data):
        self._db.apply("update", self.path, data)

    def delete(self):
        self._db.apply("delete", self.path, None)


def _matches(flt, data):
    if flt.field_path not in data:
        return False
    value = data[flt.field_path]
    if flt.op_string == "==":
        return value == flt.value
    if flt.op_string == "<=":
        return isinstance(value, datetime) and value <= flt.value
    raise AssertionError(f"unsupported operator {flt.op_string}")


class FakeQuery:
    def __init__(self, db, path, filters=(), order=None, limit=None):
        self._db = db
        self._path = path
        self._filters = filters
        self._order = order
        self._limit = limit

    def where(self, *, filter):  # noqa: A002 — mirrors the SDK keyword
        return FakeQuery(self._db, self._path, self._filters + (filter,), self._order, self._limit)

    def order_by(self, field_path):
        return FakeQuery(self._db, self._path, self._filters, field_path, self._limit)

    def limit(self, n):
        return FakeQuery(self._db, self._path, self._filters, self._order, n)

    def get(self, transaction=None):
        self._db.note_read(transaction)
        prefix = self._path + "/"
        rows = []
        for key in sorted(self._db.store):
            if not key.startswith(prefix) or "/" in key[len(prefix):]:
                continue
            data = self._db.store[key]
            if all(_matches(f, data) for f in self._filters):
                rows.append((key, data))
        if self._order:
            rows.sort(key=lambda row: row[1][self._order])
        out = [FakeSnapshot(FakeDocRef(self._db, key), data) for key, data in rows]
        return out[: self._limit] if self._limit else out


class FakeCollection(FakeQuery):
    def document(self, doc_id=None):
        if doc_id is None:
            self._db.auto_ids += 1
            doc_id = f"auto{self._db.auto_ids:04d}"
        return FakeDocRef(self._db, f"{self._path}/{doc_id}")


class FakeTransaction:
    def __init__(self, db):
        self._db = db
        self._writes = []

    def check_read(self):
        if self._writes:
            raise ReadAfterWrite("Attempted read after write in a transaction.")

    def set(self, ref, data, merge=False):
        self._writes.append(("set", ref.path, data, merge))

    def create(self, ref, data):
        self._writes.append(("create", ref.path, data, False))

    def update(self, ref, data):
        self._writes.append(("update", ref.path, data, False))

    def delete(self, ref):
        self._writes.append(("delete", ref.path, None, False))

    def commit(self):
        for op in self._writes:
            self._db.apply(*op)


class FakeDb:
    def __init__(self):
        self.store = {}
        self.auto_ids = 0
        self.reads = 0
        self.transactional_reads = 0
        self.writes = []  # (op, path) in commit order

    def collection(self, path):
        return FakeCollection(self, path)

    def transaction(self):
        return FakeTransaction(self)

    def note_read(self, transaction):
        self.reads += 1
        if transaction is not None:
            transaction.check_read()
            self.transactional_reads += 1

    def apply(self, op, path, data, merge=False):
        self.writes.append((op, path))
        if op == "create":
            if path in self.store:
                raise LookupError(f"create() on an existing document: {path}")
            op = "set"
        if op == "set":
            assert DELETE_FIELD not in data.values(), "DELETE_FIELD is only valid in update()"
            if merge and path in self.store:
                self.store[path].update(copy.deepcopy(data))
            else:
                self.store[path] = copy.deepcopy(data)
        elif op == "update":
            if path not in self.store:
                raise LookupError(f"update() on a missing document: {path}")
            for key, value in data.items():
                if value is DELETE_FIELD:
                    self.store[path].pop(key, None)
                else:
                    self.store[path][key] = copy.deepcopy(value)
        elif op == "delete":
            self.store.pop(path, None)


class NoDb:
    """Any Firestore access fails the test."""

    def collection(self, *_args, **_kwargs):
        raise AssertionError("no Firestore access expected")

    def transaction(self):
        raise AssertionError("no Firestore access expected")


def fake_transactional(fn):
    def run(transaction, *args, **kwargs):
        result = fn(transaction, *args, **kwargs)
        transaction.commit()
        return result
    return run


@pytest.fixture(autouse=True)
def _fake_transactions(monkeypatch):
    monkeypatch.setattr(tl, "transactional", fake_transactional)
    monkeypatch.setattr(trm, "transactional", fake_transactional)


@pytest.fixture
def db():
    return FakeDb()


# ---------------------------------------------------------------------------
# Fixtures in the store
# ---------------------------------------------------------------------------

def put_link(db, tg=111, google_uid="g1", credit="c1", chat=None, lang="ru", at=NOW):
    db.store[f"telegram_links/{tg}"] = {
        "telegram_user_id": tg, "chat_id": chat or tg, "telegram_username": "alice",
        "telegram_first_name": "Alice", "language_code": lang, "google_uid": google_uid,
        "credit_user_id": credit, "linked_at": at,
    }


def put_profile(db, google_uid="g1", channel="telegram", tz="Europe/Berlin", **extra):
    data = {**extra}
    if channel is not None:
        data["reminder_channel"] = channel
    if tz is not None:
        data["timezone"] = tz
    db.store[f"users/{google_uid}"] = data


def put_account(db, credit="c1", google_uid="g1", **extra):
    db.store[f"users/{credit}"] = {"google_uid": google_uid, "ai_credits": 100, **extra}


def checklist(items=(), **fields):
    return {"name": "Groceries", "itemsJson": "[]", "updatedAt": 1, "isDeleted": False,
            "fills": [{"cloudId": "f1", "isDefault": True, "isDeleted": False,
                       "itemsJson": json.dumps(list(items))}], **fields}


def put_checklist(db, cloud="cl1", owner="g1", **fields):
    doc = checklist(**fields)
    db.store[f"users/{owner}/checklists/{cloud}"] = doc
    return doc


def entries(db):
    return {k.split("/", 1)[1]: v for k, v in db.store.items() if k.startswith("telegram_reminders/")}


def rid(cloud="cl1", item_id=None, slot=rs.SLOT_MAIN, google_uid="g1"):
    return rs.make_rid(google_uid, cloud, item_id, slot)


def linked_setup(db, **profile):
    put_account(db)
    put_link(db)
    put_profile(db, **profile)


def written(db, cloud="cl1", owner="g1", now=NOW):
    doc = db.store.get(f"users/{owner}/checklists/{cloud}")
    return trm.on_checklist_written(db, now, owner, cloud, None, doc)


# ===========================================================================
# Trigger: users/{uid}/checklists/{checklistId}
# ===========================================================================

def test_checklist_write_without_reminder_data_reads_nothing():
    before = checklist([{"id": "i1", "text": "a"}])
    after = checklist([{"id": "i1", "text": "a", "checked": True}])
    assert trm.on_checklist_written(NoDb(), NOW, "g1", "cl1", before, after) == "no_reminders"
    assert trm.on_checklist_written(NoDb(), NOW, "g1", "cl1", None, after) == "no_reminders"


def test_checklist_write_of_an_unlinked_account_writes_nothing(db):
    put_profile(db)
    put_checklist(db, reminderAt=NOW_MS + HOUR)
    assert written(db) == "not_linked"
    assert entries(db) == {}


def test_linked_telegram_channel_builds_the_entries(db):
    linked_setup(db)
    put_checklist(db, reminderAt=NOW_MS + HOUR,
                  items=[{"id": "i1", "text": "Milk", "reminderAt": NOW_MS + 2 * HOUR}])
    assert written(db) == "recomputed"
    got = entries(db)
    assert set(got) == {rid(), rid(item_id="i1")}
    assert rs.to_ms(got[rid()]["next_fire_at"]) == NOW_MS + HOUR
    assert got[rid(item_id="i1")]["item_text"] == "Milk"
    assert got[rid()]["chat_id"] == 111 and got[rid()]["timezone"] == "Europe/Berlin"


def test_channel_push_or_an_unknown_zone_deletes_that_checklists_entries(db):
    linked_setup(db)
    put_checklist(db, "cl1", reminderAt=NOW_MS + HOUR)
    put_checklist(db, "cl2", reminderAt=NOW_MS + HOUR)
    written(db, "cl1")
    written(db, "cl2")
    assert len(entries(db)) == 2

    put_profile(db, channel="push")
    assert written(db, "cl1") == "channel_off"
    assert set(entries(db)) == {rid("cl2")}  # only the written checklist is touched

    put_profile(db, channel="both", tz=None)
    assert written(db, "cl2") == "channel_off"
    assert entries(db) == {}


def test_recompute_reads_the_current_doc_not_the_event_payload(db):
    linked_setup(db)
    put_checklist(db, reminderAt=NOW_MS + 3 * HOUR)  # newer than the event below
    stale_event = checklist(reminderAt=NOW_MS + HOUR)
    assert trm.on_checklist_written(db, NOW, "g1", "cl1", None, stale_event) == "recomputed"
    assert rs.to_ms(entries(db)[rid()]["next_fire_at"]) == NOW_MS + 3 * HOUR
    assert db.transactional_reads >= 2  # the doc and its entries, inside the transaction


def test_deleted_checklist_drops_every_entry_including_snoozes(db):
    linked_setup(db)
    put_checklist(db, reminderAt=NOW_MS + HOUR)
    written(db)
    assert trm.snooze_reminder(db, NOW, rid(), 60).status == 200
    assert len(entries(db)) == 2

    soft = put_checklist(db, isDeleted=True, reminderAt=NOW_MS + HOUR)
    assert trm.on_checklist_written(db, NOW, "g1", "cl1", checklist(), soft) == "recomputed"
    assert entries(db) == {}

    put_checklist(db, reminderAt=NOW_MS + HOUR)
    written(db)
    del db.store["users/g1/checklists/cl1"]  # hard delete: no reminder data in either version
    assert trm.on_checklist_written(db, NOW, "g1", "cl1", checklist(), None) == "recomputed"
    assert entries(db) == {}


def test_checklist_under_the_device_doc_resolves_through_credit_user_id(db):
    linked_setup(db)
    put_checklist(db, "cl9", owner="c1", reminderAt=NOW_MS + HOUR)
    assert written(db, "cl9", owner="c1") == "recomputed"
    entry = entries(db)[rid("cl9")]
    assert entry["google_uid"] == "g1" and entry["checklist_owner_uid"] == "c1"


# ===========================================================================
# Trigger: users/{uid}
# ===========================================================================

def test_user_doc_write_without_a_settings_change_returns_before_any_read():
    credit = {"google_uid": "g1", "ai_credits": 5}
    assert trm.on_user_written(NoDb(), NOW, "c1", credit, {**credit, "ai_credits": 4}) == "unchanged"
    same = {"timezone": "Europe/Berlin", "reminder_channel": "telegram"}
    assert trm.on_user_written(NoDb(), NOW, "g1", same, {**same, "x": 1}) == "unchanged"
    # absent channel == push
    assert trm.on_user_written(NoDb(), NOW, "g1", {}, {"reminder_channel": "push"}) == "unchanged"
    assert trm.on_user_written(NoDb(), NOW, "g1", None, {"ai_credits": 1}) == "unchanged"


def test_switching_to_telegram_recomputes_every_checklist_of_both_docs(db):
    linked_setup(db, channel="push")
    put_checklist(db, "cl1", reminderAt=NOW_MS + HOUR)
    put_checklist(db, "cl2")  # no reminder
    put_checklist(db, "cl3", owner="c1", reminderAt=NOW_MS + 2 * HOUR)
    put_checklist(db, "cl1", owner="c1", reminderAt=NOW_MS + 5 * HOUR)  # duplicate id: g1 wins
    before = dict(db.store["users/g1"])
    put_profile(db, channel="telegram")
    assert trm.on_user_written(db, NOW, "g1", before, db.store["users/g1"]) == "recomputed"
    got = entries(db)
    assert set(got) == {rid("cl1"), rid("cl3")}
    assert rs.to_ms(got[rid("cl1")]["next_fire_at"]) == NOW_MS + HOUR
    assert got[rid("cl3")]["checklist_owner_uid"] == "c1"


def test_back_to_push_deletes_all_entries(db):
    linked_setup(db)
    put_checklist(db, "cl1", reminderAt=NOW_MS + HOUR)
    put_checklist(db, "cl2", reminderAt=NOW_MS + HOUR)
    trm.recompute_user(db, NOW, "g1")
    assert len(entries(db)) == 2
    before = dict(db.store["users/g1"])
    put_profile(db, channel="push")
    assert trm.on_user_written(db, NOW, "g1", before, db.store["users/g1"]) == "channel_off"
    assert entries(db) == {}


def test_timezone_change_recomputes_in_the_new_zone(db):
    linked_setup(db)
    rule = {"type": "daily"}
    put_checklist(db, items=[{"id": "i1", "text": "Pills", "repeatRule": rule,
                              "repeatNextAt": NOW_MS + DAY, "repeatTimeOfDayMinutes": 540}])
    trm.recompute_user(db, NOW, "g1")
    assert rs.to_ms(entries(db)[rid(item_id="i1")]["next_fire_at"]) == NOW_MS + DAY
    before = dict(db.store["users/g1"])
    put_profile(db, tz="Asia/Kolkata")
    trm.on_user_written(db, NOW, "g1", before, db.store["users/g1"])
    entry = entries(db)[rid(item_id="i1")]
    assert entry["timezone"] == "Asia/Kolkata"
    # daily at 09:00 local: NOW is 13:30 IST on 11-20, so the next 09:00 IST is on 11-21 (03:30 UTC)
    assert rs.to_ms(entry["next_fire_at"]) == rs.to_ms(
        datetime(2026, 11, 21, 3, 30, tzinfo=timezone.utc))


def test_recompute_user_deletes_entries_of_vanished_checklists(db):
    linked_setup(db)
    put_checklist(db, "cl1", reminderAt=NOW_MS + HOUR)
    put_checklist(db, "cl2", reminderAt=NOW_MS + HOUR)
    trm.recompute_user(db, NOW, "g1")
    del db.store["users/g1/checklists/cl2"]
    assert trm.recompute_user(db, NOW, "g1")["gone"] == 1
    assert set(entries(db)) == {rid("cl1")}


# ===========================================================================
# claim_due_reminders
# ===========================================================================

def _due_one_shot(db, at=NOW_MS - MIN):
    linked_setup(db)
    put_checklist(db, items=[{"id": "i1", "text": "Milk", "reminderAt": at}])
    written(db, now=NOW - timedelta(minutes=2))


def test_claim_returns_a_due_reminder_once_and_a_recompute_never_resends_it(db):
    _due_one_shot(db)
    out = trm.claim_due_reminders(db, NOW, 50)
    assert out.status == 200
    [item] = out.body["reminders"]
    assert item == {
        "rid": rid(item_id="i1"), "chat_id": 111, "telegram_user_id": 111,
        "language_code": "ru", "kind": "oneshot", "checklist_cloud_id": "cl1",
        "checklist_owner_uid": "g1", "item_id": "i1", "checklist_name": "Groceries",
        "item_text": "Milk", "fire_at_ms": NOW_MS - MIN, "google_uid": "g1",
        "timezone": "Europe/Berlin",
    }
    assert trm.claim_due_reminders(db, NOW + timedelta(seconds=30), 50).body == {"reminders": []}
    tomb = entries(db)[rid(item_id="i1")]
    assert tomb["next_fire_at"] is None and rs.to_ms(tomb["last_fired_at"]) == NOW_MS - MIN
    # The web app never clears reminderAt; a checklist write a minute later must not re-arm it.
    assert written(db, now=NOW + timedelta(minutes=1)) == "recomputed"
    assert trm.claim_due_reminders(db, NOW + timedelta(minutes=1, seconds=30), 50).body == {
        "reminders": []}


def test_claim_rereads_in_a_transaction_and_skips_what_another_call_took(db):
    _due_one_shot(db)
    stale_query = list(trm._entries(db).where(
        filter=trm.FieldFilter("next_fire_at", "<=", NOW)).get())
    reads_before = db.transactional_reads
    assert trm.claim_due_reminders(db, NOW, 50).body["reminders"]
    assert db.transactional_reads > reads_before
    # A second caller holding the same stale query result gets nothing.
    assert trm._claim_one(db, NOW, stale_query[0].reference) == ("skip", None)


def test_claim_more_than_six_hours_late_advances_without_returning(db):
    linked_setup(db)
    anchor = NOW_MS - 7 * HOUR  # 02:00 Berlin today
    put_checklist(db, items=[{"id": "i1", "text": "Pills", "repeatRule": {"type": "daily"},
                              "repeatNextAt": anchor, "repeatTimeOfDayMinutes": 120}])
    written(db, now=NOW - timedelta(hours=8))
    assert rs.to_ms(entries(db)[rid(item_id="i1")]["next_fire_at"]) == anchor
    assert trm.claim_due_reminders(db, NOW, 50).body == {"reminders": []}
    entry = entries(db)[rid(item_id="i1")]
    assert rs.to_ms(entry["next_fire_at"]) == anchor + DAY
    assert entry["repeat"]["count"] == 1 and rs.to_ms(entry["last_fired_at"]) == anchor


def test_repeat_end_condition_ends_the_entry(db):
    linked_setup(db)
    rule = {"type": "daily", "endCondition": {"type": "after_count", "maxCount": 1}}
    put_checklist(db, repeatRule=json.dumps(rule), repeatNextAt=NOW_MS,
                  repeatTimeOfDayMinutes=540)
    written(db, now=NOW - timedelta(minutes=1))
    [item] = trm.claim_due_reminders(db, NOW, 50).body["reminders"]
    assert item["kind"] == "repeat" and item["item_id"] is None
    entry = entries(db)[rid()]
    assert entry["next_fire_at"] is None and entry["repeat"]["ended"] is True
    assert trm.claim_due_reminders(db, NOW + timedelta(days=2), 50).body == {"reminders": []}


def test_claim_drops_entries_of_an_unlinked_account_or_a_push_channel(db):
    _due_one_shot(db)
    del db.store["telegram_links/111"]  # the unlink hook did not run
    assert trm.claim_due_reminders(db, NOW, 50).body == {"reminders": []}
    assert entries(db) == {}

    db.store.clear()
    _due_one_shot(db)
    put_profile(db, channel="push")  # the users trigger did not run
    assert trm.claim_due_reminders(db, NOW, 50).body == {"reminders": []}
    assert entries(db) == {}


def test_claim_delivers_to_the_links_current_chat(db):
    _due_one_shot(db)
    put_link(db, chat=999, lang="hi")
    [item] = trm.claim_due_reminders(db, NOW, 50).body["reminders"]
    assert item["chat_id"] == 999 and item["language_code"] == "hi"


def test_claim_takes_the_oldest_first_up_to_the_limit(db):
    linked_setup(db)
    items = [{"id": f"i{n}", "text": f"t{n}", "reminderAt": NOW_MS - n * MIN} for n in (1, 3, 2)]
    put_checklist(db, items=items)
    written(db, now=NOW - timedelta(minutes=4))
    got = trm.claim_due_reminders(db, NOW, 2).body["reminders"]
    assert [i["item_id"] for i in got] == ["i3", "i2"]
    assert [i["item_id"] for i in trm.claim_due_reminders(db, NOW, 2).body["reminders"]] == ["i1"]


# ===========================================================================
# snooze_reminder
# ===========================================================================

def test_snooze_works_from_a_delivered_one_shot_and_from_a_delivered_snooze(db):
    _due_one_shot(db)
    trm.claim_due_reminders(db, NOW, 50)  # the main entry is now a tombstone

    out = trm.snooze_reminder(db, NOW, rid(item_id="i1"), 60, telegram_user_id=111)
    snooze_rid = rid(item_id="i1", slot=rs.SLOT_SNOOZE)
    assert out.status == 200 and out.body == {
        "rid": snooze_rid, "fire_at_ms": NOW_MS + HOUR, "timezone": "Europe/Berlin"}
    snooze = entries(db)[snooze_rid]
    assert snooze["kind"] == "snooze" and snooze["item_text"] == "Milk"
    assert snooze["chat_id"] == 111 and snooze["checklist_cloud_id"] == "cl1"

    assert trm.claim_due_reminders(db, NOW + timedelta(minutes=59), 50).body == {"reminders": []}
    [item] = trm.claim_due_reminders(db, NOW + timedelta(minutes=61), 50).body["reminders"]
    assert item["kind"] == "snooze" and item["rid"] == snooze_rid

    again = trm.snooze_reminder(db, NOW + timedelta(minutes=62), snooze_rid, 60)
    assert again.status == 200 and again.body["rid"] == snooze_rid


def test_snooze_of_an_unknown_or_foreign_reminder_is_404(db):
    _due_one_shot(db)
    assert trm.snooze_reminder(db, NOW, "0" * 24, 60).body == {"error": "unknown_reminder"}
    foreign = trm.snooze_reminder(db, NOW, rid(item_id="i1"), 60, telegram_user_id=222)
    assert foreign.status == 404
    assert rid(item_id="i1", slot=rs.SLOT_SNOOZE) not in entries(db)


def test_a_pending_snooze_survives_checklist_recomputes(db):
    linked_setup(db)
    put_checklist(db, reminderAt=NOW_MS + HOUR)
    written(db)
    trm.snooze_reminder(db, NOW, rid(), 30)
    put_checklist(db)  # reminder removed
    written(db, now=NOW + timedelta(minutes=1))
    assert rid(slot=rs.SLOT_SNOOZE) in entries(db)


# ===========================================================================
# Settings
# ===========================================================================

def test_set_reminder_channel_is_one_merge_write_with_the_device_zone(db):
    db.store["users/g1"] = {"other": 1}
    out = trm.set_reminder_channel(db, "g1", "both", "Asia/Kolkata")
    assert out.status == 200 and out.body == {"reminder_channel": "both", "timezone": "Asia/Kolkata"}
    assert db.store["users/g1"] == {"other": 1, "reminder_channel": "both",
                                    "timezone": "Asia/Kolkata", "timezone_source": "device"}
    assert db.writes == [("set", "users/g1")]

    assert trm.set_reminder_channel(db, "g2", "telegram").body == {"reminder_channel": "telegram"}
    assert db.store["users/g2"] == {"reminder_channel": "telegram"}  # created by merge


def test_set_reminder_channel_rejects_bad_values_and_writes_nothing(db):
    assert trm.set_reminder_channel(db, "g1", "sms").body == {"error": "invalid_channel"}
    for tz in ("Mars/Base", "", "../etc/passwd", 5):
        out = trm.set_reminder_channel(db, "g1", "telegram", tz)
        assert out.status == 400 and out.body == {"error": "invalid_timezone"}
    assert db.store == {} and db.writes == []


def test_reminder_settings_defaults():
    db = FakeDb()
    assert trm.reminder_settings(db, "g1") == {"reminder_channel": "push", "timezone": None}
    put_profile(db, channel="weird", tz="Nowhere/Land")
    assert trm.reminder_settings(db, "g1") == {"reminder_channel": "push", "timezone": None}


# ===========================================================================
# Unlink resets the channel (every path) — telegram_link transactions
# ===========================================================================

def _link_via_app(db, google_uid, credit, tg_fields):
    token = tl.create_token(db, NOW, google_uid=google_uid, credit_user_id=credit).body["token"]
    return tl.consume_app_token(db, NOW, token, dict(tg_fields))


TG_A = {"telegram_user_id": 111, "chat_id": 111, "telegram_username": "alice",
        "telegram_first_name": "Alice", "language_code": "ru"}
TG_B = {"telegram_user_id": 222, "chat_id": 222, "telegram_username": "bob",
        "telegram_first_name": "Bob", "language_code": "en"}


def test_app_unlink_resets_the_channel_to_push(db):
    put_account(db)
    put_profile(db, channel="telegram", other=1)
    assert _link_via_app(db, "g1", "c1", TG_A).status == 200
    out = tl.unlink(db, "g1")
    assert out.unlinked == ("g1",)
    assert db.store["users/g1"] == {"reminder_channel": "push", "timezone": "Europe/Berlin",
                                    "other": 1}


def test_bot_unlink_resets_the_channel_to_push(db):
    put_account(db)
    put_profile(db, channel="both")
    _link_via_app(db, "g1", "c1", TG_A)
    out = tl.unlink_by_telegram(db, 111)
    assert out.body == {"ok": True} and out.unlinked == ("g1",)
    assert db.store["users/g1"]["reminder_channel"] == "push"
    assert tl.unlink_by_telegram(db, 111).unlinked == ()


def test_unlink_never_creates_a_profile_doc(db):
    put_account(db)
    _link_via_app(db, "g1", "c1", TG_A)
    tl.unlink(db, "g1")
    assert "users/g1" not in db.store
    put_profile(db, channel="push")
    before = len(db.writes)
    tl.unlink(db, "g1")
    assert db.writes[before:] == []  # already push: no write at all


def test_moving_the_telegram_to_another_account_resets_the_old_one(db):
    put_account(db, "cA", "gA")
    put_account(db, "cB", "gB")
    put_profile(db, "gA", channel="telegram")
    put_profile(db, "gB", channel="both")
    assert _link_via_app(db, "gA", "cA", TG_A).status == 200
    out = _link_via_app(db, "gB", "cB", TG_A)
    assert out.status == 200 and out.unlinked == ("gA",)
    assert out.linked == {"google_uid": "gB", "credit_user_id": "cB", "telegram_user_id": 111,
                          "chat_id": 111, "language_code": "ru"}
    assert db.store["users/gA"]["reminder_channel"] == "push"
    assert db.store["users/gB"]["reminder_channel"] == "both"  # untouched


def test_switching_telegram_accounts_keeps_the_channel(db):
    put_account(db)
    put_profile(db, channel="telegram")
    _link_via_app(db, "g1", "c1", TG_A)
    out = _link_via_app(db, "g1", "c1", TG_B)
    assert out.unlinked == () and db.store["users/g1"]["reminder_channel"] == "telegram"


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
    monkeypatch.setattr(main_mod.firebase_auth, "verify_id_token", lambda tok: {"uid": tok})
    sent = []

    def post(url, json, timeout):
        sent.append((url, json))
        return MagicMock(status_code=200)

    monkeypatch.setattr(main_mod.http_requests, "post", post)
    main_mod.sent = sent
    return main_mod


def _req(payload, headers=None):
    req = MagicMock()
    req.method = "POST"
    req.get_json.return_value = payload
    req.headers = headers or {}
    return req


def _account_call(main, payload, uid="g1"):
    with app.test_request_context():
        resp = main.telegram_account(_req(payload, {"Authorization": f"Bearer {uid}"}))
    return resp.status_code, resp.get_json()


def _internal_call(main, payload, key=tl.derive_key(BOT_TOKEN, tl.INTERNAL_KEY_LABEL)):
    with app.test_request_context():
        resp = main.telegram_bot_internal(_req(payload, {"X-Gisti-Internal-Key": key}))
    return resp.status_code, resp.get_json()


def _consume(main, tg=111, credit="c1", google_uid="g1"):
    token = tl.create_token(main.db, NOW, google_uid=google_uid, credit_user_id=credit).body["token"]
    return _internal_call(main, {"action": "consume_app_token", "token": token,
                                 "telegram_user_id": tg, "chat_id": tg, "language_code": "ru"})


def test_http_status_reports_channel_and_zone_only_when_linked(main):
    put_account(main.db)
    put_profile(main.db, channel="both", tz="Asia/Kolkata")
    assert _account_call(main, {"action": "status"})[1] == {"success": True, "linked": False}
    _consume(main)
    status, body = _account_call(main, {"action": "status"})
    assert status == 200 and body["linked"] is True
    assert body["reminder_channel"] == "both" and body["timezone"] == "Asia/Kolkata"


def test_http_set_reminder_channel(main):
    status, body = _account_call(main, {"action": "set_reminder_channel", "channel": "telegram",
                                        "timezone": "Europe/Berlin"})
    assert status == 200 and body == {"success": True, "reminder_channel": "telegram",
                                      "timezone": "Europe/Berlin"}
    assert main.db.store["users/g1"]["timezone_source"] == "device"

    for bad, code in (({"channel": "sms"}, "invalid_channel"),
                      ({"channel": "push", "timezone": "Mars/Base"}, "invalid_timezone"),
                      ({"channel": "push", "timezone": 3}, "invalid_timezone")):
        status, body = _account_call(main, {"action": "set_reminder_channel", **bad})
        assert status == 400 and body == {"success": False, "error": code}
    assert main.db.store["users/g1"]["reminder_channel"] == "telegram"  # nothing written


def test_http_consume_app_token_leaves_the_offer_to_the_worker(main):
    put_account(main.db)
    status, body = _consume(main)
    # app → bot: the response shape is unchanged and Python sends nothing — the worker reads the
    # profile's reminder_channel itself and offers after its «linked» message.
    assert status == 200 and "offer_reminders" not in body
    assert main.sent == []


def test_http_bot_to_app_claim_sends_the_offer_only_while_push(main):
    put_account(main.db)
    _status, body = _internal_call(main, {"action": "create_login_token", "telegram_user_id": 111,
                                          "chat_id": 111, "language_code": "ru"})
    status, body = _account_call(main, {"action": "claim", "token": body["token"]})
    assert status == 200 and body == {"success": True, "linked": True, "telegram_username": None,
                                      "telegram_first_name": None}
    [(url, payload)] = main.sent
    assert url == f"https://api.telegram.org/bot{BOT_TOKEN}/sendMessage"
    assert payload["chat_id"] == 111 and payload["text"] == trm.OFFER_TEXTS["ru"][0]
    rows = payload["reply_markup"]["inline_keyboard"]
    assert [row[0]["callback_data"] for row in rows] == ["rc:t", "rc:b", "rc:p"]
    assert [row[0]["text"] for row in rows] == list(trm.OFFER_TEXTS["ru"][1])

    main.sent.clear()
    put_profile(main.db, channel="telegram")
    _status, body = _internal_call(main, {"action": "create_login_token", "telegram_user_id": 222,
                                          "chat_id": 222})
    assert _account_call(main, {"action": "claim", "token": body["token"]})[0] == 200
    assert main.sent == []


def test_http_offer_failure_does_not_change_the_answer_nor_leak_the_token(main, monkeypatch, caplog):
    put_account(main.db)

    def broken(url, json, timeout):
        raise ConnectionError(f"Max retries exceeded with url: {url}")

    monkeypatch.setattr(main.http_requests, "post", broken)
    _status, body = _internal_call(main, {"action": "create_login_token", "telegram_user_id": 111,
                                          "chat_id": 111})
    with caplog.at_level(logging.WARNING, logger="gisti"):
        status, body = _account_call(main, {"action": "claim", "token": body["token"]})
    assert status == 200 and body["linked"] is True
    assert any("channel offer not delivered" in r.getMessage() for r in caplog.records)
    assert all(BOT_TOKEN not in r.getMessage() and r.exc_info is None for r in caplog.records)


def test_http_link_builds_entries_and_both_unlinks_reset_and_clean_up(main):
    put_account(main.db)
    put_profile(main.db, channel="telegram")
    put_checklist(main.db, reminderAt=NOW_MS + HOUR)
    assert _consume(main)[0] == 200
    assert set(entries(main.db)) == {rid()}

    status, body = _account_call(main, {"action": "unlink"})
    assert status == 200 and body == {"success": True, "linked": False}
    assert entries(main.db) == {} and main.db.store["users/g1"]["reminder_channel"] == "push"

    put_profile(main.db, channel="both")
    _consume(main)
    assert set(entries(main.db)) == {rid()}
    status, body = _internal_call(main, {"action": "unlink", "telegram_user_id": 111})
    assert status == 200 and body == {"success": True, "ok": True}
    assert entries(main.db) == {} and main.db.store["users/g1"]["reminder_channel"] == "push"


def test_http_relink_to_another_account_cleans_the_old_accounts_entries(main):
    put_account(main.db, "cA", "gA")
    put_account(main.db, "cB", "gB")
    put_profile(main.db, "gA", channel="telegram")
    put_checklist(main.db, owner="gA", reminderAt=NOW_MS + HOUR)
    _consume(main, credit="cA", google_uid="gA")
    assert set(entries(main.db)) == {rid(google_uid="gA")}
    _consume(main, credit="cB", google_uid="gB")
    assert entries(main.db) == {}
    assert main.db.store["users/gA"]["reminder_channel"] == "push"


def test_http_claim_and_snooze_round_trip(main):
    linked_setup(main.db)
    put_checklist(main.db, items=[{"id": "i1", "text": "Milk", "reminderAt": NOW_MS - MIN}])
    written(main.db, now=NOW - timedelta(minutes=2))
    status, body = _internal_call(main, {"action": "claim_due_reminders"})
    assert status == 200 and [r["item_id"] for r in body["reminders"]] == ["i1"]
    status, body = _internal_call(main, {"action": "snooze_reminder", "rid": rid(item_id="i1"),
                                         "minutes": 60, "telegram_user_id": 111})
    assert status == 200 and body["fire_at_ms"] == NOW_MS + HOUR
    status, body = _internal_call(main, {"action": "snooze_reminder", "rid": "f" * 24,
                                         "minutes": 60})
    assert status == 404 and body["error"] == "unknown_reminder"


@pytest.mark.parametrize("payload", [
    {"action": "claim_due_reminders", "limit": 0},
    {"action": "claim_due_reminders", "limit": 201},
    {"action": "claim_due_reminders", "limit": "5"},
    {"action": "claim_due_reminders", "limit": True},
    {"action": "snooze_reminder", "rid": "short", "minutes": 60},
    {"action": "snooze_reminder", "rid": "A" * 24, "minutes": 60},
    {"action": "snooze_reminder", "rid": "a" * 24, "minutes": 0},
    {"action": "snooze_reminder", "rid": "a" * 24, "minutes": 10081},
    {"action": "snooze_reminder", "rid": "a" * 24, "minutes": 60, "telegram_user_id": "1"},
])
def test_http_reminder_actions_validate_input(main, payload):
    assert _internal_call(main, payload)[0] == 400


def test_http_reminder_actions_require_the_internal_key(main):
    for payload in ({"action": "claim_due_reminders"},
                    {"action": "snooze_reminder", "rid": "a" * 24, "minutes": 60}):
        assert _internal_call(main, payload, key="wrong")[0] == 401


# ===========================================================================
# Trigger wrappers in main.py
# ===========================================================================

def _event(before, after, **params):
    def snap(data):
        return None if data is None else SimpleNamespace(exists=True, to_dict=lambda: dict(data))
    return SimpleNamespace(data=SimpleNamespace(before=snap(before), after=snap(after)),
                           params=params)


def test_trigger_wrappers_decode_the_change(main):
    linked_setup(main.db)
    doc = put_checklist(main.db, reminderAt=NOW_MS + HOUR)
    main.on_checklist_written_tg.__wrapped__(_event(None, doc, uid="g1", checklistId="cl1"))
    assert set(entries(main.db)) == {rid()}

    before = dict(main.db.store["users/g1"])
    put_profile(main.db, channel="push")
    main.on_user_settings_written_tg.__wrapped__(
        _event(before, main.db.store["users/g1"], uid="g1"))
    assert entries(main.db) == {}


def test_trigger_wrappers_log_and_never_raise(main, monkeypatch, caplog):
    def boom(*_a, **_k):
        raise RuntimeError("firestore down")

    monkeypatch.setattr(main.telegram_reminders, "on_checklist_written", boom)
    monkeypatch.setattr(main.telegram_reminders, "on_user_written", boom)
    with caplog.at_level(logging.ERROR, logger="gisti"):
        main.on_checklist_written_tg.__wrapped__(_event(None, {}, uid="g1", checklistId="c"))
        main.on_user_settings_written_tg.__wrapped__(_event(None, {}, uid="g1"))
    assert sum(1 for r in caplog.records if r.exc_info) == 2


# ===========================================================================
# Channel change reaches the phone (data-only FCM from the users-doc trigger)
# ===========================================================================

class UnregisteredError(Exception):
    """Named like firebase_admin.messaging.UnregisteredError (classified by name)."""


class FakeMessaging:
    """Stand-in for firebase_admin.messaging: Message / AndroidConfig build dicts, send records."""

    def __init__(self, dead=()):
        self.sent = []
        self.dead = set(dead)

    @staticmethod
    def Message(**fields):  # noqa: N802 — mirrors the SDK class
        return fields

    @staticmethod
    def AndroidConfig(**fields):  # noqa: N802 — mirrors the SDK class
        return fields

    def send(self, message):
        if message["token"] in self.dead:
            raise UnregisteredError(f"Requested entity was not found: {message['token']}")
        self.sent.append(message)
        return "projects/x/messages/1"


def _channel_push(token, channel):
    return {"token": token,
            "data": {"type": "reminder_channel", "channel": channel},
            "android": {"priority": "high", "collapse_key": "reminder_channel"}}


def _devices(db):
    put_account(db, "c1", "g1", fcmToken="tok-phone")
    put_account(db, "c2", "g1", fcmToken="tok-tablet")
    put_account(db, "c3", "g1")                       # a device doc without a token
    put_account(db, "cX", "gX", fcmToken="tok-other")  # someone else


def test_channel_change_sends_one_data_message_per_device(db):
    _devices(db)
    fcm = FakeMessaging()
    trm.on_user_written(db, NOW, "g1", {"reminder_channel": "telegram"},
                        {"reminder_channel": "push"}, messaging=fcm)
    assert fcm.sent == [_channel_push("tok-phone", "push"), _channel_push("tok-tablet", "push")]
    assert all("notification" not in m for m in fcm.sent)

    fcm = FakeMessaging()
    trm.on_user_written(db, NOW, "g1", {}, {"reminder_channel": "both"}, messaging=fcm)
    assert [m["data"]["channel"] for m in fcm.sent] == ["both", "both"]


def test_no_channel_message_without_a_channel_change_or_without_devices(db):
    _devices(db)
    fcm = FakeMessaging()
    same = {"reminder_channel": "telegram", "timezone": "Europe/Berlin"}
    assert trm.on_user_written(NoDb(), NOW, "g1", same, dict(same), messaging=fcm) == "unchanged"
    trm.on_user_written(db, NOW, "g1", same, {**same, "timezone": "Asia/Kolkata"}, messaging=fcm)
    trm.on_user_written(db, NOW, "g1", {}, {"reminder_channel": "push"}, messaging=fcm)  # absent=push
    assert fcm.sent == []

    trm.on_user_written(db, NOW, "gNone", {"reminder_channel": "telegram"},
                        {"reminder_channel": "push"}, messaging=fcm)
    assert fcm.sent == []


def test_a_dead_token_is_logged_without_the_token_and_the_rest_still_get_it(db, caplog):
    _devices(db)
    fcm = FakeMessaging(dead={"tok-phone"})
    with caplog.at_level(logging.WARNING, logger="gisti"):
        result = trm.notify_channel_change(db, fcm, "g1", "push")
    assert result == {"sent": 1, "failed": 1}
    assert fcm.sent == [_channel_push("tok-tablet", "push")]
    [record] = [r for r in caplog.records if "channel push not delivered" in r.getMessage()]
    assert "UnregisteredError" in record.getMessage() and "unrecoverable" in record.getMessage()
    assert all("tok-" not in r.getMessage() for r in caplog.records)


def test_unlink_reset_reaches_the_phone_through_the_trigger_wrapper(main, monkeypatch):
    _devices(main.db)
    put_profile(main.db, channel="telegram")
    _link_via_app(main.db, "g1", "c1", TG_A)
    before = dict(main.db.store["users/g1"])
    tl.unlink(main.db, "g1")  # resets the channel to push in its transaction
    fcm = FakeMessaging()
    monkeypatch.setattr(main, "messaging", fcm)
    main.on_user_settings_written_tg.__wrapped__(
        _event(before, main.db.store["users/g1"], uid="g1"))
    assert fcm.sent == [_channel_push("tok-phone", "push"), _channel_push("tok-tablet", "push")]


# ===========================================================================
# Snooze while the channel is off
# ===========================================================================

def test_snooze_is_refused_when_the_channel_is_off_or_the_account_unlinked(db):
    _due_one_shot(db)
    put_profile(db, channel="push")
    out = trm.snooze_reminder(db, NOW, rid(item_id="i1"), 60)
    assert out.status == 409 and out.body == {"error": "channel_off"}
    assert rid(item_id="i1", slot=rs.SLOT_SNOOZE) not in entries(db)

    put_profile(db, channel="telegram")
    del db.store["telegram_links/111"]
    assert trm.snooze_reminder(db, NOW, rid(item_id="i1"), 60).body == {"error": "channel_off"}
    put_link(db, google_uid="gOther")  # the Telegram account now belongs to someone else
    assert trm.snooze_reminder(db, NOW, rid(item_id="i1"), 60).status == 409
    assert rid(item_id="i1", slot=rs.SLOT_SNOOZE) not in entries(db)


def test_http_snooze_with_the_channel_off_is_409(main):
    _due_one_shot(main.db)
    put_profile(main.db, channel="push")
    status, body = _internal_call(main, {"action": "snooze_reminder", "rid": rid(item_id="i1"),
                                         "minutes": 60, "telegram_user_id": 111})
    assert status == 409 and body == {"success": False, "error": "channel_off"}
