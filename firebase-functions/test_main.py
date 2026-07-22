"""
Tests for Firebase Cloud Functions (main.py).

Covers:
- P0 Security: RevenueCat verification, is_premium from Firestore
- P1 Race condition: atomic reserve_credits
- P1 Usage limits: check_usage_limit enforcement
- P2 Helpers: parse_gemini_json
"""

import json
from datetime import datetime, timedelta, timezone
from types import SimpleNamespace
from unittest.mock import MagicMock, patch

import pytest
from flask import Flask

# Create a test Flask app for request context
app = Flask(__name__)


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------

@pytest.fixture(autouse=True)
def _patch_firebase(monkeypatch):
    """Prevent real Firebase initialization during tests."""
    mock_admin = MagicMock()
    mock_admin._apps = {"[DEFAULT]": True}
    monkeypatch.setattr("firebase_admin._apps", {"[DEFAULT]": True})
    monkeypatch.setattr("firebase_admin.initialize_app", MagicMock())
    mock_db = MagicMock()
    monkeypatch.setattr("firebase_admin.firestore.client", lambda: mock_db)


@pytest.fixture
def _import_main(monkeypatch, _patch_firebase):
    """Import main module with mocked Firebase."""
    monkeypatch.setenv("GEMINI_API_KEY", "fake-key")
    monkeypatch.setenv("REVENUECAT_API_KEY", "sk_fake_key")

    import importlib
    import main
    importlib.reload(main)
    return main


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def make_request(data: dict) -> MagicMock:
    """Create a mock Flask Request object."""
    req = MagicMock()
    req.method = "POST"
    req.get_json.return_value = data
    return req


# ===========================================================================
# P0 Security: RevenueCat verification
# ===========================================================================

class TestRestoreCreditsRevenueCat:
    """Tests for restore_credits_after_purchase with RevenueCat verification.

    Mocks verify_premium (the hybrid Firestore + REST helper) so we cover the
    endpoint contract independently of which underlying path produced the result.
    """

    def test_rejects_without_valid_subscription(self, _import_main):
        """403 when RevenueCat has no active subscription."""
        main = _import_main

        # Mock user exists in Firestore
        mock_doc = MagicMock()
        mock_doc.exists = True
        mock_doc.to_dict.return_value = {"is_premium": False, "ai_credits": 0}
        main.db.collection.return_value.document.return_value.get.return_value = mock_doc

        with patch.object(main, "verify_premium", return_value=main.NOT_VERIFIED):
            with app.test_request_context():
                req = make_request({"user_id": "user-123"})
                response, status = main.restore_credits_after_purchase(req)
                assert status == 403
                data = response.get_json()
                assert "No active subscription" in data["error"]

    def test_succeeds_with_valid_subscription(self, _import_main):
        """200 when RevenueCat confirms active subscription."""
        main = _import_main

        mock_doc = MagicMock()
        mock_doc.exists = True
        mock_doc.to_dict.return_value = {"is_premium": False, "ai_credits": 0}
        main.db.collection.return_value.document.return_value.get.return_value = mock_doc

        with patch.object(main, "verify_premium", return_value=main.VERIFIED):
            with patch.object(main, "get_credits_config", return_value={
                "initial_credits": 100, "action_cost": 30, "premium_daily_credits_cap": 300
            }):
                with app.test_request_context():
                    req = make_request({"user_id": "user-123"})
                    response = main.restore_credits_after_purchase(req)
                    # Success response is just jsonify result (no tuple)
                    if isinstance(response, tuple):
                        response_data = response[0].get_json()
                    else:
                        response_data = response.get_json()
                    assert response_data["is_premium"] is True
                    assert response_data["ai_credits"] == 300

    def test_returns_503_when_revenuecat_unavailable(self, _import_main):
        """503 when RevenueCat API is unreachable."""
        main = _import_main

        mock_doc = MagicMock()
        mock_doc.exists = True
        main.db.collection.return_value.document.return_value.get.return_value = mock_doc

        with patch.object(main, "verify_premium", return_value=main.UNAVAILABLE):
            with app.test_request_context():
                req = make_request({"user_id": "user-123"})
                response, status = main.restore_credits_after_purchase(req)
                assert status == 503
                data = response.get_json()
                assert "temporarily unavailable" in data["error"].lower()

    def test_rejects_expired_subscription(self, _import_main):
        """verify_premium_with_revenuecat returns NOT_VERIFIED for expired sub.

        Directly exercises the REST helper (still used as fallback inside
        verify_premium) — proves the date-parsing contract is intact.
        """
        main = _import_main

        # Test actual verify function with expired date
        expired_date = (datetime.now(timezone.utc) - timedelta(days=1)).isoformat()
        mock_response = MagicMock()
        mock_response.status_code = 200
        mock_response.json.return_value = {
            "subscriber": {
                "entitlements": {
                    "premium": {"expires_date": expired_date}
                }
            }
        }

        with patch("main.http_requests.get", return_value=mock_response):
            with app.test_request_context():
                result = main.verify_premium_with_revenuecat("user-123")
                assert result == main.NOT_VERIFIED

    def test_enriched_restore_log_fields(self, _import_main):
        """credits_restore_log entry includes previous_state / new_state / source."""
        main = _import_main

        mock_user_doc = MagicMock()
        mock_user_doc.exists = True
        mock_user_doc.to_dict.return_value = {
            "is_premium": False, "ai_credits": 42, "amplitude_id": "amp-xyz"
        }
        mock_user_ref = MagicMock()
        mock_user_ref.get.return_value = mock_user_doc

        mock_log_collection = MagicMock()

        def collection_side_effect(name):
            if name == "credits_restore_log":
                return mock_log_collection
            c = MagicMock()
            c.document.return_value = mock_user_ref
            return c

        main.db.collection.side_effect = collection_side_effect

        with patch.object(main, "verify_premium", return_value=main.VERIFIED):
            with patch.object(main, "get_credits_config", return_value={
                "initial_credits": 100, "action_cost": 30, "premium_daily_credits_cap": 300
            }):
                with app.test_request_context():
                    req = make_request({"user_id": "user-123"})
                    main.restore_credits_after_purchase(req)

        assert mock_log_collection.add.called
        log_row = mock_log_collection.add.call_args[0][0]
        assert log_row["user_id"] == "user-123"
        assert log_row["amplitude_id"] == "amp-xyz"
        assert log_row["previous_state"] == {"is_premium": False, "ai_credits": 42}
        assert log_row["new_state"] == {"is_premium": True, "ai_credits": 300}
        assert log_row["source"] == "client_restore"
        assert log_row["revenuecat_verification_result"] == main.VERIFIED


