package com.antonchuraev.aichecklists.startup

import android.app.Application
import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.startup.AppInitializer
import androidx.startup.InitializationProvider
import androidx.startup.Initializer
import androidx.work.WorkManager
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Nothing may initialize WorkManager on the process-start path.
 *
 * On 2026-08-13 one Pixel 8 Pro on 1.19.1 (vc80) could not launch the app at all — 8 fatal events,
 * every one of them:
 *
 * ```
 * Unable to get provider androidx.startup.InitializationProvider:
 *   NoSuchMethodError: No virtual method forNamespace(...)Landroid/app/job/JobScheduler;
 *   at androidx.work.impl.background.systemjob.JobScheduler34.forNamespace
 *   at androidx.work.impl.WorkManagerImpl.initialize
 *   at androidx.work.WorkManagerInitializer.create
 *   at androidx.startup.AppInitializer.doInitialize
 *   at androidx.startup.InitializationProvider.onCreate
 *   at android.app.ActivityThread.installProvider
 * ```
 *
 * The library is not at fault: WorkManager 2.11.2 guards the call with `SDK_INT >= 34` and the ROM
 * claims API 34 while shipping a `framework.jar` without `JobScheduler.forNamespace`. What made a
 * device-specific ROM gap fatal is WHERE the call happens — `androidx.work.WorkManagerInitializer`
 * is declared under `androidx.startup.InitializationProvider` in the merged manifest, so it runs in
 * `ContentProvider.onCreate()`, BEFORE `Application.onCreate()`. A throw there is uncatchable by
 * the app and kills the process before any code of ours runs. The app itself schedules no work
 * (`WidgetUpdateWorker` is never enqueued), so the whole cost is paid for nothing.
 *
 * Both tests pin the same invariant at the two layers it is observable on — the build artifact and
 * the runtime — because the fix has to hold in both: dropping the meta-data with
 * `tools:node="remove"` and moving to lazy `Configuration.Provider` init on the Application.
 *
 * Run: ./gradlew :androidApp:testDebugUnitTest --tests "*AppStartupWorkManagerTest*"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AppStartupWorkManagerTest {

    private companion object {
        const val WORK_MANAGER_INITIALIZER = "androidx.work.WorkManagerInitializer"

        /** The meta-data value androidx.startup looks for when discovering initializers. */
        const val STARTUP_VALUE = "androidx.startup"
    }

    /**
     * Build layer. Reads the APK's merged manifest through the same call `AppInitializer` makes —
     * `getProviderInfo(InitializationProvider, GET_META_DATA)` — and requires WorkManager to be
     * absent from the eager-initializer list.
     */
    @Test
    fun mergedManifest_startupProvider_doesNotDeclareWorkManagerInitializer() {
        val declared = declaredStartupInitializers()

        // Guard against a vacuous pass: an empty read (wrong component, no GET_META_DATA, manifest
        // not wired into the test) would satisfy the assertion below for the wrong reason.
        assertTrue(
            declared.isNotEmpty(),
            "The merged manifest lookup returned no androidx.startup initializers at all, so the " +
                "WorkManager assertion cannot be trusted. Fix the lookup first."
        )

        assertFalse(
            WORK_MANAGER_INITIALIZER in declared,
            "$WORK_MANAGER_INITIALIZER is declared under androidx.startup.InitializationProvider " +
                "in the merged manifest, so WorkManager is built on EVERY process start inside " +
                "ContentProvider.onCreate() — before Application.onCreate(), where a throw is " +
                "fatal and uncatchable. That is the 2026-08-13 crash on 1.19.1 " +
                "(JobScheduler.forNamespace NoSuchMethodError, Pixel 8 Pro / Android 14). This " +
                "app enqueues no work, so nothing may sit on the startup path. Declared " +
                "initializers: $declared"
        )
    }

    /**
     * Runtime layer. Drives the production entry point (`InitializationProvider.onCreate()`, the
     * frame `ActivityThread.installProvider` calls) and requires WorkManager to still be
     * uninitialized afterwards.
     */
    @Test
    fun startupProviderOnCreate_leavesWorkManagerUninitialized() {
        val declared = declaredStartupInitializers()
        val probe = (declared - WORK_MANAGER_INITIALIZER).firstNotNullOfOrNull(::loadInitializer)
        assertNotNull(
            probe,
            "No loadable non-WorkManager initializer found in $declared — without one there is no " +
                "way to prove androidx.startup discovery actually ran, and the assertion below " +
                "would pass vacuously."
        )

        Robolectric.buildContentProvider(InitializationProvider::class.java).create()

        assertTrue(
            AppInitializer.getInstance(RuntimeEnvironment.getApplication()).isEagerlyInitialized(probe),
            "androidx.startup discovery did not run (${probe.name} was never initialized), so the " +
                "WorkManager check below proves nothing."
        )

        assertFalse(
            WorkManager.isInitialized(),
            "WorkManager was initialized by app startup alone — no app code asked for it. It is " +
                "created inside ContentProvider.onCreate(), before Application.onCreate(), where " +
                "any failure (e.g. the 2026-08-13 JobScheduler.forNamespace NoSuchMethodError) " +
                "kills the process before the app can run or report. WorkManager must be built " +
                "lazily, on first use."
        )
    }

    /**
     * The other half of the fix. Removing the initializer only moves the build to the first
     * `WorkManager.getInstance(context)`; without [Configuration.Provider] on the Application that
     * call throws `IllegalStateException` instead — one crash traded for another.
     *
     * This is not hypothetical: no app code enqueues work, but Glance does. `GlanceAppWidget.update()`
     * runs through `SessionManagerImpl.startSession()`, which calls `WorkManager.getInstance(context)`
     * + `enqueueUniqueWork(...)` on every home-screen widget session. So the provider is load-bearing,
     * and "the app never uses WorkManager, drop this" is exactly the wrong refactor.
     *
     * Names the class directly. Resolving it from the merged manifest would be stricter — it would
     * also catch someone repointing `android:name` — but `@Config(application = Application::class)`
     * above replaces `ApplicationInfo.className` too, so the manifest reports `android.app.Application`
     * here. Booting the real [GistiAndroidApplication] under Robolectric to recover that check would
     * drag in Koin, Firebase and Amplitude and make this test fail for unrelated reasons. NOT covered
     * as a result: swapping the manifest's `android:name` to a class without the interface.
     */
    @Test
    fun applicationClass_providesWorkManagerConfiguration() {
        val declaredName = "com.antonchuraev.homesearchchecklist.GistiAndroidApplication"

        val appClass = requireNotNull(runCatching { Class.forName(declaredName) }.getOrNull()) {
            "$declaredName is not loadable from the test classpath — the application class was " +
                "renamed or moved. Update this test to follow it; do not delete the check."
        }

        assertTrue(
            androidx.work.Configuration.Provider::class.java.isAssignableFrom(appClass),
            "$declaredName does not implement androidx.work.Configuration.Provider. The manifest " +
                "removes androidx.work.WorkManagerInitializer from androidx.startup, so WorkManager " +
                "is built on demand — and on-demand init REQUIRES this interface. Without it the " +
                "first WorkManager.getInstance(context) throws IllegalStateException. That call is " +
                "reached on every home-screen widget update via Glance's SessionManagerImpl, so " +
                "removing the provider as 'unused' breaks the widget on every device."
        )
    }

    /** Startup initializers the merged manifest asks androidx.startup to run eagerly. */
    private fun declaredStartupInitializers(): Set<String> {
        val context = RuntimeEnvironment.getApplication()
        val component = ComponentName(context.packageName, InitializationProvider::class.java.name)
        val metaData = context.packageManager
            .getProviderInfo(component, PackageManager.GET_META_DATA)
            .metaData
            ?: return emptySet()
        return metaData.keySet()
            .filter { metaData.getString(it) == STARTUP_VALUE }
            .toSortedSet()
    }

    @Suppress("UNCHECKED_CAST")
    private fun loadInitializer(className: String): Class<out Initializer<*>>? =
        runCatching { Class.forName(className) }
            .getOrNull()
            ?.takeIf { Initializer::class.java.isAssignableFrom(it) }
            ?.let { it as Class<out Initializer<*>> }
}
