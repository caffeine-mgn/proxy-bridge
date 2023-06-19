buildscript {
    repositories {
    }
    dependencies {
    }
}
plugins {
    kotlin("jvm") version "1.8.21"
    id("com.github.gmazzo.buildconfig") version "3.0.3"
}
val binomVersion = project.property("binom.version") as String
val kotlinVersion = kotlin.coreLibrariesVersion
val shadowVersion = project.property("shadow.version") as String
buildConfig {
    packageName(project.group.toString())
    buildConfigField("String", "BINOM_VERSION", "\"$binomVersion\"")
    buildConfigField("String", "KOTLIN_VERSION", "\"$kotlinVersion\"")
    buildConfigField("String", "SHADOW_VERSION", "\"$shadowVersion\"")
}
repositories {
    mavenCentral()
}
dependencies {
    api("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")
    api("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
    api("org.jetbrains.kotlin:kotlin-compiler-embeddable:$kotlinVersion")
    api("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
    api("org.jetbrains.kotlin:kotlin-serialization:$kotlinVersion")
}