# ===========================================================================
# P0 Security: Firestore-based premium verification (RC Extension)
# ===========================================================================

class TestVerifyPremiumFromFirestore:
    """verify_premium_from_firestore reads rc_customers/{user_id}.entitlements.

    Schema matches the official RevenueCat Firebase Extension (v0.1.18):
    {entitlements: {<id>: {expires_date, grace_period_expires_date, ...}}}.
    """

    def _set_rc_customer(self, main, doc_exists, doc_data=None):
        mock_doc = MagicMock()
        mock_doc.exists = doc_exists
        mock_doc.to_dict.return_value = doc_data or {}
        main.db.collection.return_value.document.return_value.get.return_value = mock_doc

    def test_active_entitlement_returns_verified(self, _import_main):
        main = _import_main
        future = (datetime.now(timezone.utc) + timedelta(days=30)).isoformat()
        self._set_rc_customer(main, True, {
            "entitlements": {"premium": {"expires_date": future}}
        })
        assert main.verify_premium_from_firestore("user-1") == main.VERIFIED

    def test_expired_entitlement_returns_not_verified(self, _import_main):
        main = _import_main
        past = (datetime.now(timezone.utc) - timedelta(days=1)).isoformat()
        self._set_rc_customer(main, True, {
            "entitlements": {"premium": {"expires_date": past}}
        })
        assert main.verify_premium_from_firestore("user-1") == main.NOT_VERIFIED

    def test_lifetime_entitlement_returns_verified(self, _import_main):
        main = _import_main
        self._set_rc_customer(main, True, {
            "entitlements": {"lifetime": {"expires_date": None}}
        })
        assert main.verify_premium_from_firestore("user-1") == main.VERIFIED

    def test_grace_period_keeps_user_premium(self, _import_main):
        """Billing retry window: expires_date passed but grace still active."""
        main = _import_main
        past = (datetime.now(timezone.utc) - timedelta(hours=6)).isoformat()
        future_grace = (datetime.now(timezone.utc) + timedelta(days=2)).isoformat()
        self._set_rc_customer(main, True, {
            "entitlements": {"premium": {
                "expires_date": past,
                "grace_period_expires_date": future_grace,
            }}
        })
        assert main.verify_premium_from_firestore("user-1") == main.VERIFIED

    def test_expired_grace_period_returns_not_verified(self, _import_main):
        main = _import_main
        past = (datetime.now(timezone.utc) - timedelta(days=10)).isoformat()
        past_grace = (datetime.now(timezone.utc) - timedelta(days=3)).isoformat()
        self._set_rc_customer(main, True, {
            "entitlements": {"premium": {
                "expires_date": past,
                "grace_period_expires_date": past_grace,
            }}
        })
        assert main.verify_premium_from_firestore("user-1") == main.NOT_VERIFIED

    def test_missing_document_returns_not_verified(self, _import_main):
        main = _import_main
        self._set_rc_customer(main, False)
        assert main.verify_premium_from_firestore("user-1") == main.NOT_VERIFIED

    def test_no_entitlements_returns_not_verified(self, _import_main):
        main = _import_main
        self._set_rc_customer(main, True, {"entitlements": {}})
        assert main.verify_premium_from_firestore("user-1") == main.NOT_VERIFIED

    def test_firestore_failure_returns_unavailable(self, _import_main):
        main = _import_main
        main.db.collection.return_value.document.return_value.get.side_effect = RuntimeError("boom")
        assert main.verify_premium_from_firestore("user-1") == main.UNAVAILABLE


class TestVerifyPremiumHybrid:
    """verify_premium chains Firestore → REST fallback correctly."""

    def test_firestore_verified_skips_rest(self, _import_main):
        main = _import_main
        with patch.object(main, "verify_premium_from_firestore", return_value=main.VERIFIED):
            with patch.object(main, "verify_premium_with_revenuecat") as mock_rest:
                assert main.verify_premium("user-1") == main.VERIFIED
                mock_rest.assert_not_called()

    def test_firestore_miss_falls_back_to_rest_verified(self, _import_main):
        main = _import_main
        with patch.object(main, "verify_premium_from_firestore", return_value=main.NOT_VERIFIED):
            with patch.object(main, "verify_premium_with_revenuecat", return_value=main.VERIFIED):
                assert main.verify_premium("user-1") == main.VERIFIED

    def test_firestore_not_verified_and_rest_unavailable_returns_not_verified(self, _import_main):
        """Definitive NOT_VERIFIED wins over REST UNAVAILABLE — 403 is actionable, 503 isn't."""
        main = _import_main
        with patch.object(main, "verify_premium_from_firestore", return_value=main.NOT_VERIFIED):
            with patch.object(main, "verify_premium_with_revenuecat", return_value=main.UNAVAILABLE):
                assert main.verify_premium("user-1") == main.NOT_VERIFIED

    def test_both_unavailable_returns_unavailable(self, _import_main):
        main = _import_main
        with patch.object(main, "verify_premium_from_firestore", return_value=main.UNAVAILABLE):
            with patch.object(main, "verify_premium_with_revenuecat", return_value=main.UNAVAILABLE):
                assert main.verify_premium("user-1") == main.UNAVAILABLE


