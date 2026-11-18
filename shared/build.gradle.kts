plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlinx.serialization)
    id("org.jetbrains.kotlinx.kover") version "0.9.1"
    id("com.google.devtools.ksp") version "2.0.21-1.0.28"
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
            api(kotlin("stdlib"))
            api(project(":multiplexer"))
            api(project(":webdav"))
            api("pw.binom:serial:1.0.0")
            api(libs.kotlinx.io.core)
            api(libs.koin.core)
            api(libs.koin.annotations)
            api(libs.kotlinx.serialization.json)
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.serialization.core)
            api(libs.kotlinx.serialization.protobuf)
            api("io.github.oshai:kotlin-logging:7.0.3")

            api("io.ktor:ktor-server-cio:${libs.versions.ktor.get()}")
            api("io.ktor:ktor-server-core:${libs.versions.ktor.get()}")
            api("io.ktor:ktor-client-cio:${libs.versions.ktor.get()}")
            api("io.ktor:ktor-client-core:${libs.versions.ktor.get()}")
        }
        val nativeMain by getting {
            dependsOn(commonMain.get())
        }
        commonTest.dependencies {
            api(libs.koin.test)
            implementation(kotlin("test-common"))
            implementation(kotlin("test-annotations-common"))
            api(libs.kotlinx.coroutines.test)
        }
        jvmTest {
            dependsOn(commonTest.get())
            dependencies {
                api(kotlin("test"))
            }
        }
    }
}

tasks {
    withType(Test::class) {
        testLogging.showStandardStreams = true
        testLogging.showCauses = true
        testLogging.showExceptions = true
        testLogging.showStackTraces = true
        testLogging.exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
