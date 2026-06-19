import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.jetbrains.kotlin.serialization)
    alias(libs.plugins.googleServices)
    alias(libs.plugins.firebaseCrashlytics)
}

// ─── Read local.properties ────────────────────────────────────────────────────
val localProperties = project.rootProject.file("local.properties")
val localProps = Properties().apply {
    if (localProperties.exists()) load(localProperties.inputStream())
}

android {
    // Namespace differs from applicationId: the R class package is .app-specific,
    // while applicationId (the Play Store package) stays "com.antonchuraev.aichecklists".
    // This avoids R class namespace collision with composeApp which uses the base namespace.
    namespace = "com.antonchuraev.aichecklists.app"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.antonchuraev.aichecklists"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 53
        versionName = "1.17.4"

        testInstrumentationRunner = "com.antonchuraev.aichecklists.TestRunner"

        // Test Orchestrator - isolate tests with clearPackageData
        testInstrumentationRunnerArguments["clearPackageData"] = "true"

        // BuildConfig fields — Amplitude key set per build type below.
        // NOTE: Gemini API key intentionally NOT here. AI calls go through Cloud Functions
        // (server holds the key in Secret Manager), so the APK never ships a key.
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${localProps.getProperty("GOOGLE_WEB_CLIENT_ID", "")}\"")
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
            val keystorePath = localProps.getProperty("KEYSTORE_FILE", "")
            if (keystorePath.isNotEmpty()) {
                storeFile = rootProject.file(keystorePath)
                storePassword = localProps.getProperty("KEYSTORE_PASSWORD", "")
                keyAlias = localProps.getProperty("KEY_ALIAS", "gisti")
                keyPassword = localProps.getProperty("KEY_PASSWORD", "")
            }
        }
    }

    buildTypes {
        getByName("debug") {
            buildConfigField("String", "AMPLITUDE_KEY", "\"${localProps.getProperty("AMPLITUDE_DEBUG_KEY", "")}\"")
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
            buildConfigField("String", "AMPLITUDE_KEY", "\"${localProps.getProperty("AMPLITUDE_KEY", "")}\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // composeApp KMP library — provides all shared KMP code + actuals
    implementation(projects.composeApp)

    // Direct deps on core+feature modules — needed because composeApp uses `implementation`
    // (non-transitive). androidApp references types from these modules directly in
    // GistiAndroidApplication (AnalyticsTracker), AndroidAppModule (ChecklistReminderScheduler,
    // ChecklistRepository), and the moved widget code.
    implementation(projects.core.common.api)
    implementation(projects.core.common.impl)
    implementation(projects.core.datastore.api)
    implementation(projects.core.datastore.impl)
    implementation(projects.core.designsystem)
    implementation(projects.core.navigation.api)
    implementation(projects.core.navigation.impl)
    implementation(projects.core.remoteconfig.api)
    implementation(projects.core.remoteconfig.impl)
    implementation(projects.feature.analyze)
    implementation(projects.feature.checklist)
    implementation(projects.feature.create)
    implementation(projects.feature.debug)
    implementation(projects.feature.home)
    implementation(projects.feature.onboarding)
    implementation(projects.feature.paywall)
    implementation(projects.feature.settings)
    implementation(projects.feature.sharing)
    implementation(projects.feature.splash)
    implementation(projects.feature.updatefeed)
    implementation(projects.feature.user)

    // Android UI essentials
    implementation(libs.androidx.activity.compose)
    debugImplementation("androidx.compose.ui:ui-tooling:1.7.8")

    // Glance for App Widgets (widget code lives in androidApp)
    implementation(libs.glance)
    implementation(libs.glance.appwidget)

    // WorkManager for widget sync
    implementation(libs.workmanager)

    // Google Play In-App Review
    implementation(libs.play.review.ktx)

    // Amplitude Analytics
    implementation(libs.amplitude.analytics)
    implementation(libs.play.services.appset)

    // Firebase — Android only
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.config)

    // Koin Android — for androidContext() / androidLogger() in startKoin
    // koin-android pulls in koin-core transitively
    implementation(libs.koin.android)

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