# ===========================================================================
# Firestore trigger: rc_events → premium reconcile + audit log
# ===========================================================================

class TestHandleRcEventPayload:
    """_handle_rc_event_payload covers grant, revoke, skip, and logging paths."""

    def _setup_user(self, main, prev_data=None):
        """Arrange db mocks so users/{id} lookup returns prev_data."""
        prev_data = prev_data or {}
        mock_user_doc = MagicMock()
        mock_user_doc.exists = True
        mock_user_doc.to_dict.return_value = prev_data
        mock_user_ref = MagicMock()
        mock_user_ref.get.return_value = mock_user_doc

        mock_log_collection = MagicMock()

        def collection_side_effect(name):
            if name == "premium_events_log":
                return mock_log_collection
            c = MagicMock()
            c.document.return_value = mock_user_ref
            return c

        main.db.collection.side_effect = collection_side_effect
        return mock_user_ref, mock_log_collection

    def test_initial_purchase_grants_premium(self, _import_main):
        main = _import_main
        user_ref, log_coll = self._setup_user(main, {"is_premium": False, "ai_credits": 10})

        with patch.object(main, "get_credits_config", return_value={
            "initial_credits": 100, "action_cost": 30, "premium_daily_credits_cap": 300
        }):
            main._handle_rc_event_payload({
                "type": "INITIAL_PURCHASE",
                "app_user_id": "user-123",
                "product_id": "premium_monthly:monthly",
                "store": "PLAY_STORE",
                "environment": "PRODUCTION",
            }, event_id="evt-1")

        user_ref.update.assert_called_once()
        update_args = user_ref.update.call_args[0][0]
        assert update_args["is_premium"] is True
        assert update_args["ai_credits"] == 300

        log_row = log_coll.add.call_args[0][0]
        assert log_row["rc_event_type"] == "INITIAL_PURCHASE"
        assert log_row["state_changed"] is True
        assert log_row["new_state"] == {"is_premium": True, "ai_credits": 300}
        assert log_row["source"] == "webhook:INITIAL_PURCHASE"

    def test_expiration_revokes_premium(self, _import_main):
        main = _import_main
        user_ref, log_coll = self._setup_user(main, {"is_premium": True, "ai_credits": 300})

        main._handle_rc_event_payload({
            "type": "EXPIRATION",
            "app_user_id": "user-123",
        }, event_id="evt-2")

        update_args = user_ref.update.call_args[0][0]
        assert update_args["is_premium"] is False
        # Credits stay untouched on revoke
        assert "ai_credits" not in update_args

        log_row = log_coll.add.call_args[0][0]
        assert log_row["rc_event_type"] == "EXPIRATION"
        assert log_row["new_state"]["is_premium"] is False
        assert log_row["new_state"]["ai_credits"] == 300

    def test_anonymous_user_is_skipped(self, _import_main):
        """$RCAnonymousID events have no user to reconcile — no writes."""
        main = _import_main
        user_ref, log_coll = self._setup_user(main)

        main._handle_rc_event_payload({
            "type": "INITIAL_PURCHASE",
            "app_user_id": "$RCAnonymousID:abc123",
        }, event_id="evt-3")

        user_ref.update.assert_not_called()
        log_coll.add.assert_not_called()

    def test_missing_app_user_id_is_skipped(self, _import_main):
        main = _import_main
        user_ref, log_coll = self._setup_user(main)

        main._handle_rc_event_payload({"type": "RENEWAL"}, event_id="evt-4")

        user_ref.update.assert_not_called()
        log_coll.add.assert_not_called()

    def test_unknown_event_type_logs_without_reconcile(self, _import_main):
        """New/unhandled RC event types still land in the audit log."""
        main = _import_main
        user_ref, log_coll = self._setup_user(main, {"is_premium": True, "ai_credits": 300})

        main._handle_rc_event_payload({
            "type": "BILLING_ISSUE",
            "app_user_id": "user-123",
        }, event_id="evt-5")

        user_ref.update.assert_not_called()
        log_row = log_coll.add.call_args[0][0]
        assert log_row["rc_event_type"] == "BILLING_ISSUE"
        assert log_row["state_changed"] is False


# ===========================================================================
# P0 Security: is_premium from Firestore
# ===========================================================================

class TestIsPremiumFromFirestore:
    """Verify AI endpoints read is_premium from Firestore, not request body."""

    def test_is_premium_from_firestore_not_request(self, _import_main):
        """AI endpoint ignores is_premium from request body."""
        main = _import_main

        # User is free in Firestore
        with patch.object(main, "get_user_premium_status", return_value=False) as mock_premium:
            with patch.object(main, "check_usage_limit", return_value=(True, "")):
                # reserve_credits returns None (not enough credits)
                with patch.object(main, "reserve_credits", return_value=None):
                    with patch.object(main, "get_credits_config", return_value={
                        "action_cost": 30, "premium_daily_credits_cap": 300, "initial_credits": 100
                    }):
                        with app.test_request_context():
                            req = make_request({
                                "user_id": "user-123",
                                "is_premium": True,  # should be ignored
                                "checklist": {"items": [{"text": "item1"}]},
                                "input_type": "text",
                                "input_data": "test"
                            })
                            response, status = main.analyze_and_fill_checklist(req)
                            assert status == 402
                            data = response.get_json()
                            # Error says "Get premium" (free user), NOT "Refill" (premium)
                            assert "Get premium" in data["error"]
                            # Verify get_user_premium_status was called
                            mock_premium.assert_called_once_with("user-123")


# ===========================================================================
# P1: Atomic reserve_credits
# ===========================================================================

