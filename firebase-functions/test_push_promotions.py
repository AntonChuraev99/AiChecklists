"""Unit tests for push_promotions.py — the pure/testable half of send_promotions_batch.

No firebase_admin needed (that's the whole point of the module split). Covers the
audience filter (premium/holdout/frequency suppression), the RC-driven copy A/B
fallback chain, the FCM data-payload contract, the Amplitude event, and send-error
classification for token cleanup.
"""
from datetime import datetime, timedelta, timezone

import push_promotions as p

NOW = datetime(2026, 7, 3, 12, 0, tzinfo=timezone.utc)
# Consent model = "opt-out via the Tips & Offers channel" (no server-side opt-in flag).
# A token-holder who is dormant, not premium, not in the holdout, and outside the cooldown
# is eligible; the OS drops the push if they disabled the channel.
BASE = {"fcmToken": "tok"}


# --------------------------------------------------------------------------
# is_eligible_for_promo — the audience filter (the compliance-critical part)
# --------------------------------------------------------------------------
class TestEligibility:
    def test_dormant_token_holder_is_eligible(self):
        assert p.is_eligible_for_promo(dict(BASE), NOW, 20) == (True, "")

    def test_missing_token_skipped(self):
        assert p.is_eligible_for_promo({}, NOW, 20) == (False, "no_token")

    def test_empty_token_skipped(self):
        assert p.is_eligible_for_promo({"fcmToken": ""}, NOW, 20) == (False, "no_token")

    def test_premium_unconditionally_suppressed(self):
        ud = {**BASE, "is_premium": True}
        assert p.is_eligible_for_promo(ud, NOW, 20) == (False, "premium")

    def test_holdout_unconditionally_suppressed(self):
        ud = {**BASE, "pushHoldout": True}
        assert p.is_eligible_for_promo(ud, NOW, 20) == (False, "holdout")

    def test_frequency_capped_recent_send(self):
        ud = {**BASE, "lastPromoSentAt": NOW - timedelta(hours=2)}
        assert p.is_eligible_for_promo(ud, NOW, 20) == (False, "frequency_capped")

    def test_eligible_again_past_cooldown(self):
        ud = {**BASE, "lastPromoSentAt": NOW - timedelta(hours=30)}
        assert p.is_eligible_for_promo(ud, NOW, 20) == (True, "")

    def test_frequency_cap_reads_iso_string_timestamp(self):
        ud = {**BASE, "lastPromoSentAt": "2026-07-03T11:00:00Z"}
        assert p.is_eligible_for_promo(ud, NOW, 20)[0] is False

    def test_premium_precedence_over_frequency(self):
        # Premium is checked before the cooldown, so a premium user is reported as premium.
        ud = {"fcmToken": "t", "is_premium": True, "lastPromoSentAt": NOW - timedelta(hours=2)}
        assert p.is_eligible_for_promo(ud, NOW, 20) == (False, "premium")


# --------------------------------------------------------------------------
# to_aware_datetime
# --------------------------------------------------------------------------
class TestToAwareDatetime:
    def test_none(self):
        assert p.to_aware_datetime(None) is None

    def test_naive_datetime_gets_utc(self):
        d = p.to_aware_datetime(datetime(2026, 7, 3, 12))
        assert d.tzinfo is not None

    def test_aware_datetime_preserved(self):
        assert p.to_aware_datetime(NOW) == NOW

    def test_iso_z_suffix(self):
        assert p.to_aware_datetime("2026-07-03T12:00:00Z") == NOW

    def test_epoch_number(self):
        assert p.to_aware_datetime(NOW.timestamp()) == NOW

    def test_garbage_returns_none(self):
        assert p.to_aware_datetime("not-a-date") is None
        assert p.to_aware_datetime({"x": 1}) is None


# --------------------------------------------------------------------------
# select_copy — RC-override → RC-control → in-code → generic fallback chain
# --------------------------------------------------------------------------
class TestSelectCopy:
    def test_rc_variant_wins(self):
        rc = '{"reengagement":{"a":{"title":"RC-A","body":"rc-a-body"}}}'
        assert p.select_copy("reengagement", "a", rc) == ("RC-A", "rc-a-body")

    def test_rc_control_fallback_then_incode(self):
        # RC defines only arm "a"; arm "b" falls through RC-control (also absent) to in-code "b".
        rc = '{"reengagement":{"a":{"title":"RC-A","body":"x"}}}'
        title, _ = p.select_copy("reengagement", "b", rc)
        assert title == "One small win today?"  # in-code reengagement/b

    def test_incode_fallback_when_no_rc(self):
        assert p.select_copy("winback", "control", "") == (
            "It's been a while", "Here's a quick template to get started again.")

    def test_unknown_arm_falls_to_control(self):
        # digest has only "control" in-code; arm "b" → control.
        assert p.select_copy("digest", "b", "") == (
            "Your week in checklists", "See what you finished and what's still open.")

    def test_malformed_rc_json_falls_back_silently(self):
        title, body = p.select_copy("reengagement", "a", "{not valid json")
        assert (title, body) == ("Pick up where you left off", "Your lists are ready when you are.")

    def test_unknown_push_type_returns_generic(self):
        title, body = p.select_copy("nonexistent_type", "control", "")
        assert title and body  # never empty

    def test_rc_entry_missing_body_ignored(self):
        rc = '{"reengagement":{"a":{"title":"only-title"}}}'  # no body → skip RC, use in-code
        assert p.select_copy("reengagement", "a", rc) == (
            "Pick up where you left off", "Your lists are ready when you are.")


