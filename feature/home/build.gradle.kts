@file:Suppress("DEPRECATION", "OPT_IN_USAGE")


plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.roborazzi)
}

kotlin {
    android {
        namespace = "com.antonchuraev.homesearchchecklist.feature.home"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        // isIncludeAndroidResources lets Robolectric resolve the Compose-Resources strings the
        // folder UI reads from core/designsystem (Res.string.folder_*) during screenshot tests.
        withHostTest {
            isIncludeAndroidResources = true
        }
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
            implementation(projects.core.auth.api)
            implementation(projects.core.filepicker.api)

            implementation(projects.feature.checklist)
            implementation(projects.feature.paywall)
            implementation(projects.feature.user)

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
            // Haze — rememberHazeState + hazeSource for the ChecklistDetail backdrop captured by the chat dock.
            implementation(libs.haze)
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
            // ui-tooling provides @PreviewLightDark and other preview annotations for androidMain
            implementation(libs.androidx.compose.ui.tooling)
        }

        // androidHostTest source set: JVM/Robolectric screenshot tests (Roborazzi) for the folder UI.
        // Task: ./gradlew :feature:home:recordRoborazziAndroidHostTest  (record golden)
        //       ./gradlew :feature:home:verifyRoborazziAndroidHostTest  (verify)
        getByName("androidHostTest").dependencies {
            implementation(libs.junit)
            implementation(libs.robolectric)
            implementation(libs.roborazzi)
            implementation(libs.roborazzi.compose)
            implementation(libs.roborazzi.junit.rule)
            implementation(libs.androidx.compose.ui.tooling)
            implementation(libs.androidx.compose.ui.test.junit4)
            implementation(libs.androidx.compose.ui.test.manifest)
        }
    }
}

roborazzi {
    // Store golden PNGs under src/ so they are versioned alongside the test code.
    // Verify task compares against these files; record task writes them.
    outputDir.set(file("src/androidHostTest/roborazzi"))
}
