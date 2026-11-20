"""Telegram reminders (phase 4) — the Firestore half.

Spec: docs/active/telegram-bot-phase4-reminders-2026-11-20.md. The schedule itself (recurrence,
entry shape, at-most-once rules) is reminder_schedule.py; this module only reads and writes:

    users/{google_uid}                     timezone (IANA), reminder_channel push|telegram|both
    users/{uid}/checklists/{cloudId}       the source of every reminder (uid = google_uid, or the
                                           credit doc id for a device doc that holds checklists)
    telegram_reminders/{rid}               server-only due index (firestore.rules: deny all)

Every function takes the Firestore client `db` and the clock `now`, like telegram_link.py, so the
tests run against an in-memory Firestore. Writes to the index happen in transactions that re-read
what they depend on: the recompute of a checklist re-reads the checklist doc and its entries, a
claim re-reads the entry, the link and the channel — so a claim racing a recompute can neither send
an occurrence twice nor lose one.

Queries stay on single-field indexes (equality on one or two fields, or a range + order on
next_fire_at alone) — the repo has no firestore.indexes.json and needs none.
"""

import logging
from datetime import datetime, timedelta

from google.cloud.firestore import transactional
from google.cloud.firestore_v1.base_query import FieldFilter

import push_promotions
import reminder_schedule as rs
from telegram_link import (
    CHANNEL_PUSH,
    LINKS_COLLECTION,
    REMINDER_CHANNEL_FIELD,
    USERS_COLLECTION,
    Outcome,
    is_telegram_user_id,
    send_bot_message,
)

logger = logging.getLogger("gisti")

REMINDERS_COLLECTION = "telegram_reminders"

CHANNEL_TELEGRAM = "telegram"
CHANNEL_BOTH = "both"
CHANNELS = (CHANNEL_PUSH, CHANNEL_TELEGRAM, CHANNEL_BOTH)
ACTIVE_CHANNELS = (CHANNEL_TELEGRAM, CHANNEL_BOTH)

TIMEZONE_FIELD = "timezone"
TIMEZONE_SOURCE_FIELD = "timezone_source"
TIMEZONE_SOURCE_DEVICE = "device"

CLAIM_DEFAULT_LIMIT = 50
CLAIM_MAX_LIMIT = 200
SNOOZE_MAX_MINUTES = 7 * 24 * 60
ENTRIES_PAGE = 500
DELETE_MAX_PAGES = 20
MAX_CHECKLISTS_PER_OWNER = 1000

ERR_UNKNOWN_REMINDER = "unknown_reminder"
ERR_INVALID_TIMEZONE = "invalid_timezone"
ERR_INVALID_CHANNEL = "invalid_channel"
ERR_CHANNEL_OFF = "channel_off"

# Data-only FCM to the account's phones when reminder_channel changes: the Android app
# suppresses its own notification while its cached channel is "telegram", and that cache is
# otherwise refreshed only on app start / the Telegram screen.
FCM_CHANNEL_MESSAGE_TYPE = "reminder_channel"
FCM_DEVICES_LIMIT = 20

# Inline buttons of the channel offer; the worker handles the callbacks (`rc:*` → PATCH channel).
OFFER_CALLBACKS = (("rc:t", 0), ("rc:b", 1), ("rc:p", 2))
OFFER_TEXTS = {
    "en": ("Telegram is linked to Gisti. Want your reminders here?",
           ("Send reminders here instead of the phone", "Both", "No")),
    "ru": ("Telegram привязан к Gisti. Присылать напоминания сюда?",
           ("Присылать сюда вместо телефона", "И сюда, и на телефон", "Нет")),
    "hi": ("Telegram अब Gisti से जुड़ गया है। क्या रिमाइंडर यहाँ भेजें?",
           ("फ़ोन के बजाय यहाँ भेजें", "दोनों", "नहीं")),
}


# ---------------------------------------------------------------------------
# Settings and the link
# ---------------------------------------------------------------------------

def normalize_channel(value) -> str:
    return value if value in CHANNELS else CHANNEL_PUSH


def _safe_id(value) -> bool:
    """A single path segment: never lets a value address a sub-collection or a reserved id."""
    return (isinstance(value, str) and 0 < len(value) <= 256 and "/" not in value
            and value not in (".", "..") and not (value.startswith("__") and value.endswith("__")))


