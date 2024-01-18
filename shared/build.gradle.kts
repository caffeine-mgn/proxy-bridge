plugins {
    kotlin("multiplatform")
    id("kotlinx-serialization")
}
val nativeEntryPoint = "pw.binom.proxy.main"
kotlin {
    linuxX64()
    mingwX64 {
        binaries {
            executable {
                entryPoint = nativeEntryPoint
            }
        }
    }
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "1.8"
        }
    }
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(kotlin("stdlib"))
                api("pw.binom.io:http:${pw.binom.Versions.BINOM_VERSION}")
                api("pw.binom.io:logger:${pw.binom.Versions.BINOM_VERSION}")
                api("pw.binom.io:file:${pw.binom.Versions.BINOM_VERSION}")
                api("pw.binom.io:metric:${pw.binom.Versions.BINOM_VERSION}")
//                api("pw.binom.io:httpClient:${pw.binom.Versions.BINOM_VERSION}")
                api("org.jetbrains.kotlinx:kotlinx-coroutines-core:${pw.binom.Versions.KOTLINX_COROUTINES_VERSION}")
                api(
                    "org.jetbrains.kotlinx:kotlinx-serialization-core:${pw.binom.Versions.KOTLINX_SERIALIZATION_VERSION}"
                )
                api(
                    "org.jetbrains.kotlinx:kotlinx-serialization-protobuf:${pw.binom.Versions.KOTLINX_SERIALIZATION_VERSION}"
                )
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
                api("org.jetbrains.kotlinx:kotlinx-coroutines-test:${pw.binom.Versions.KOTLINX_COROUTINES_VERSION}")
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
        useJUnitPlatform()
        testLogging.showStandardStreams = true
        testLogging.showCauses = true
        testLogging.showExceptions = true
        testLogging.showStackTraces = true
        testLogging.exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
