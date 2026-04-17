rootProject.name = "AIChecklists"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        mavenCentral()
        gradlePluginPortal()
    }
}

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