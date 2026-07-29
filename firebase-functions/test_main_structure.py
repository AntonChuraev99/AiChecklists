"""Structural guards over the SOURCE of main.py — no import, no Firebase, no Flask.

Kept out of test_main.py on purpose. That module has an autouse fixture which imports
firebase_admin and monkeypatches it, so every test there needs the full Cloud Functions
dependency set. These checks only parse main.py with `ast`, so as their own module they run
anywhere with nothing but pytest — which is what lets CI gate every PR on them cheaply
(.github/workflows/functions-guard.yml). A guard that is expensive to run is a guard people skip.
"""

import ast
import pathlib

# ---------------------------------------------------------------------------
# Structural guard: every 500 must leave a traceback, and none may leak str(e)
# ---------------------------------------------------------------------------

class TestFiveHundredBranchesAreDiagnosable:
    """Source-level invariants over main.py, deliberately NOT per-handler tests.

    This defect class recurred twice by the same mechanism: someone enumerated the
    handlers that return 500, fixed the ones on the list, and a handler that was not
    on the list kept the defect. On 2026-07-27 `link_google_account` returned a 500
    that left zero app-log lines and could not be diagnosed at all; the 2026-07-28
    pass fixed eight handlers and still missed `register_user` — the second-busiest
    endpoint — plus the shared `verify_firebase_token` helper.

    Enumerating by hand is the bug. These tests walk the AST instead, so a new
    500-branch is covered the moment it is written, without anyone maintaining a list.
    """

    @staticmethod
    def _main_tree():
        import ast
        import pathlib
        source = (pathlib.Path(__file__).parent / "main.py").read_text(encoding="utf-8")
        return ast.parse(source), source

    @staticmethod
    def _returns_500(node):
        import ast
        return any(
            isinstance(n, ast.Constant) and n.value == 500 for n in ast.walk(node)
        )

    @staticmethod
    def _logs_the_exception(handler):
        """A log line is only worth anything here if the TRACEBACK survives.

        `logger.exception(...)` always attaches it. `logger.error(...)` does so only with
        `exc_info=` or the caught exception among its arguments — a bare
        `logger.error("register failed")` leaves the same undiagnosable 500 this test exists to
        prevent, and the project convention (`AppLogger.error(tag, msg, throwable)`) makes that
        bare form the natural thing to type. Accepting any `.error` call was the first version of
        this check and it passed a probe that logged nothing useful.
        """
        import ast
        bound = handler.name  # `except Exception as e` -> "e"
        for n in ast.walk(handler):
            if not (isinstance(n, ast.Call) and isinstance(n.func, ast.Attribute)):
                continue
            if n.func.attr == "exception":
                return True
            if n.func.attr != "error":
                continue
            if any(kw.arg == "exc_info" for kw in n.keywords):
                return True
            if bound and any(
                isinstance(sub, ast.Name) and sub.id == bound
                for arg in n.args
                for sub in ast.walk(arg)
            ):
                return True
        return False

    @staticmethod
    def _mentions_exception_text(node, bound):
        """Every way the caught exception's text can reach a response body.

        `str(e)` was only the form that happened to be in `register_user`. An f-string
        `f"failed: {e}"` compiles to `JoinedStr`/`FormattedValue` with no `str()` call at all, and
        `repr(e)` is a different callee — both leaked past the first version of this check.
        """
        import ast
        for sub in ast.walk(node):
            if (
                isinstance(sub, ast.Call)
                and getattr(sub.func, "id", None) in ("str", "repr")
                and sub.args
                and getattr(sub.args[0], "id", None) == bound
            ):
                return True
            if isinstance(sub, ast.FormattedValue) and any(
                isinstance(inner, ast.Name) and inner.id == bound
                for inner in ast.walk(sub.value)
            ):
                return True
        return False

    def test_every_500_branch_logs_the_exception(self):
        import ast
        tree, _ = self._main_tree()
        unlogged = []
        for handler in ast.walk(tree):
            if not isinstance(handler, ast.ExceptHandler):
                continue
            if not self._returns_500(handler):
                continue
            if not self._logs_the_exception(handler):
                unlogged.append(handler.lineno)

        assert not unlogged, (
            "except-branches that return 500 without logging the exception, at main.py lines "
            f"{unlogged}. A 500 with no traceback is unrecoverable after the fact: Cloud Logging "
            "keeps the request log (a bare '500') but nothing about the cause. Add "
            "logger.exception(...) as the FIRST statement of the handler."
        )

    def test_no_500_branch_leaks_the_exception_text_to_the_client(self):
        import ast
        tree, _ = self._main_tree()
        leaking = []
        for handler in ast.walk(tree):
            if not isinstance(handler, ast.ExceptHandler):
                continue
            if not self._returns_500(handler):
                continue
            bound = handler.name  # `except Exception as e` -> "e"
            if not bound:
                continue
            for call in ast.walk(handler):
                # jsonify(...) / create_error_response(...) carrying the exception's text
                if not isinstance(call, ast.Call):
                    continue
                func = call.func
                name = getattr(func, "id", None) or getattr(func, "attr", None)
                if name not in ("jsonify", "create_error_response", "make_response"):
                    continue
                if self._mentions_exception_text(call, bound):
                    leaking.append(handler.lineno)
        assert not leaking, (
            f"500 response bodies that embed the exception text, at main.py lines {leaking}. "
            "That ships Firestore/genai internals to the client. Covers str(e), repr(e) and "
            'f"...{e}". Log the exception instead and return a static message.'
        )
