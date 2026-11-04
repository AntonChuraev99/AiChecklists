@file:Suppress("DEPRECATION", "OPT_IN_USAGE")


plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
}

// Voice call implementation: platform audio (androidMain: SpeechRecognizer, TextToSpeech, AudioRecord barge-in,
// audio focus, output route) and the pure helpers they share with the call engine (commonMain). The call is
// Android only; wasmJs and iOS bind an honest "unavailable" VoiceCapabilities. Only the Android SDK
// (android.speech.*, android.media.*) is used: no extra audio library.
kotlin {
    android {
        namespace = "com.antonchuraev.homesearchchecklist.core.voice.impl"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        withHostTest {}
}

    listOf(iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework {
            baseName = "VoiceImpl"
            isStatic = true
        }
    }

    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.voice.api)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.bundles.koin.library)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
