// Baseline Profile producer + Macrobenchmark module for :androidApp.
// Code lives in src/main (com.android.test modules have no androidTest source set).
//   Generate:  ./gradlew :androidApp:generateBaselineProfile
//   Benchmark: ./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest
plugins {
    alias(libs.plugins.androidTest)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.antonchuraev.aichecklists.baselineprofile"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        // Macrobenchmark / BaselineProfileRule on a non-rooted device need API 28+ (33+ to
        // generate profiles without root).
        minSdk = 28
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    targetProjectPath = ":androidApp"
    // The test APK instruments itself and drives :androidApp through UiAutomator/shell.
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

baselineProfile {
    // Connected device or emulator (API 33+ unrooted). No Gradle Managed Device configured.
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.testExt.junit)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}

// Pass the tested app's applicationId to the instrumentation as `targetAppId`.
androidComponents {
    onVariants { variant ->
        val artifactsLoader = variant.artifacts.getBuiltArtifactsLoader()
        variant.instrumentationRunnerArguments.put(
            "targetAppId",
            variant.testedApks.map { artifactsLoader.load(it)?.applicationId },
        )
    }
}