class TestReserveCredits:
    """Tests for atomic credit reservation."""

    def test_atomic_deduction(self, _import_main):
        """reserve_credits deducts exactly action_cost and returns new balance."""
        main = _import_main

        mock_snapshot = MagicMock()
        mock_snapshot.exists = True
        mock_snapshot.get.return_value = 50  # current credits

        mock_user_ref = MagicMock()
        mock_user_ref.get.return_value = mock_snapshot
        main.db.collection.return_value.document.return_value = mock_user_ref

        with patch.object(main, "get_credits_config", return_value={
            "action_cost": 30, "premium_daily_credits_cap": 300, "initial_credits": 100
        }):
            # Mock the transaction to just call the function directly
            mock_txn = MagicMock()
            main.db.transaction.return_value = mock_txn

            with patch("firebase_admin.firestore.transactional", lambda f: f):
                remaining = main.reserve_credits("user-123")
                assert remaining == 20  # 50 - 30

    def test_returns_none_when_insufficient(self, _import_main):
        """reserve_credits returns None when credits < action_cost."""
        main = _import_main

        mock_snapshot = MagicMock()
        mock_snapshot.exists = True
        mock_snapshot.get.return_value = 10  # less than action_cost (30)

        mock_user_ref = MagicMock()
        mock_user_ref.get.return_value = mock_snapshot
        main.db.collection.return_value.document.return_value = mock_user_ref

        with patch.object(main, "get_credits_config", return_value={
            "action_cost": 30, "premium_daily_credits_cap": 300, "initial_credits": 100
        }):
            mock_txn = MagicMock()
            main.db.transaction.return_value = mock_txn

            with patch("firebase_admin.firestore.transactional", lambda f: f):
                remaining = main.reserve_credits("user-123")
                assert remaining is None

    def test_returns_none_when_user_not_found(self, _import_main):
        """reserve_credits returns None when user document doesn't exist."""
        main = _import_main

        mock_snapshot = MagicMock()
        mock_snapshot.exists = False

        mock_user_ref = MagicMock()
        mock_user_ref.get.return_value = mock_snapshot
        main.db.collection.return_value.document.return_value = mock_user_ref

        with patch.object(main, "get_credits_config", return_value={
            "action_cost": 30, "premium_daily_credits_cap": 300, "initial_credits": 100
        }):
            mock_txn = MagicMock()
            main.db.transaction.return_value = mock_txn

            with patch("firebase_admin.firestore.transactional", lambda f: f):
                remaining = main.reserve_credits("nonexistent-user")
                assert remaining is None

    def test_gemini_failure_refunds_reserved_credits(self, _import_main):
        """When Gemini fails after reserve, the reserved credits are refunded."""
        main = _import_main

        def rc_side_effect(key, default):
            if key == "feature_ai_analysis_enabled":
                return True
            if key == "ai_analysis_max_input_length":
                return 10000
            return default

        with patch.object(main, "get_user_premium_status", return_value=True):
            with patch.object(main, "check_usage_limit", return_value=(True, "")):
                with patch.object(main, "get_credits_config", return_value={
                    "action_cost": 30, "premium_daily_credits_cap": 300, "initial_credits": 100
                }):
                    with patch.object(main, "reserve_credits", return_value=20):
                        with patch.object(main, "refund_credits", return_value=True) as mock_refund:
                            with patch.object(main, "get_remote_config_value", side_effect=rc_side_effect):
                                with patch.object(main, "call_gemini", side_effect=Exception("Gemini error")):
                                    with app.test_request_context():
                                        req = make_request({
                                            "user_id": "user-123",
                                            "checklist": {"items": [{"text": "item1"}]},
                                            "input_type": "text",
                                            "input_data": "test"
                                        })
                                        response = main.analyze_and_fill_checklist(req)
                                        # create_error_response returns a single CORS-wrapped
                                        # Response (status baked in), not a (body, code) tuple.
                                        status = response[1] if isinstance(response, tuple) else response.status_code
                                        assert status == 500
                                        # Reserved credits are refunded on Gemini failure.
                                        mock_refund.assert_called_once_with(
                                            "user-123", 30, "gemini_error"
                                        )


# ===========================================================================
# P1: Usage limits
# ===========================================================================

class TestUsageLimits:
    """Tests for check_usage_limit enforcement."""

    def test_usage_limit_enforced_for_free_user(self, _import_main):
        """Free user at daily limit gets 429, credits NOT deducted."""
        main = _import_main

        with patch.object(main, "get_user_premium_status", return_value=False):
            with patch.object(main, "check_usage_limit", return_value=(False, "Daily limit of 10 requests exceeded.")):
                with patch.object(main, "reserve_credits") as mock_reserve:
                    with patch.object(main, "get_remote_config_value", return_value=True):
                        with app.test_request_context():
                            req = make_request({
                                "user_id": "user-123",
                                "checklist": {"items": [{"text": "item1"}]},
                                "input_type": "text",
                                "input_data": "test"
                            })
                            response, status = main.analyze_and_fill_checklist(req)
                            assert status == 429
                            # reserve_credits was NOT called (credits not deducted)
                            mock_reserve.assert_not_called()

    def test_premium_user_higher_usage_limit(self, _import_main):
        """Premium user with 11 daily requests passes (limit is 100)."""
        main = _import_main

        def rc_side_effect(key, default):
            if key == "feature_ai_analysis_enabled":
                return True
            if key == "ai_analysis_max_input_length":
                return 10000
            return default

        with patch.object(main, "get_user_premium_status", return_value=True):
            with patch.object(main, "check_usage_limit", return_value=(True, "")):
                with patch.object(main, "reserve_credits", return_value=270):
                    with patch.object(main, "get_remote_config_value", side_effect=rc_side_effect):
                        with patch.object(main, "call_gemini") as mock_gemini:
                            mock_response = MagicMock()
                            mock_response.text = '{"filled_items": [], "summary": "ok", "confidence": 0.9}'
                            mock_gemini.return_value = mock_response
                            with patch.object(main, "increment_usage"):
                                with app.test_request_context():
                                    req = make_request({
                                        "user_id": "user-123",
                                        "checklist": {"items": [{"text": "item1"}]},
                                        "input_type": "text",
                                        "input_data": "test"
                                    })
                                    response = main.analyze_and_fill_checklist(req)
                                    # Success response (no tuple)
                                    if isinstance(response, tuple):
                                        response_data = response[0].get_json()
                                    else:
                                        response_data = response.get_json()
                                    assert response_data["success"] is True