def read_settings(db, google_uid: str) -> tuple:
    """(valid IANA zone or None, channel) of users/{google_uid}; absent channel = push."""
    snap = db.collection(USERS_COLLECTION).document(google_uid).get()
    data = (snap.to_dict() or {}) if snap.exists else {}
    zone_name = data.get(TIMEZONE_FIELD)
    return (zone_name if rs.parse_zone(zone_name) else None,
            normalize_channel(data.get(REMINDER_CHANNEL_FIELD)))


def resolve_link(db, uid):
    """The Telegram link of the account `uid` belongs to: telegram_links where google_uid == uid,
    else where credit_user_id == uid (a device doc that holds checklists). Most recent wins."""
    if not _safe_id(uid):
        return None
    links = db.collection(LINKS_COLLECTION)
    for field_name in ("google_uid", "credit_user_id"):
        docs = [d.to_dict() or {} for d in
                links.where(filter=FieldFilter(field_name, "==", uid)).limit(10).get()]
        docs = [d for d in docs if _safe_id(d.get("google_uid"))
                and is_telegram_user_id(d.get("telegram_user_id"))]
        if docs:
            return max(docs, key=lambda d: rs.to_ms(d.get("linked_at")) or 0)
    return None


def reminder_settings(db, google_uid: str) -> dict:
    """What `telegram_account status` reports: {reminder_channel, timezone}."""
    zone_name, channel = read_settings(db, google_uid)
    return {"reminder_channel": channel, "timezone": zone_name}


def should_offer(db, google_uid: str) -> bool:
    """The "send reminders here?" offer after a link: only while the channel is push / absent."""
    return read_settings(db, google_uid)[1] == CHANNEL_PUSH


def set_reminder_channel(db, google_uid: str, channel, tz=None) -> Outcome:
    """App side: one merge-write of the channel (+ the device zone when sent), so the users-doc
    trigger recomputes once. An invalid zone writes nothing."""
    if channel not in CHANNELS:
        return Outcome(400, {"error": ERR_INVALID_CHANNEL})
    update = {REMINDER_CHANNEL_FIELD: channel}
    if tz is not None:
        if rs.parse_zone(tz) is None:
            return Outcome(400, {"error": ERR_INVALID_TIMEZONE})
        update[TIMEZONE_FIELD] = tz
        update[TIMEZONE_SOURCE_FIELD] = TIMEZONE_SOURCE_DEVICE
    # merge: the data doc may not exist yet (checklists live in its sub-collection).
    db.collection(USERS_COLLECTION).document(google_uid).set(update, merge=True)
    body = {"reminder_channel": channel}
    if tz is not None:
        body["timezone"] = tz
    return Outcome(200, body)


# ---------------------------------------------------------------------------
# Index maintenance
# ---------------------------------------------------------------------------

def _entries(db):
    return db.collection(REMINDERS_COLLECTION)


def _entries_query(db, google_uid: str, cloud_id=None):
    query = _entries(db).where(filter=FieldFilter("google_uid", "==", google_uid))
    if cloud_id is not None:
        query = query.where(filter=FieldFilter("checklist_cloud_id", "==", cloud_id))
    return query.limit(ENTRIES_PAGE)


def _delete_matching(db, google_uid: str, cloud_id=None) -> int:
    deleted = 0
    for _ in range(DELETE_MAX_PAGES):
        docs = list(_entries_query(db, google_uid, cloud_id).get())
        for doc in docs:
            doc.reference.delete()
        deleted += len(docs)
        if len(docs) < ENTRIES_PAGE:
            break
    return deleted


def delete_user_entries(db, google_uid: str) -> int:
    """Unlink / channel off / no zone: every entry of the account (main, snooze, tombstones)."""
    return _delete_matching(db, google_uid) if _safe_id(google_uid) else 0


def delete_checklist_entries(db, google_uid: str, cloud_id: str) -> int:
    return _delete_matching(db, google_uid, cloud_id) if _safe_id(google_uid) else 0


