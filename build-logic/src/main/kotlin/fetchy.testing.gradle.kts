plugins {
    id("fetchy.kotlin-jvm")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    testImplementation(libs.findLibrary("junit-jupiter").get())
    testImplementation(libs.findLibrary("mockk").get())
    testImplementation(libs.findLibrary("coroutines-test").get())
    testImplementation(libs.findLibrary("turbine").get())
}

tasks.test {
    useJUnitPlatform()
    systemProperty(
        "fetchy.fixtures.dir",
        layout.projectDirectory.dir("src/test/resources/fixtures").asFile.absolutePath,
    )
}