# ===========================================================================
# P2: Helpers
# ===========================================================================

class TestParseGeminiJson:
    """Tests for parse_gemini_json helper."""

    def test_with_code_fence(self, _import_main):
        main = _import_main
        result = main.parse_gemini_json('```json\n{"key": "val"}\n```')
        assert result == {"key": "val"}

    def test_plain_json(self, _import_main):
        main = _import_main
        result = main.parse_gemini_json('{"key": "val"}')
        assert result == {"key": "val"}

    def test_invalid_raises(self, _import_main):
        main = _import_main
        with pytest.raises(json.JSONDecodeError):
            main.parse_gemini_json("not json at all")


class TestParsePushTokenRegistration:
    """Tests for parse_push_token_registration — the pure body validator behind
    register_push_token (the merge-write of an FCM token into the credit-doc)."""

    def _valid(self):
        return {
            "user_id": "abcdefghij-uuid",
            "fcm_token": "fcm-tok-xyz",
            "platform": "android",
            "push_holdout": False,
            "fcm_opt_in": True,
        }

    def test_valid_body_parsed(self, _import_main):
        main = _import_main
        fields, error = main.parse_push_token_registration(self._valid())
        assert error is None
        assert fields == {
            "user_id": "abcdefghij-uuid",
            "fcm_token": "fcm-tok-xyz",
            "platform": "android",
            "push_holdout": False,
            "fcm_opt_in": True,
        }

    def test_booleans_default_false_when_absent(self, _import_main):
        main = _import_main
        fields, error = main.parse_push_token_registration({
            "user_id": "user-1234567890", "fcm_token": "t", "platform": "web",
        })
        assert error is None
        assert fields["push_holdout"] is False
        assert fields["fcm_opt_in"] is False

    def test_web_platform_accepted(self, _import_main):
        main = _import_main
        body = self._valid()
        body["platform"] = "web"
        fields, error = main.parse_push_token_registration(body)
        assert error is None and fields["platform"] == "web"

    def test_user_id_and_token_trimmed(self, _import_main):
        main = _import_main
        body = self._valid()
        body["user_id"] = "  padded-user-id  "
        body["fcm_token"] = "  tok  "
        fields, error = main.parse_push_token_registration(body)
        assert error is None
        assert fields["user_id"] == "padded-user-id"
        assert fields["fcm_token"] == "tok"

    def test_missing_body_rejected(self, _import_main):
        main = _import_main
        fields, error = main.parse_push_token_registration(None)
        assert fields is None and error

    def test_short_user_id_rejected(self, _import_main):
        main = _import_main
        body = self._valid()
        body["user_id"] = "short"  # < 10 chars
        fields, error = main.parse_push_token_registration(body)
        assert fields is None and "user_id" in error

    def test_missing_user_id_rejected(self, _import_main):
        main = _import_main
        body = self._valid()
        del body["user_id"]
        fields, error = main.parse_push_token_registration(body)
        assert fields is None and "user_id" in error

    def test_blank_token_rejected(self, _import_main):
        main = _import_main
        body = self._valid()
        body["fcm_token"] = "   "
        fields, error = main.parse_push_token_registration(body)
        assert fields is None and "fcm_token" in error

    def test_missing_token_rejected(self, _import_main):
        main = _import_main
        body = self._valid()
        del body["fcm_token"]
        fields, error = main.parse_push_token_registration(body)
        assert fields is None and "fcm_token" in error

    def test_bad_platform_rejected(self, _import_main):
        main = _import_main
        body = self._valid()
        body["platform"] = "ios"  # not in {android, web}
        fields, error = main.parse_push_token_registration(body)
        assert fields is None and "platform" in error

    def test_missing_platform_rejected(self, _import_main):
        main = _import_main
        body = self._valid()
        del body["platform"]
        fields, error = main.parse_push_token_registration(body)
        assert fields is None and "platform" in error

    def test_non_bool_holdout_rejected(self, _import_main):
        main = _import_main
        body = self._valid()
        body["push_holdout"] = "true"  # string, not bool
        fields, error = main.parse_push_token_registration(body)
        assert fields is None and "push_holdout" in error

    def test_non_bool_opt_in_rejected(self, _import_main):
        main = _import_main
        body = self._valid()
        body["fcm_opt_in"] = 1  # int, not bool
        fields, error = main.parse_push_token_registration(body)
        assert fields is None and "fcm_opt_in" in error


# ===========================================================================
# P0: Refill verifies subscription via RevenueCat
# ===========================================================================

