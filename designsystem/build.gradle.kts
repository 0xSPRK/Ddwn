plugins {
    id("fetchy.compose")
    id("fetchy.testing")
}

dependencies {
    testImplementation(libs.compose.ui.test)
}

tasks.test {
    systemProperty("java.awt.headless", "true")
    systemProperty("skiko.renderApi", "SOFTWARE")
}
