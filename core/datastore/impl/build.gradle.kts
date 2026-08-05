@file:Suppress("DEPRECATION", "OPT_IN_USAGE")


plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.jetbrains.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.antonchuraev.homesearchchecklist.core.datastore.impl"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        withHostTest {}
}

    listOf(iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework {
            baseName = "DatastoreImpl"
            isStatic = true
        }
    }

    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.datastore.api)
            // AppLogger — the prefs reads degrade to defaults on an unreadable store, and a silent
            // fallback with no log is undiagnosable. Same reason core/remoteconfig/impl takes it.
            implementation(projects.core.common.api)

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.datastore.preferences.core)

            implementation(libs.bundles.koin.library)
        }
        commonTest.dependencies {
            implementation(projects.core.datastore.api)
            implementation(projects.core.common.api)

            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.datastore.preferences.core)
        }
        androidMain.dependencies {
            implementation(libs.datastore.preferences)
        }
    }
}
