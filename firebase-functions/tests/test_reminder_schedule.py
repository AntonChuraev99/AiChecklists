"""reminder_schedule.py — recurrence and index entries for Telegram reminders. OFFLINE, pure.

The first block ports every computeNextOccurrence / findNextWeekday vector of
feature/checklist/src/commonTest/.../ReminderRepeatRuleTest.kt (UTC, like the Kotlin test). The
DST blocks pin the INTENDED divergence from Kotlin's catch-up (fixed 24 h / 7 d of millis): the
server steps local dates and localises them at repeatTimeOfDayMinutes.

Run from the repo root:
    python -m pytest firebase-functions/tests/test_reminder_schedule.py -q -p no:cacheprovider
"""

import json
import os
import sys
from datetime import date, datetime, timedelta, timezone
from zoneinfo import ZoneInfo

import pytest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import reminder_schedule as rs  # noqa: E402

UTC = ZoneInfo("UTC")
BERLIN = ZoneInfo("Europe/Berlin")
NEW_YORK = ZoneInfo("America/New_York")
KOLKATA = ZoneInfo("Asia/Kolkata")


def ms(y, mo, d, h=9, mi=0, zone=UTC):
    return rs.to_ms(datetime(y, mo, d, h, mi, tzinfo=zone))


def utc_ms(y, mo, d, h, mi=0):
    return rs.to_ms(datetime(y, mo, d, h, mi, tzinfo=timezone.utc))


def rule(type_, interval=1, week_days=None, end=None, reset=False):
    raw = {"type": type_, "interval": interval}
    if week_days is not None:
        raw["weekDays"] = sorted(week_days)
    if end is not None:
        raw["endCondition"] = end
    if reset:
        raw["resetChecks"] = True
    parsed = rs.parse_rule(raw)
    assert parsed is not None, raw
    return parsed


def kotlin_next(r, trigger_ms, count, now_ms, zone=UTC):
    """computeNextOccurrence(currentTriggerMillis, currentCount, nowMillis) — the time of day is
    the trigger's local time, as in Kotlin."""
    local = rs.from_ms(trigger_ms).astimezone(zone)
    return rs.next_after_fire(r, local.hour * 60 + local.minute, trigger_ms, count, trigger_ms,
                              now_ms, zone)


UNTIL_0306 = {"type": "until_date", "dateMillis": ms(2026, 3, 6, 23, 59)}
UNTIL_0310 = {"type": "until_date", "dateMillis": ms(2026, 3, 10, 23, 59)}

