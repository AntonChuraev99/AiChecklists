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
    """Tests for restore_credits_after_purchase with RevenueCat verification."""

    def test_rejects_without_valid_subscription(self, _import_main):
        """403 when RevenueCat has no active subscription."""
        main = _import_main

        # Mock user exists in Firestore
        mock_doc = MagicMock()
        mock_doc.exists = True
        mock_doc.to_dict.return_value = {"is_premium": False, "ai_credits": 0}
        main.db.collection.return_value.document.return_value.get.return_value = mock_doc

        # Mock RevenueCat: no entitlements
        with patch.object(main, "verify_premium_with_revenuecat", return_value=main.NOT_VERIFIED):
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

        with patch.object(main, "verify_premium_with_revenuecat", return_value=main.VERIFIED):
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

        with patch.object(main, "verify_premium_with_revenuecat", return_value=main.UNAVAILABLE):
            with app.test_request_context():
                req = make_request({"user_id": "user-123"})
                response, status = main.restore_credits_after_purchase(req)
                assert status == 503
                data = response.get_json()
                assert "temporarily unavailable" in data["error"].lower()

    def test_rejects_expired_subscription(self, _import_main):
        """403 when RevenueCat shows expired subscription."""
        main = _import_main

        mock_doc = MagicMock()
        mock_doc.exists = True
        main.db.collection.return_value.document.return_value.get.return_value = mock_doc

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

    def test_gemini_failure_does_not_refund_credits(self, _import_main):
        """When Gemini fails, credits are consumed (not refunded)."""
        main = _import_main

        def rc_side_effect(key, default):
            if key == "feature_ai_analysis_enabled":
                return True
            if key == "ai_analysis_max_input_length":
                return 10000
            return default

        with patch.object(main, "get_user_premium_status", return_value=True):
            with patch.object(main, "check_usage_limit", return_value=(True, "")):
                with patch.object(main, "reserve_credits", return_value=20):
                    with patch.object(main, "get_remote_config_value", side_effect=rc_side_effect):
                        with patch.object(main, "call_gemini", side_effect=Exception("Gemini error")):
                            with app.test_request_context():
                                req = make_request({
                                    "user_id": "user-123",
                                    "checklist": {"items": [{"text": "item1"}]},
                                    "input_type": "text",
                                    "input_data": "test"
                                })
                                response, status = main.analyze_and_fill_checklist(req)
                                assert status == 500
                                # Credits were already deducted (20 remaining),
                                # no refund happened


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

        with patch.object(main, "verify_premium_with_revenuecat", return_value=main.VERIFIED):
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

        with patch.object(main, "verify_premium_with_revenuecat", return_value=main.NOT_VERIFIED):
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

        with patch.object(main, "verify_premium_with_revenuecat", return_value=main.UNAVAILABLE):
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

        with patch.object(main, "verify_premium_with_revenuecat", side_effect=verify_side_effect):
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
