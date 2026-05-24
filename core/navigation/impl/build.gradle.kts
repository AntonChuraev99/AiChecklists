@file:Suppress("DEPRECATION", "OPT_IN_USAGE")


plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
}

kotlin {
    android {
        namespace = "com.antonchuraev.homesearchchecklist.core.navigation.impl"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        withHostTest {}
}

    listOf(iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework {
            baseName = "NavigationImpl"
            isStatic = true
        }
    }

    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.navigation.api)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.bundles.koin.library)
            // Navigation 3 — NavBackStack mutableStateListOf wrapper; pulled transitively
            // via compose-adaptive-navigation3 in navigation api module but declared here
            // explicitly so impl has direct access to NavBackStack/toNavBackStack().
            implementation(libs.compose.adaptive.navigation3)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