# (name, rule, trigger, count, now, expected) — ReminderRepeatRuleTest.kt, one row per test.
KOTLIN_VECTORS = [
    ("daily_nextOccurrence_addsOneDay", rule("daily"), ms(2026, 3, 4), 0, None, ms(2026, 3, 5)),
    ("daily_everyThreeDays", rule("daily", 3), ms(2026, 3, 4), 0, None, ms(2026, 3, 7)),
    ("daily_preservesTimeOfDay", rule("daily"), ms(2026, 3, 4, 14, 30), 0, None,
     ms(2026, 3, 5, 14, 30)),
    ("weekly_simpleNoWeekdays_addsOneWeek", rule("weekly"), ms(2026, 3, 4), 0, None,
     ms(2026, 3, 11)),
    ("weekly_everyTwoWeeks", rule("weekly", 2), ms(2026, 3, 4), 0, None, ms(2026, 3, 18)),
    ("weekly_withWeekdays_nextDayInSameWeek", rule("weekly", week_days={3, 5}), ms(2026, 3, 4),
     0, None, ms(2026, 3, 6)),
    ("weekly_withWeekdays_jumpsToNextWeek", rule("weekly", week_days={1, 5}), ms(2026, 3, 6),
     0, None, ms(2026, 3, 9)),
    ("weekly_withWeekdays_everyTwoWeeks_jumpsCorrectly", rule("weekly", 2, {1, 5}),
     ms(2026, 3, 6), 0, None, ms(2026, 3, 16)),
    ("weekly_monWedFri_fromMonday", rule("weekly", week_days={1, 3, 5}), ms(2026, 3, 2), 0,
     None, ms(2026, 3, 4)),
    ("monthly_addsOneMonth", rule("monthly"), ms(2026, 3, 15), 0, None, ms(2026, 4, 15)),
    ("monthly_everyThreeMonths", rule("monthly", 3), ms(2026, 1, 15), 0, None, ms(2026, 4, 15)),
    ("monthly_monthEndClamping_jan31ToFeb28", rule("monthly"), ms(2026, 1, 31), 0, None,
     ms(2026, 2, 28)),
    ("monthly_monthEndClamping_jan31ToFeb29LeapYear", rule("monthly"), ms(2028, 1, 31), 0,
     None, ms(2028, 2, 29)),
    ("monthly_yearBoundary", rule("monthly"), ms(2026, 12, 15), 0, None, ms(2027, 1, 15)),
    ("endCondition_afterCount_continuesBelowMax",
     rule("daily", end={"type": "after_count", "maxCount": 5}), ms(2026, 3, 4), 2, None,
     ms(2026, 3, 5)),
    ("endCondition_untilDate_continuesBeforeEndDate", rule("daily", end=UNTIL_0310),
     ms(2026, 3, 4), 0, None, ms(2026, 3, 5)),
    ("fastForward_daily_deviceOffForThreeDays", rule("daily"), ms(2026, 3, 4), 0,
     ms(2026, 3, 7, 10), ms(2026, 3, 8)),
    ("fastForward_daily_deviceOffForOneYear", rule("daily"), ms(2026, 3, 4), 0,
     ms(2027, 3, 4, 10), ms(2027, 3, 5)),
    ("fastForward_weekly_deviceOffForFourWeeks", rule("weekly"), ms(2026, 3, 4), 0,
     ms(2026, 4, 1, 10), ms(2026, 4, 8)),
    ("fastForward_monthly_deviceOffForSixMonths", rule("monthly"), ms(2026, 1, 15), 0,
     ms(2026, 7, 20, 10), ms(2026, 8, 15)),
    ("fastForward_everyThreeDays_deviceOff", rule("daily", 3), ms(2026, 3, 4), 0,
     ms(2026, 3, 14, 10), ms(2026, 3, 16)),
    ("fastForward_weeklyWithWeekdays_deviceOff", rule("weekly", week_days={1, 5}),
     ms(2026, 3, 2), 0, ms(2026, 3, 19, 10), ms(2026, 3, 20)),
    ("nextOccurrence_alreadyInFuture_noSkip", rule("daily"), ms(2026, 3, 4), 0,
     ms(2026, 3, 4) - 60_000, ms(2026, 3, 5)),
    ("yearly_nextOccurrence_addsOneYear", rule("yearly"), ms(2026, 3, 15), 0, None,
     ms(2027, 3, 15)),
    ("yearly_everyTwoYears", rule("yearly", 2), ms(2026, 6, 1, 10), 0, None, ms(2028, 6, 1, 10)),
    ("yearly_leapYear_feb29ToFeb28", rule("yearly"), ms(2028, 2, 29), 0, None, ms(2029, 2, 28)),
    ("yearly_preservesTimeOfDay", rule("yearly"), ms(2026, 1, 1, 14, 30), 0, None,
     ms(2027, 1, 1, 14, 30)),
    ("yearly_fastForward_deviceOffThreeYears", rule("yearly"), ms(2024, 7, 4), 0,
     ms(2027, 8, 1, 10), ms(2028, 7, 4)),
    ("yearly_fastForward_everyTwoYears", rule("yearly", 2), ms(2024, 1, 1), 0,
     ms(2029, 6, 1, 10), ms(2030, 1, 1)),
]

KOTLIN_NULL_VECTORS = [
    ("endCondition_afterCount_stopsAtMax",
     rule("daily", end={"type": "after_count", "maxCount": 3}), ms(2026, 3, 4), 2, None),
    ("endCondition_afterCount_oneOccurrence",
     rule("daily", end={"type": "after_count", "maxCount": 1}), ms(2026, 3, 4), 0, None),
    ("endCondition_untilDate_stopsWhenPastEndDate", rule("daily", 5, end=UNTIL_0306),
     ms(2026, 3, 4), 0, None),
    ("nextOccurrence_fastForwardWithEndCondition_untilDateReached",
     rule("daily", end=UNTIL_0310), ms(2026, 3, 4), 0, ms(2026, 3, 14, 10)),
    ("nextOccurrence_fastForwardMonthly_withEndDate",
     rule("monthly", end={"type": "until_date", "dateMillis": ms(2026, 6, 1, 0)}),
     ms(2026, 1, 15), 0, ms(2026, 7, 20, 10)),
    ("yearly_afterCount_reachesLimit",
     rule("yearly", end={"type": "after_count", "maxCount": 3}), ms(2026, 1, 1), 2, None),
    ("yearly_untilDate_stopsWhenPast",
     rule("yearly", end={"type": "until_date", "dateMillis": ms(2028, 1, 1, 0)}),
     ms(2027, 6, 15), 0, None),
]


