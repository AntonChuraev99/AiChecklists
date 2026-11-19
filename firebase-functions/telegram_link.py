"""Telegram account linking — the pure half of telegram_account / telegram_bot_internal.

Kept out of main.py so the money-adjacent link state can be tested without Flask, Gemini or a
live Firestore: every action takes the Firestore client `db` and the clock `now` as parameters,
and the only network call (the best-effort "you were unlinked" message) takes its HTTP `post`
function as a parameter too.

Data model (spec: docs/active/telegram-bot-phase1-2026-11-19.md, "Firestore schema"):

    telegram_link_tokens/{sha256_hex(token)}   one-time, TTL_SEC; the raw token is never stored
    telegram_links/{telegram_user_id}          the link itself (one per Telegram account)
    users/{credit_user_id}                     gets telegram_user_id / telegram_username /
                                               telegram_linked_at; deleted on unlink

Linking is 1:1 and is written in ONE transaction together with consuming the token: a token can
only be spent once (used_at), and binding a Telegram account that is linked elsewhere, or a
Google account that already has another Telegram, removes the old link and its credit-doc fields
in the same commit. Every read happens before the first write — Firestore rejects a read after a
write inside a transaction.

Phase 3 — Telegram Stars subscription (spec section "Phase 3"):

    telegram_payments/{telegram_payment_charge_id}   idempotency key + audit of every payment
    users/{credit_user_id}                            telegram_premium_until, telegram_subscription
                                                      {charge_id, state, updated_at}; is_premium /
                                                      ai_credits like the RevenueCat grant path

A payment that arrives while the Telegram account has no link is stored `unassigned` (nothing
granted). Linking that Telegram account later — either direction — applies its unassigned payments
whose paid period has not ended yet, inside the link transaction (`_consume_and_link`).

Premium = RevenueCat OR telegram_premium_until > now. The RevenueCat check lives in main.py and
is passed in as a callable (`store_premium(credit_user_id) -> True | False | None`, None =
RevenueCat unreachable), so this module stays network-free.
"""

import base64
import hashlib
import hmac
import re
import secrets
from dataclasses import dataclass, field
from datetime import datetime, timedelta, timezone

from google.cloud.firestore import DELETE_FIELD, transactional
from google.cloud.firestore_v1.base_query import FieldFilter

TOKENS_COLLECTION = "telegram_link_tokens"
LINKS_COLLECTION = "telegram_links"
USERS_COLLECTION = "users"

APP_TO_BOT = "app_to_bot"
BOT_TO_APP = "bot_to_app"

TOKEN_TTL_SEC = 600
DEEP_LINK_PREFIX = "https://t.me/gisti_app_bot?start="
LOGIN_URL_PREFIX = "https://app.gisti-ai.com/?tg_link="

INTERNAL_KEY_LABEL = "gisti-telegram-internal-v1"
WEBHOOK_SECRET_LABEL = "gisti-telegram-webhook-v1"

TELEGRAM_API_BASE = "https://api.telegram.org"
NOTIFY_TIMEOUT_SEC = 5

# Fields the server writes onto the credit doc users/{credit_user_id}.
CREDIT_DOC_FIELDS = ("telegram_user_id", "telegram_username", "telegram_linked_at")

# 32 random bytes in base64url without padding is exactly 43 chars of [A-Za-z0-9_-] — the shape
# of Telegram's /start parameter. Anything else cannot be a token we issued.
_TOKEN_RE = re.compile(r"^[A-Za-z0-9_-]{43}$")
# The credit doc id is a server UUID; this bound keeps a hostile body from addressing a
# sub-collection ("a/b") or a reserved id ("__x__" is rejected by Firestore with a 500).
_DOC_ID_RE = re.compile(r"^[A-Za-z0-9_-]{1,128}$")

# Telegram limits: username 5-32, first_name 1-64, language_code is an IETF tag. Bounded here so
# a buggy caller cannot park arbitrary blobs in a server-only collection.
_MAX_USERNAME = 64
_MAX_FIRST_NAME = 256
_MAX_LANGUAGE_CODE = 35

ERR_UNKNOWN_TOKEN = "unknown_token"
ERR_TOKEN_USED = "token_used"
ERR_TOKEN_EXPIRED = "token_expired"
ERR_NO_ACCOUNT = "no_account"
ERR_UNKNOWN_PAYMENT = "unknown_payment"

# ---- Phase 3: Stars subscription ----
PAYMENTS_COLLECTION = "telegram_payments"
PREMIUM_EVENTS_COLLECTION = "premium_events_log"
STARS_CURRENCY = "XTR"
PREMIUM_PERIOD = timedelta(days=30)  # Telegram allows only subscription_period = 2592000 s
DEFAULT_PRICE_STARS = 100

PAYMENT_APPLIED = "applied"
PAYMENT_UNASSIGNED = "unassigned"
PAYMENT_REFUNDED = "refunded"

# Unassigned payments of one Telegram account looked at when it gets linked. One live period at a
# time is the norm (30-day renewals); the bound keeps a hostile backlog out of the transaction.
PENDING_PAYMENTS_LIMIT = 20
APPLIED_VIA_LINK = "link"

