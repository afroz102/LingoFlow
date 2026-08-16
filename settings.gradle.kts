pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "LingoFlow"

// Stage 0A: the shipping InstantTranslate module (Technical Plan §3 boundaries).
include(":app")

// Dev-only controlled host app for Process Text compatibility testing (Validation Plan §2.2).
// Never published; not a dependency of :app.
include(":testhost")