@pytest.mark.parametrize("name,r,trigger,count,now,expected", KOTLIN_VECTORS,
                         ids=[v[0] for v in KOTLIN_VECTORS])
def test_kotlin_vectors(name, r, trigger, count, now, expected):
    now = trigger + 1000 if now is None else now
    assert kotlin_next(r, trigger, count, now) == expected


@pytest.mark.parametrize("name,r,trigger,count,now", KOTLIN_NULL_VECTORS,
                         ids=[v[0] for v in KOTLIN_NULL_VECTORS])
def test_kotlin_end_condition_vectors(name, r, trigger, count, now):
    now = trigger + 1000 if now is None else now
    assert kotlin_next(r, trigger, count, now) is None


def test_end_condition_never_always_continues():
    assert kotlin_next(rule("daily", end={"type": "never"}), ms(2026, 3, 4), 100,
                       ms(2026, 3, 4) + 1000) is not None


@pytest.mark.parametrize("from_day,days,interval,expected", [
    (date(2026, 3, 3), (4,), 1, date(2026, 3, 5)),      # findNextWeekday_sameWeek_nextDay
    (date(2026, 3, 6), (1,), 1, date(2026, 3, 9)),      # findNextWeekday_wrapsToNextWeek
    (date(2026, 3, 8), (1, 3), 1, date(2026, 3, 9)),    # findNextWeekday_sundayWraps
    (date(2026, 3, 6), (1,), 2, date(2026, 3, 16)),     # findNextWeekday_withIntervalWeeks
])
def test_find_next_weekday_vectors(from_day, days, interval, expected):
    assert rs.next_weekday(from_day, days, interval) == expected


# ---------------------------------------------------------------------------
# Rule parsing (the app's JSON, kotlinx defaults omitted)
# ---------------------------------------------------------------------------

def test_parse_rule_reads_the_app_json_and_its_omitted_defaults():
    future = ('{"type":"daily","interval":1,"endCondition":{"type":"never"},'
              '"resetChecks":false,"newFutureField":"value"}')
    assert rs.parse_rule(future) == rs.Rule("daily")
    assert rs.parse_rule('{"type":"daily"}') == rs.Rule("daily")  # interval / end omitted
    weekly = rs.parse_rule({"type": "weekly", "weekDays": [5, 1, 3, 3]})
    assert weekly.week_days == (1, 3, 5)
    until = rs.parse_rule('{"type":"monthly","endCondition":{"type":"until_date","dateMillis":5}}')
    assert until.end == "until_date" and until.until_ms == 5
    after = rs.parse_rule({"type": "yearly", "interval": 2, "resetChecks": True,
                           "endCondition": {"type": "after_count", "maxCount": 5}})
    assert after == rs.Rule("yearly", 2, (), "after_count", None, 5, True)
    assert rs.parse_rule(after.to_map()) == after  # a stored rule reads back


@pytest.mark.parametrize("raw", [
    '{"type": "unknown_type"}',                                   # corruptedJson_doesNotCrash
    {"type": "daily", "interval": 0},                             # Kotlin throws on < 1
    {"type": "daily", "interval": -2},
    {"type": "daily", "interval": "2"},
    {"type": "daily", "interval": rs.MAX_INTERVAL + 1},
    {"type": "daily", "endCondition": {"type": "until_date"}},    # no dateMillis
    {"type": "daily", "endCondition": {"type": "after_count"}},
    {"type": "daily", "endCondition": {"type": "sometimes"}},
    "not json", "", None, 5,
])
def test_parse_rule_rejects_what_the_app_cannot_use(raw):
    assert rs.parse_rule(raw) is None


def test_out_of_range_weekdays_are_dropped():
    assert rs.parse_rule({"type": "weekly", "weekDays": [0, 8, 3]}).week_days == (3,)
    assert rs.parse_rule({"type": "weekly", "weekDays": [9]}).week_days == ()  # plain weekly


# ---------------------------------------------------------------------------
# Intended divergences: DST, time of day, zones
# ---------------------------------------------------------------------------

def test_dst_spring_forward_catch_up_keeps_the_wall_clock_berlin():
    # Daily 09:00, anchor Fri 2026-03-27 09:00 CET; offline over the 03-29 switch until Mon 09:30.
    anchor = ms(2026, 3, 27, 9, 0, BERLIN)
    now = ms(2026, 3, 30, 9, 30, BERLIN)
    found = rs.first_occurrence_after(rule("daily"), 9 * 60, anchor, BERLIN, now)
    assert found[0] == ms(2026, 3, 31, 9, 0, BERLIN) == utc_ms(2026, 3, 31, 7)
    # Kotlin's catch-up adds 2 x 24 h to 03-28 08:00Z and answers 03-30 08:00Z = 10:00 CEST.
    assert found[0] != utc_ms(2026, 3, 30, 8)


