plugins {
    kotlin("multiplatform")
    id("kotlinx-serialization")
}
kotlin {
    jvm {
    }
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(kotlin("stdlib"))
                api(libs.kotlinx.serialization.core)
                api(libs.kotlinx.serialization.json)
                api(libs.kotlinx.serialization.protobuf)
                api(libs.kotlinx.coroutines.core)
                api(project(":shared"))
                api(libs.loggeing)
                api("com.fazecast:jSerialComm:2.11.0")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
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
