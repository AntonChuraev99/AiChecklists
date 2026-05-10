package com.antonchuraev.aichecklists

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Screenshot capture helpers for [ScreenshotCatalogTest].
 *
 * On API 29+ (Android 10+) PNGs are written to the shared MediaStore location:
 *   /sdcard/Pictures/GistiScreenshots/<name>.png
 * This directory lives OUTSIDE the app's private storage and is NOT wiped by
 * `pm clear <package>` (Test Orchestrator's clearPackageData). Screenshots survive
 * even after a failed test run.
 *
 * On API 24-28 (rare in practice; our emulators run API 36) we fall back to
 * a public /sdcard/Pictures/GistiScreenshots/ path created via the legacy File
 * API — this path also survives `pm clear`.
 *
 * Counter [screenshotIndex] produces zero-padded prefixes (01_, 02_, …) so the
 * output folder lists in capture order.
 */

internal var screenshotIndex = 0

/** Relative path for MediaStore (API 29+). */
private const val RELATIVE_PATH = "Pictures/GistiScreenshots"

/** Absolute fallback path for API 24-28. */
private const val LEGACY_SDCARD_DIR = "/sdcard/Pictures/GistiScreenshots"

/** Delete prior screenshots from the GistiScreenshots collection before a new run. */
internal fun clearScreenshotsDir() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    screenshotIndex = 0

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        // API 29+: delete via MediaStore query
        val contentResolver = instrumentation.context.contentResolver
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} = ?"
        val selectionArgs = arrayOf("$RELATIVE_PATH/")
        contentResolver.delete(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            selection,
            selectionArgs
        )
    } else {
        // API 24-28: delete the legacy directory
        File(LEGACY_SDCARD_DIR).deleteRecursively()
        File(LEGACY_SDCARD_DIR).mkdirs()
    }
}

/**
 * Write [bytes] as a PNG to the shared Pictures/GistiScreenshots directory.
 *
 * On API 29+ uses MediaStore so no permission is needed and `pm clear` won't
 * delete the file. On API 24-28 writes to /sdcard/Pictures/GistiScreenshots/
 * via the legacy File API (requires WRITE_EXTERNAL_STORAGE in androidTest manifest
 * for those API levels).
 */
private fun savePng(name: String, bytes: ByteArray) {
    val fileName = "%02d_%s.png".format(screenshotIndex++, name)
    val instrumentation = InstrumentationRegistry.getInstrumentation()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val contentResolver = instrumentation.context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_PATH)
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("MediaStore.insert returned null for $fileName")
        contentResolver.openOutputStream(uri)!!.use { it.write(bytes) }
    } else {
        val dir = File(LEGACY_SDCARD_DIR).also { it.mkdirs() }
        FileOutputStream(File(dir, fileName)).use { it.write(bytes) }
    }
}

/** Compress a [Bitmap] to a PNG [ByteArray]. */
private fun Bitmap.toPngBytes(): ByteArray {
    val out = ByteArrayOutputStream()
    compress(Bitmap.CompressFormat.PNG, 100, out)
    return out.toByteArray()
}

/**
 * Write raw bytes to the shared GistiScreenshots directory under [fileName].
 * Used by [saveTextFile] for logcat dumps.
 */
internal fun saveTextFile(fileName: String, bytes: ByteArray) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val contentResolver = instrumentation.context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_PATH)
        }
        // Use MediaStore.Files for non-image content (text/plain)
        val uri = MediaStore.Files.getContentUri("external").let { collection ->
            contentResolver.insert(collection, values)
        } ?: error("MediaStore.insert returned null for $fileName")
        contentResolver.openOutputStream(uri)!!.use { it.write(bytes) }
    } else {
        val dir = File(LEGACY_SDCARD_DIR).also { it.mkdirs() }
        FileOutputStream(File(dir, fileName)).use { it.write(bytes) }
    }
}

/**
 * Capture a Compose screenshot via [ComposeTestRule.onRoot].
 *
 * NOTE: This uses the Compose semantics tree — it works for any Compose surface.
 * For native system dialogs (share sheet, permission dialogs) use [captureSystemScreenshot].
 */
internal fun ComposeTestRule.captureScreenshot(name: String) {
    val bitmap = onRoot().captureToImage().asAndroidBitmap()
    savePng(name, bitmap.toPngBytes())
}

/**
 * Capture a screenshot via UiAutomator — works for native system dialogs, overlays, and
 * any view outside the Compose hierarchy (e.g. the Android share sheet).
 *
 * UiAutomator's [UiDevice.takeScreenshot] requires a [File] path. We write to a temp
 * file in the test app's cache dir (always writable, unaffected by pm clear of the
 * target app), then copy it into the shared GistiScreenshots directory via [savePng].
 */
internal fun captureSystemScreenshot(name: String) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    // Use the TEST app's cache dir — immune to pm clear of the target package.
    val tempFile = File(instrumentation.context.cacheDir, "tmp_screenshot.png")
    val device = UiDevice.getInstance(instrumentation)
    device.takeScreenshot(tempFile)
    savePng(name, tempFile.readBytes())
    tempFile.delete()
}

/**
 * Best-effort "no loading" wait: tries to ensure no Compose loading indicators are
 * still visible before a screenshot is taken.
 *
 * There is no project-wide convention for loading testTags; this is a best-effort guard.
 * Anchor-based waits in the test body are the primary correctness lever.
 *
 * The three sub-waits:
 *  a) waits until any node with testTag "loading" disappears
 *  b) waits until the Splash "Getting things ready..." text disappears
 *  c) waits for Compose to be idle
 */
internal fun ComposeTestRule.awaitNoLoading(timeoutMs: Long = 5000) {
    // (a) generic loading testTag
    try {
        waitUntil(timeoutMillis = timeoutMs) {
            onAllNodes(hasTestTag("loading")).fetchSemanticsNodes().isEmpty()
        }
    } catch (_: AssertionError) {
        // No "loading" nodes found at all — that's fine
    }
    // (b) Splash anchor
    try {
        waitUntil(timeoutMillis = timeoutMs) {
            onAllNodesWithText("Getting things ready...").fetchSemanticsNodes().isEmpty()
        }
    } catch (_: AssertionError) {
        // Text was never present — that's fine
    }
    // (c) Compose idle
    waitForIdle()
}

/**
 * Composite capture: waits until [anchor] text is visible, drains loading, then saves PNG.
 *
 * The default [timeoutMs] is 30 000 ms to accommodate slow cold-start screens (fresh
 * install after Test Orchestrator's pm clear — Firebase RC, RevenueCat, and anonymous
 * auth all initialise from scratch).
 */
internal fun ComposeTestRule.captureFinal(name: String, anchor: String, timeoutMs: Long = 30_000) {
    waitUntil(timeoutMillis = timeoutMs) {
        onAllNodesWithText(anchor).fetchSemanticsNodes().isNotEmpty()
    }
    awaitNoLoading()
    captureScreenshot(name)
}
