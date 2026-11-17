@file:Suppress("DEPRECATION", "OPT_IN_USAGE")


plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.roborazzi)
}

kotlin {
    android {
        namespace = "com.antonchuraev.homesearchchecklist.feature.onboarding"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        // isIncludeAndroidResources lets Robolectric resolve the Compose-Resources strings
        // (core/designsystem Res.string.onboarding_*) in the screenshot tests (mirrors feature/voicecall).
        withHostTest {
            isIncludeAndroidResources = true
        }
    }

    listOf(iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework {
            baseName = "FeatureOnboarding"
            isStatic = true
        }
    }

    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    sourceSets {
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.androidx.navigation.compose)
        }
        commonMain.dependencies {
            implementation(projects.core.navigation.api)
            implementation(projects.core.common.api)
            implementation(projects.core.designsystem)
            implementation(projects.feature.user)
            implementation(projects.feature.checklist)
            // AI generation for the Welcome onboarding's typed-text final-step branch (analyzeData +
            // createChecklistFromResult). analyze depends on checklist/paywall/user (NOT onboarding),
            // so there is no dependency cycle.
            implementation(projects.feature.analyze)
            // Voice dictation on Write / Talk (DESIGN_SPEC 2026-11-17, owner decision Q1/Q4): the
            // chat's recorder + press gate, and the chat's transcription (AiChatRepository.transcribeAudio
            // over TranscribeAudioApiService). Both are api-only modules with no feature deps — no cycle.
            implementation(projects.core.filepicker.api)
            implementation(projects.feature.aichat.api)

            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)

            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.bundles.koin.feature)
        }

        // androidHostTest: JVM/Robolectric screenshot matrix (Roborazzi) of the onboarding,
        // DESIGN_SPEC §13. Task: ./gradlew :feature:onboarding:recordRoborazziAndroidHostTest
        //                         ./gradlew :feature:onboarding:verifyRoborazziAndroidHostTest
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
    // Golden PNGs versioned next to the test code (same convention as feature/voicecall).
    outputDir.set(file("src/androidHostTest/roborazzi"))
}
