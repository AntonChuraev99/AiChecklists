@file:Suppress("DEPRECATION", "OPT_IN_USAGE")


plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.jetbrains.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.antonchuraev.homesearchchecklist.feature.create"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        // isIncludeAndroidResources lets Robolectric resolve the Compose-Resources strings this
        // module's screens read from core/designsystem (Res.string.create_*) during host tests.
        // Without it `getString` dies with "Method getSystem in android.content.res.Resources not
        // mocked", so any test touching a user-facing string fails for the environment instead of
        // for the behaviour. Mirrors feature/home.
        withHostTest {
            isIncludeAndroidResources = true
        }
        androidResources {
            enable = true
        }
    }

    listOf(iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework {
            baseName = "FeatureCreate"
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
            implementation(projects.core.designsystem)
            implementation(projects.core.navigation.api)

            implementation(projects.feature.checklist)
            implementation(projects.feature.paywall)

            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)

            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.koin.core)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            // The staged reminder turns a date-picker result and a time-of-day into an epoch trigger.
            // `implementation(projects.feature.checklist)` is not transitive, so the module needs its
            // own dependency even though the reminder sheet it drives already uses the same library.
            implementation(libs.kotlinx.datetime)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.androidx.navigation.compose)
            implementation(projects.core.remoteconfig.api)
            implementation(projects.feature.user)
            implementation(projects.feature.paywall)
        }

        // androidHostTest: JVM/Robolectric tests for the paths that resolve Compose Resources
        // (user-facing error copy). Task: ./gradlew :feature:create:testAndroidHostTest
        getByName("androidHostTest").dependencies {
            implementation(libs.junit)
            implementation(libs.robolectric)
        }
    }
}
