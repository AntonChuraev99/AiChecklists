rootProject.name = "HomeSearchChecklist"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        mavenCentral()
        gradlePluginPortal()
    }
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