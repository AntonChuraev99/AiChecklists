@file:Suppress("DEPRECATION", "OPT_IN_USAGE")


plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    android {
        namespace = "com.antonchuraev.homesearchchecklist.feature.onboarding"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        withHostTest {}
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
            // Welcome onboarding reads the activation_bundle_v1 flag to drive the activation funnel
            // after creating the first AI checklist (mirrors AnalyzeResultPreviewViewModel).
            implementation(projects.core.remoteconfig.api)
            implementation(projects.feature.updatefeed)
            implementation(projects.feature.user)
            implementation(projects.feature.paywall)
            implementation(projects.feature.create)
            implementation(projects.feature.checklist)
            implementation(projects.feature.sharing)
            // AI generation for the Welcome onboarding's typed-text final-step branch (analyzeData +
            // createChecklistFromResult). analyze depends on checklist/paywall/user (NOT onboarding),
            // so there is no dependency cycle.
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
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
        }
    }
}
