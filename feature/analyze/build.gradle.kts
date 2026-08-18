@file:Suppress("DEPRECATION", "OPT_IN_USAGE")


plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.jetbrains.kotlin.serialization)
    alias(libs.plugins.roborazzi)
}

kotlin {
    android {
        namespace = "com.antonchuraev.homesearchchecklist.feature.analyze"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        // isIncludeAndroidResources lets Robolectric resolve the Activity theme the Compose test rule
        // inflates for androidHostTest.
        withHostTest {
            isIncludeAndroidResources = true
        }
}

    listOf(iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework {
            baseName = "FeatureAnalyze"
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
            implementation(projects.core.filepicker.api)
            implementation(projects.core.remoteconfig.api)

            implementation(projects.feature.checklist)
            implementation(projects.feature.user)
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

            // Ktor HTTP Client
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }

        // androidHostTest source set: JVM/Robolectric Compose tests. The on-entry picker's contract
        // is "opens exactly once and survives a rotation", which only a real composition that can be
        // saved and restored can answer — a plain commonTest cannot.
        // Task: ./gradlew :feature:analyze:testAndroidHostTest
        getByName("androidHostTest").dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.junit)
            implementation(libs.robolectric)
            implementation(libs.roborazzi)
            implementation(libs.roborazzi.compose)
            implementation(libs.roborazzi.junit.rule)
            implementation(libs.androidx.compose.ui.test.junit4)
            implementation(libs.androidx.compose.ui.test.manifest)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.ktor.client.okhttp)
        }

        val iosMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                implementation(libs.ktor.client.darwin)
            }
        }
        iosArm64Main {
            dependsOn(iosMain)
        }
        iosSimulatorArm64Main {
            dependsOn(iosMain)
        }
        wasmJsMain.dependencies {
            implementation(libs.ktor.client.js)
        }
    }
}

roborazzi {
    // Store golden PNGs under src/ so they are versioned alongside the test code — same convention
    // as :core:designsystem and :feature:home. Left unset they would land in build/ and be lost on
    // the next clean, which makes "verify" compare against nothing.
    outputDir.set(file("src/androidHostTest/roborazzi"))
}
