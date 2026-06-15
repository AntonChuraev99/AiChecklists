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
        namespace = "com.antonchuraev.homesearchchecklist.feature.paywall"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        withHostTest {}
}

    listOf(iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework {
            baseName = "FeaturePaywall"
            isStatic = true
        }
    }

    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.navigation.api)
            implementation(projects.core.common.api)
            implementation(projects.core.designsystem)
            implementation(projects.core.remoteconfig.api)
            implementation(projects.feature.user)
            implementation(projects.feature.checklist)

            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)

            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.bundles.koin.feature)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }

        // mobileMain: shared by Android + iOS — where RevenueCat lives
        val mobileMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                implementation(libs.revenuecat.purchases.core)
                implementation(libs.revenuecat.purchases.result)
            }
        }
        androidMain {
            dependsOn(mobileMain)
            dependencies {
                implementation(libs.androidx.activity.compose)
                // BillingClient for AndroidBillingPlatformPreCheck pre-flight check.
                // Transitive via RevenueCat 3.x (purchases-android 10.4.0 → billing 8.3.0),
                // but androidMain compile needs explicit decl. Keep in sync with RC transitive.
                implementation("com.android.billingclient:billing:8.3.0")
            }
        }
        val iosMain by creating {
            dependsOn(mobileMain)
        }
        iosArm64Main {
            dependsOn(iosMain)
        }
        iosSimulatorArm64Main {
            dependsOn(iosMain)
        }
        // wasmJs: no RevenueCat — stub PaywallRepository provided
    }
}
