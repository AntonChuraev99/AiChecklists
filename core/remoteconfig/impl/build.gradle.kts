@file:Suppress("DEPRECATION", "OPT_IN_USAGE")


plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
}

kotlin {
    android {
        namespace = "com.antonchuraev.homesearchchecklist.core.remoteconfig.impl"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
}

    listOf(iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework {
            baseName = "RemoteConfigImpl"
            isStatic = true
        }
    }

    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.remoteconfig.api)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.bundles.koin.library)
        }
    }
}

dependencies {
    // Firebase Remote Config — Android only. Must be in top-level dependencies{} because
    // platform() BOM resolution is not available inside kotlin { sourceSets { } }.
    add("androidMainImplementation", platform(libs.firebase.bom))
    add("androidMainImplementation", libs.firebase.config)
}