SUBSCRIPTION_STATES = ("active", "canceled", "failed")

_MAX_CHARGE_ID = 256
_MAX_INVOICE_PAYLOAD = 128

UNLINK_MESSAGES = {
    "en": "Your Telegram is no longer linked to Gisti. Send /start to link it again.",
    "ru": "Telegram отвязан от Gisti. Чтобы привязать его снова, отправьте /start.",
    "hi": "आपका Telegram अब Gisti से जुड़ा नहीं है। फिर से जोड़ने के लिए /start भेजें।",
}


@dataclass(frozen=True)
class Outcome:
    """What an action decided: HTTP status, response body, and the chats to tell about an unlink.

    `notify` is filled only by the app-side unlink: (chat_id, language_code) of every link it
    removed. Sending is the caller's job, after the transaction has committed.
    """

    status: int
    body: dict
    notify: tuple = field(default_factory=tuple)


def _error(status: int, code: str) -> Outcome:
    return Outcome(status, {"error": code})


# ---------------------------------------------------------------------------
# Tokens and derived keys
# ---------------------------------------------------------------------------

def _b64url(raw: bytes) -> str:
    return base64.urlsafe_b64encode(raw).rstrip(b"=").decode("ascii")


def generate_token() -> str:
    """32 random bytes, base64url without padding (43 chars)."""
    return _b64url(secrets.token_bytes(32))


def sha256_hex(token: str) -> str:
    return hashlib.sha256(token.encode("utf-8")).hexdigest()


def derive_key(bot_token: str, label: str) -> str:
    """HMAC_SHA256(key=bot_token, msg=label), base64url without '='.

    The worker derives the same values from the same bot token; the shared test vector in
    tests/test_telegram_link.py pins both implementations together.
    """
    mac = hmac.new(bot_token.encode("utf-8"), label.encode("utf-8"), hashlib.sha256).digest()
    return _b64url(mac)


def internal_key_matches(provided, bot_token: str) -> bool:
    """Constant-time check of the X-Gisti-Internal-Key header. Bytes on both sides, because
    `hmac.compare_digest` raises TypeError on a non-ASCII str and the header is caller-controlled."""
    if not isinstance(provided, str) or not provided or not bot_token:
        return False
    expected = derive_key(bot_token, INTERNAL_KEY_LABEL)
    return hmac.compare_digest(provided.encode("utf-8"), expected.encode("utf-8"))


def is_token_shaped(token) -> bool:
    return isinstance(token, str) and bool(_TOKEN_RE.match(token))


# ---------------------------------------------------------------------------
# Input normalisation
# ---------------------------------------------------------------------------

def _is_int(value) -> bool:
    return isinstance(value, int) and not isinstance(value, bool)


def is_telegram_user_id(value) -> bool:
    return _is_int(value) and value > 0


def _optional_str(value, limit: int):
    if not isinstance(value, str):
        return None
    value = value.strip()
    return value[:limit] if value else None


def parse_telegram_fields(data: dict) -> tuple[dict | None, str | None]:
    """Validate the worker's Telegram fields: (fields, None) or (None, error message).

    `telegram_user_id` must be a positive int and `chat_id` a non-zero int — JSON numbers, not
    strings, so the link doc id is always the canonical decimal form of the user id.
    """
    tg_id = data.get("telegram_user_id")
    if not is_telegram_user_id(tg_id):
        return None, "telegram_user_id must be a positive integer"
    chat_id = data.get("chat_id")
    if not _is_int(chat_id) or chat_id == 0:
        return None, "chat_id must be a non-zero integer"
    return {
        "telegram_user_id": tg_id,
        "chat_id": chat_id,
        "telegram_username": _optional_str(data.get("username"), _MAX_USERNAME),
        "telegram_first_name": _optional_str(data.get("first_name"), _MAX_FIRST_NAME),
        "language_code": _optional_str(data.get("language_code"), _MAX_LANGUAGE_CODE),
    }, None


def resolve_credit_user_id(db, google_uid: str, requested_user_id=None) -> str | None:
    """Credit doc of a Google account: `users where google_uid == <firebase uid>`.

    Prefers `requested_user_id` (the body `user_id`) when it is one of the matches — read
    directly, so it is found no matter how many credit docs carry this uid — else the first
    match; None when there is none (→ 409 no_account).
    """
    if isinstance(requested_user_id, str) and _DOC_ID_RE.match(requested_user_id):
        snap = db.collection(USERS_COLLECTION).document(requested_user_id).get()
        if snap.exists and (snap.to_dict() or {}).get("google_uid") == google_uid:
            return requested_user_id
    matches = (db.collection(USERS_COLLECTION)
               .where(filter=FieldFilter("google_uid", "==", google_uid))
               .limit(1).get())
    for doc in matches:
        return doc.id
    return None


# ---------------------------------------------------------------------------
# Token creation (both directions)
# ---------------------------------------------------------------------------

def _store_token(db, now: datetime, fields: dict) -> str:
    token = generate_token()
    doc = {
        **fields,
        "created_at": now,
        "expires_at": now + timedelta(seconds=TOKEN_TTL_SEC),
        "used_at": None,
    }
    db.collection(TOKENS_COLLECTION).document(sha256_hex(token)).set(doc)
    return token


