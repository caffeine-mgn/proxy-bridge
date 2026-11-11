plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvm()
    linuxX64()
    linuxArm64()
    mingwX64()
    macosX64()
    macosArm64()

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.io.core)
            api(libs.kotlinx.coroutines.core)
            api("io.github.oshai:kotlin-logging:7.0.3")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