def recompute_checklist(db, now: datetime, link: dict, zone_name: str, owner_uid: str,
                        cloud_id: str) -> dict:
    """Rebuild the main entries of one checklist in ONE transaction that re-reads the checklist
    doc and its current entries. A missing / soft-deleted doc deletes every entry of it."""
    google_uid = link["google_uid"]
    if not (_safe_id(owner_uid) and _safe_id(cloud_id)):
        return {"written": 0, "deleted": 0, "warnings": []}
    doc_ref = db.collection(f"{USERS_COLLECTION}/{owner_uid}/checklists").document(cloud_id)
    entries = _entries(db)

    @transactional
    def txn(transaction):
        snap = doc_ref.get(transaction=transaction)
        existing = {d.id: d.to_dict() or {} for d in
                    _entries_query(db, google_uid, cloud_id).get(transaction=transaction)}
        doc = (snap.to_dict() or {}) if snap.exists else None
        if doc is None or doc.get("isDeleted") is True:
            for rid in existing:
                transaction.delete(entries.document(rid))
            return {"written": 0, "deleted": len(existing), "warnings": []}
        build = rs.build_entries(doc, google_uid=google_uid, owner_uid=owner_uid,
                                 cloud_id=cloud_id, link=link, zone_name=zone_name, now=now,
                                 existing=existing)
        for rid, entry in build.writes.items():
            transaction.set(entries.document(rid), entry)
        for rid in build.deletes:
            transaction.delete(entries.document(rid))
        return {"written": len(build.writes), "deleted": len(build.deletes),
                "warnings": build.warnings}

    result = txn(db.transaction())
    if result["warnings"]:
        # Counts and reasons only — never the checklist content.
        logger.warning("telegram reminders: checklist recompute skipped data: %s",
                       "; ".join(sorted(set(result["warnings"]))))
    return result


def recompute_user(db, now: datetime, uid: str) -> dict:
    """Link, unlink-free settings change: rebuild every checklist of the account `uid` belongs
    to — or delete all of its entries when the channel is not Telegram or the zone is unknown."""
    link = resolve_link(db, uid)
    if link is None:
        return {"status": "not_linked"}
    google_uid = link["google_uid"]
    zone_name, channel = read_settings(db, google_uid)
    if channel not in ACTIVE_CHANNELS or zone_name is None:
        return {"status": "channel_off", "deleted": delete_user_entries(db, google_uid)}

    owners = [google_uid]
    credit_user_id = link.get("credit_user_id")
    if _safe_id(credit_user_id) and credit_user_id != google_uid:
        owners.append(credit_user_id)
    with_entries = {(d.to_dict() or {}).get("checklist_cloud_id")
                    for d in _entries_query(db, google_uid).get()}
    seen = set()
    recomputed = 0
    for owner in owners:
        docs = (db.collection(f"{USERS_COLLECTION}/{owner}/checklists")
                .limit(MAX_CHECKLISTS_PER_OWNER).get())
        for snap in docs:
            if snap.id in seen:
                continue  # the google_uid doc wins over a duplicate under the device doc
            seen.add(snap.id)
            if rs.has_reminder_config(snap.to_dict() or {}) or snap.id in with_entries:
                recompute_checklist(db, now, link, zone_name, owner, snap.id)
                recomputed += 1
    gone = {c for c in with_entries if isinstance(c, str)} - seen
    for cloud_id in gone:
        delete_checklist_entries(db, google_uid, cloud_id)
    return {"status": "recomputed", "checklists": recomputed, "gone": len(gone)}


# ---------------------------------------------------------------------------
# Firestore triggers (decoded payloads in, no Cloud Events here)
# ---------------------------------------------------------------------------

def _is_deleted(doc) -> bool:
    return doc is None or doc.get("isDeleted") is True


def on_checklist_written(db, now: datetime, uid, cloud_id, before, after) -> str:
    """users/{uid}/checklists/{cloudId} written. Returns what it did (for logs and tests).

    Most checklist writes carry no reminder at all, so the payload decides first — no read for
    them. A version with reminder data, or a delete, re-reads the CURRENT doc in the recompute
    transaction, so out-of-order events still converge on the latest state.
    """
    relevant = (rs.has_reminder_config(before) or rs.has_reminder_config(after)
                or (_is_deleted(after) and not _is_deleted(before)))
    if not relevant:
        return "no_reminders"
    link = resolve_link(db, uid)
    if link is None:
        return "not_linked"
    zone_name, channel = read_settings(db, link["google_uid"])
    if channel not in ACTIVE_CHANNELS or zone_name is None:
        if _safe_id(cloud_id):
            delete_checklist_entries(db, link["google_uid"], cloud_id)
        return "channel_off"
    recompute_checklist(db, now, link, zone_name, uid, cloud_id)
    return "recomputed"


def _settings_of(doc) -> tuple:
    doc = doc or {}
    return doc.get(TIMEZONE_FIELD), normalize_channel(doc.get(REMINDER_CHANNEL_FIELD))


