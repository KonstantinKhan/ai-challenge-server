plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.ktor) apply false
    alias(libs.plugins.kotlin.plugin.serialization) apply false
}

group = "com.khan366kos"
version = "0.0.1"

subprojects {
    group = rootProject.group
    version = rootProject.version
}
