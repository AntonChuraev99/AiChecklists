plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    // androidLibrary alias now points to com.android.kotlin.multiplatform.library
    alias(libs.plugins.androidLibrary) apply false
    // :baselineprofile producer module (com.android.test) + consumer in :androidApp.
    alias(libs.plugins.androidTest) apply false
    alias(libs.plugins.baselineprofile) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room3) apply false
    alias(libs.plugins.googleServices) apply false
    alias(libs.plugins.firebaseCrashlytics) apply false
}

// Compose compiler config for every module that applies the compose compiler plugin.
// - Stability list: types in compose_compiler_config.conf are treated as stable (see the file header).
// - Compiler reports/metrics are opt-in: ./gradlew <task> -PcomposeReports
//   -> <module>/build/compose_reports, <module>/build/compose_metrics
subprojects {
    pluginManager.withPlugin("org.jetbrains.kotlin.plugin.compose") {
        val stabilityConfig = rootProject.layout.projectDirectory.file("compose_compiler_config.conf")
        val composeReports = providers.gradleProperty("composeReports").isPresent
        extensions.configure<org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension> {
            stabilityConfigurationFiles.add(stabilityConfig)
            if (composeReports) {
                reportsDestination.set(layout.buildDirectory.dir("compose_reports"))
                metricsDestination.set(layout.buildDirectory.dir("compose_metrics"))
            }
        }
        // KGP passes stabilityConfigurationPath / reportsDestination as untracked (internal)
        // compiler options: editing the conf or toggling -PcomposeReports leaves compile
        // tasks UP-TO-DATE (and build-cache keys blind to it). Track both explicitly.
        tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
            inputs.file(stabilityConfig)
                .withPropertyName("composeStabilityConfig")
                .withPathSensitivity(PathSensitivity.NONE)
            inputs.property("composeReports", composeReports)
        }
        if (composeReports) {
            // The compose compiler names report files after the Kotlin module name
            // (KMP default "AIChecklists.feature:home") and only sanitizes '.' and '<>',
            // so the ':' makes File.createNewFile fail on Windows ("Invalid file path").
            // Reports-only: swap ':' for '_' (the same name the .kotlin_module file already uses).
            tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile>().configureEach {
                val current = compilerOptions.moduleName.orNull
                if (current != null && ':' in current) {
                    compilerOptions.moduleName.set(current.replace(':', '_'))
                }
            }
        }
    }
}