def test_dst_fall_back_catch_up_keeps_the_wall_clock_new_york():
    anchor = ms(2026, 10, 30, 9, 0, NEW_YORK)        # 13:00Z (EDT)
    now = ms(2026, 11, 2, 9, 30, NEW_YORK)           # 14:30Z (EST)
    found = rs.first_occurrence_after(rule("daily"), 9 * 60, anchor, NEW_YORK, now)
    assert found[0] == ms(2026, 11, 3, 9, 0, NEW_YORK) == utc_ms(2026, 11, 3, 14)
    # Kotlin: 10-31 13:00Z + 3 x 24 h = 11-03 13:00Z = 08:00 EST.
    assert found[0] != utc_ms(2026, 11, 3, 13)


def test_single_step_over_dst_moves_the_local_date():
    nxt = kotlin_next(rule("daily"), ms(2026, 3, 28, 9, 0, BERLIN), 0,
                      ms(2026, 3, 28, 9, 1, BERLIN), BERLIN)
    assert nxt == utc_ms(2026, 3, 29, 7)  # 09:00 CEST, not 08:00Z


def test_weekly_weekdays_across_dst_berlin():
    nxt = kotlin_next(rule("weekly", week_days={1, 5}), ms(2026, 3, 27, 9, 0, BERLIN), 0,
                      ms(2026, 3, 27, 9, 1, BERLIN), BERLIN)
    assert nxt == ms(2026, 3, 30, 9, 0, BERLIN) == utc_ms(2026, 3, 30, 7)


def test_nonexistent_local_time_moves_forward_by_the_gap():
    berlin = kotlin_next(rule("daily"), ms(2026, 3, 28, 2, 30, BERLIN), 0,
                         ms(2026, 3, 28, 3, 0, BERLIN), BERLIN)
    assert berlin == utc_ms(2026, 3, 29, 1, 30)  # 02:30 does not exist → 03:30 CEST
    ny = kotlin_next(rule("daily"), ms(2026, 3, 7, 2, 30, NEW_YORK), 0,
                     ms(2026, 3, 7, 3, 0, NEW_YORK), NEW_YORK)
    assert ny == utc_ms(2026, 3, 8, 7, 30)  # → 03:30 EDT


def test_ambiguous_local_time_fires_once_at_the_earlier_offset():
    r = rule("daily")
    first = kotlin_next(r, ms(2026, 10, 31, 1, 30, NEW_YORK), 0,
                        ms(2026, 10, 31, 1, 31, NEW_YORK), NEW_YORK)
    assert first == utc_ms(2026, 11, 1, 5, 30)   # 01:30 EDT, the first of the two 01:30s
    second = rs.next_after_fire(r, 90, ms(2026, 10, 31, 1, 30, NEW_YORK), 1, first, first, NEW_YORK)
    assert second == utc_ms(2026, 11, 2, 6, 30)  # next day 01:30 EST — never 06:30Z on 11-01


def test_kolkata_half_hour_offset():
    found = rs.first_occurrence_after(rule("daily"), 9 * 60, ms(2026, 11, 20, 9, 0, KOLKATA),
                                      KOLKATA, ms(2026, 11, 20, 9, 1, KOLKATA))
    assert found == (utc_ms(2026, 11, 21, 3, 30), 1)


def test_time_of_day_comes_from_repeat_time_not_the_drifted_anchor():
    # Anchor drifted to 10:00 by the phone's DST catch-up; repeatTimeOfDayMinutes still says 09:00.
    drifted = ms(2026, 4, 1, 10, 0, BERLIN)
    found = rs.first_occurrence_after(rule("daily"), 9 * 60, drifted, BERLIN, drifted)
    assert found[0] == ms(2026, 4, 2, 9, 0, BERLIN)


def test_monthly_clamping_is_path_dependent_like_the_phone():
    anchor = ms(2026, 1, 31)
    # Jan 31 → Feb 28 → Mar 28 → Apr 28 (Kotlin chains from each fired date).
    assert rs.first_occurrence_after(rule("monthly"), 540, anchor, UTC, ms(2026, 3, 1))[0] == \
        ms(2026, 3, 28)
    assert rs.first_occurrence_after(rule("monthly"), 540, anchor, UTC, ms(2026, 4, 1))[0] == \
        ms(2026, 4, 28)


