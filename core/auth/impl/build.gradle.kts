@file:Suppress("DEPRECATION", "OPT_IN_USAGE")

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.jetbrains.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.antonchuraev.homesearchchecklist.core.auth.impl"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
    }

    listOf(iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework {
            baseName = "AuthImpl"
            isStatic = true
        }
    }

    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.auth.api)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.bundles.koin.library)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

dependencies {
    // Firebase BOM + Auth — Android only (KMP platform() workaround)
    add("androidMainImplementation", platform(libs.firebase.bom))
    add("androidMainImplementation", libs.firebase.auth)
    add("androidMainImplementation", libs.credentials)
    add("androidMainImplementation", libs.credentials.play.services)
    add("androidMainImplementation", libs.googleid)
}
