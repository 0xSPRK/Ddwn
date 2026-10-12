import org.jetbrains.compose.resources.ResourcesExtension.ResourceClassGeneration

plugins {
    id("fetchy.kotlin-jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

compose.resources {
    generateResClass = ResourceClassGeneration.Always
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.foundation)
    implementation(compose.ui)
    implementation(libs.findLibrary("compose-multiplatform").get())
    implementation(libs.findLibrary("compose-components-resources").get())
    implementation(libs.findLibrary("material3").get())
    implementation(libs.findLibrary("materialkolor").get())
}
