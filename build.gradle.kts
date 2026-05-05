plugins {
    alias(libs.plugins.shadow) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlinx.serialization) apply false
//    id("com.github.johnrengelman.shadow") version pw.binom.Versions.SHADOW_VERSION apply false
}


buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("org.hidetake:groovy-ssh:2.11.2")
    }
}
allprojects {
    repositories {
        mavenLocal()
        maven(url = "https://repo.binom.pw")
        maven(url = "https://jitpack.io")
        mavenCentral()
    }
}