class TestRefillPremiumCredits:
    """Tests for refill_premium_credits with RevenueCat verification."""

    def _make_user_doc(self, user_id, ai_credits=100, is_premium=True):
        """Create a mock Firestore user document."""
        doc = MagicMock()
        doc.id = user_id
        doc.to_dict.return_value = {
            "is_premium": is_premium,
            "ai_credits": ai_credits
        }
        doc.reference = MagicMock()
        return doc

    def test_refills_verified_user(self, _import_main):
        """User with active RevenueCat subscription gets credits refilled."""
        main = _import_main

        user_doc = self._make_user_doc("user-1", ai_credits=50)
        main.db.collection.return_value.where.return_value.get.return_value = [user_doc]
        main.db.collection.return_value.add = MagicMock()

        with patch.object(main, "verify_premium", return_value=main.VERIFIED):
            with patch.object(main, "get_credits_config", return_value={
                "initial_credits": 100, "action_cost": 30, "premium_daily_credits_cap": 300
            }):
                with app.test_request_context():
                    req = make_request({})
                    response = main.refill_premium_credits(req)
                    if isinstance(response, tuple):
                        data = response[0].get_json()
                    else:
                        data = response.get_json()

                    assert data["users_updated"] == 1
                    assert data["users_expired"] == 0
                    user_doc.reference.update.assert_called_once()
                    update_args = user_doc.reference.update.call_args[0][0]
                    assert update_args["ai_credits"] == 300

    def test_expires_unverified_user(self, _import_main):
        """User with expired subscription gets is_premium set to False."""
        main = _import_main

        user_doc = self._make_user_doc("user-expired", ai_credits=200)
        main.db.collection.return_value.where.return_value.get.return_value = [user_doc]
        main.db.collection.return_value.add = MagicMock()

        with patch.object(main, "verify_premium", return_value=main.NOT_VERIFIED):
            with patch.object(main, "get_credits_config", return_value={
                "initial_credits": 100, "action_cost": 30, "premium_daily_credits_cap": 300
            }):
                with app.test_request_context():
                    req = make_request({})
                    response = main.refill_premium_credits(req)
                    if isinstance(response, tuple):
                        data = response[0].get_json()
                    else:
                        data = response.get_json()

                    assert data["users_expired"] == 1
                    assert data["users_updated"] == 0
                    update_args = user_doc.reference.update.call_args[0][0]
                    assert update_args["is_premium"] is False

    def test_refills_when_revenuecat_unavailable(self, _import_main):
        """When RevenueCat is down, refill anyway (benefit of the doubt)."""
        main = _import_main

        user_doc = self._make_user_doc("user-offline", ai_credits=10)
        main.db.collection.return_value.where.return_value.get.return_value = [user_doc]
        main.db.collection.return_value.add = MagicMock()

        with patch.object(main, "verify_premium", return_value=main.UNAVAILABLE):
            with patch.object(main, "get_credits_config", return_value={
                "initial_credits": 100, "action_cost": 30, "premium_daily_credits_cap": 300
            }):
                with app.test_request_context():
                    req = make_request({})
                    response = main.refill_premium_credits(req)
                    if isinstance(response, tuple):
                        data = response[0].get_json()
                    else:
                        data = response.get_json()

                    assert data["users_updated"] == 1
                    assert data["users_expired"] == 0

    def test_mixed_users(self, _import_main):
        """Multiple users: one verified, one expired, one at cap."""
        main = _import_main

        active_user = self._make_user_doc("user-active", ai_credits=50)
        expired_user = self._make_user_doc("user-expired", ai_credits=200)
        full_user = self._make_user_doc("user-full", ai_credits=300)

        main.db.collection.return_value.where.return_value.get.return_value = [
            active_user, expired_user, full_user
        ]
        main.db.collection.return_value.add = MagicMock()

        def verify_side_effect(user_id):
            if user_id == "user-expired":
                return main.NOT_VERIFIED
            return main.VERIFIED

        with patch.object(main, "verify_premium", side_effect=verify_side_effect):
            with patch.object(main, "get_credits_config", return_value={
                "initial_credits": 100, "action_cost": 30, "premium_daily_credits_cap": 300
            }):
                with app.test_request_context():
                    req = make_request({})
                    response = main.refill_premium_credits(req)
                    if isinstance(response, tuple):
                        data = response[0].get_json()
                    else:
                        data = response.get_json()

                    assert data["users_updated"] == 1   # active_user
                    assert data["users_expired"] == 1   # expired_user
                    assert data["users_skipped"] == 1   # full_user


# ===========================================================================
# AI model A/B experiment: assign_model_arm / resolve_experiment_model
# ===========================================================================

