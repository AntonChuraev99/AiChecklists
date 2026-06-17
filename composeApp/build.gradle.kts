@file:Suppress("DEPRECATION", "OPT_IN_USAGE")

import java.util.Properties
import org.jetbrains.kotlin.gradle.targets.wasm.binaryen.BinaryenExec

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.jetbrains.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.antonchuraev.aichecklists"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        withHostTest {}
        androidResources {
            enable = true
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            //todo rename
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        // Capture path at config time so KotlinWebpack task doesn't drag a Project reference.
        val staticServePath = layout.projectDirectory.dir("src/wasmJsMain/resources").asFile.absolutePath
        browser {
            commonWebpackConfig {
                outputFileName = "composeApp.js"
                devServer = (devServer ?: org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig.DevServer()).apply {
                    port = 9090
                    open = false
                    static = (static ?: mutableListOf()).apply {
                        add(staticServePath)
                    }
                }
            }
        }
        binaries.executable()
    }

    sourceSets {
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)

            // Koin Android — required for androidContext() / androidLogger() in GistiApplication
            implementation(libs.koin.android)

            // Glance for App Widgets — needed for InAppReviewLauncher.android.kt and widget actuals
            implementation(libs.glance)
            implementation(libs.glance.appwidget)

            // WorkManager for widget sync
            implementation(libs.workmanager)

            // Google Play In-App Review — needed for InAppReviewLauncher.android.kt actual
            implementation(libs.play.review.ktx)

            // Google Play In-App Updates — needed for AppUpdateController + AppUpdateLauncher.android.kt actual
            implementation(libs.play.app.update.ktx)

            // Amplitude Analytics — needed for Analytics.kt (actual platform code)
            implementation(libs.amplitude.analytics)
            implementation(libs.play.services.appset)

            // Firebase — Analytics, Crashlytics, ConsentManager actuals stay in composeApp/androidMain
            // (runtime init via google-services plugin happens in androidApp)
            // BOM is added via dependencies { add(...) } below — KMP DSL deprecates platform() in sourceSet blocks
            implementation(libs.firebase.analytics)
            implementation(libs.firebase.crashlytics)
            implementation(libs.firebase.config)
            implementation(libs.firebase.firestore)
            // FCM client for re-engagement push. firebase-messaging-ktx is gone in BOM 34+ —
            // the base artifact ships the Kotlin APIs. Version resolved by firebase-bom below.
            implementation(libs.firebase.messaging)
        }
        wasmJsMain.dependencies {
            // Coil 3 — needed for the OPFS image Fetcher/Keyer registered in main.kt
            // (coil3.fetch.*, coil3.decode.ImageSource, okio.Buffer come transitively).
            // Feature modules declare coil as `implementation`, so it does not leak here.
            implementation(libs.coil3.compose)
        }
        commonMain.dependencies {
            implementation(projects.core.common.api)
            implementation(projects.core.common.impl)
            implementation(projects.core.filepicker.api)
            implementation(projects.core.datastore.api)
            implementation(projects.core.designsystem)
            implementation(projects.core.datastore.api)
            implementation(projects.core.navigation.api)
            implementation(projects.core.navigation.impl)
            implementation(projects.core.remoteconfig.api)
            implementation(projects.core.remoteconfig.impl)
            implementation(projects.core.auth.api)
            implementation(projects.core.auth.impl)

            implementation(projects.feature.splash)
            implementation(projects.feature.checklist)
            implementation(projects.feature.create)
            implementation(projects.feature.onboarding)
            implementation(projects.feature.debug)
            implementation(projects.feature.home)
            implementation(projects.feature.user)
            implementation(projects.feature.analyze)
            implementation(projects.feature.paywall)
            implementation(projects.feature.sharing)
            implementation(projects.feature.updatefeed)
            implementation(projects.feature.settings)
            implementation(projects.feature.aichat.impl)
            implementation(projects.core.datastore.impl)

            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            // Navigation 3 — NavDisplay + entryProvider DSL + NavBackStack for App.kt.
            // Replaces androidx.navigation.compose (Nav 2). NavDisplay lives in
            // navigation3-ui which is pulled transitively; adaptive-navigation3 is the
            // KMP umbrella artifact with wasmJs target verified at 1.3.0-alpha02.
            implementation(libs.compose.adaptive.navigation3)
            implementation(libs.kotlinx.serialization.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)

            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
            // Okio FakeFileSystem is used by CsatViewModelTest. datastore-core-okio
            // re-exports okio so we pull it transitively without adding a new version pin.
            implementation(libs.datastore.core.okio)
        }
    }
}

// Compose UI tooling for Android debug builds — androidRuntimeClasspath is the AGP9 replacement
// for debugImplementation(compose.uiTooling) in KMP library modules
dependencies {
    add("androidRuntimeClasspath", compose.uiTooling)

    // Firebase BOM — pins versions of firebase-analytics/crashlytics/config in androidMain.
    // platform() is deprecated in KMP source-set blocks (KT-58759), so we pin via dependencies{}.
    add("androidMainImplementation", platform(libs.firebase.bom))
}

