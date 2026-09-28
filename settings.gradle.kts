pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "CoreAlert"
include(":app")
include(":feature")
include(":core:domain")
include(":core:policy")
include(":core:data")
include(":core:monitoring")
include(":core:ui")
