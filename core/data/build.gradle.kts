plugins {
    id("fetchy.kotlin-jvm")
    id("fetchy.testing")
    alias(libs.plugins.sqldelight)
}

sqldelight {
    databases {
        create("FetchyDatabase") {
            packageName.set("fetchy.data.db")
            schemaOutputDirectory.set(file("src/main/sqldelight/databases"))
        }
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.sqldelight.runtime)
    implementation(libs.sqldelight.coroutines.extensions)
    implementation(libs.sqldelight.driver.sqlite)
    implementation(libs.coroutines.core)
}
