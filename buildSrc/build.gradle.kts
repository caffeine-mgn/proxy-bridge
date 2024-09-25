buildscript {
    repositories {
    }
    dependencies {
    }
}
plugins {
    kotlin("jvm") version "2.0.20"
    id("com.github.gmazzo.buildconfig") version "3.0.3"
}
val binomVersion = project.property("binom.version") as String
val kotlinVersion = kotlin.coreLibrariesVersion
val shadowVersion = project.property("shadow.version") as String
val kotlinxCoroutinesVersion = project.property("kotlinx_coroutines.version") as String
val kotlinxSerializationVersion = project.property("kotlinx_serialization.version") as String
buildConfig {
    packageName(project.group.toString())
    buildConfigField("String", "BINOM_VERSION", "\"$binomVersion\"")
    buildConfigField("String", "KOTLIN_VERSION", "\"$kotlinVersion\"")
    buildConfigField("String", "SHADOW_VERSION", "\"$shadowVersion\"")
    buildConfigField("String", "KOTLINX_COROUTINES_VERSION", "\"$kotlinxCoroutinesVersion\"")
    buildConfigField("String", "KOTLINX_SERIALIZATION_VERSION", "\"$kotlinxSerializationVersion\"")
}
repositories {
    mavenLocal()
    mavenCentral()
    gradlePluginPortal()
}
dependencies {
    api("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")
    api("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
    api("org.jetbrains.kotlin:kotlin-compiler-embeddable:$kotlinVersion")
    api("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
    api("org.jetbrains.kotlin:kotlin-serialization:$kotlinVersion")
    api("com.bmuschko:gradle-docker-plugin:9.4.0")
    api("net.lingala.zip4j:zip4j:2.9.0")
}
