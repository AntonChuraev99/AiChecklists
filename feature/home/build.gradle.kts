@file:Suppress("DEPRECATION", "OPT_IN_USAGE")


plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    android {
        namespace = "com.antonchuraev.homesearchchecklist.feature.home"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        withHostTest {}
}

    listOf(iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework {
            baseName = "FeatureHome"
            isStatic = true
        }
    }

    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.common.api)
            implementation(projects.core.datastore.api)
            implementation(projects.core.navigation.api)
            implementation(projects.core.designsystem)

            implementation(projects.feature.checklist)
            implementation(projects.feature.paywall)
            implementation(projects.feature.user)
            // Pending: docs/todos/2026-05-15-extract-filepicker-to-core.md
            // FilePicker currently lives under feature:analyze; sharing it across features
            // creates a lateral coupling. Extract to core/common/api or new core/filepicker.
            implementation(projects.feature.analyze)

            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)

            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.bundles.koin.feature)
            implementation(libs.kotlinx.datetime)
            implementation(libs.reorderable)
            implementation(libs.coil3.compose)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.datastore.preferences.core)
            implementation(libs.androidx.navigation.compose)
            implementation(projects.core.remoteconfig.api)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
        }
    }
}
