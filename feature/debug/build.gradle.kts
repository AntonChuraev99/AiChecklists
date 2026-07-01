@file:Suppress("DEPRECATION", "OPT_IN_USAGE")


plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    android {
        namespace = "com.antonchuraev.homesearchchecklist.feature.debug"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
}

    listOf(iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework {
            baseName = "FeatureDebug"
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
            implementation(projects.core.navigation.api)
            implementation(projects.core.designsystem)

            implementation(projects.feature.checklist)
            implementation(projects.feature.user)
            // Debug-only: render the paywall post-cancel reason sheet standalone (visual test).
            // No cycle — feature:paywall does not depend on feature:debug.
            implementation(projects.feature.paywall)

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
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
        }
    }
}