// ============================================================
// Task: generateWasmInitJs
// Reads Firebase web config from local.properties and substitutes
// placeholders in init.js.template → composeApp/src/wasmJsMain/resources/init.js.
// init.js is gitignored (contains API keys).
// init.js.template is committed (safe — contains only placeholders).
// ============================================================
// Read local.properties at configuration time (config-cache-friendly).
// Values get captured into the registered task as inputs, so the generated
// init.js is invalidated when local.properties changes.
val firebaseWebProps: Map<String, String> = run {
    val localPropertiesFile = rootProject.file("local.properties")
    val props = Properties()
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { props.load(it) }
    }
    mapOf(
        "FIREBASE_API_KEY" to props.getProperty("FIREBASE_WEB_API_KEY", "MISSING_FIREBASE_WEB_API_KEY"),
        // authDomain MUST equal the site's own origin (gisti-ai.com) so Firebase Auth's
        // /__/auth/ handler+iframe are SAME-ORIGIN, served via the reverse-proxy in
        // src/redirect.js. The default <project>.firebaseapp.com is a third-party origin;
        // Chrome 130+ mobile partitions its iframe storage → Google sign-in breaks.
        // Local Google-auth testing on localhost: override to aichecklists-40230.firebaseapp.com.
        "FIREBASE_AUTH_DOMAIN" to props.getProperty("FIREBASE_WEB_AUTH_DOMAIN", "gisti-ai.com"),
        "FIREBASE_PROJECT_ID" to props.getProperty("FIREBASE_WEB_PROJECT_ID", "aichecklists-40230"),
        "FIREBASE_STORAGE_BUCKET" to props.getProperty("FIREBASE_WEB_STORAGE_BUCKET", "aichecklists-40230.firebasestorage.app"),
        "FIREBASE_MESSAGING_SENDER_ID" to props.getProperty("FIREBASE_WEB_MESSAGING_SENDER_ID", "27698629989"),
        "FIREBASE_APP_ID" to props.getProperty("FIREBASE_WEB_APP_ID", "MISSING_FIREBASE_WEB_APP_ID"),
        // GA4 measurementId (G-XXXXXXX). Empty string when unset → Analytics inits but
        // sends nothing to GA4 (graceful: no throw, just no data). Required for web analytics.
        "FIREBASE_MEASUREMENT_ID" to props.getProperty("FIREBASE_WEB_MEASUREMENT_ID", ""),
    )
}

val generateWasmInitJs by tasks.registering {
    group = "wasm"
    description = "Generate init.js from init.js.template using Firebase web config from local.properties"

    val templateFile = project.file("src/wasmJsMain/resources/init.js.template")
    val outputFile = project.file("src/wasmJsMain/resources/init.js")
    val captured = firebaseWebProps  // capture map for config-cache safety
    val apiKeyMissing = captured["FIREBASE_API_KEY"] == "MISSING_FIREBASE_WEB_API_KEY"

    inputs.file(templateFile)
    inputs.property("firebaseWebProps", captured.toString())
    outputs.file(outputFile)

    doLast {
        var content = templateFile.readText()
        for ((key, value) in captured) {
            content = content.replace("__${key}__", value)
        }
        outputFile.writeText(content)
        if (apiKeyMissing) {
            println("[generateWasmInitJs] WARNING: FIREBASE_WEB_API_KEY not set in local.properties — RC fetch will fail, defaultConfig used")
        }
    }
}

// Wire generateWasmInitJs to run before every wasmJs task that touches resources or
// compiles wasmJs code. wasmJsProcessResources is critical — it reads from
// src/wasmJsMain/resources where init.js is generated.
afterEvaluate {
    tasks.matching {
        it.name == "wasmJsProcessResources" ||
        it.name.startsWith("compileKotlinWasmJs") ||
        it.name.startsWith("wasmJsBrowser") ||
        it.name.startsWith("compileDevelopmentExecutableKotlinWasmJs") ||
        it.name.startsWith("compileProductionExecutableKotlinWasmJs")
    }.configureEach {
        dependsOn(generateWasmInitJs)
    }
}

// ============================================================
// Tune BinaryenExec (wasm-opt) for Cloudflare Workers Builds.
//
// Kotlin 2.3.20 default runs 7 optimization passes on a single thread:
//   -O3 -O3 --gufa -O3 --type-merging -O3 -Oz
// On Cloudflare's 2 vCPU runner this takes 5–7 minutes and tips the
// 20-minute build timeout for the wasmJsBrowserDistribution pipeline.
//
// We keep all feature flags (--enable-gc etc — required by Kotlin/Wasm-GC
// bytecode) and --no-inline correctness directives, and replace the seven
// optimization passes with a single -O2. Bundle grows ~10–20%; the
// Optimize task drops from 5–7 min to ~30–90 sec.
//
// Default args reference (kotlin-gradle-plugin BinaryenConfig):
//   --enable-gc, --enable-reference-types, --enable-exception-handling,
//   --enable-bulk-memory, --enable-nontrapping-float-to-int, --closed-world,
//   --no-inline=kotlin.wasm.internal.throwValue,
//   --no-inline=kotlin.wasm.internal.getKotlinException,
//   --no-inline=kotlin.wasm.internal.jsToKotlinStringAdapter,
//   --inline-functions-with-loops, --traps-never-happen, --fast-math,
//   --type-ssa, -O3, -O3, --gufa, -O3, --type-merging, -O3, -Oz
// ============================================================
tasks.withType<BinaryenExec>().configureEach {
    binaryenArgs = mutableListOf(
        // Feature flags — required by Kotlin/Wasm-GC bytecode
        "--enable-gc",
        "--enable-reference-types",
        "--enable-exception-handling",
        "--enable-bulk-memory",
        "--enable-nontrapping-float-to-int",
        "--closed-world",
        // No-inline directives — correctness (preserve exception unwinding)
        "--no-inline=kotlin.wasm.internal.throwValue",
        "--no-inline=kotlin.wasm.internal.getKotlinException",
        "--no-inline=kotlin.wasm.internal.jsToKotlinStringAdapter",
        // Cheap optimization toggles
        "--inline-functions-with-loops",
        "--traps-never-happen",
        "--fast-math",
        // Single -O2 pass instead of 5x -O3 + --gufa + --type-merging + -Oz
        "-O2",
    )
}
