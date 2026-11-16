package com.antonchuraev.aichecklists.app.layoutdebug

import android.app.Activity
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.view.PixelCopy
import android.view.View
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.layoutdebug.LayoutDebugBridge
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.lang.reflect.Method
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "layout-debug"
private const val PORT = 8790

/** A client that stalls mid-request must not block the single-threaded server forever. */
private const val READ_TIMEOUT_MS = 5_000
private const val MAX_HEADER_LINES = 100

/**
 * Debug-only agent: serves the live Compose tree, a screenshot, and a live override
 * channel over loopback HTTP. Reach it from a computer with:
 *
 *     adb forward tcp:8790 tcp:8790
 *
 * Lives in `src/debug`, so release builds do not contain this class at all.
 */
object LayoutDebugAgent {

    @Volatile
    private var current: Activity? = null
    private var server: ServerSocket? = null

    // State below is confined to the main thread. The HTTP thread reaches it only through
    // onMain(), so a request that timed out can never race an iteration still running there.

    /** Layout nodes of the last capture by id — the address space of `/override`. */
    private var nodes: Map<String, Any> = emptyMap()

    /**
     * Active overrides keyed by layout node *instance*. Most ids are positional and shift
     * between captures (a new subcomposition, a collected slot table), so re-applying by id
     * would move a different element; the instance is what the user actually picked.
     */
    private val overrides = IdentityHashMap<Any, Active>()

    private data class Override(val dx: Int, val dy: Int, val w: Int, val h: Int)

    /**
     * One applied override. [applied] is the modifier this agent wrote into the node, [base]
     * the node's own modifier it wraps. A current modifier that is no longer [applied] means
     * recomposition wrote a fresh one, which then becomes the base.
     */
    private class Active(val id: String, val override: Override, val base: Modifier, val applied: Modifier)

    private val main = Handler(Looper.getMainLooper())

    fun install(app: Application) {
        LayoutDebugBridge.enabled = true
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                current = activity
            }

            override fun onActivityPaused(activity: Activity) {
                if (current === activity) current = null
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
        startServer()
    }

    // --- http ---------------------------------------------------------------

    private fun startServer() {
        Thread({
            try {
                // Loopback only: `ServerSocket(PORT)` binds 0.0.0.0, which would expose the
                // screen tree (user content included) and the override channel to the whole
                // Wi-Fi network. `adb forward` reaches the device's loopback, so nothing is lost.
                val socket = ServerSocket(PORT, 0, InetAddress.getLoopbackAddress())
                server = socket
                Log.i(TAG, "агент слушает 127.0.0.1:$PORT — на компьютере: adb forward tcp:$PORT tcp:$PORT")
                while (!socket.isClosed) {
                    val client = socket.accept()
                    runCatching { handle(client) }
                        .onFailure { Log.w(TAG, "запрос упал: ${it.message}", it) }
                    runCatching { client.close() }
                }
            } catch (e: Exception) {
                Log.e(TAG, "сервер не поднялся: ${e.message}", e)
            }
        }, "layout-debug-agent").apply { isDaemon = true }.start()
    }

    private fun handle(client: Socket) {
        client.soTimeout = READ_TIMEOUT_MS
        val reader = client.getInputStream().bufferedReader()
        val requestLine = reader.readLine() ?: return
        val headers = readHeaders(reader)
        val out = client.getOutputStream()
        if (isBrowserRequest(headers)) {
            Log.w(TAG, "отклонён запрос из браузера: $requestLine (sec-fetch-site=${headers["sec-fetch-site"]}, origin=${headers["origin"]})")
            respond(out, "403 Forbidden", JSON, """{"error":"cross-origin request refused"}""".toByteArray())
            return
        }

        val path = requestLine.split(' ').getOrNull(1) ?: return
        val query = path.substringAfter('?', "")
        val route = path.substringBefore('?')
        val params = query.split('&')
            .filter { it.contains('=') }
            .associate { it.substringBefore('=') to it.substringAfter('=') }

        when (route) {
            "/tree" -> respondJson(out, captureTree())
            "/screenshot" -> respondScreenshot(out, screenshot())
            "/override" -> respondJson(out, applyOverride(params))
            "/overrides/clear" -> respondJson(out, clearOverrides())
            "/health" -> respondJson(out, """{"ok":true,"port":$PORT}""")
            else -> respond(out, "404 Not Found", "application/json", """{"error":"unknown route"}""".toByteArray())
        }
    }

