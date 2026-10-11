plugins {
    id("fetchy.compose")
    id("fetchy.testing")
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":core:ytdlp"))
    implementation(project(":core:platform"))
    implementation(project(":designsystem"))
    implementation(project(":feature:add"))
    implementation(project(":feature:queue"))
    implementation(project(":feature:history"))
    implementation(project(":feature:presets"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:tools"))
}

compose.desktop {
    application {
        mainClass = "fetchy.app.MainKt"
    }
}
