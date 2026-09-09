pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "HZ-Logistics-parent-service"

include(
    "logistics-parent-service-bom",
    "logistics-parent-service-autoconfigure",
    "logistics-parent-service-starter",
)
