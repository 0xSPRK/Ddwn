plugins {
    id("fetchy.compose")
    id("fetchy.testing")
}

dependencies {
    implementation(libs.graphics.shapes)
    testImplementation(libs.compose.ui.test)
}

tasks.test {
    systemProperty("java.awt.headless", "true")
    systemProperty("skiko.renderApi", "SOFTWARE")
}