def test_yearly_feb_29_stays_on_feb_28_after_the_first_clamp():
    found = rs.first_occurrence_after(rule("yearly"), 540, ms(2028, 2, 29), UTC, ms(2031, 6, 1))
    assert found == (ms(2032, 2, 28), 4)


def test_the_anchor_itself_fires_without_an_end_condition_check():
    r = rule("daily", end={"type": "until_date", "dateMillis": ms(2026, 3, 1)})
    assert rs.first_occurrence_after(r, 540, ms(2026, 3, 4), UTC, ms(2026, 3, 3)) == \
        (ms(2026, 3, 4), 0)
    assert rs.first_occurrence_after(r, 540, ms(2026, 3, 4), UTC, ms(2026, 3, 4)) is None


def test_long_stale_anchors_resolve_without_walking_every_day():
    found = rs.first_occurrence_after(rule("daily"), 540, ms(1971, 1, 1), UTC, ms(2026, 11, 20, 10))
    assert found[0] == ms(2026, 11, 21)
    weekdays = rs.first_occurrence_after(rule("weekly", 3, {2, 6}), 540, ms(1990, 5, 9), UTC,
                                         ms(2026, 11, 20, 10))
    # Same answer as walking the chain step by step.
    day, k, walked = date(1990, 5, 9), 0, None
    target = datetime(2026, 11, 20, 10, tzinfo=timezone.utc)
    while walked is None:
        day = rs._step(day, rule("weekly", 3, {2, 6}))
        k += 1
        if datetime(day.year, day.month, day.day, 9, tzinfo=timezone.utc) > target:
            walked = (ms(day.year, day.month, day.day), k)
    assert weekdays == walked


def test_overflow_and_step_bound_end_the_chain(monkeypatch):
    huge = rs.Rule("yearly", rs.MAX_INTERVAL)
    assert rs.first_occurrence_after(huge, 540, ms(9500, 1, 1), UTC, ms(9600, 6, 1)) is None
    monkeypatch.setattr(rs, "MAX_DATE_STEPS", 5)
    assert rs.first_occurrence_after(rule("monthly"), 540, ms(2000, 1, 1), UTC, ms(2026, 1, 1)) is None


def test_parse_zone():
    assert rs.parse_zone("Europe/Berlin") is not None
    for bad in ("Mars/Olympus", "../etc/passwd", "", None, 5, "Europe", "x" * 65):
        assert rs.parse_zone(bad) is None


# ---------------------------------------------------------------------------
# Index entries of one checklist
# ---------------------------------------------------------------------------

NOW = datetime(2026, 11, 20, 8, 0, tzinfo=timezone.utc)  # 09:00 in Berlin (CET)
NOW_MS = rs.to_ms(NOW)
LINK = {"telegram_user_id": 111, "chat_id": 111, "language_code": "ru",
        "google_uid": "g1", "credit_user_id": "c1"}
MIN = 60_000
HOUR = 60 * MIN
DAY = 24 * HOUR


def item(item_id, text="Milk", **fields):
    return {"id": item_id, "text": text, **fields}


def doc(items=(), **fields):
    return {"name": "Groceries", "itemsJson": "[]", "updatedAt": 1, "isDeleted": False,
            "fills": [{"cloudId": "f1", "isDefault": True, "isDeleted": False,
                       "itemsJson": json.dumps(list(items))}], **fields}


def build(d, now=NOW, existing=None, zone="Europe/Berlin"):
    return rs.build_entries(d, google_uid="g1", owner_uid="g1", cloud_id="cl1", link=LINK,
                            zone_name=zone, now=now, existing=existing or {})


def rid(item_id=None, slot=rs.SLOT_MAIN):
    return rs.make_rid("g1", "cl1", item_id, slot)


def daily_item(item_id="i1", nxt=NOW_MS + DAY, count=0, rule_map=None, tod=9 * 60):
    return item(item_id, repeatRule=rule_map or {"type": "daily"}, repeatNextAt=nxt,
                repeatTimeOfDayMinutes=tod, repeatOccurrenceCount=count)


def test_rid_is_24_hex_and_separates_slots_and_targets():
    assert len(rid()) == 24 and int(rid(), 16) >= 0
    assert len({rid(), rid("i1"), rid(None, rs.SLOT_SNOOZE), rid("i1", rs.SLOT_SNOOZE)}) == 4
    assert rid() == rs.make_rid("g1", "cl1", "", rs.SLOT_MAIN)  # "-" for checklist-level