def create_token(db, now: datetime, *, google_uid: str, credit_user_id: str) -> Outcome:
    """App → bot: the signed-in app user opens t.me/gisti_app_bot?start=<token>."""
    token = _store_token(db, now, {
        "direction": APP_TO_BOT,
        "google_uid": google_uid,
        "credit_user_id": credit_user_id,
    })
    return Outcome(200, {
        "token": token,
        "deep_link": DEEP_LINK_PREFIX + token,
        "expires_in_sec": TOKEN_TTL_SEC,
    })


def create_login_token(db, now: datetime, telegram: dict) -> Outcome:
    """Bot → app: the Telegram user opens the web app, signs in with Google, the app claims."""
    token = _store_token(db, now, {"direction": BOT_TO_APP, **telegram})
    return Outcome(200, {"token": token, "url": LOGIN_URL_PREFIX + token})


# ---------------------------------------------------------------------------
# Consume a token and link — ONE transaction
# ---------------------------------------------------------------------------

def _clear_credit_fields() -> dict:
    return {name: DELETE_FIELD for name in CREDIT_DOC_FIELDS}


def _consume_and_link(db, now: datetime, token: str, direction: str, *,
                      telegram: dict | None = None,
                      account: tuple[str, str] | None = None,
                      credits_cap: int | None = None) -> Outcome:
    """Spend `token` (which must be of `direction`) and write the 1:1 link atomically.

    app_to_bot: the account comes from the token, `telegram` from the worker.
    bot_to_app: the Telegram fields come from the token, `account` = (google_uid, credit_user_id)
    from the authenticated app caller.

    The same commit applies this Telegram account's `unassigned` Stars payments whose period has
    not ended (paid while unlinked — e.g. renewals after an unlink): each becomes `applied` to the
    linked credit doc, which gets the same grant as apply_payment. `credits_cap` is required as
    soon as there is such a payment; expired ones stay `unassigned`. The success body carries
    "premium_applied": <count> only when it is non-zero.

    A wrong-direction token answers 404 exactly like an unknown one and is NOT consumed — the
    caller learns nothing about tokens minted for the other side.
    """
    if not is_token_shaped(token):
        return _error(404, ERR_UNKNOWN_TOKEN)

    tokens = db.collection(TOKENS_COLLECTION)
    links = db.collection(LINKS_COLLECTION)
    users = db.collection(USERS_COLLECTION)
    payments = db.collection(PAYMENTS_COLLECTION)
    token_ref = tokens.document(sha256_hex(token))
    # Outside the transaction function, like apply_payment: a retried attempt reuses the id.
    log_ref = db.collection(PREMIUM_EVENTS_COLLECTION).document()

    @transactional
    def txn(transaction):
        # ---- reads (all of them before the first write) ----
        token_snap = token_ref.get(transaction=transaction)
        if not token_snap.exists:
            return _error(404, ERR_UNKNOWN_TOKEN)
        tok = token_snap.to_dict() or {}
        if tok.get("direction") != direction:
            return _error(404, ERR_UNKNOWN_TOKEN)
        if tok.get("used_at") is not None:
            return _error(410, ERR_TOKEN_USED)
        expires_at = tok.get("expires_at")
        if not isinstance(expires_at, datetime) or expires_at <= now:
            return _error(410, ERR_TOKEN_EXPIRED)

        if direction == APP_TO_BOT:
            google_uid = tok.get("google_uid")
            credit_user_id = tok.get("credit_user_id")
            tg = telegram
        else:
            google_uid, credit_user_id = account
            tg = {
                "telegram_user_id": tok.get("telegram_user_id"),
                "chat_id": tok.get("chat_id"),
                "telegram_username": tok.get("telegram_username"),
                "telegram_first_name": tok.get("telegram_first_name"),
                "language_code": tok.get("language_code"),
            }
        if not google_uid or not credit_user_id or not _is_int(tg.get("telegram_user_id")):
            # A malformed token doc — never written by this module; refuse rather than guess.
            return _error(404, ERR_UNKNOWN_TOKEN)

        tg_id = tg["telegram_user_id"]
        link_id = str(tg_id)
        link_ref = links.document(link_id)
        credit_ref = users.document(credit_user_id)

        own_link = link_ref.get(transaction=transaction)
        account_links = list(
            links.where(filter=FieldFilter("google_uid", "==", google_uid))
            .limit(10).get(transaction=transaction)
        )
        credit_snap = credit_ref.get(transaction=transaction)
        prev_credit = credit_snap.to_dict() or {}
        if not credit_snap.exists or prev_credit.get("google_uid") != google_uid:
            return _error(409, ERR_NO_ACCOUNT)

        # Payments this Telegram account made while unlinked and whose period is still running.
        pending = (payments
                   .where(filter=FieldFilter("telegram_user_id", "==", tg_id))
                   .where(filter=FieldFilter("status", "==", PAYMENT_UNASSIGNED))
                   .limit(PENDING_PAYMENTS_LIMIT).get(transaction=transaction))
        live = []
        for doc in pending:
            rec = doc.to_dict() or {}
            end = _period_end(rec.get("subscription_expiration_date"),
                              _as_utc(rec.get("created_at")))
            if end is not None and end > now:
                live.append((doc, rec, end))
        if live and credits_cap is None:
            raise ValueError("credits_cap is required to apply unassigned Telegram payments")

        # Links this Google account holds for OTHER Telegram accounts: removed (1:1).
        stale_link_ids = []
        # credit_user_id -> the telegram_user_id its fields must still carry to be cleared.
        to_clear = {}
        for doc in account_links:
            if doc.id == link_id:
                continue
            stale_link_ids.append(doc.id)
            old = doc.to_dict() or {}
            old_credit = old.get("credit_user_id")
            if old_credit and old_credit != credit_user_id:
                to_clear[old_credit] = old.get("telegram_user_id")
        # This Telegram account linked to a different credit doc (another Google account, or
        # another credit doc of the same one): that doc loses its telegram fields.
        if own_link.exists:
            old_credit = (own_link.to_dict() or {}).get("credit_user_id")
            if old_credit and old_credit != credit_user_id:
                to_clear[old_credit] = tg_id
        clear_snaps = {cid: users.document(cid).get(transaction=transaction) for cid in to_clear}

        # ---- writes ----
        transaction.update(token_ref, {"used_at": now})
        for stale_id in stale_link_ids:
            transaction.delete(links.document(stale_id))
        for cid, snap in clear_snaps.items():
            # Only when the doc still carries THAT Telegram id — never wipe someone else's link.
            if snap.exists and (snap.to_dict() or {}).get("telegram_user_id") == to_clear[cid]:
                transaction.update(users.document(cid), _clear_credit_fields())
        transaction.set(link_ref, {
            "telegram_user_id": tg_id,
            "chat_id": tg.get("chat_id"),
            "telegram_username": tg.get("telegram_username"),
            "telegram_first_name": tg.get("telegram_first_name"),
            "language_code": tg.get("language_code"),
            "google_uid": google_uid,
            "credit_user_id": credit_user_id,
            "linked_at": now,
        })
        credit_update = {
            "telegram_user_id": tg_id,
            "telegram_username": tg.get("telegram_username"),
            "telegram_linked_at": now,
        }
        if live:
            # The payment whose period ends last defines the grant and the subscription marker.
            latest_doc, latest_rec, latest_end = max(live, key=lambda entry: entry[2])
            grant = _premium_grant_update(prev_credit, latest_end, latest_doc.id,
                                          credits_cap=credits_cap, now=now)
            credit_update.update(grant)
            for doc, _rec, end in live:
                transaction.update(doc.reference, {
                    "status": PAYMENT_APPLIED,
                    "credit_user_id": credit_user_id,
                    "google_uid": google_uid,
                    "telegram_premium_until": end,
                    "applied_at": now,
                    "applied_via": APPLIED_VIA_LINK,
                })
            transaction.set(log_ref, {
                **_premium_grant_log(prev_credit, {**latest_rec, "charge_id": latest_doc.id},
                                     credit_user_id=credit_user_id,
                                     event_type="telegram_stars_payment_linked",
                                     until=grant["telegram_premium_until"],
                                     credits_cap=credits_cap, now=now),
                "applied_charge_ids": [doc.id for doc, _rec, _end in live],
            })
        transaction.update(credit_ref, credit_update)
        applied = {"premium_applied": len(live)} if live else {}
        if direction == APP_TO_BOT:
            return Outcome(200, {"google_uid": google_uid, "credit_user_id": credit_user_id,
                                 **applied})
        return Outcome(200, {
            "linked": True,
            "telegram_username": tg.get("telegram_username"),
            # Fallback label for Telegram users without an @username.
            "telegram_first_name": tg.get("telegram_first_name"),
            **applied,
        })

    return txn(db.transaction())


