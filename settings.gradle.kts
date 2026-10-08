pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "fetchy"

include(":core:model")
include(":core:ytdlp")
include(":core:data")
include(":core:platform")
include(":designsystem")
include(":feature:add")
include(":feature:queue")
include(":feature:history")
include(":feature:presets")
include(":feature:settings")
include(":feature:tools")
include(":app")
