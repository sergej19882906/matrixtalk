pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        maven { url = uri("https://s3.eu-central-1.amazonaws.com/matrix-sdk-android/matrix-sdk-android") }
    }
}

rootProject.name = "MatrixTalk"
include(":shared")
include(":app")
include(":desktop-app")