def consume_app_token(db, now: datetime, token: str, telegram: dict, *,
                      credits_cap: int | None = None) -> Outcome:
    """Worker side of app → bot: `/start <token>` from the Telegram user in `telegram`."""
    return _consume_and_link(db, now, token, APP_TO_BOT, telegram=telegram,
                             credits_cap=credits_cap)


def claim(db, now: datetime, token: str, *, google_uid: str, credit_user_id: str,
          credits_cap: int | None = None) -> Outcome:
    """App side of bot → app: the signed-in app user spends the bot's login token."""
    return _consume_and_link(db, now, token, BOT_TO_APP, account=(google_uid, credit_user_id),
                             credits_cap=credits_cap)


# ---------------------------------------------------------------------------
# Status / unlink
# ---------------------------------------------------------------------------

def _account_links_query(db, google_uid: str):
    return (db.collection(LINKS_COLLECTION)
            .where(filter=FieldFilter("google_uid", "==", google_uid))
            .limit(10))


def status(db, google_uid: str) -> Outcome:
    docs = [d.to_dict() or {} for d in _account_links_query(db, google_uid).get()]
    if not docs:
        return Outcome(200, {"linked": False})
    # 1:1 is enforced on write; should two ever coexist, report the most recent.
    link = max(docs, key=lambda d: _epoch_ms(d) or 0)
    body = {
        "linked": True,
        "telegram_username": link.get("telegram_username"),
        "telegram_first_name": link.get("telegram_first_name"),
    }
    linked_ms = _epoch_ms(link)
    if linked_ms is not None:
        body["linked_at_ms"] = linked_ms
    return Outcome(200, body)


