rootProject.name = "AIChecklists"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        // Compose pre-release channel — content-filtered to Compose/Skiko groups and
        // placed LAST so Gradle does not probe this slow JetBrains Space repo for every
        // non-Compose dependency. That per-dependency probing added minutes to cold CI
        // dependency resolution and tipped the Cloudflare Workers Builds 20-min timeout.
        // Compose 1.11.0 ships on Maven Central, so nothing here needs the dev repo today;
        // the filter keeps it harmless if a Compose pre-release is ever pinned.
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev") {
            content { includeGroupByRegex("org\\.jetbrains\\.(compose|skiko).*") }
        }
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        // See note in pluginManagement above — same content filter + last-position trick
        // to stop slow JetBrains Space probing on cold CI dependency resolution.
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev") {
            content { includeGroupByRegex("org\\.jetbrains\\.(compose|skiko).*") }
        }
    }
}

include(":androidApp")
include(":composeApp")
include(":core:common:api")
include(":core:common:impl")
include(":core:designsystem")
include(":core:datastore:api")
include(":core:datastore:impl")
include(":core:navigation:api")
include(":core:navigation:impl")
include(":feature:checklist")
include(":feature:create")
include(":feature:onboarding")
include(":feature:debug")
include(":feature:home")
include(":feature:user")
include(":feature:splash")
include(":feature:analyze")
include(":feature:paywall")
include(":feature:sharing")
include(":feature:updatefeed")
include(":core:remoteconfig:api")
include(":core:remoteconfig:impl")
include(":core:auth:api")
include(":core:auth:impl")
include(":feature:settings")
include(":feature:aichat:api")
include(":feature:aichat:impl")