def test_checklist_and_item_one_shots_become_entries():
    at = NOW_MS + HOUR
    out = build(doc([item("i1", reminderAt=at + MIN)], reminderAt=at))
    checklist, it = out.writes[rid()], out.writes[rid("i1")]
    assert checklist["kind"] == "oneshot" and checklist["item_id"] is None
    assert checklist["item_text"] is None and checklist["checklist_name"] == "Groceries"
    assert rs.to_ms(checklist["next_fire_at"]) == at
    assert it["item_id"] == "i1" and it["item_text"] == "Milk"
    assert rs.to_ms(it["next_fire_at"]) == at + MIN
    for entry in (checklist, it):
        assert entry["google_uid"] == "g1" and entry["chat_id"] == 111
        assert entry["telegram_user_id"] == 111 and entry["language_code"] == "ru"
        assert entry["checklist_cloud_id"] == "cl1" and entry["checklist_owner_uid"] == "g1"
        assert entry["timezone"] == "Europe/Berlin" and entry["slot"] == "main"
        assert entry["last_fired_at"] is None and entry["source_updated_at"] == 1
    assert out.deletes == set() and out.warnings == []


def test_checked_items_disabled_alarms_and_items_without_id_are_skipped():
    at = NOW_MS + HOUR
    out = build(doc([
        item("done", checked=True, reminderAt=at),
        item("silent", alarmEnabled=False, reminderAt=at),
        item("armed", alarmEnabled=True, reminderAt=at),
        item("default", reminderAt=at),            # alarmEnabled absent = true
        {"text": "no id", "reminderAt": at},
        item("plain"),                             # no reminder at all
    ]))
    assert set(out.writes) == {rid("armed"), rid("default")}


def test_past_one_shot_is_skipped_but_one_inside_the_grace_window_is_kept():
    assert build(doc(reminderAt=NOW_MS - 10 * MIN)).writes == {}
    kept = build(doc(reminderAt=NOW_MS - 2 * MIN)).writes[rid()]
    assert rs.to_ms(kept["next_fire_at"]) == NOW_MS - 2 * MIN


def test_daily_repeat_written_a_minute_after_its_fire_time_is_not_lost():
    # Daily 09:00 Berlin = NOW; a checklist write 30 s later, before the cron claimed it.
    at = NOW_MS
    out = build(doc([daily_item(nxt=at)]), now=NOW + timedelta(seconds=30))
    entry = out.writes[rid("i1")]
    assert entry["kind"] == "repeat"
    assert rs.to_ms(entry["next_fire_at"]) == at
    assert entry["repeat"]["count"] == 0 and entry["repeat"]["time_of_day_minutes"] == 540


def test_fired_occurrence_is_never_rebuilt_inside_the_grace_window():
    at = NOW_MS - 2 * MIN
    first = build(doc(reminderAt=at)).writes[rid()]
    claimed = rs.consume_due(first, NOW)                      # the cron sent it
    assert claimed["next_fire_at"] is None and rs.to_ms(claimed["last_fired_at"]) == at
    # The web app never clears reminderAt: the next checklist write must not re-arm it.
    again = build(doc(reminderAt=at), now=NOW + timedelta(minutes=1),
                  existing={rid(): claimed})
    assert again.writes == {} and again.deletes == set()


def test_repeat_progress_survives_a_recompute_of_an_unchanged_schedule():
    d = doc([daily_item(nxt=NOW_MS)])
    entry = build(d).writes[rid("i1")]
    fired = rs.consume_due(entry, NOW + timedelta(seconds=20))
    assert rs.to_ms(fired["next_fire_at"]) == NOW_MS + DAY and fired["repeat"]["count"] == 1
    later = build(d, now=NOW + timedelta(minutes=1), existing={rid("i1"): fired})
    assert later.writes == {}  # same src, same zone → the server's own progress stands


def test_occurrence_due_when_the_phone_advanced_the_schedule_is_carried():
    # 09:00 due, not yet claimed; the phone fired it too, advanced repeatNextAt and synced.
    d0 = doc([daily_item(nxt=NOW_MS)])
    entry = build(d0, now=NOW - timedelta(hours=1)).writes[rid("i1")]
    advanced = doc([daily_item(nxt=NOW_MS + DAY, count=1)])
    out = build(advanced, now=NOW + timedelta(seconds=10), existing={rid("i1"): entry})
    carried = out.writes[rid("i1")]
    assert rs.to_ms(carried["carry_at"]) == NOW_MS
    assert rs.to_ms(carried["next_fire_at"]) == NOW_MS
    assert rs.to_ms(carried["repeat"]["next_at"]) == NOW_MS + DAY
    after = rs.consume_due(carried, NOW + timedelta(seconds=30))
    assert rs.to_ms(after["next_fire_at"]) == NOW_MS + DAY and after["carry_at"] is None


