"""Telegram reminders (phase 4) — the PURE half: recurrence and the index entries of one checklist.

No Firestore, no clock, no network: every function takes the user's zone and `now` as
parameters, so the whole schedule is unit-testable. telegram_reminders.py is the Firestore half
(triggers, the claim transaction, snooze).

Index entry — telegram_reminders/{rid}, rid = make_rid(google_uid, cloud_id, item_id, slot):

    slot              "main" (from the checklist doc) | "snooze" (created by snooze_reminder)
    kind              "oneshot" | "repeat" | "snooze"
    google_uid, telegram_user_id, chat_id, language_code      the link, refreshed on recompute
    checklist_cloud_id, checklist_owner_uid, item_id (None = checklist-level)
    checklist_name, item_text                                  display snapshot
    timezone          IANA id the occurrences were computed in
    next_fire_at      min(carry_at, oneshot_at, repeat.next_at); None = nothing pending
    oneshot_at        pending one-shot `reminderAt`
    carry_at          an occurrence that was already due when a recompute ran (see below)
    repeat            {src, rule, anchor_ms, time_of_day_minutes, count, next_at, ended}
    last_fired_at     the occurrence the last claim consumed — never moves backwards
    source_updated_at the checklist doc's `updatedAt`; updated_at — write time

At most once, never lost:
  * A claim consumes every slot that is due and stores last_fired_at. A recompute only picks
    occurrences strictly after max(now - GRACE, last_fired_at), so an occurrence that was sent is
    never re-created, and a checklist write in the minute between the fire time and the next cron
    tick does not skip one.
  * An entry with nothing pending is KEPT as a tombstone (next_fire_at None) while it still guards
    something: a repeat config (its count / ended state survives recomputes — the web app never
    advances `repeatNextAt`, so the server's own progress is the only record), or a fire within
    TOMBSTONE_KEEP (it guards the GRACE window and lets "In 1 hour" snooze a delivered reminder).
    Deleting it instead would let the next recompute rebuild a just-sent one-shot that the web app
    never clears, and send it again.
  * carry_at: the phone clears a one-shot / advances `repeatNextAt` the moment its alarm fires and
    syncs that within seconds. If that write lands before the cron claimed the due occurrence, the
    rebuilt schedule no longer contains it — so an occurrence that was due (within STALE_AFTER) and
    not yet claimed is carried over. Only while its target is still remindable: an item that was
    checked, deleted or set to "no alarm" in that minute is finished, and is not carried.

Recurrence — a port of ReminderRepeatRule.computeNextOccurrence (feature/checklist commonMain,
ReminderRepeatRule.kt:62-116, findNextWeekday :214). Intended divergences, each pinned by a test:
  1. Wall-clock occurrences in the user's zone: every step moves the LOCAL date and localises it
     at `repeatTimeOfDayMinutes`. Kotlin's catch-up (skipToFuture, :249-273) adds fixed 24 h / 7 d
     of millis, so after a DST change its catch-up lands an hour off (09:00 becomes 10:00 or
     08:00 local) and keeps that time from then on.
  2. The time of day is `repeatTimeOfDayMinutes`, not the local time of `repeatNextAt` (they
     differ once Kotlin's catch-up drifted); the anchor's local time is only a fallback.
  3. The zone is the profile's `timezone`, not the device default.
  4. A non-existent local time (spring-forward gap) moves forward by the gap and an ambiguous one
     (fall-back) takes the earlier offset — what kotlinx-datetime's toInstant does as well.
  5. interval < 1 (Kotlin throws in skipToFuture), interval > MAX_INTERVAL, an unknown type or
     end condition, or a weekDays value outside 1..7 makes the rule unreadable → no repeat entry;
     out-of-range weekDays values are dropped (Kotlin would compute a wrong weekday from them).
  6. Every loop is bounded (MAX_DATE_STEPS); Kotlin's catch-up gives up after 120/100/400 steps.
Kept on purpose (same as the phone): monthly / yearly clamping is path-dependent (Jan 31 → Feb 28
→ Mar 28; Feb 29 → Feb 28 → Feb 28), the scheduled anchor fires without an end-condition check,
and after_count counts fires — a catch-up over missed occurrences counts as ONE (the phone's
RecoverRecurringRemindersUseCase does the same).
"""

