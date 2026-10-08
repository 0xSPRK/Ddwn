plugins {
    id("fetchy.kotlin-jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    implementation(libs.findLibrary("compose-multiplatform").get())
    implementation(libs.findLibrary("material3").get())
    implementation(libs.findLibrary("materialkolor").get())
}