def test_one_shot_due_when_the_phone_cleared_it_is_carried_then_tombstoned():
    entry = build(doc(reminderAt=NOW_MS), now=NOW - timedelta(minutes=10)).writes[rid()]
    cleared = build(doc(), now=NOW + timedelta(seconds=5), existing={rid(): entry})
    carried = cleared.writes[rid()]
    assert rs.to_ms(carried["next_fire_at"]) == NOW_MS and carried["kind"] == "oneshot"
    tomb = rs.consume_due(carried, NOW + timedelta(seconds=40))
    assert tomb["next_fire_at"] is None
    # Kept while it guards anything, gone after TOMBSTONE_KEEP.
    assert build(doc(), now=NOW + timedelta(days=1), existing={rid(): tomb}).deletes == set()
    assert build(doc(), now=NOW + timedelta(days=8), existing={rid(): tomb}).deletes == {rid()}


def test_one_shot_and_repeat_on_the_same_minute_send_one_message():
    d = doc([daily_item(nxt=NOW_MS, rule_map={"type": "daily"}) | {"reminderAt": NOW_MS}])
    entry = build(d, now=NOW - timedelta(minutes=1)).writes[rid("i1")]
    assert rs.to_ms(entry["oneshot_at"]) == NOW_MS == rs.to_ms(entry["repeat"]["next_at"])
    after = rs.consume_due(entry, NOW)
    assert after["oneshot_at"] is None and rs.to_ms(after["next_fire_at"]) == NOW_MS + DAY


def test_after_count_on_the_web_ends_and_is_never_resurrected():
    # The web app never advances repeatNextAt / repeatOccurrenceCount: the server counts.
    d = doc([daily_item(nxt=NOW_MS, rule_map={"type": "daily", "endCondition":
                                               {"type": "after_count", "maxCount": 2}})])
    entry = build(d, now=NOW - timedelta(minutes=1)).writes[rid("i1")]
    fires = 0
    clock = NOW
    while entry["next_fire_at"] is not None:
        clock = entry["next_fire_at"]
        entry = rs.consume_due(entry, clock)
        fires += 1
        rebuilt = build(d, now=clock + timedelta(minutes=1), existing={rid("i1"): entry})
        assert rebuilt.writes == {} and rebuilt.deletes == set()
    assert fires == 2 and entry["repeat"]["ended"] is True
    assert build(d, now=clock + timedelta(days=3), existing={rid("i1"): entry}).writes == {}


def test_skipping_past_the_anchor_counts_one_fire_like_the_phone_recovery():
    rule_map = {"type": "daily", "endCondition": {"type": "after_count", "maxCount": 2}}
    stale = doc([daily_item(nxt=NOW_MS - 3 * DAY, count=0, rule_map=rule_map)])
    entry = build(stale, now=NOW + timedelta(hours=1)).writes[rid("i1")]
    assert entry["repeat"]["count"] == 1 and rs.to_ms(entry["next_fire_at"]) == NOW_MS + DAY
    exhausted = doc([daily_item(nxt=NOW_MS - 3 * DAY, count=1, rule_map=rule_map)])
    ended = build(exhausted, now=NOW + timedelta(hours=1)).writes[rid("i1")]
    assert ended["next_fire_at"] is None and ended["repeat"]["ended"] is True


def test_repeat_with_until_date_in_the_past_has_nothing_pending():
    rule_map = {"type": "daily", "endCondition": {"type": "until_date", "dateMillis": NOW_MS - DAY}}
    entry = build(doc([daily_item(nxt=NOW_MS - 3 * DAY, rule_map=rule_map)])).writes[rid("i1")]
    assert entry["next_fire_at"] is None and entry["repeat"]["ended"] is True


def test_ended_or_unreadable_repeats_make_no_entry():
    ended_on_phone = item("i1", repeatRule={"type": "daily"}, repeatNextAt=None)
    bad = item("i2", repeatRule={"type": "daily", "interval": 0}, repeatNextAt=NOW_MS + DAY)
    out = build(doc([ended_on_phone, bad], repeatRule='{"type":"hourly"}', repeatNextAt=NOW_MS))
    assert out.writes == {}
    assert sorted(out.warnings) == ["checklist: unreadable repeatRule", "item: unreadable repeatRule"]


def test_checklist_level_repeat_rule_is_a_json_string():
    out = build(doc(repeatRule=json.dumps({"type": "weekly", "weekDays": [1, 5]}),
                    repeatNextAt=NOW_MS + HOUR, repeatTimeOfDayMinutes=600))
    entry = out.writes[rid()]
    assert entry["kind"] == "repeat" and rs.to_ms(entry["next_fire_at"]) == NOW_MS + HOUR
    assert entry["repeat"]["rule"]["weekDays"] == [1, 5]