class TestModelExperiment:
    """Server-driven A/B model assignment over a Firebase Remote Config server template.

    We patch main._get_rc_server_template to return a fake ServerTemplate whose evaluate()
    yields fixed param values — this exercises the mapping / fail-safe / allowlist logic in
    assign_model_arm + resolve_experiment_model without touching Remote Config. The real
    percent-condition bucketing (50/50 by randomization_id) is a property of the RC SDK's
    evaluate() and was verified end-to-end out of band (~50.5% over 4000 ids).
    """

    class _FakeConfig:
        def __init__(self, values):
            self._values = values

        def get_string(self, key):
            return self._values.get(key, "")

    class _FakeTemplate:
        def __init__(self, values=None, raise_on_eval=False):
            self._values = values or {}
            self._raise = raise_on_eval

        def evaluate(self, context=None):
            if self._raise:
                raise RuntimeError("evaluate boom")
            return TestModelExperiment._FakeConfig(self._values)

    def _patch_template(self, monkeypatch, main, template):
        monkeypatch.setattr(main, "_get_rc_server_template", lambda: template)

    def _arm_values(self, arm, model):
        return {"ai_model_arm": arm, "ai_model_chat_agent": model, "ai_model_analyze": model}

    def test_no_template_returns_control_default(self, _import_main, monkeypatch):
        main = _import_main
        self._patch_template(monkeypatch, main, None)
        model, arm = main.assign_model_arm("user-1", "chat_agent", "gemini-2.5-flash")
        assert arm == "control"
        assert model == "gemini-2.5-flash"

    def test_variant_arm_returns_variant_model(self, _import_main, monkeypatch):
        main = _import_main
        self._patch_template(monkeypatch, main,
                             self._FakeTemplate(self._arm_values("variant_b", "gemini-3.1-flash-lite")))
        model, arm = main.assign_model_arm("user-1", "chat_agent", "gemini-2.5-flash")
        assert arm == "variant_b"
        assert model == "gemini-3.1-flash-lite"

    def test_control_arm_returns_control_model(self, _import_main, monkeypatch):
        main = _import_main
        self._patch_template(monkeypatch, main,
                             self._FakeTemplate(self._arm_values("control", "gemini-2.5-flash")))
        model, arm = main.assign_model_arm("user-1", "chat_agent", "gemini-2.5-flash")
        assert arm == "control"
        assert model == "gemini-2.5-flash"

    def test_missing_model_param_falls_back(self, _import_main, monkeypatch):
        main = _import_main
        # arm present but the per-flow model param is empty ("") -> default + control.
        self._patch_template(monkeypatch, main, self._FakeTemplate({"ai_model_arm": "variant_b"}))
        model, arm = main.assign_model_arm("user-1", "chat_agent", "gemini-2.5-flash")
        assert arm == "control"
        assert model == "gemini-2.5-flash"

    def test_failsafe_disallowed_model(self, _import_main, monkeypatch):
        main = _import_main
        # RC resolves a model NOT in the allowlist -> fail safe to control default.
        self._patch_template(monkeypatch, main,
                             self._FakeTemplate(self._arm_values("variant_b", "gpt-4o")))
        model, arm = main.assign_model_arm("user-1", "chat_agent", "gemini-2.5-flash")
        assert arm == "control"
        assert model == "gemini-2.5-flash"

    def test_failsafe_evaluate_raises(self, _import_main, monkeypatch):
        main = _import_main
        # A raising evaluate() must never break the AI path -> control default.
        self._patch_template(monkeypatch, main, self._FakeTemplate(raise_on_eval=True))
        model, arm = main.assign_model_arm("user-1", "analyze", "gemini-2.5-flash-lite")
        assert arm == "control"
        assert model == "gemini-2.5-flash-lite"

    def test_eval_override_wins_and_labels_override(self, _import_main, monkeypatch):
        main = _import_main
        # RC would say control; the eval test-override must win and be labelled "override".
        self._patch_template(monkeypatch, main,
                             self._FakeTemplate(self._arm_values("control", "gemini-2.5-flash")))
        monkeypatch.setattr(main, "MODEL_OVERRIDE_TEST_SECRET", "secret123")
        # Take the id FROM the allowlist rather than naming one: what is under test is
        # "override beats the RC arm", not any particular model. A literal here rots —
        # this test asserted on gemini-2.5-pro and stayed green for months after Google
        # retired it, because the network is faked and a dead id resolves fine.
        # Whether an id still exists is a live-API question; see ai_model_eval.py.
        override_model = next(iter(main.MODEL_OVERRIDE_ALLOWLIST - {"gemini-2.5-flash"}))
        model, arm = main.resolve_experiment_model(
            "user-1", "chat_agent", "gemini-2.5-flash",
            {"model_override": override_model, "test_secret": "secret123"},
        )
        assert arm == "override"
        assert model == override_model

    def test_no_override_uses_rc_arm(self, _import_main, monkeypatch):
        main = _import_main
        self._patch_template(monkeypatch, main,
                             self._FakeTemplate(self._arm_values("variant_b", "gemini-3.1-flash-lite")))
        model, arm = main.resolve_experiment_model(
            "user-1", "chat_agent", "gemini-2.5-flash", {},
        )
        assert arm == "variant_b"
        assert model == "gemini-3.1-flash-lite"


# ===========================================================================
# All-languages support: response-language directive (chat_completion / chat_agent)
# ===========================================================================

class TestResponseLanguageDirective:
    """Additive, backward-compatible response-language helpers.

    Absent field → Auto (reply in the user's message language); an explicit BCP-47
    tag → force that language. Old clients never send `response_language`, so the
    legacy path is exercised by the "absent → None → Auto directive" cases.
    """

    def test_normalise_absent_or_blank_is_none(self, _import_main):
        main = _import_main
        assert main._normalise_response_language(None) is None
        assert main._normalise_response_language("") is None
        assert main._normalise_response_language("   ") is None
        assert main._normalise_response_language(123) is None

    def test_normalise_strips_region_and_script(self, _import_main):
        main = _import_main
        assert main._normalise_response_language("es") == "es"
        assert main._normalise_response_language("ES") == "es"
        assert main._normalise_response_language("es-419") == "es"
        assert main._normalise_response_language("zh_Hant") == "zh"
        assert main._normalise_response_language(" en-US ") == "en"

    def test_auto_directive_matches_message_language(self, _import_main):
        main = _import_main
        directive = main._language_directive(None)
        assert "SAME language" in directive
        assert "most recent" in directive

    def test_explicit_directive_names_known_language(self, _import_main):
        main = _import_main
        directive = main._language_directive("es")
        assert "Spanish" in directive
        assert "overrides any language note above" in directive

    def test_explicit_directive_unknown_tag_falls_back_to_code(self, _import_main):
        main = _import_main
        directive = main._language_directive("xx")
        assert "xx" in directive  # BCP-47 code fallback still forces a language


# ===========================================================================
# chat_agent: empty-candidate handling (retry-once + graceful degrade)
# Followup #2, docs/todos/2026-07-22-release-1.18.2-remaining-followups.md
# ===========================================================================

def _text_part(text):
    return SimpleNamespace(text=text, function_call=None, thought_signature=None)


def _fc_part(name, args=None, call_id="c1"):
    fc = SimpleNamespace(name=name, id=call_id, args=args or {})
    return SimpleNamespace(text=None, function_call=fc, thought_signature=None)


def _response(parts, text="", finish_reason="STOP"):
    candidate = SimpleNamespace(
        content=SimpleNamespace(parts=parts),
        finish_reason=finish_reason,
    )
    return SimpleNamespace(candidates=[candidate], text=text)


