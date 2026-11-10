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
//include(":proxy-gateway")
include(":shared")
include(":hid")
include(":multiplexer")
include(":webdav")
include(":file-upload-service")
include(":shared-models")
include(":front")
