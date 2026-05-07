import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    listOf(iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework {
            baseName = "DatastoreApi"
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

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.datastore.preferences.core)
        }
        wasmJsMain.dependencies {
            // Custom DataStore<Preferences> impl backed by browser localStorage —
            // see AppDatastore.wasmJs.kt. Uses kotlinx-serialization JSON for the
            // small persisted blob format ({"key":{"t":"B|S|I|L|F|D","v":<val>}}).
            implementation(libs.kotlinx.serialization.json)
        }
    }
}

android {
    namespace = "com.antonchuraev.homesearchchecklist.core.datastore.api"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