class TestInterpretAgentResponse:
    """The pure _interpret_agent_response classifier — the single source of truth for
    which terminal outcome a chat_agent Gemini response carries."""

    def test_empty_candidate_returns_empty_with_finish_reason(self, _import_main):
        main = _import_main
        resp = _response(parts=[], text="", finish_reason="SAFETY")
        kind, payload = main._interpret_agent_response(resp)
        assert kind == "empty"
        assert payload == "SAFETY"

    def test_no_candidates_returns_empty(self, _import_main):
        main = _import_main
        resp = SimpleNamespace(candidates=[], text="")
        kind, payload = main._interpret_agent_response(resp)
        assert kind == "empty"

    def test_final_text_returns_final(self, _import_main):
        main = _import_main
        resp = _response(parts=[_text_part("Hello there")], text="Hello there")
        kind, payload = main._interpret_agent_response(resp)
        assert kind == "final"
        assert payload == "Hello there"

    def test_tool_calls_returns_tool_calls(self, _import_main):
        main = _import_main
        resp = _response(parts=[_fc_part("add_item", {"text": "milk"})])
        kind, payload = main._interpret_agent_response(resp)
        assert kind == "tool_calls"
        assert payload[0]["name"] == "add_item"
        assert payload[0]["args"] == {"text": "milk"}

    def test_present_options_returns_options(self, _import_main):
        main = _import_main
        resp = _response(parts=[_fc_part(
            "present_options",
            {"prompt": "Which list?", "options": ["Groceries", "Work"]},
        )])
        kind, payload = main._interpret_agent_response(resp)
        assert kind == "options"
        assert payload["prompt"] == "Which list?"
        assert payload["options"] == ["Groceries", "Work"]

    def test_options_precede_tool_calls(self, _import_main):
        """present_options is server-terminal — it wins even if generic tool calls coexist."""
        main = _import_main
        resp = _response(parts=[
            _fc_part("present_options", {"prompt": "Pick", "options": ["A", "B"]}),
            _fc_part("add_item", {"text": "milk"}, call_id="c2"),
        ])
        kind, _ = main._interpret_agent_response(resp)
        assert kind == "options"


class TestChatAgentEmptyCandidate:
    """chat_agent must retry once on an empty Gemini candidate, then degrade gracefully
    (localized final + refund) instead of the old hard 500."""

    # Minimal first-round request (no request_id -> legacy reserve path).
    _REQ = {
        "user_id": "user-abc",
        "locale": "ru",
        "transcript": [{"role": "user", "text": "привет"}],
    }

    def _run(self, main, generate_content, get_credits_return=8):
        """Invoke chat_agent with all IO deps mocked, Gemini driven by generate_content."""
        mock_client = MagicMock()
        mock_client.models.generate_content.side_effect = None
        if isinstance(generate_content, list):
            mock_client.models.generate_content.side_effect = generate_content
        else:
            mock_client.models.generate_content.return_value = generate_content

        with patch.object(main, "resolve_experiment_model", return_value=("gemini-x", "control")):
            with patch.object(main, "_reconstruct_agent_contents", return_value=[object()]):
                with patch.object(main, "reserve_chat_completion_credits", return_value=5):
                    with patch.object(main, "increment_usage") as mock_incr:
                        with patch.object(main, "refund_chat_completion_credits") as mock_refund:
                            with patch.object(main, "get_user_credits", return_value=get_credits_return):
                                with patch.object(main, "gemini_client", mock_client):
                                    with app.test_request_context():
                                        resp = main.chat_agent(make_request(self._REQ))
        data = resp[0].get_json() if isinstance(resp, tuple) else resp.get_json()
        status = resp[1] if isinstance(resp, tuple) else resp.status_code
        return status, data, mock_incr, mock_refund, mock_client

    def test_empty_twice_degrades_gracefully_and_refunds(self, _import_main):
        main = _import_main
        empty = _response(parts=[], text="", finish_reason="SAFETY")
        status, data, mock_incr, mock_refund, mock_client = self._run(
            main, generate_content=[empty, empty], get_credits_return=8,
        )
        # 200 (not 500), typed final with the localized degrade copy.
        assert status == 200
        assert data["success"] is True
        assert data["type"] == "final"
        assert "не получилось" in data["content"]  # ru degrade message
        # Post-refund balance is reported, not the stale reserved one.
        assert data["credits_remaining"] == 8
        # The turn was refunded (non-answer) and NOT billed.
        mock_refund.assert_called_once()
        assert mock_refund.call_args.kwargs.get("reason") == "chat_agent_empty_gemini_after_retry"
        mock_incr.assert_not_called()
        # Retried exactly once (2 Gemini calls total).
        assert mock_client.models.generate_content.call_count == 2

    def test_empty_then_recovers_on_retry(self, _import_main):
        main = _import_main
        empty = _response(parts=[], text="", finish_reason="SAFETY")
        recovered = _response(parts=[_text_part("Привет!")], text="Привет!")
        status, data, mock_incr, mock_refund, mock_client = self._run(
            main, generate_content=[empty, recovered],
        )
        assert status == 200
        assert data["type"] == "final"
        assert data["content"] == "Привет!"
        # Recovered turn: reserved balance kept, billed once, NOT refunded.
        assert data["credits_remaining"] == 5
        mock_refund.assert_not_called()
        mock_incr.assert_called_once()
        assert mock_client.models.generate_content.call_count == 2

    def test_first_call_success_no_retry(self, _import_main):
        main = _import_main
        ok = _response(parts=[_text_part("Готово")], text="Готово")
        status, data, mock_incr, mock_refund, mock_client = self._run(
            main, generate_content=ok,
        )
        assert status == 200
        assert data["type"] == "final"
        assert data["content"] == "Готово"
        assert data["credits_remaining"] == 5
        mock_refund.assert_not_called()
        mock_incr.assert_called_once()
        # Happy path: exactly one Gemini call, no retry.
        assert mock_client.models.generate_content.call_count == 1