import calendar
import hashlib
import json
from dataclasses import dataclass
from datetime import date, datetime, timedelta, timezone
from zoneinfo import ZoneInfo

GRACE = timedelta(minutes=5)
STALE_AFTER = timedelta(hours=6)
TOMBSTONE_KEEP = timedelta(days=7)

SLOT_MAIN = "main"
SLOT_SNOOZE = "snooze"
KIND_ONESHOT = "oneshot"
KIND_REPEAT = "repeat"
KIND_SNOOZE = "snooze"

REPEAT_TYPES = ("daily", "weekly", "monthly", "yearly")
END_NEVER = "never"
END_UNTIL = "until_date"
END_AFTER = "after_count"

MAX_INTERVAL = 1000
MAX_DATE_STEPS = 20000
MAX_TARGETS = 240  # one transaction writes <= 500 docs; items per checklist are capped at 100
MAX_NAME = 256
MAX_ITEM_TEXT = 1000

_EPOCH = datetime(1970, 1, 1, tzinfo=timezone.utc)
_MAX_MS = 253402300799999  # 9999-12-31T23:59:59.999Z
_MS = timedelta(milliseconds=1)
_GRACE_MS = GRACE // _MS
_STALE_MS = STALE_AFTER // _MS
_KEEP_MS = TOMBSTONE_KEEP // _MS
_DAY = timedelta(days=1)

# Fields that change on every checklist write without changing what the entry does; a recompute
# that differs only in these does not rewrite the entry.
_COMPARE_IGNORE = ("updated_at", "source_updated_at")


# ---------------------------------------------------------------------------
# Small conversions
# ---------------------------------------------------------------------------

def _is_int(value) -> bool:
    return isinstance(value, int) and not isinstance(value, bool)


def ms_value(value):
    """Epoch millis from an int / integral float (the JS SDK may store a double), else None."""
    if isinstance(value, float) and value.is_integer():
        value = int(value)
    if _is_int(value) and 0 <= value <= _MAX_MS:
        return value
    return None


def to_ms(value):
    """Epoch millis from a datetime (a Firestore timestamp) or a number; None otherwise."""
    if isinstance(value, datetime):
        if value.tzinfo is None:
            value = value.replace(tzinfo=timezone.utc)
        return (value - _EPOCH) // _MS
    return ms_value(value)


def from_ms(ms):
    return None if ms is None else _EPOCH + timedelta(milliseconds=ms)


def parse_zone(name):
    """A ZoneInfo for a valid IANA id, else None (also for '../x', '' and non-strings)."""
    if not isinstance(name, str) or not name or len(name) > 64:
        return None
    try:
        return ZoneInfo(name)
    except Exception:  # noqa: BLE001 — ZoneInfoNotFoundError, ValueError, OSError on odd keys
        return None


def _local(ms, zone) -> datetime:
    return from_ms(ms).astimezone(zone)