def on_user_written(db, now: datetime, uid, before, after, messaging=None) -> str:
    """users/{uid} written. This collection is hot (credit docs change on every AI call): unless
    `timezone` or `reminder_channel` changed, return before any read.

    A changed channel (absent = push) is also pushed to the account's phones — whoever wrote it
    (bot PATCH, app set_reminder_channel, the unlink reset). `messaging` = firebase_admin.messaging.
    """
    old, new = _settings_of(before), _settings_of(after)
    if old == new:
        return "unchanged"
    if messaging is not None and old[1] != new[1]:
        notify_channel_change(db, messaging, uid, new[1], after)
    return recompute_user(db, now, uid)["status"]


def notify_channel_change(db, messaging, uid, channel: str, profile=None) -> dict:
    """Data-only FCM {"type": "reminder_channel", "channel": <channel>} to every device of the
    account `uid`: the fcmToken of each credit doc `users where google_uid == uid` (and of `profile`
    itself, should it carry one). High priority, no notification block, collapsed so an offline
    phone gets only the latest value.

    Best-effort: a failed or dead token is logged (exception type + class, never the token or the
    error text) and never raises.
    """
    tokens = []

    def add(data):
        token = (data or {}).get("fcmToken")
        if isinstance(token, str) and token.strip() and token.strip() not in tokens:
            tokens.append(token.strip())

    try:
        add(profile)
        if _safe_id(uid):
            for doc in (db.collection(USERS_COLLECTION)
                        .where(filter=FieldFilter("google_uid", "==", uid))
                        .limit(FCM_DEVICES_LIMIT).get()):
                add(doc.to_dict())
    except Exception as e:
        logger.exception("telegram reminders: device lookup for the channel push failed (%s)",
                         type(e).__name__)
        return {"sent": 0, "failed": 0}

    sent = failed = 0
    for token in tokens:
        try:
            messaging.send(messaging.Message(
                token=token,
                data={"type": FCM_CHANNEL_MESSAGE_TYPE, "channel": channel},
                android=messaging.AndroidConfig(priority="high",
                                                collapse_key=FCM_CHANNEL_MESSAGE_TYPE),
            ))
            sent += 1
        except Exception as e:  # noqa: BLE001 — best-effort by contract
            failed += 1
            logger.warning("telegram reminders: channel push not delivered (%s, %s)",
                           type(e).__name__, push_promotions.classify_send_error(e))
    return {"sent": sent, "failed": failed}


# ---------------------------------------------------------------------------
# Worker actions
# ---------------------------------------------------------------------------

def _claim_one(db, now: datetime, ref):
    """One entry in one transaction: (outcome, item) with outcome claimed | late | dropped | skip."""
    links = db.collection(LINKS_COLLECTION)
    users = db.collection(USERS_COLLECTION)
    now_ms = rs.to_ms(now)

    @transactional
    def txn(transaction):
        snap = ref.get(transaction=transaction)
        if not snap.exists:
            return "skip", None
        entry = snap.to_dict() or {}
        fire_ms = rs.to_ms(entry.get("next_fire_at"))
        if fire_ms is None or fire_ms > now_ms:
            return "skip", None  # claimed by a parallel call, or moved by a recompute
        google_uid = entry.get("google_uid")
        tg_id = entry.get("telegram_user_id")
        link_snap = (links.document(str(tg_id)).get(transaction=transaction)
                     if is_telegram_user_id(tg_id) else None)
        profile_snap = (users.document(google_uid).get(transaction=transaction)
                        if _safe_id(google_uid) else None)
        link = (link_snap.to_dict() or {}) if link_snap is not None and link_snap.exists else None
        profile = (profile_snap.to_dict() or {}) if profile_snap is not None and profile_snap.exists else {}
        channel = normalize_channel(profile.get(REMINDER_CHANNEL_FIELD))
        if link is None or link.get("google_uid") != google_uid or channel not in ACTIVE_CHANNELS:
            # Unlinked / relinked elsewhere / channel back on the phone, and the hook that should
            # have removed this entry did not run (triggers do not retry): never deliver it.
            transaction.delete(ref)
            return "dropped", None
        transaction.set(ref, rs.consume_due(entry, now))
        if rs.is_stale(fire_ms, now):
            return "late", None
        chat_id = link.get("chat_id", entry.get("chat_id"))
        return "claimed", {
            "rid": snap.id,
            "chat_id": chat_id,
            "telegram_user_id": tg_id,
            "language_code": link.get("language_code", entry.get("language_code")),
            "kind": entry.get("kind"),
            "checklist_cloud_id": entry.get("checklist_cloud_id"),
            "checklist_owner_uid": entry.get("checklist_owner_uid"),
            "item_id": entry.get("item_id"),
            "checklist_name": entry.get("checklist_name"),
            "item_text": entry.get("item_text"),
            "fire_at_ms": fire_ms,
            "google_uid": google_uid,
            "timezone": entry.get("timezone"),
        }

    return txn(db.transaction())


