@file:Suppress("DEPRECATION", "OPT_IN_USAGE")


plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.jetbrains.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.antonchuraev.homesearchchecklist.core.navigation.api"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
}

    listOf(iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework {
            baseName = "NavigationApi"
            isStatic = true
        }
    }

    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.core)
            implementation(libs.kotlinx.coroutines.core)
            // Navigation 3 — NavKey is part of AppNavRoute's public API (sealed interface AppNavRoute : NavKey).
            // Must be `api` so consumers of :core:navigation:api can see NavKey without adding
            // compose-adaptive-navigation3 to their own dependencies.
            api(libs.compose.adaptive.navigation3)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
