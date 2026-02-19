import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.jetbrains.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.googleServices)
    alias(libs.plugins.firebaseCrashlytics)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            //todo rename
            baseName = "ComposeApp"
            isStatic = true
        }
    }
    
    sourceSets {
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.room.ktx)

            // Glance for App Widgets
            implementation(libs.glance)
            implementation(libs.glance.appwidget)

            // WorkManager for widget sync
            implementation(libs.workmanager)

            // Google Play In-App Review
            implementation(libs.play.review.ktx)

            // Amplitude Analytics
            implementation(libs.amplitude.analytics)
            implementation(libs.play.services.appset)
        }
        commonMain.dependencies {
            implementation(projects.core.common.api)
            implementation(projects.core.common.impl)
            implementation(projects.core.datastore.api)
            implementation(projects.core.designsystem)
            implementation(projects.core.datastore.api)
            implementation(projects.core.navigation.api)
            implementation(projects.core.navigation.impl)
            implementation(projects.core.remoteconfig.api)
            implementation(projects.core.remoteconfig.impl)

            implementation(projects.feature.splash)
            implementation(projects.feature.checklist)
            implementation(projects.feature.create)
            implementation(projects.feature.onboarding)
            implementation(projects.feature.debug)
            implementation(projects.feature.home)
            implementation(projects.feature.user)
            implementation(projects.feature.analyze)
            implementation(projects.feature.paywall)
            implementation(projects.feature.sharing)
            
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.androidx.navigation.compose)
            implementation(libs.kotlinx.serialization.core)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)

            implementation(libs.room.runtime)
            implementation(libs.sqlite.bundled)
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
        }
    }
}

android {
    namespace = "com.antonchuraev.aichecklists"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.antonchuraev.aichecklists"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 13
        versionName = "1.8.3"

        testInstrumentationRunner = "com.antonchuraev.aichecklists.TestRunner"

        // Test Orchestrator - isolate tests with clearPackageData
        testInstrumentationRunnerArguments["clearPackageData"] = "true"

        // Read Gemini API key from local.properties
        val localProperties = project.rootProject.file("local.properties")
        val properties = Properties()
        if (localProperties.exists()) {
            properties.load(localProperties.inputStream())
        }
        buildConfigField("String", "GEMINI_API_KEY", "\"${properties.getProperty("GEMINI_API_KEY", "")}\"")
        buildConfigField("String", "AMPLITUDE_KEY", "\"${properties.getProperty("AMPLITUDE_KEY", "")}\"")
    }

    testOptions {
        // Enable Test Orchestrator for test isolation
        execution = "ANDROIDX_TEST_ORCHESTRATOR"

        // Disable animations to prevent flaky tests
        animationsDisabled = true

        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    buildFeatures {
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/LICENSE.md"
            excludes += "/META-INF/LICENSE-notice.md"
        }
    }
    signingConfigs {
        create("release") {
            val localProperties = project.rootProject.file("local.properties")
            val properties = Properties()
            if (localProperties.exists()) {
                properties.load(localProperties.inputStream())
            }

            val keystorePath = properties.getProperty("KEYSTORE_FILE", "")
            if (keystorePath.isNotEmpty()) {
                storeFile = file(keystorePath)
                storePassword = properties.getProperty("KEYSTORE_PASSWORD", "")
                keyAlias = properties.getProperty("KEY_ALIAS", "gisti")
                keyPassword = properties.getProperty("KEY_PASSWORD", "")
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    debugImplementation(compose.uiTooling)
    // KSP для Room под каждую целевую платформу
    add("kspAndroid", libs.room.compiler)
    add("kspIosSimulatorArm64", libs.room.compiler)
    add("kspIosArm64", libs.room.compiler)

    // Firebase (Android only)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.config)

    // Android UI Tests
    androidTestImplementation(libs.androidx.testExt.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.7.8")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.7.8")

    // Test Orchestrator - runs each test in isolated process
    androidTestUtil("androidx.test:orchestrator:1.5.0")

    // MockK - mocking library for Kotlin
    androidTestImplementation("io.mockk:mockk-android:1.13.8")
    androidTestImplementation("io.mockk:mockk-agent:1.13.8")
}

