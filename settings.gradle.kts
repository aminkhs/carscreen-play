pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "CarScreen Play"
include(":app", ":core", ":player", ":mirroring", ":automotive", ":diagnostics", ":settings", ":receiver", ":browser")
