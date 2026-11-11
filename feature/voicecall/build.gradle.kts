@file:Suppress("DEPRECATION", "OPT_IN_USAGE")

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.roborazzi)
    alias(libs.plugins.jetbrains.kotlin.serialization)
}

// Voice call with Gisti: the call overlay's state, actions and screen. The call engine itself
// (:core:voice) is Android-only in v1; the module still compiles for every app target because
// composeApp's commonMain hosts the overlay — off Android the entry button is never shown.
// The agent turn transport (VoiceTurnGateway) lives in :feature:aichat:impl next to the chat's agent
// loop and is resolved from Koin; this module owns the session transport (voice_session), the
// ViewModel and the Route.
kotlin {
    android {
        namespace = "com.antonchuraev.homesearchchecklist.feature.voicecall"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        // isIncludeAndroidResources lets Robolectric resolve the Compose-Resources strings
        // (core/designsystem Res.string.voice_call_*) in the screenshot tests (mirrors feature/home).
        withHostTest {
            isIncludeAndroidResources = true
        }
    }

    listOf(iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework {
            baseName = "FeatureVoiceCall"
            isStatic = true
        }
    }

    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.designsystem)
            implementation(projects.core.common.api)
            implementation(projects.core.remoteconfig.api)
            implementation(projects.core.voice.api)
            // user_id for voice_session, read where the chat reads it (UserDataRepository).
            implementation(projects.feature.user)

            implementation(libs.koin.core)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.androidx.lifecycle.viewmodelCompose)

            // voice_session HTTP client (same setup as feature/aichat's ChatAgentApiServiceImpl).
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.json)

            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
            // @Preview annotations only (release-safe); the renderer is debug-only below.
            implementation(compose.components.uiToolingPreview)
        }
        androidMain.dependencies {
            // rememberLauncherForActivityResult for the RECORD_AUDIO request (platform/VoiceCallPlatform.android.kt).
            implementation(libs.androidx.activity.compose)
            // koinInject<AppLogger>() in the same platform file (error paths of the settings intents).
            implementation(libs.koin.compose)
            implementation(libs.ktor.client.okhttp)
        }
        val iosMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                implementation(libs.ktor.client.darwin)
            }
        }
        iosArm64Main {
            dependsOn(iosMain)
        }
        iosSimulatorArm64Main {
            dependsOn(iosMain)
        }
        wasmJsMain.dependencies {
            implementation(libs.ktor.client.js)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }

        // androidHostTest: JVM/Robolectric screenshot matrix (Roborazzi) of the call overlay,
        // DESIGN_SPEC §14. Task: ./gradlew :feature:voicecall:recordRoborazziAndroidHostTest
        //                         ./gradlew :feature:voicecall:verifyRoborazziAndroidHostTest
        getByName("androidHostTest").dependencies {
            implementation(libs.junit)
            implementation(libs.robolectric)
            implementation(libs.roborazzi)
            implementation(libs.roborazzi.compose)
            implementation(libs.roborazzi.junit.rule)
            implementation(libs.androidx.compose.ui.tooling)
            implementation(libs.androidx.compose.ui.test.junit4)
            implementation(libs.androidx.compose.ui.test.manifest)
        }
    }
}

roborazzi {
    // Golden PNGs versioned next to the test code (same convention as feature/home).
    outputDir.set(file("src/androidHostTest/roborazzi"))
}

// Compose UI tooling for Android debug builds only (AGP9 replacement for
// debugImplementation(compose.uiTooling) in KMP library modules, mirrors feature/home).
dependencies {
    add("androidRuntimeClasspath", compose.uiTooling)
}
