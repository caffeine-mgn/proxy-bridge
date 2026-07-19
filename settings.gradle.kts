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
include(":com")
include(":ble")
include(":proxy-server")
//include(":proxy-gateway")
include(":shared")
include(":sound")
include(":multiplexer")
include(":bootstrap")
include(":webdav")
include(":file-upload-service")
include(":shared-models")
include(":front")