    /** Header names lowercased; reads up to the blank line that ends the request head. */
    private fun readHeaders(reader: BufferedReader): Map<String, String> {
        val headers = HashMap<String, String>()
        repeat(MAX_HEADER_LINES) {
            val line = reader.readLine()
            if (line.isNullOrEmpty()) return headers
            val colon = line.indexOf(':')
            if (colon > 0) headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
        }
        return headers
    }

    /**
     * With `adb forward` up, any page open in a desktop browser can reach this port. Browsers
     * attach `Sec-Fetch-Site` to every request — `<img>` and no-cors GETs to localhost included —
     * and `Origin` to cross-origin ones; the desktop tool's Node client sends neither. `none`
     * means a user typed the URL into the address bar, which is not a cross-site request.
     */
    private fun isBrowserRequest(headers: Map<String, String>): Boolean {
        if ("origin" in headers) return true
        val site = headers["sec-fetch-site"] ?: return false
        return !site.equals("none", ignoreCase = true)
    }

    private fun respondJson(out: OutputStream, body: String) =
        respond(out, "200 OK", JSON, body.toByteArray())

    private fun respondScreenshot(out: OutputStream, shot: Result<ByteArray>) {
        shot.fold(
            onSuccess = { respond(out, "200 OK", "image/png", it) },
            // Non-2xx: the client reads a 200 body as PNG bytes, so an error must not be one.
            onFailure = {
                val body = """{"error":${quote("скриншот недоступен — ${it.message}")}}"""
                respond(out, "503 Service Unavailable", JSON, body.toByteArray())
            },
        )
    }

    private fun respond(out: OutputStream, status: String, contentType: String, body: ByteArray) {
        val header = buildString {
            append("HTTP/1.1 ").append(status).append("\r\n")
            append("Content-Type: ").append(contentType).append("\r\n")
            append("Content-Length: ").append(body.size).append("\r\n")
            append("Connection: close\r\n\r\n")
        }
        out.write(header.toByteArray())
        out.write(body)
        out.flush()
    }

    // --- capture ------------------------------------------------------------

    private fun captureTree(): String {
        val activity = current ?: return """{"error":"нет активной Activity"}"""
        val outcome = onMain {
            val root = activity.findViewById<View>(android.R.id.content)
            val density = activity.resources.displayMetrics.density
            val result = TreeCapture.capture(density, root.width, root.height)
            nodes = result.nodes
            reapplyOverrides(result.nodes)
            result.json
        } ?: return """{"error":"таймаут захвата на главном потоке"}"""
        return outcome.getOrElse { """{"error":${quote("захват упал: ${it.message}")}}""" }
    }