def claim_due_reminders(db, now: datetime, limit: int = CLAIM_DEFAULT_LIMIT) -> Outcome:
    """Worker cron: claim every due entry (oldest first, up to `limit`) BEFORE it is sent — so
    a reminder is sent at most once. Entries more than STALE_AFTER late are consumed silently."""
    due = (_entries(db).where(filter=FieldFilter("next_fire_at", "<=", now))
           .order_by("next_fire_at").limit(limit).get())
    claimed = []
    counts = {"late": 0, "dropped": 0}
    for snap in due:
        outcome, item = _claim_one(db, now, snap.reference)
        if outcome == "claimed":
            claimed.append(item)
        elif outcome in counts:
            counts[outcome] += 1
    if counts["late"] or counts["dropped"]:
        logger.info("telegram reminders: claim skipped %d late and dropped %d orphaned entries",
                    counts["late"], counts["dropped"])
    return Outcome(200, {"reminders": claimed})


def snooze_reminder(db, now: datetime, rid: str, minutes: int, telegram_user_id=None) -> Outcome:
    """«⏰ In 1 hour»: (re)create the snooze entry of the target behind `rid` (a main or a snooze
    entry, also a tombstone of a delivered one-shot), firing `minutes` from now.

    `telegram_user_id` (optional) must own the entry — a mismatch answers 404 like an unknown rid.
    409 "channel_off" when the account is no longer linked to that Telegram user or its channel
    is not telegram / both: the claim would drop the snooze, so nothing is written and the bot
    must not promise a reminder.
    """
    source = _entries(db).document(rid).get()
    entry = (source.to_dict() or {}) if source.exists else None
    if (entry is None or not _safe_id(entry.get("google_uid"))
            or not _safe_id(entry.get("checklist_cloud_id"))
            or (telegram_user_id is not None and entry.get("telegram_user_id") != telegram_user_id)):
        return Outcome(404, {"error": ERR_UNKNOWN_REMINDER})
    tg_id = entry.get("telegram_user_id")
    link_snap = (db.collection(LINKS_COLLECTION).document(str(tg_id)).get()
                 if is_telegram_user_id(tg_id) else None)
    link = (link_snap.to_dict() or {}) if link_snap is not None and link_snap.exists else None
    if (link is None or link.get("google_uid") != entry["google_uid"]
            or read_settings(db, entry["google_uid"])[1] not in ACTIVE_CHANNELS):
        return Outcome(409, {"error": ERR_CHANNEL_OFF})
    fire_at = now + timedelta(minutes=minutes)
    snooze_rid = rs.make_rid(entry["google_uid"], entry["checklist_cloud_id"],
                             entry.get("item_id"), rs.SLOT_SNOOZE)
    _entries(db).document(snooze_rid).set(rs.snooze_entry(entry, fire_at, now))
    return Outcome(200, {"rid": snooze_rid, "fire_at_ms": rs.to_ms(fire_at),
                         "timezone": entry.get("timezone")})


# ---------------------------------------------------------------------------
# The channel offer after a bot → app link (Python sends it; app → bot is the worker's)
# ---------------------------------------------------------------------------

def channel_offer_payload(chat_id: int, language_code) -> dict:
    lang = (language_code or "").split("-")[0].strip().lower() if isinstance(language_code, str) else ""
    text, buttons = OFFER_TEXTS.get(lang, OFFER_TEXTS["en"])
    return {
        "chat_id": chat_id,
        "text": text,
        "reply_markup": {"inline_keyboard": [
            [{"text": buttons[index], "callback_data": data}] for data, index in OFFER_CALLBACKS
        ]},
    }


def send_channel_offer(bot_token: str, chat_id: int, language_code, post) -> tuple:
    """Best-effort, like notify_unlinked: (delivered, log-safe reason), never raises."""
    return send_bot_message(bot_token, channel_offer_payload(chat_id, language_code), post)