def _epoch_ms(link: dict):
    linked_at = link.get("linked_at")
    if isinstance(linked_at, datetime):
        return int(linked_at.timestamp() * 1000)
    return None


def _remove_links_txn(db, link_refs_and_snaps, transaction) -> list:
    """Shared write half of both unlinks. Caller has done every read already."""
    users = db.collection(USERS_COLLECTION)
    removed = []
    for link_ref, link, credit_snap in link_refs_and_snaps:
        transaction.delete(link_ref)
        if (credit_snap is not None and credit_snap.exists
                and (credit_snap.to_dict() or {}).get("telegram_user_id") == link.get("telegram_user_id")):
            transaction.update(users.document(credit_snap.id), _clear_credit_fields())
        removed.append(link)
    return removed


def unlink(db, google_uid: str) -> Outcome:
    """App side: remove every Telegram link of this Google account. Idempotent."""
    users = db.collection(USERS_COLLECTION)

    @transactional
    def txn(transaction):
        found = list(_account_links_query(db, google_uid).get(transaction=transaction))
        entries = []
        for doc in found:
            link = doc.to_dict() or {}
            cid = link.get("credit_user_id")
            credit_snap = (users.document(cid).get(transaction=transaction)
                           if isinstance(cid, str) and cid else None)
            entries.append((doc.reference, link, credit_snap))
        return _remove_links_txn(db, entries, transaction)

    removed = txn(db.transaction())
    notify = tuple(
        (link.get("chat_id"), link.get("language_code"))
        for link in removed if _is_int(link.get("chat_id"))
    )
    return Outcome(200, {"linked": False}, notify=notify)


def unlink_by_telegram(db, telegram_user_id: int) -> Outcome:
    """Worker side (/unlink in the bot). Idempotent; the bot itself confirms in chat."""
    links = db.collection(LINKS_COLLECTION)
    users = db.collection(USERS_COLLECTION)
    link_ref = links.document(str(telegram_user_id))

    @transactional
    def txn(transaction):
        snap = link_ref.get(transaction=transaction)
        if not snap.exists:
            return []
        link = snap.to_dict() or {}
        cid = link.get("credit_user_id")
        credit_snap = (users.document(cid).get(transaction=transaction)
                       if isinstance(cid, str) and cid else None)
        return _remove_links_txn(db, [(link_ref, link, credit_snap)], transaction)

    txn(db.transaction())
    return Outcome(200, {"ok": True})


# ---------------------------------------------------------------------------
# Best-effort Bot API notice after an app-side unlink
# ---------------------------------------------------------------------------

def unlink_message(language_code) -> str:
    lang = (language_code or "").split("-")[0].strip().lower() if isinstance(language_code, str) else ""
    return UNLINK_MESSAGES.get(lang, UNLINK_MESSAGES["en"])


def notify_unlinked(bot_token: str, chat_id: int, language_code, post) -> tuple[bool, str]:
    """POST sendMessage; returns (delivered, reason). Never raises.

    The reason is safe to log: it is the exception TYPE or Telegram's status/description, never
    the exception text — a requests error message embeds the URL, and the URL embeds the bot token.
    """
    url = f"{TELEGRAM_API_BASE}/bot{bot_token}/sendMessage"
    try:
        resp = post(url, json={"chat_id": chat_id, "text": unlink_message(language_code)},
                    timeout=NOTIFY_TIMEOUT_SEC)
    except Exception as e:  # noqa: BLE001 — best-effort by contract
        return False, type(e).__name__
    status_code = getattr(resp, "status_code", None)
    if status_code == 200:
        return True, "ok"
    description = ""
    try:
        description = str((resp.json() or {}).get("description", ""))[:120]
    except Exception:  # noqa: BLE001 — a non-JSON error body is still just a failed notice
        pass
    return False, f"http {status_code} {description}".strip()


# ===========================================================================
# Phase 3 — Telegram Stars subscription
# ===========================================================================

def _as_utc(value):
    """A Firestore timestamp (aware datetime) or None. A naive value is read as UTC."""
    if not isinstance(value, datetime):
        return None
    return value if value.tzinfo else value.replace(tzinfo=timezone.utc)


def _ms(value):
    value = _as_utc(value)
    return int(value.timestamp() * 1000) if value is not None else None


def _period_end(expiration, start):
    """End of the period a payment paid for: Telegram's subscription_expiration_date (unix
    seconds) when present, else `start` + PREMIUM_PERIOD; None when neither is known."""
    if _is_int(expiration) and expiration > 0:
        return datetime.fromtimestamp(expiration, tz=timezone.utc)
    return start + PREMIUM_PERIOD if start is not None else None