    private fun screenshot(): Result<ByteArray> = runCatching {
        // PixelCopy over a Window exists from API 26; minSdk is lower.
        check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { "нужен Android 8.0+, у устройства API ${Build.VERSION.SDK_INT}" }
        val activity = current ?: error("нет активной Activity")
        val frame = onMain {
            val view = activity.findViewById<View>(android.R.id.content) ?: error("у Activity нет content-view")
            check(view.width > 0 && view.height > 0) { "content-view не измерен (${view.width}x${view.height})" }
            val location = IntArray(2).also(view::getLocationInWindow)
            Rect(location[0], location[1], location[0] + view.width, location[1] + view.height)
        } ?: error("главный поток не ответил за 3 с")
        val rect = frame.getOrThrow()

        val bitmap = Bitmap.createBitmap(rect.width(), rect.height(), Bitmap.Config.ARGB_8888)
        val latch = CountDownLatch(1)
        val code = AtomicInteger(PixelCopy.ERROR_UNKNOWN)
        val thread = HandlerThread("layout-debug-pixelcopy").apply { start() }
        val answered = runCatching {
            PixelCopy.request(
                activity.window,
                rect,
                bitmap,
                { result ->
                    code.set(result)
                    latch.countDown()
                },
                Handler(thread.looper),
            )
            latch.await(3, TimeUnit.SECONDS)
        }
        thread.quitSafely()
        check(answered.getOrThrow()) { "PixelCopy не ответил за 3 с" }
        check(code.get() == PixelCopy.SUCCESS) { "PixelCopy вернул ${pixelCopyError(code.get())}" }

        ByteArrayOutputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.toByteArray()
        }
    }

    private fun pixelCopyError(code: Int): String = when (code) {
        PixelCopy.ERROR_UNKNOWN -> "ERROR_UNKNOWN"
        PixelCopy.ERROR_TIMEOUT -> "ERROR_TIMEOUT"
        PixelCopy.ERROR_SOURCE_NO_DATA -> "ERROR_SOURCE_NO_DATA (окно ещё не отрисовано)"
        PixelCopy.ERROR_SOURCE_INVALID -> "ERROR_SOURCE_INVALID"
        PixelCopy.ERROR_DESTINATION_INVALID -> "ERROR_DESTINATION_INVALID"
        else -> "код $code"
    }

    // --- live overrides -----------------------------------------------------

    private fun applyOverride(params: Map<String, String>): String {
        val id = params["id"] ?: return failure("нет параметра id")
        val override = Override(
            dx = params["dx"]?.toIntOrNull() ?: 0,
            dy = params["dy"]?.toIntOrNull() ?: 0,
            w = params["w"]?.toIntOrNull() ?: -1,
            h = params["h"]?.toIntOrNull() ?: -1,
        )
        // Null = applied; a string = why not.
        val outcome = onMain<String?> {
            val node = nodes[id] ?: return@onMain "узел $id не из последнего снимка"
            val access = ModifierAccess.of(node) ?: return@onMain "у узла $id нет доступного modifier"
            val currentModifier = access.get(node) ?: return@onMain "modifier узла $id не читается"
            overrides[node] = apply(id, node, override, access, currentModifier, overrides[node])
            null
        } ?: return failure("главный поток не ответил за 3 с — оверрайд может примениться позже")
        return outcome.fold(
            onSuccess = { refusal -> if (refusal == null) """{"ok":true,"id":"$id"}""" else failure(refusal) },
            onFailure = { failure("оверрайд $id упал: ${it.message}") },
        )
    }

    /**
     * Prepending the tweak means it wraps the node's own modifier chain, so the whole
     * element moves — background, padding and all — instead of just its content.
     *
     * The next recomposition that changes this composable's modifier writes a fresh one and
     * drops the tweak, which is why every capture re-applies what is still active — on top
     * of that fresh modifier, never on a stale copy (a stale `clickable` would call an old
     * lambda). Main thread only.
     */
    private fun apply(
        id: String,
        node: Any,
        override: Override,
        access: ModifierAccess.Accessors,
        currentModifier: Modifier,
        previous: Active?,
    ): Active {
        val base = if (previous != null && currentModifier === previous.applied) previous.base else currentModifier
        var tweak: Modifier = Modifier
        if (override.dx != 0 || override.dy != 0) {
            tweak = tweak.offset { IntOffset(override.dx, override.dy) }
        }
        if (override.w >= 0 && override.h >= 0) {
            val density = current?.resources?.displayMetrics?.density ?: 1f
            tweak = tweak.requiredSize((override.w / density).dp, (override.h / density).dp)
        }
        val applied = tweak.then(base)
        access.set(node, applied)
        Log.i(TAG, "оверрайд $id: dx=${override.dx} dy=${override.dy} w=${override.w} h=${override.h}")
        return Active(id, override, base, applied)
    }

    /** Main thread only. Nodes absent from this capture keep their entry but are not touched. */
    private fun reapplyOverrides(captured: Map<String, Any>) {
        if (overrides.isEmpty()) return
        val present = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())
        present.addAll(captured.values)
        for (entry in overrides.entries) {
            val node = entry.key
            if (node !in present) continue
            val active = entry.value
            runCatching {
                val access = ModifierAccess.of(node) ?: error("нет modifier-аксессоров")
                val currentModifier = access.get(node) ?: error("modifier не читается")
                apply(active.id, node, active.override, access, currentModifier, active)
            }
                .onSuccess { entry.setValue(it) }
                .onFailure { Log.w(TAG, "переприменение оверрайда ${active.id} упало: ${it.message}", it) }
        }
    }

    private fun clearOverrides(): String {
        // (cleared, failed)
        val outcome = onMain {
            val total = overrides.size
            var failed = 0
            for ((node, active) in overrides) {
                runCatching {
                    val access = ModifierAccess.of(node) ?: error("нет modifier-аксессоров")
                    // Anything but our own modifier was written by recomposition and is current.
                    if (access.get(node) === active.applied) access.set(node, active.base)
                }.onFailure {
                    failed++
                    Log.w(TAG, "не снял оверрайд ${active.id}: ${it.message}", it)
                }
            }
            overrides.clear()
            total to failed
        } ?: return failure("главный поток не ответил за 3 с — сброс может выполниться позже")
        return outcome.fold(
            onSuccess = { (total, failed) ->
                if (failed == 0) {
                    """{"ok":true,"cleared":$total}"""
                } else {
                    failure("не снято $failed из $total оверрайдов — подробности в logcat $TAG")
                }
            },
            onFailure = { failure("сброс упал: ${it.message}") },
        )
    }

    /**
     * `LayoutNode.modifier` is reachable only by reflection: the interface declaring it
     * is `internal` in this Compose build, and Kotlin mangles internal member names, so
     * the accessors are matched by prefix rather than by exact signature.
     */
    private object ModifierAccess {
        class Accessors(private val getter: Method, private val setter: Method) {
            fun get(node: Any): Modifier? = getter.invoke(node) as? Modifier
            fun set(node: Any, modifier: Modifier) {
                setter.invoke(node, modifier)
            }
        }

        private val cache = HashMap<Class<*>, Accessors?>()

        fun of(node: Any): Accessors? = synchronized(cache) {
            cache.getOrPut(node.javaClass) {
                val methods = node.javaClass.methods
                val getter = methods.firstOrNull {
                    it.parameterCount == 0 &&
                        it.name.startsWith("getModifier") &&
                        Modifier::class.java.isAssignableFrom(it.returnType)
                }
                val setter = methods.firstOrNull {
                    it.parameterCount == 1 &&
                        it.name.startsWith("setModifier") &&
                        it.parameterTypes[0].isAssignableFrom(Modifier::class.java)
                }
                if (getter != null && setter != null) {
                    Accessors(getter, setter)
                } else {
                    Log.w(TAG, "не нашёл modifier-аксессоры у ${node.javaClass.name}")
                    null
                }
            }
        }
    }

    // --- helpers ------------------------------------------------------------

    private const val JSON = "application/json; charset=utf-8"

    private fun failure(reason: String): String = """{"ok":false,"error":${quote(reason)}}"""

    private fun quote(value: String): String = buildString {
        append('"')
        for (ch in value) {
            when (ch) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (ch < ' ') append("\\u%04x".format(ch.code)) else append(ch)
            }
        }
        append('"')
    }

    /**
     * Compose state must be touched on the main thread; the HTTP thread is not it.
     * Null when the main thread did not finish within 3 s (the block may still run later),
     * otherwise the block's own outcome.
     */
    private fun <T> onMain(block: () -> T): Result<T>? {
        if (Looper.myLooper() == Looper.getMainLooper()) return runCatching(block)
        var result: Result<T>? = null
        val latch = CountDownLatch(1)
        main.post {
            result = runCatching(block)
                .onFailure { Log.e(TAG, "работа на главном потоке упала: ${it.message}", it) }
            latch.countDown()
        }
        return if (latch.await(3, TimeUnit.SECONDS)) result else null
    }
}

/** Zero-config startup: the system instantiates providers before Application.onCreate finishes. */
class LayoutDebugInstaller : ContentProvider() {
    override fun onCreate(): Boolean {
        (context?.applicationContext as? Application)?.let(LayoutDebugAgent::install)
        return true
    }

    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?): Int = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?): Int = 0
}
