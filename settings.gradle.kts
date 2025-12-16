rootProject.name = "ai-challenge-server"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include("mcp-app")
include("mcp-lib")
