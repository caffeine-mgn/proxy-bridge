plugins {
    kotlin("multiplatform")
    id("kotlinx-serialization")
    id("org.jetbrains.kotlinx.kover") version "0.9.1"
    id("com.google.devtools.ksp") version "2.0.21-1.0.28"
}
val nativeEntryPoint = "pw.binom.proxy.main"
kotlin {
//    linuxX64()
//    mingwX64 {
//        binaries {
//            executable {
//                entryPoint = nativeEntryPoint
//            }
//        }
//    }
    jvm {
//        compilations.all {
//            kotlinOptions.jvmTarget = "1.8"
//        }
    }
    sourceSets {

        commonMain.dependencies {
            api("dev.bluefalcon:blue-falcon:2.5.4")
            api(kotlin("stdlib"))
            api(project(":multiplexer"))
            api(libs.kotlinx.io.core)
            api(libs.koin.core)
            api(libs.koin.annotations)
            api(libs.kotlinx.serialization.json)
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.serialization.core)
            api(libs.kotlinx.serialization.protobuf)
            api(libs.kotlinx.coroutines.core)
            api("com.github.hypfvieh:dbus-java-core:5.2.0")
            api("com.github.hypfvieh:dbus-java-transport-native-unixsocket:5.2.0")
            api("com.github.hypfvieh:bluez-dbus:0.3.2")
            api("io.klogging:klogging:0.11.7")

            api("io.ktor:ktor-server-cio:${libs.versions.ktor.get()}")
            api("io.ktor:ktor-server-core:${libs.versions.ktor.get()}")
            api("io.ktor:ktor-client-cio:${libs.versions.ktor.get()}")
            api("io.ktor:ktor-client-core:${libs.versions.ktor.get()}")
        }
        commonTest.dependencies {
            api(libs.koin.test)
            implementation(kotlin("test-common"))
            implementation(kotlin("test-annotations-common"))
            api(libs.kotlinx.coroutines.test)
        }
        jvmMain.dependencies {
            api("io.ultreia:bluecove:2.1.1")
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
//        useJUnitPlatform()
        testLogging.showStandardStreams = true
        testLogging.showCauses = true
        testLogging.showExceptions = true
        testLogging.showStackTraces = true
        testLogging.exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