def _premium_grant_update(prev: dict, period_end: datetime, charge_id: str, *,
                          credits_cap: int, now: datetime) -> dict:
    """Credit-doc fields a Stars payment grants — the ONE copy, used by apply_payment and by
    link-time application of unassigned payments.

    Mirrors the RevenueCat grant path in main._handle_rc_event_payload: is_premium=True,
    ai_credits=premium_daily_credits_cap, premium_activated_at / credits_restored_at /
    updated_at (ISO strings). telegram_premium_until never moves backwards: max(existing,
    period_end), returned in the dict.
    """
    existing = _as_utc(prev.get("telegram_premium_until"))
    until = max(existing, period_end) if existing is not None else period_end
    now_iso = now.isoformat()
    update = {
        "telegram_premium_until": until,
        "is_premium": True,
        "ai_credits": credits_cap,
        "premium_activated_at": now_iso,
        "credits_restored_at": now_iso,
        "updated_at": now_iso,
    }
    # telegram_subscription.charge_id marks the payment that defines the current period —
    # revoke_payment ends premium only for THAT charge. A late, out-of-order event with an
    # earlier expiration must not take the marker over.
    if existing is None or period_end >= existing:
        update["telegram_subscription"] = {
            "charge_id": charge_id,
            "state": "active",
            "updated_at": now,
        }
    return update


def _premium_grant_log(prev: dict, payment: dict, *, credit_user_id: str, event_type: str,
                       until: datetime, credits_cap: int, now: datetime) -> dict:
    """premium_events_log row for a Stars grant. `payment` carries charge_id, telegram_user_id,
    total_amount, currency, is_recurring, is_first_recurring (a parse_payment result, or a
    stored telegram_payments record plus its charge_id)."""
    prev_state = {"is_premium": prev.get("is_premium", False),
                  "ai_credits": prev.get("ai_credits", 0)}
    new_state = {"is_premium": True, "ai_credits": credits_cap}
    return {
        "user_id": credit_user_id,
        "amplitude_id": prev.get("amplitude_id"),
        "event_type": event_type,
        "telegram_payment_charge_id": payment["charge_id"],
        "telegram_user_id": payment.get("telegram_user_id"),
        "server_timestamp": now.isoformat(),
        "server_timestamp_ms": _ms(now),
        "expiration_at_ms": _ms(until),
        "price": payment.get("total_amount"),
        "currency": payment.get("currency"),
        "is_recurring": payment.get("is_recurring"),
        "is_first_recurring": payment.get("is_first_recurring"),
        "previous_state": prev_state,
        "new_state": new_state,
        "state_changed": prev_state != new_state,
        "source": "telegram_stars",
    }


def telegram_premium_active(user_data, now: datetime) -> bool:
    """True when the credit doc carries telegram_premium_until in the future.

    Tolerates anything that is not a dict (a missing doc, a test double) as "no Telegram premium",
    so callers on the RevenueCat path keep their behaviour for everyone without it.
    """
    if not isinstance(user_data, dict):
        return False
    until = _as_utc(user_data.get("telegram_premium_until"))
    return until is not None and until > now


def price_stars_or_default(value) -> int:
    return value if _is_int(value) and value > 0 else DEFAULT_PRICE_STARS


def is_charge_id(value) -> bool:
    """Telegram's charge id doubles as a document id: refuse what Firestore would reject."""
    return (isinstance(value, str) and 0 < len(value) <= _MAX_CHARGE_ID and "/" not in value
            and value not in (".", "..") and not (value.startswith("__") and value.endswith("__")))


def parse_payment(data: dict) -> tuple[dict | None, str | None]:
    """Validate the worker's successful_payment fields: (payment, None) or (None, error)."""
    if not is_telegram_user_id(data.get("telegram_user_id")):
        return None, "telegram_user_id must be a positive integer"
    charge_id = data.get("telegram_payment_charge_id")
    if not is_charge_id(charge_id):
        return None, "telegram_payment_charge_id is required"
    if data.get("currency") != STARS_CURRENCY:
        return None, "currency must be XTR"
    total = data.get("total_amount")
    if not _is_int(total) or total <= 0:
        return None, "total_amount must be a positive integer"
    expiration = data.get("subscription_expiration_date")
    if expiration is not None and (not _is_int(expiration) or expiration <= 0):
        return None, "subscription_expiration_date must be a unix timestamp"
    payload = data.get("invoice_payload")
    return {
        "telegram_user_id": data["telegram_user_id"],
        "charge_id": charge_id,
        "total_amount": total,
        "currency": STARS_CURRENCY,
        "invoice_payload": payload[:_MAX_INVOICE_PAYLOAD] if isinstance(payload, str) else None,
        "is_recurring": data.get("is_recurring") is True,
        "is_first_recurring": data.get("is_first_recurring") is True,
        "subscription_expiration_date": expiration,
    }, None