# --------------------------------------------------------------------------
# build_data_payload — the fixed FCM data contract (client reads these keys)
# --------------------------------------------------------------------------
class TestBuildDataPayload:
    def test_all_contract_keys_present(self):
        pl = p.build_data_payload("reengagement", "reengagement_20260703", "a", "T", "B")
        assert set(pl) == {
            "title", "body", "channel", "push_type", "audience_class",
            "campaign_id", "push_ab_experiment", "push_ab_arm",
        }

    def test_all_values_are_strings(self):
        pl = p.build_data_payload("winback", "c1", "b", "T", "B")
        assert all(isinstance(v, str) for v in pl.values())

    def test_fixed_promo_values(self):
        pl = p.build_data_payload("reengagement", "c1", "control", "T", "B")
        assert pl["channel"] == "promo"
        assert pl["audience_class"] == "promotional"
        assert pl["push_ab_experiment"] == "copy"
        assert pl["push_ab_arm"] == "control"

    def test_optional_checklist_id_added(self):
        pl = p.build_data_payload("reengagement", "c1", "a", "T", "B", checklist_id="cl-9")
        assert pl["checklist_id"] == "cl-9"

    def test_no_checklist_id_by_default(self):
        pl = p.build_data_payload("reengagement", "c1", "a", "T", "B")
        assert "checklist_id" not in pl


# --------------------------------------------------------------------------
# build_amplitude_event — CTR denominator
# --------------------------------------------------------------------------
class TestBuildAmplitudeEvent:
    def test_event_shape(self):
        ev = p.build_amplitude_event("uid1", "reengagement", "c1", "a", 1720000000000, "c1:uid1")
        assert ev["event_type"] == "push_sent"
        assert ev["user_id"] == "uid1"
        assert ev["insert_id"] == "c1:uid1"
        assert ev["time"] == 1720000000000

    def test_props_include_ab_and_suppression_flags(self):
        ev = p.build_amplitude_event("uid1", "winback", "c1", "b", 1, "c1:uid1")
        props = ev["event_properties"]
        assert props["push_ab_experiment"] == "copy"
        assert props["push_ab_arm"] == "b"
        assert props["is_premium"] is False
        assert props["push_holdout"] is False
        assert props["audience_class"] == "promotional"
        assert ev["user_properties"] == {"push_holdout": False}


# --------------------------------------------------------------------------
# classify_send_error — drives token cleanup
# --------------------------------------------------------------------------
class TestClassifySendError:
    def _named(self, name, msg=""):
        exc = Exception(msg)
        type(exc).__name__  # noqa
        e = type(name, (Exception,), {})(msg)
        return e

    def test_unregistered_is_unrecoverable(self):
        assert p.classify_send_error(self._named("UnregisteredError")) == "unrecoverable"

    def test_sender_id_mismatch_is_unrecoverable(self):
        assert p.classify_send_error(self._named("SenderIdMismatchError")) == "unrecoverable"

    def test_third_party_auth_is_unrecoverable(self):
        assert p.classify_send_error(self._named("ThirdPartyAuthError")) == "unrecoverable"

    def test_invalid_arg_message_sniff_unrecoverable(self):
        exc = self._named("InvalidArgumentError", "The registration token is not a valid FCM registration token")
        assert p.classify_send_error(exc) == "unrecoverable"

    def test_quota_is_transient(self):
        assert p.classify_send_error(self._named("QuotaExceededError", "quota")) == "transient"

    def test_unavailable_is_transient(self):
        assert p.classify_send_error(self._named("UnavailableError", "backend unavailable")) == "transient"


# --------------------------------------------------------------------------
# chunked — FCM 500 / Amplitude 100 batching
# --------------------------------------------------------------------------
class TestChunked:
    def test_splits_evenly(self):
        assert [len(c) for c in p.chunked(list(range(1000)), 500)] == [500, 500]

    def test_remainder_chunk(self):
        assert [len(c) for c in p.chunked(list(range(1201)), 500)] == [500, 500, 201]

    def test_empty(self):
        assert list(p.chunked([], 500)) == []
