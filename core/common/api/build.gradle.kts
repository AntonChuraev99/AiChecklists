@file:Suppress("DEPRECATION", "OPT_IN_USAGE")


plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.jetbrains.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.antonchuraev.homesearchchecklist.core.common.api"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
}

    listOf(iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework {
            baseName = "CommonApi"
            isStatic = true
        }
    }

    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)

            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.androidx.lifecycle.viewmodelCompose)

            // Room 3.0 runtime — supports all KMP targets (Android, iOS, wasmJs)
            implementation(libs.room3.runtime)
        }

        androidMain.dependencies {
            // BundledSQLiteDriver for Android (non-framework SQLite, matches sqlite-bundled KMP)
            implementation(libs.sqlite3.bundled)
        }

        val iosMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                // BundledSQLiteDriver for iOS
                implementation(libs.sqlite3.bundled)
            }
        }
        iosArm64Main {
            dependsOn(iosMain)
        }
        iosSimulatorArm64Main {
            dependsOn(iosMain)
        }

        wasmJsMain.dependencies {
            // WebWorkerSQLiteDriver for web — OPFS persistence via sqlite-wasm worker
            implementation(libs.sqlite3.web)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