def _localize_ms(day: date, minutes: int, zone) -> int:
    # fold=0: a gap time resolves with the pre-transition offset (= moved forward by the gap),
    # an ambiguous time with the earlier offset — PEP 495, and what kotlinx toInstant does.
    local = datetime(day.year, day.month, day.day, minutes // 60, minutes % 60, tzinfo=zone)
    return (local - _EPOCH) // _MS


def make_rid(google_uid: str, cloud_id: str, item_id, slot: str) -> str:
    raw = f"{google_uid}|{cloud_id}|{item_id if item_id else '-'}|{slot}"
    return hashlib.sha256(raw.encode("utf-8")).hexdigest()[:24]


# ---------------------------------------------------------------------------
# Rule
# ---------------------------------------------------------------------------

@dataclass(frozen=True)
class Rule:
    type: str
    interval: int = 1
    week_days: tuple = ()
    end: str = END_NEVER
    until_ms: int | None = None
    max_count: int | None = None
    reset_checks: bool = False

    def to_map(self) -> dict:
        """Same shape as the app's JSON, so parse_rule reads a stored rule back."""
        if self.end == END_UNTIL:
            end = {"type": END_UNTIL, "dateMillis": self.until_ms}
        elif self.end == END_AFTER:
            end = {"type": END_AFTER, "maxCount": self.max_count}
        else:
            end = {"type": END_NEVER}
        return {
            "type": self.type,
            "interval": self.interval,
            "weekDays": list(self.week_days) or None,
            "endCondition": end,
            "resetChecks": self.reset_checks,
        }


def _int_like(value):
    if isinstance(value, float) and value.is_integer():
        return int(value)
    return value if _is_int(value) else None


def parse_rule(raw):
    """A Rule from the app's JSON: a dict (item-level) or a JSON string (checklist-level).

    kotlinx omits default fields (encodeDefaults=false): interval 1, endCondition never,
    resetChecks false, weekDays null. Returns None when the app itself could not have produced or
    used the rule (unknown type / end condition, interval < 1 — Kotlin throws on it).
    """
    if isinstance(raw, str):
        raw = raw.strip()
        if not raw:
            return None
        try:
            raw = json.loads(raw)
        except ValueError:
            return None
    if not isinstance(raw, dict) or raw.get("type") not in REPEAT_TYPES:
        return None

    interval = raw.get("interval", 1)
    interval = 1 if interval is None else _int_like(interval)
    if interval is None or interval < 1 or interval > MAX_INTERVAL:
        return None

    week_days = ()
    days = raw.get("weekDays")
    if isinstance(days, list):
        valid = {d for d in (_int_like(x) for x in days) if d is not None and 1 <= d <= 7}
        week_days = tuple(sorted(valid))

    end_raw = raw.get("endCondition")
    end, until_ms, max_count = END_NEVER, None, None
    if end_raw is not None:
        if not isinstance(end_raw, dict):
            return None
        end_type = end_raw.get("type")
        if end_type == END_UNTIL:
            until_ms = _int_like(end_raw.get("dateMillis"))
            if until_ms is None:
                return None
            end = END_UNTIL
        elif end_type == END_AFTER:
            max_count = _int_like(end_raw.get("maxCount"))
            if max_count is None:
                return None
            end = END_AFTER
        elif end_type != END_NEVER:
            return None

    return Rule(raw["type"], interval, week_days, end, until_ms, max_count,
                raw.get("resetChecks") is True)


# ---------------------------------------------------------------------------
# Recurrence (local dates)
# ---------------------------------------------------------------------------

def _add_months(day: date, months: int) -> date:
    index = day.month - 1 + months
    year, month = day.year + index // 12, index % 12 + 1
    return date(year, month, min(day.day, calendar.monthrange(year, month)[1]))


def next_weekday(from_day: date, week_days: tuple, interval_weeks: int) -> date:
    """findNextWeekday (ReminderRepeatRule.kt:214): the next selected ISO weekday after
    `from_day` in the same week, else the first selected day `interval_weeks` weeks on."""
    current = from_day.isoweekday()
    for day in week_days:
        if day > current:
            return from_day + timedelta(days=day - current)
    next_week_monday = from_day + timedelta(days=8 - current + (interval_weeks - 1) * 7)
    return next_week_monday + timedelta(days=week_days[0] - 1)


def _step(day: date, rule: Rule) -> date:
    if rule.type == "daily":
        return day + timedelta(days=rule.interval)
    if rule.type == "weekly":
        if rule.week_days:
            return next_weekday(day, rule.week_days, rule.interval)
        return day + timedelta(days=7 * rule.interval)
    if rule.type == "monthly":
        return _add_months(day, rule.interval)
    return _add_months(day, 12 * rule.interval)


def _jump(rule: Rule, anchor: date, target: date) -> tuple[int, date]:
    """A chain position (index, date) at least a day before `target` — or the anchor.

    Daily and plain weekly are arithmetic; weekday-weekly skips whole cycles (after the anchor's
    own week every cycle is the same n days, `interval` weeks apart); monthly / yearly walk from
    the anchor because clamping is path-dependent.
    """
    gap = (target - anchor).days
    if gap <= 2:
        return 0, anchor
    if rule.type == "daily" or (rule.type == "weekly" and not rule.week_days):
        period = rule.interval * (1 if rule.type == "daily" else 7)
        k = (gap - 2) // period
        return k, anchor + timedelta(days=k * period)
    if rule.type == "weekly":
        days = rule.week_days
        weekday = anchor.isoweekday()
        rest_of_first_week = len([d for d in days if d > weekday])
        monday = anchor - timedelta(days=weekday - 1)
        cycle_days = 7 * rule.interval
        cycles = (target - monday).days // cycle_days - 1
        if cycles < 1:
            return 0, anchor
        k = rest_of_first_week + 1 + (cycles - 1) * len(days)
        return k, monday + timedelta(days=cycles * cycle_days + days[0] - 1)
    return 0, anchor


def first_occurrence_after(rule: Rule, time_of_day_minutes: int, anchor_ms: int, zone,
                           after_ms: int):
    """(occurrence_ms, index) of the first occurrence strictly after `after_ms`; None when the
    chain ends first (until_date, date overflow, step bound).

    Index 0 is the anchor — the occurrence the phone has armed — and is exempt from the end
    condition like on the phone; every later one must be <= until_date.
    """
    anchor = _local(anchor_ms, zone).date()
    target = _local(after_ms, zone).date()
    until_day = _local(rule.until_ms, zone).date() if rule.until_ms is not None else None
    try:
        k, day = _jump(rule, anchor, target) if target > anchor else (0, anchor)
        for _ in range(MAX_DATE_STEPS):
            if k >= 1 and until_day is not None and day > until_day + _DAY:
                return None
            if day >= target - _DAY:
                occurrence = _localize_ms(day, time_of_day_minutes, zone)
                if occurrence > after_ms:
                    if k >= 1 and rule.until_ms is not None and occurrence > rule.until_ms:
                        return None
                    return occurrence, k
            day = _step(day, rule)
            k += 1
    except (OverflowError, ValueError):
        return None
    return None


def next_after_fire(rule: Rule, time_of_day_minutes: int, anchor_ms: int, count: int,
                    fired_ms: int, now_ms: int, zone):
    """computeNextOccurrence: the occurrence after the one that just fired, skipping everything
    up to `now`; None when the end condition is reached. `count` = fires before this one."""
    if rule.end == END_AFTER and count + 1 >= rule.max_count:
        return None
    found = first_occurrence_after(rule, time_of_day_minutes, anchor_ms, zone,
                                   max(now_ms, fired_ms))
    return found[0] if found else None


def _valid_minutes(value):
    value = _int_like(value)
    return value if value is not None and 0 <= value < 24 * 60 else None


def _anchor_minutes(anchor_ms: int, zone) -> int:
    local = _local(anchor_ms, zone)
    return local.hour * 60 + local.minute


# ---------------------------------------------------------------------------
# Targets of a checklist doc
# ---------------------------------------------------------------------------

@dataclass(frozen=True)
class Target:
    item_id: str | None
    text: str | None
    oneshot_ms: int | None
    rule: Rule | None
    anchor_ms: int | None
    tod_raw: int | None
    count: int


def default_fill_items(doc: dict) -> list:
    """Items of the default fill (isDefault, else the first one); deleted fills are ignored."""
    fills = doc.get("fills")
    if not isinstance(fills, list):
        return []
    live = [f for f in fills if isinstance(f, dict) and f.get("isDeleted") is not True]
    fill = next((f for f in live if f.get("isDefault") is True), live[0] if live else None)
    if fill is None or not isinstance(fill.get("itemsJson"), str):
        return []
    try:
        items = json.loads(fill["itemsJson"])
    except ValueError:
        return []
    return [i for i in items if isinstance(i, dict)] if isinstance(items, list) else []


def _has_config(holder: dict) -> bool:
    rule = holder.get("repeatRule")
    return ms_value(holder.get("reminderAt")) is not None or bool(rule)


def has_reminder_config(doc) -> bool:
    """Any checklist- or item-level reminder data at all — checked items and disabled alarms
    included, so a change that turns one off still reaches the index."""
    if not isinstance(doc, dict):
        return False
    return _has_config(doc) or any(_has_config(item) for item in default_fill_items(doc))


def _target(holder: dict, item_id, text, warnings: list, where: str):
    raw_rule = holder.get("repeatRule")
    rule = parse_rule(raw_rule) if raw_rule else None
    if raw_rule and rule is None:
        warnings.append(f"{where}: unreadable repeatRule")
    anchor_ms = ms_value(holder.get("repeatNextAt"))
    oneshot_ms = ms_value(holder.get("reminderAt"))
    if rule is None or anchor_ms is None:
        # repeatNextAt == null with a rule = a repeat the phone already ended.
        rule, anchor_ms = None, None
    if oneshot_ms is None and rule is None:
        return None
    count = _int_like(holder.get("repeatOccurrenceCount"))
    return Target(item_id, text, oneshot_ms, rule, anchor_ms,
                  holder.get("repeatTimeOfDayMinutes"), max(count or 0, 0))


def _eligible(item: dict) -> bool:
    """An item that may still be reminded about: not checked, alarm not switched off."""
    return item.get("checked") is not True and item.get("alarmEnabled") is not False


def carry_eligible_item_ids(doc: dict) -> set:
    """Default-fill item ids whose due-but-unclaimed occurrence may still be carried: the item
    is there, not checked, alarm on. Its reminder may be gone — the phone clears it on firing."""
    return {item["id"] for item in default_fill_items(doc)
            if isinstance(item.get("id"), str) and item["id"] and _eligible(item)}


def extract_targets(doc: dict) -> tuple[dict, list]:
    """{item_id or None: Target} for every reminder the doc asks for, plus warnings.

    Item-level: default-fill items with an id, not checked and with alarmEnabled != false
    (false = "a date, no alarm" — never notify; absent = true).
    """
    warnings = []
    targets = {}
    checklist = _target(doc, None, None, warnings, "checklist")
    if checklist is not None:
        targets[None] = checklist
    for item in default_fill_items(doc):
        item_id = item.get("id")
        if not isinstance(item_id, str) or not item_id or item_id in targets:
            continue
        if not _eligible(item):
            continue
        text = item.get("text") if isinstance(item.get("text"), str) else ""
        target = _target(item, item_id, text[:MAX_ITEM_TEXT], warnings, "item")
        if target is None:
            continue
        if len(targets) >= MAX_TARGETS:
            warnings.append("too many reminders in one checklist — the rest are skipped")
            break
        targets[item_id] = target
    return targets, warnings


# ---------------------------------------------------------------------------
# Index entries of one checklist
# ---------------------------------------------------------------------------

@dataclass
class Build:
    writes: dict
    deletes: set
    warnings: list


def _repeat_state(target: Target, prev: dict | None, zone, zone_name: str, threshold: int):
    rule_map = target.rule.to_map()
    src = {"rule": rule_map, "anchor_ms": target.anchor_ms,
           "time_of_day_minutes": target.tod_raw, "count": target.count}
    prev_repeat = prev.get("repeat") if prev else None
    same_src = isinstance(prev_repeat, dict) and prev_repeat.get("src") == src
    if same_src and prev.get("timezone") == zone_name:
        # Nothing about the schedule changed: the server's own progress stands.
        return dict(prev_repeat)

    tod = _valid_minutes(target.tod_raw)
    if tod is None:
        tod = _anchor_minutes(target.anchor_ms, zone)
    found = first_occurrence_after(target.rule, tod, target.anchor_ms, zone, threshold)
    count = target.count
    next_ms, index = found if found is not None else (None, 0)
    if index >= 1:
        # Skipping past the anchor = the phone fired it (or recovered past it): one more fire.
        count = target.count + 1
    if same_src:  # only the zone changed: keep what was already fired / ended on this schedule
        count = max(count, int(prev_repeat.get("count") or 0))
        if prev_repeat.get("ended"):
            next_ms = None
    # The anchor itself fires unconditionally (like on the phone); any later occurrence needs
    # fires-so-far < maxCount — the check computeNextOccurrence makes before stepping.
    if (next_ms is not None and target.rule.end == END_AFTER
            and (index >= 1 or count > target.count) and count >= target.rule.max_count):
        next_ms = None
    return {
        "src": src,
        "rule": rule_map,
        "anchor_ms": target.anchor_ms,
        "time_of_day_minutes": tod,
        "count": count,
        "next_at": from_ms(next_ms),
        "ended": next_ms is None,
    }


def _comparable(entry: dict | None) -> dict | None:
    if entry is None:
        return None
    return {k: v for k, v in entry.items() if k not in _COMPARE_IGNORE}


def _schedule(target: Target | None, prev: dict | None, *, base: dict, zone, zone_name: str,
              now_ms: int, carry_allowed: bool):
    """The new main entry for one target (None = no entry). `prev` = its current entry.

    `carry_allowed` = the target is still there and remindable (see build_entries). A checked,
    deleted or alarm-off item does not get its due occurrence carried — the user finished it.
    """
    last_fired = to_ms(prev.get("last_fired_at")) if prev else None
    threshold = now_ms - _GRACE_MS if last_fired is None else max(now_ms - _GRACE_MS, last_fired)

    oneshot = None
    repeat = None
    if target is not None:
        if target.oneshot_ms is not None and target.oneshot_ms > threshold:
            oneshot = target.oneshot_ms
        if target.rule is not None:
            repeat = _repeat_state(target, prev, zone, zone_name, threshold)

    pending = [t for t in (oneshot, to_ms(repeat.get("next_at")) if repeat else None)
               if t is not None]
    carry = None
    prev_next = to_ms(prev.get("next_fire_at")) if prev else None
    if (carry_allowed and prev_next is not None and now_ms - _STALE_MS <= prev_next <= now_ms
            and prev_next not in pending):
        carry = prev_next
        pending.append(carry)
    next_fire = min(pending) if pending else None

    if next_fire is None and repeat is None:
        if last_fired is None or now_ms - last_fired > _KEEP_MS:
            return None

    if repeat is not None:
        kind = KIND_REPEAT
    elif target is None and prev:
        kind = prev.get("kind") or KIND_ONESHOT
    else:
        kind = KIND_ONESHOT
    item_text = target.text if target is not None else (prev or {}).get("item_text")
    return {
        **base,
        "slot": SLOT_MAIN,
        "kind": kind,
        "item_id": target.item_id if target is not None else prev.get("item_id"),
        "item_text": item_text if (target is None or target.item_id) else None,
        "next_fire_at": from_ms(next_fire),
        "oneshot_at": from_ms(oneshot),
        "carry_at": from_ms(carry),
        "repeat": repeat,
        "last_fired_at": from_ms(last_fired),
    }


def build_entries(doc: dict, *, google_uid: str, owner_uid: str, cloud_id: str, link: dict,
                  zone_name: str, now: datetime, existing: dict) -> Build:
    """The main entries a LIVE checklist doc needs, against its current entries.

    `existing` = {rid: entry} of this checklist (main and snooze). Snooze entries are left alone,
    except tombstones older than TOMBSTONE_KEEP, which are deleted. A deleted doc is the caller's
    case (it deletes every entry of the checklist).
    """
    zone = parse_zone(zone_name)
    if zone is None:
        raise ValueError("build_entries needs a valid zone")
    now_ms = to_ms(now)
    targets, warnings = extract_targets(doc)
    name = doc.get("name") if isinstance(doc.get("name"), str) else ""
    base = {
        "google_uid": google_uid,
        "telegram_user_id": link.get("telegram_user_id"),
        "chat_id": link.get("chat_id"),
        "language_code": link.get("language_code"),
        "checklist_cloud_id": cloud_id,
        "checklist_owner_uid": owner_uid,
        "checklist_name": name[:MAX_NAME],
        "timezone": zone_name,
        "source_updated_at": ms_value(doc.get("updatedAt")),
        "updated_at": now,
    }

    wanted = {make_rid(google_uid, cloud_id, item_id, SLOT_MAIN): t for item_id, t in targets.items()}
    writes, deletes = {}, set()
    for rid, entry in existing.items():
        if entry.get("slot") == SLOT_SNOOZE:
            last = to_ms(entry.get("last_fired_at"))
            if entry.get("next_fire_at") is None and (last is None or now_ms - last > _KEEP_MS):
                deletes.add(rid)
    main_rids = set(wanted) | {rid for rid, e in existing.items() if e.get("slot") != SLOT_SNOOZE}
    eligible_items = carry_eligible_item_ids(doc)
    for rid in sorted(main_rids):
        prev = existing.get(rid)
        target = wanted.get(rid)
        # The doc is live here, so a checklist-level target always qualifies; an item only while
        # it is still in the default fill, unchecked and with its alarm on.
        prev_item = (prev or {}).get("item_id")
        carry_allowed = target is not None or prev_item is None or prev_item in eligible_items
        new = _schedule(target, prev, base=base, zone=zone, zone_name=zone_name,
                        now_ms=now_ms, carry_allowed=carry_allowed)
        if new is None:
            if prev is not None:
                deletes.add(rid)
        elif _comparable(new) != _comparable(prev):
            writes[rid] = new
    return Build(writes, deletes, warnings)


# ---------------------------------------------------------------------------
# Claim and snooze (pure parts)
# ---------------------------------------------------------------------------

def consume_due(entry: dict, now: datetime) -> dict:
    """The entry after a claim at `now`: every slot due by `now` is consumed in this one fire
    (a one-shot and a repeat on the same minute send ONE message), a due repeat advances like
    computeNextOccurrence or ends, and last_fired_at = the occurrence that fired.

    Never deletes: the result with next_fire_at None is the tombstone (see the module doc).
    """
    now_ms = to_ms(now)
    fired_ms = to_ms(entry.get("next_fire_at"))
    carry = to_ms(entry.get("carry_at"))
    oneshot = to_ms(entry.get("oneshot_at"))
    carry = None if carry is not None and carry <= now_ms else carry
    oneshot = None if oneshot is not None and oneshot <= now_ms else oneshot

    repeat = entry.get("repeat")
    repeat = dict(repeat) if isinstance(repeat, dict) else None
    if repeat is not None and not repeat.get("ended"):
        repeat_next = to_ms(repeat.get("next_at"))
        if repeat_next is not None and repeat_next <= now_ms:
            rule = parse_rule(repeat.get("rule"))
            zone = parse_zone(entry.get("timezone"))
            count = int(repeat.get("count") or 0)
            tod = _valid_minutes(repeat.get("time_of_day_minutes"))
            anchor_ms = ms_value(repeat.get("anchor_ms"))
            following = None
            if rule is not None and zone is not None and tod is not None and anchor_ms is not None:
                following = next_after_fire(rule, tod, anchor_ms, count, repeat_next, now_ms, zone)
            repeat.update({"count": count + 1, "next_at": from_ms(following),
                           "ended": following is None})

    pending = [t for t in (carry, oneshot, to_ms(repeat.get("next_at")) if repeat else None)
               if t is not None]
    previous_fire = to_ms(entry.get("last_fired_at"))
    last_fired = fired_ms if previous_fire is None or fired_ms is None else max(fired_ms, previous_fire)
    return {
        **entry,
        "carry_at": from_ms(carry),
        "oneshot_at": from_ms(oneshot),
        "repeat": repeat,
        "next_fire_at": from_ms(min(pending)) if pending else None,
        "last_fired_at": from_ms(last_fired),
        "updated_at": now,
    }


def snooze_entry(source: dict, fire_at: datetime, now: datetime) -> dict:
    """The snooze entry of `source`'s target: same link and display snapshot, one shot at
    `fire_at`. Works from a tombstone too (a one-shot that was already claimed)."""
    keep = ("google_uid", "telegram_user_id", "chat_id", "language_code", "checklist_cloud_id",
            "checklist_owner_uid", "item_id", "checklist_name", "item_text", "timezone")
    return {
        **{k: source.get(k) for k in keep},
        "slot": SLOT_SNOOZE,
        "kind": KIND_SNOOZE,
        "next_fire_at": fire_at,
        "oneshot_at": fire_at,
        "carry_at": None,
        "repeat": None,
        "last_fired_at": None,
        "source_updated_at": None,
        "updated_at": now,
    }


def is_stale(fire_at_ms: int, now: datetime) -> bool:
    """More than STALE_AFTER late — consumed but not delivered (no stale reminders after an outage)."""
    return to_ms(now) - fire_at_ms > _STALE_MS
