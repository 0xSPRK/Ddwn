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
}
