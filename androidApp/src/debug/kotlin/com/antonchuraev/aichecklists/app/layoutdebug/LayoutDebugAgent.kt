package com.antonchuraev.aichecklists.app.layoutdebug

import android.app.Activity
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
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
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.lang.reflect.Method
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private const val TAG = "layout-debug"
private const val PORT = 8790

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

    /** Cached layout nodes from the last capture — the targets overrides act on. */
    @Volatile
    private var nodes: Map<String, Any> = emptyMap()

    /** Original modifier per node, so an override can be undone exactly. */
    private val originals = HashMap<Any, Modifier>()
    private val overrides = HashMap<String, Override>()

    private data class Override(val dx: Int, val dy: Int, val w: Int, val h: Int)

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
                val socket = ServerSocket(PORT)
                server = socket
                Log.i(TAG, "агент слушает 127.0.0.1:$PORT — на компьютере: adb forward tcp:$PORT tcp:$PORT")
                while (!socket.isClosed) {
                    val client = socket.accept()
                    try {
                        handle(client)
                    } catch (e: Exception) {
                        Log.w(TAG, "запрос упал: ${e.message}")
                    } finally {
                        runCatching { client.close() }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "сервер не поднялся: ${e.message}", e)
            }
        }, "layout-debug-agent").apply { isDaemon = true }.start()
    }

    private fun handle(client: Socket) {
        val reader = client.getInputStream().bufferedReader()
        val requestLine = reader.readLine() ?: return
        val path = requestLine.split(' ').getOrNull(1) ?: return
        val query = path.substringAfter('?', "")
        val route = path.substringBefore('?')
        val params = query.split('&')
            .filter { it.contains('=') }
            .associate { it.substringBefore('=') to it.substringAfter('=') }

        val out = client.getOutputStream()
        when (route) {
            "/tree" -> respondJson(out, captureTree())
            "/screenshot" -> respondPng(out, screenshot())
            "/override" -> respondJson(out, applyOverride(params))
            "/overrides/clear" -> respondJson(out, clearOverrides())
            "/health" -> respondJson(out, """{"ok":true,"port":$PORT}""")
            else -> respond(out, "404 Not Found", "application/json", """{"error":"unknown route"}""".toByteArray())
        }
    }

    private fun respondJson(out: OutputStream, body: String) =
        respond(out, "200 OK", "application/json; charset=utf-8", body.toByteArray())

    private fun respondPng(out: OutputStream, body: ByteArray?) {
        if (body == null) {
            respondJson(out, """{"error":"скриншот недоступен — нет активной Activity"}""")
        } else {
            respond(out, "200 OK", "image/png", body)
        }
    }

    private fun respond(out: OutputStream, status: String, contentType: String, body: ByteArray) {
        val header = buildString {
            append("HTTP/1.1 ").append(status).append("\r\n")
            append("Content-Type: ").append(contentType).append("\r\n")
            append("Content-Length: ").append(body.size).append("\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("Connection: close\r\n\r\n")
        }
        out.write(header.toByteArray())
        out.write(body)
        out.flush()
    }

    // --- capture ------------------------------------------------------------

    private fun captureTree(): String {
        val activity = current ?: return """{"error":"нет активной Activity"}"""
        val result = onMain {
            val root = activity.findViewById<View>(android.R.id.content)
            val density = activity.resources.displayMetrics.density
            TreeCapture.capture(density, root.width, root.height)
        } ?: return """{"error":"таймаут захвата на главном потоке"}"""
        nodes = result.nodes
        reapplyOverrides()
        return result.json
    }

    private fun screenshot(): ByteArray? {
        val activity = current ?: return null
        val view = activity.findViewById<View>(android.R.id.content) ?: return null
        if (view.width <= 0 || view.height <= 0) return null

        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val location = IntArray(2)
        onMain { view.getLocationInWindow(location) }

        val latch = CountDownLatch(1)
        var ok = false
        val thread = HandlerThread("layout-debug-pixelcopy").apply { start() }
        try {
            PixelCopy.request(
                activity.window,
                android.graphics.Rect(
                    location[0], location[1],
                    location[0] + view.width, location[1] + view.height,
                ),
                bitmap,
                { result ->
                    ok = result == PixelCopy.SUCCESS
                    latch.countDown()
                },
                Handler(thread.looper),
            )
            if (!latch.await(3, TimeUnit.SECONDS) || !ok) return null
        } finally {
            thread.quitSafely()
        }

        return ByteArrayOutputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.toByteArray()
        }
    }

    // --- live overrides -----------------------------------------------------

    private fun applyOverride(params: Map<String, String>): String {
        val id = params["id"] ?: return """{"error":"нет параметра id"}"""
        val override = Override(
            dx = params["dx"]?.toIntOrNull() ?: 0,
            dy = params["dy"]?.toIntOrNull() ?: 0,
            w = params["w"]?.toIntOrNull() ?: -1,
            h = params["h"]?.toIntOrNull() ?: -1,
        )
        val node = nodes[id] ?: return """{"error":"узел $id не из последнего снимка"}"""
        if (ModifierAccess.of(node) == null) return """{"error":"у узла $id нет доступного modifier"}"""

        overrides[id] = override
        onMain { apply(id, node, override) }
        return """{"ok":true,"id":"$id"}"""
    }

    /**
     * Prepending the tweak means it wraps the node's own modifier chain, so the whole
     * element moves — background, padding and all — instead of just its content.
     *
     * The next recomposition of this composable writes its modifier back from source
     * and drops the tweak, which is why every capture re-applies what is still active.
     */
    private fun apply(id: String, node: Any, override: Override) {
        val access = ModifierAccess.of(node) ?: return
        val base = originals.getOrPut(node) { access.get(node) ?: return }
        var tweak: Modifier = Modifier
        if (override.dx != 0 || override.dy != 0) {
            tweak = tweak.offset { IntOffset(override.dx, override.dy) }
        }
        if (override.w >= 0 && override.h >= 0) {
            val density = current?.resources?.displayMetrics?.density ?: 1f
            tweak = tweak.requiredSize((override.w / density).dp, (override.h / density).dp)
        }
        access.set(node, tweak.then(base))
        Log.i(TAG, "оверрайд $id: dx=${override.dx} dy=${override.dy} w=${override.w} h=${override.h}")
    }

    private fun reapplyOverrides() {
        if (overrides.isEmpty()) return
        onMain {
            for ((id, override) in overrides) {
                val node = nodes[id] ?: continue
                apply(id, node, override)
            }
        }
    }

    private fun clearOverrides(): String {
        val count = overrides.size
        overrides.clear()
        onMain {
            for ((node, base) in originals) ModifierAccess.of(node)?.set(node, base)
            originals.clear()
        }
        return """{"ok":true,"cleared":$count}"""
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

    /** Compose state must be touched on the main thread; the HTTP thread is not it. */
    private fun <T> onMain(block: () -> T): T? {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        var result: T? = null
        val latch = CountDownLatch(1)
        main.post {
            try {
                result = block()
            } catch (e: Throwable) {
                Log.e(TAG, "работа на главном потоке упала: ${e.message}", e)
            } finally {
                latch.countDown()
            }
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