def _read_linked_credit(db, transaction, telegram_user_id: int):
    """(link dict | None, credit ref | None, credit snapshot | None) for the CURRENT link."""
    link_snap = db.collection(LINKS_COLLECTION).document(str(telegram_user_id)).get(
        transaction=transaction)
    if not link_snap.exists:
        return None, None, None
    link = link_snap.to_dict() or {}
    cid = link.get("credit_user_id")
    if not isinstance(cid, str) or not _DOC_ID_RE.match(cid):
        return link, None, None
    credit_ref = db.collection(USERS_COLLECTION).document(cid)
    return link, credit_ref, credit_ref.get(transaction=transaction)


def _payment_outcome(record: dict, duplicate: bool) -> Outcome:
    status_value = record.get("status")
    return Outcome(200, {
        "applied": status_value == PAYMENT_APPLIED,
        "status": status_value,
        "telegram_premium_until_ms": _ms(record.get("telegram_premium_until")),
        "duplicate": duplicate,
    })


def apply_payment(db, now: datetime, payment: dict, *, credits_cap: int) -> Outcome:
    """Record a successful Stars payment and grant premium — idempotent by charge id.

    One transaction: the payment doc is created with transaction.create(), so a replayed
    successful_payment (Telegram retries, worker retries) finds it and gets the STORED outcome
    back without applying anything twice. The grant is _premium_grant_update (mirrors the
    RevenueCat grant path in main._handle_rc_event_payload).
    No current link (or no credit doc) → recorded as `unassigned`, nothing granted; the next
    link of this Telegram account applies it while its period is still running.
    """
    pay_ref = db.collection(PAYMENTS_COLLECTION).document(payment["charge_id"])
    log_ref = db.collection(PREMIUM_EVENTS_COLLECTION).document()

    @transactional
    def txn(transaction):
        pay_snap = pay_ref.get(transaction=transaction)
        if pay_snap.exists:
            return _payment_outcome(pay_snap.to_dict() or {}, duplicate=True)
        link, credit_ref, credit_snap = _read_linked_credit(
            db, transaction, payment["telegram_user_id"])

        record = {
            "telegram_user_id": payment["telegram_user_id"],
            "credit_user_id": None,
            "google_uid": None,
            "total_amount": payment["total_amount"],
            "currency": payment["currency"],
            "invoice_payload": payment["invoice_payload"],
            "is_recurring": payment["is_recurring"],
            "is_first_recurring": payment["is_first_recurring"],
            "subscription_expiration_date": payment["subscription_expiration_date"],
            "created_at": now,
        }
        if credit_snap is None or not credit_snap.exists:
            record["status"] = PAYMENT_UNASSIGNED
            transaction.create(pay_ref, record)
            return _payment_outcome(record, duplicate=False)

        prev = credit_snap.to_dict() or {}
        update = _premium_grant_update(
            prev, _period_end(payment["subscription_expiration_date"], now), payment["charge_id"],
            credits_cap=credits_cap, now=now)
        until = update["telegram_premium_until"]

        record.update({
            "status": PAYMENT_APPLIED,
            "credit_user_id": credit_ref.id,
            "google_uid": link.get("google_uid"),
            "telegram_premium_until": until,
        })
        transaction.create(pay_ref, record)
        transaction.update(credit_ref, update)
        transaction.set(log_ref, _premium_grant_log(
            prev, payment, credit_user_id=credit_ref.id, event_type="telegram_stars_payment",
            until=until, credits_cap=credits_cap, now=now))
        return _payment_outcome(record, duplicate=False)

    return txn(db.transaction())


def subscription_update(db, now: datetime, telegram_user_id: int, state: str,
                        charge_id: str | None = None) -> Outcome:
    """Record the subscription state the bot learnt about. Never changes premium itself:
    a canceled / failed subscription stays paid until telegram_premium_until, and expiry is
    picked up by the daily refill job (verify_premium → NOT_VERIFIED)."""

    @transactional
    def txn(transaction):
        _link, credit_ref, credit_snap = _read_linked_credit(db, transaction, telegram_user_id)
        if credit_snap is None or not credit_snap.exists:
            return Outcome(200, {"updated": False, "subscription_state": None})
        prev = credit_snap.to_dict() or {}
        sub = prev.get("telegram_subscription")
        sub = dict(sub) if isinstance(sub, dict) else {}
        sub.update({"state": state, "updated_at": now})
        if charge_id:
            sub["charge_id"] = charge_id
        transaction.update(credit_ref, {"telegram_subscription": sub})
        return Outcome(200, {"updated": True, "subscription_state": state})

    return txn(db.transaction())


