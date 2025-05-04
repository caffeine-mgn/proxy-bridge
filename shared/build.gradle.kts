plugins {
    kotlin("multiplatform")
    id("kotlinx-serialization")
}
val nativeEntryPoint = "pw.binom.proxy.main"
kotlin {
    linuxX64()
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
        val commonMain by getting {
            dependencies {
                api(kotlin("stdlib"))
                api(libs.binom.strong.core)
                api(libs.binom.http)
                api(libs.binom.logger)
                api(libs.binom.file)
                api(libs.binom.httpServer)
                api(libs.binom.metric)
                api(libs.binom.compression)
                api(libs.binom.coroutines)
                api(libs.binom.validate)
                api(libs.binom.thread)
                api(libs.binom.socket)
                api(libs.binom.xml)
                api(libs.binom.network)
                api(libs.binom.httpClient)
                api(libs.binom.propertiesSerialization)
                api(libs.kotlinx.serialization.json)
                api(libs.kotlinx.coroutines.core)
                api(libs.kotlinx.serialization.core)
                api(libs.kotlinx.serialization.protobuf)
                api(libs.binom.sqlite)
                api(libs.kotlinx.coroutines.core)
            }
        }
        val commonTest by getting {
            dependencies {
                api(project(":testing-tools"))
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
                api(libs.binom.testing)
                api(libs.kotlinx.coroutines.test)
            }
        }

        val jvmTest by getting {
            dependsOn(commonTest)
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