def test_unchanged_recompute_writes_nothing_and_ignores_updated_at():
    d = doc([item("i1", reminderAt=NOW_MS + HOUR)])
    first = build(d).writes
    again = build({**d, "updatedAt": 99}, now=NOW + timedelta(seconds=5), existing=first)
    assert again.writes == {} and again.deletes == set()


def test_removed_or_checked_target_keeps_a_recent_tombstone_and_drops_the_rest():
    d = doc([item("i1", reminderAt=NOW_MS + HOUR), item("i2", reminderAt=NOW_MS + HOUR)])
    entries = build(d).writes
    fired = rs.consume_due(entries[rid("i1")], NOW + timedelta(hours=1))
    # i2 was never claimed (cron outage) and is now more than STALE_AFTER old: not carried.
    later = NOW + timedelta(hours=8)
    out = build(doc([item("i1", checked=True, reminderAt=NOW_MS + HOUR)]), now=later,
                existing={rid("i1"): fired, rid("i2"): entries[rid("i2")]})
    assert out.deletes == {rid("i2")}  # never fired → nothing to guard
    assert rid("i1") not in out.writes  # tombstone unchanged


@pytest.mark.parametrize("change,carried", [
    ("reminder cleared (the phone's own clear on firing)", True),
    ("item checked", False),
    ("item deleted", False),
    ("alarm switched off", False),
])
def test_due_unclaimed_occurrence_is_carried_only_while_the_item_is_remindable(change, carried):
    # 10:00-style case: due at NOW, cron not there yet; the item changes 20 s later.
    entry = build(doc([item("i2", reminderAt=NOW_MS)]), now=NOW - timedelta(minutes=10)).writes
    after = {
        "reminder cleared (the phone's own clear on firing)": [item("i2")],
        "item checked": [item("i2", checked=True, reminderAt=NOW_MS)],
        "item deleted": [],
        "alarm switched off": [item("i2", alarmEnabled=False, reminderAt=NOW_MS)],
    }[change]
    out = build(doc(after), now=NOW + timedelta(seconds=20), existing=entry)
    if carried:
        assert rs.to_ms(out.writes[rid("i2")]["carry_at"]) == NOW_MS
        assert rs.to_ms(out.writes[rid("i2")]["next_fire_at"]) == NOW_MS
    else:
        assert rid("i2") not in out.writes and out.deletes == {rid("i2")}


def test_checklist_level_due_occurrence_is_carried_while_the_doc_is_live():
    entry = build(doc(reminderAt=NOW_MS), now=NOW - timedelta(minutes=10)).writes
    out = build(doc(), now=NOW + timedelta(seconds=20), existing=entry)
    assert rs.to_ms(out.writes[rid()]["carry_at"]) == NOW_MS


def test_snooze_entries_are_left_alone_until_an_old_tombstone():
    snooze = rs.snooze_entry(build(doc(reminderAt=NOW_MS + HOUR)).writes[rid()],
                             NOW + timedelta(hours=1), NOW)
    old = {**snooze, "next_fire_at": None, "oneshot_at": None,
           "last_fired_at": NOW - timedelta(days=8)}
    keep = build(doc(), existing={rid(None, rs.SLOT_SNOOZE): snooze})
    assert keep.writes == {} and keep.deletes == set()
    drop = build(doc(), existing={rid(None, rs.SLOT_SNOOZE): old})
    assert drop.deletes == {rid(None, rs.SLOT_SNOOZE)}


def test_zone_change_recomputes_but_keeps_progress():
    d = doc([daily_item(nxt=NOW_MS + DAY, count=0)])
    entry = build(d).writes[rid("i1")]
    fired = rs.consume_due(entry, entry["next_fire_at"])
    moved = build(d, now=NOW + timedelta(days=1, minutes=5), existing={rid("i1"): fired},
                  zone="Asia/Kolkata").writes[rid("i1")]
    assert moved["timezone"] == "Asia/Kolkata" and moved["repeat"]["count"] == 1
    assert rs.to_ms(moved["next_fire_at"]) == utc_ms(2026, 11, 22, 3, 30)  # 09:00 IST


def test_stale_claim_detection():
    assert rs.is_stale(NOW_MS - 6 * HOUR - 1, NOW)
    assert not rs.is_stale(NOW_MS - 6 * HOUR, NOW)


def test_build_refuses_an_invalid_zone():
    with pytest.raises(ValueError):
        build(doc(), zone="Nowhere/Land")