def revoke_payment(db, now: datetime, charge_id: str, *, store_premium) -> Outcome:
    """After refundStarPayment: mark the payment refunded and end the premium it paid for.

    Kept deliberately simple: when this charge is the LATEST one on the credit doc
    (telegram_subscription.charge_id), Telegram premium ends now; an older charge leaves the
    newer payment's period alone. is_premium is then recomputed as RevenueCat OR Telegram active —
    left unchanged when RevenueCat is unreachable and Telegram is no longer active, so a store
    subscriber is never demoted on a network blip. Idempotent: a second revoke changes nothing.
    """
    pay_ref = db.collection(PAYMENTS_COLLECTION).document(charge_id)
    pre = pay_ref.get()
    if not pre.exists:
        return _error(404, ERR_UNKNOWN_PAYMENT)
    cid = (pre.to_dict() or {}).get("credit_user_id")
    has_credit = isinstance(cid, str) and bool(_DOC_ID_RE.match(cid))
    # The RevenueCat check is network I/O: done once, before the transaction (which may retry).
    rc = store_premium(cid) if has_credit else None
    log_ref = db.collection(PREMIUM_EVENTS_COLLECTION).document()

    @transactional
    def txn(transaction):
        pay_snap = pay_ref.get(transaction=transaction)
        if not pay_snap.exists:
            return _error(404, ERR_UNKNOWN_PAYMENT)
        record = pay_snap.to_dict() or {}
        credit_ref = db.collection(USERS_COLLECTION).document(cid) if has_credit else None
        credit_snap = credit_ref.get(transaction=transaction) if credit_ref is not None else None
        if record.get("status") == PAYMENT_REFUNDED:
            prev = (credit_snap.to_dict() or {}) if credit_snap is not None and credit_snap.exists else {}
            return Outcome(200, {
                "revoked": True, "status": PAYMENT_REFUNDED, "duplicate": True,
                "is_premium": bool(prev.get("is_premium", False)),
                "telegram_premium_until_ms": _ms(prev.get("telegram_premium_until")),
            })

        transaction.update(pay_ref, {"status": PAYMENT_REFUNDED, "refunded_at": now})
        if credit_snap is None or not credit_snap.exists:
            return Outcome(200, {"revoked": True, "status": PAYMENT_REFUNDED, "duplicate": False,
                                 "is_premium": False, "telegram_premium_until_ms": None})

        prev = credit_snap.to_dict() or {}
        until = _as_utc(prev.get("telegram_premium_until"))
        update = {}
        sub = prev.get("telegram_subscription")
        if isinstance(sub, dict) and sub.get("charge_id") == charge_id:
            if until is not None and until > now:
                until = now
                update["telegram_premium_until"] = now
            update["telegram_subscription"] = {**sub, "state": "canceled", "updated_at": now}
        telegram_active = until is not None and until > now
        prev_premium = bool(prev.get("is_premium", False))
        if rc is True or telegram_active:
            is_premium = True
        elif rc is False:
            is_premium = False
        else:
            is_premium = prev_premium  # RevenueCat unreachable: do not demote on a blip
        now_iso = now.isoformat()
        if is_premium != prev_premium:
            update["is_premium"] = is_premium
            if not is_premium:
                update["premium_expired_at"] = now_iso
        if update:
            update["updated_at"] = now_iso
            transaction.update(credit_ref, update)
        prev_state = {"is_premium": prev_premium, "ai_credits": prev.get("ai_credits", 0)}
        new_state = {"is_premium": is_premium, "ai_credits": prev.get("ai_credits", 0)}
        transaction.set(log_ref, {
            "user_id": credit_ref.id,
            "amplitude_id": prev.get("amplitude_id"),
            "event_type": "telegram_stars_refund",
            "telegram_payment_charge_id": charge_id,
            "server_timestamp": now_iso,
            "server_timestamp_ms": _ms(now),
            "store_premium_verified": rc,
            "previous_state": prev_state,
            "new_state": new_state,
            "state_changed": prev_state != new_state,
            "source": "telegram_stars",
        })
        return Outcome(200, {
            "revoked": True, "status": PAYMENT_REFUNDED, "duplicate": False,
            "is_premium": is_premium, "telegram_premium_until_ms": _ms(until),
        })

    return txn(db.transaction())


def premium_status(db, now: datetime, telegram_user_id: int, *, store_premium,
                   price_stars: int) -> Outcome:
    """What /premium shows. `source`: "store" when RevenueCat pays for premium (so the bot never
    sells a second subscription), "telegram" when only Telegram premium is active, else None."""
    link_snap = db.collection(LINKS_COLLECTION).document(str(telegram_user_id)).get()
    link = link_snap.to_dict() if link_snap.exists else None
    cid = (link or {}).get("credit_user_id")
    credit_snap = None
    if isinstance(cid, str) and _DOC_ID_RE.match(cid):
        credit_snap = db.collection(USERS_COLLECTION).document(cid).get()
    if credit_snap is None or not credit_snap.exists:
        return Outcome(200, {
            "linked": False, "is_premium": False, "source": None,
            "telegram_premium_until_ms": None, "subscription_state": None,
            "price_stars": price_stars,
        })
    prev = credit_snap.to_dict() or {}
    telegram_active = telegram_premium_active(prev, now)
    rc = store_premium(cid)
    # RevenueCat unreachable: a premium doc that Telegram does not explain is a store subscriber.
    store = rc is True or (rc is None and bool(prev.get("is_premium")) and not telegram_active)
    sub = prev.get("telegram_subscription")
    return Outcome(200, {
        "linked": True,
        "is_premium": store or telegram_active,
        "source": "store" if store else ("telegram" if telegram_active else None),
        "telegram_premium_until_ms": _ms(prev.get("telegram_premium_until")),
        "subscription_state": sub.get("state") if isinstance(sub, dict) else None,
        "price_stars": price_stars,
    })
