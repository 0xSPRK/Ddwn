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
    implementation(libs.koin.core)
    testImplementation(libs.compose.ui.test)
}

tasks.test {
    systemProperty("java.awt.headless", "true")
    systemProperty("skiko.renderApi", "SOFTWARE")
}

