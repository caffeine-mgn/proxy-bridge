pluginManagement {
  plugins {
  }
  repositories {
    mavenCentral()
    gradlePluginPortal()
  }
}
rootProject.name="proxy-bridge"
include(":proxy-node")
include(":proxy-client")
include(":proxy-server")
