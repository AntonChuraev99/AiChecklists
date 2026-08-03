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


# ---------------------------------------------------------------------------
# Structural guard: FCM multicast fan-out must not outgrow the HTTP pool
# ---------------------------------------------------------------------------

class TestFcmChunkFitsConnectionPool:
    """`send_each_for_multicast` opens one thread per token; the pool must be able to hold them.

    `messaging.send_each_for_multicast` delegates to `send_each()`, which builds
    `ThreadPoolExecutor(max_workers=len(messages))` — one thread per token — while every
    thread shares one `requests.Session` whose urllib3 pool defaults to 10 connections.
    Chunking by FCM's 500-token cap therefore raced 500 threads over 10 slots and urllib3
    discarded the surplus: `Connection pool is full, discarding connection:
    fcm.googleapis.com`, 21 times in the 7 days to 2026-07-31, 8 of them on the
    then-current revision.

    The failure is silent by construction — every send still returns 2xx, so nothing in the
    response, the metrics or the error rate moves. It only ever showed up as a WARNING line
    that a human happened to read. That is exactly the kind of regression a test has to
    catch, because production will not complain the second time either.
    """

    @staticmethod
    def _assignments():
        source = (pathlib.Path(__file__).parent / "main.py").read_text(encoding="utf-8")
        tree = ast.parse(source)
        found = {}
        for node in ast.walk(tree):
            if not isinstance(node, ast.Assign):
                continue
            for target in node.targets:
                if isinstance(target, ast.Name) and isinstance(node.value, ast.Constant):
                    found[target.id] = node.value.value
        return found, tree

    def test_chunk_size_does_not_exceed_pool(self):
        found, _ = self._assignments()
        pool = found.get("_FCM_URLLIB3_POOL_MAXSIZE")
        assert pool is not None, (
            "_FCM_URLLIB3_POOL_MAXSIZE disappeared from main.py. It records the urllib3 "
            "default that firebase-admin does not override; without it the chunk size below "
            "is an unexplained magic number."
        )
        # The chunk may be written as the pool constant itself (an ast.Name, not a Constant),
        # which is the intended form — resolve that case before falling back to a literal.
        source = (pathlib.Path(__file__).parent / "main.py").read_text(encoding="utf-8")
        tree = ast.parse(source)
        chunk = None
        for node in ast.walk(tree):
            if isinstance(node, ast.Assign) and any(
                isinstance(t, ast.Name) and t.id == "_FCM_TOKENS_PER_MULTICAST"
                for t in node.targets
            ):
                if isinstance(node.value, ast.Name):
                    chunk = found.get(node.value.id)
                elif isinstance(node.value, ast.Constant):
                    chunk = node.value.value
        assert chunk is not None, "_FCM_TOKENS_PER_MULTICAST missing from main.py"
        assert chunk <= pool, (
            f"FCM chunk is {chunk} tokens but the HTTP pool holds {pool} connections. "
            "send_each_for_multicast opens one thread per token, so the surplus threads "
            "will fight over the pool and urllib3 will discard connections. Raise "
            "_FCM_URLLIB3_POOL_MAXSIZE together with the chunk, and make the pool actually "
            "that large (firebase-admin exposes no pool_maxsize — see the comment in main.py)."
        )

    def test_multicast_chunking_uses_the_constant(self):
        """A literal in the chunked() call would silently bypass the bound asserted above."""
        source = (pathlib.Path(__file__).parent / "main.py").read_text(encoding="utf-8")
        tree = ast.parse(source)
        offenders = []
        for node in ast.walk(tree):
            if not isinstance(node, ast.Call):
                continue
            if getattr(node.func, "attr", None) != "chunked":
                continue
            # chunked(seq, size) — a bare int for `size` is the regression we guard against.
            if len(node.args) >= 2 and isinstance(node.args[1], ast.Constant):
                offenders.append((node.lineno, node.args[1].value))
        assert not offenders, (
            f"chunked() called with a hardcoded size at main.py lines {offenders}. "
            "FCM token chunking must go through _FCM_TOKENS_PER_MULTICAST so the pool bound "
            "stays enforceable; Amplitude batching should use a named constant too, so that a "
            "reader can tell which limit a number belongs to."
        )


class TestAmplitudeBatchWithinApiLimit:
    """The other `chunked()` caller has its own, unrelated ceiling.

    `test_multicast_chunking_uses_the_constant` above only forbids a bare literal — it cannot
    tell whether the named constant holds a legal value. Amplitude's HTTP V2 API rejects a
    batch of more than 100 events outright (413), so a well-meaning "let's send fewer, larger
    batches" edit would break ingestion rather than degrade it. Both callers now share the
    same shape (named constant), which is precisely why each needs its own bound asserted:
    the FCM number is capped by a connection pool, this one by a remote API.
    """

    AMPLITUDE_HTTP_V2_MAX_EVENTS = 100

    def test_amplitude_batch_constant_exists_and_fits_the_api(self):
        source = (pathlib.Path(__file__).parent / "main.py").read_text(encoding="utf-8")
        tree = ast.parse(source)

        value = None
        for node in ast.walk(tree):
            if isinstance(node, ast.Assign) and any(
                isinstance(t, ast.Name) and t.id == "_AMPLITUDE_EVENTS_PER_BATCH"
                for t in node.targets
            ) and isinstance(node.value, ast.Constant):
                value = node.value.value

        assert value is not None, (
            "_AMPLITUDE_EVENTS_PER_BATCH missing from main.py. Without it the batch size at "
            "the call site is a bare number again, and a reader cannot tell which service's "
            "limit it encodes — the exact confusion that made the FCM chunk wrong."
        )
        assert isinstance(value, int) and value > 0, (
            f"_AMPLITUDE_EVENTS_PER_BATCH must be a positive int, got {value!r}"
        )
        assert value <= self.AMPLITUDE_HTTP_V2_MAX_EVENTS, (
            f"_AMPLITUDE_EVENTS_PER_BATCH is {value}, but Amplitude's HTTP V2 endpoint "
            f"rejects batches over {self.AMPLITUDE_HTTP_V2_MAX_EVENTS} events with a 413. "
            "Analytics would stop ingesting silently from the app's point of view — nothing "
            "in the product breaks, the data just stops arriving."
        )
