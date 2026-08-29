pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        maven {
            url = uri("https://api.mapbox.com/downloads/v2/releases/maven")
            authentication {
                create<org.gradle.authentication.http.BasicAuthentication>("basic")
            }
            credentials {
                username = "mapbox"
                password = providers.gradleProperty("MAPBOX_DOWNLOADS_TOKEN")
                    .orElse(providers.environmentVariable("MAPBOX_DOWNLOADS_TOKEN"))
                    .orNull
            }
        }
        mavenLocal()
    }
}

rootProject.name = "agame_common"
include(":app")
include(":frame")
include(":ipay")
include(":ads_admob")
include(":gpay")
include(":imap")
include(":ilocation")
include(":mapboxmap")
include(":googlelocation")
include(":amapmap")
include(":amaplocation")
