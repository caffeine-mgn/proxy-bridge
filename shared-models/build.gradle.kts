plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlinx.serialization)
}

repositories {
    mavenCentral()
    maven("https://maven.google.com")
}

kotlin {
    jvm()
    linuxX64()
    linuxArm64()
    mingwX64()
    macosX64()
    macosArm64()
    js {
        browser()
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
