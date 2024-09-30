pluginManagement {
    plugins {
    }
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
    }
}
rootProject.name = "proxy-bridge"
include(":proxy-server")
include(":proxy-gateway")
include(":testing")
include(":shared")
include(":testing-tools")
