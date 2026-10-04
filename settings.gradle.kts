pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven { url = java.net.URI("https://chaquo.com/maven") }
    }
    plugins {
        id("com.android.application") version "8.4.0"
        id("org.jetbrains.kotlin.android") version "1.9.24"
        id("com.chaquo.python") version "15.0.1"
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = java.net.URI("https://chaquo.com/maven") }
    }
}
rootProject.name = "VideoDownloaderApp"
include(":app")
