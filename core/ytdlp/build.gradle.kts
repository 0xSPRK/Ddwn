plugins {
    id("fetchy.kotlin-jvm")
    id("fetchy.testing")
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.ktor.client.mock)
}
