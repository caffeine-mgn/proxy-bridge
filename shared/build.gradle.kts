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
                api("pw.binom.io:strong:${pw.binom.Versions.BINOM_VERSION}")
                api("pw.binom.io:http:${pw.binom.Versions.BINOM_VERSION}")
                api("pw.binom.io:logger:${pw.binom.Versions.BINOM_VERSION}")
                api("pw.binom.io:file:${pw.binom.Versions.BINOM_VERSION}")
                api("pw.binom.io:httpServer:${pw.binom.Versions.BINOM_VERSION}")
                api("pw.binom.io:metric:${pw.binom.Versions.BINOM_VERSION}")
                api("pw.binom.io:compression:${pw.binom.Versions.BINOM_VERSION}")
                api("pw.binom.io:coroutines:${pw.binom.Versions.BINOM_VERSION}")
                api("pw.binom.io:validate:${pw.binom.Versions.BINOM_VERSION}")
                api("pw.binom.io:thread:${pw.binom.Versions.BINOM_VERSION}")
                api("pw.binom.io:socket:${pw.binom.Versions.BINOM_VERSION}")
                api("pw.binom.io:xml:${pw.binom.Versions.BINOM_VERSION}")
                api("pw.binom.io:network:${pw.binom.Versions.BINOM_VERSION}")
                api("pw.binom.io:httpClient:${pw.binom.Versions.BINOM_VERSION}")
                api("pw.binom.io:properties-serialization:${pw.binom.Versions.BINOM_VERSION}")
                api("org.jetbrains.kotlinx:kotlinx-serialization-json:${pw.binom.Versions.KOTLINX_SERIALIZATION_VERSION}")
                api("org.jetbrains.kotlinx:kotlinx-coroutines-core:${pw.binom.Versions.KOTLINX_COROUTINES_VERSION}")
                api(
                    "org.jetbrains.kotlinx:kotlinx-serialization-core:${pw.binom.Versions.KOTLINX_SERIALIZATION_VERSION}"
                )
                api(
                    "org.jetbrains.kotlinx:kotlinx-serialization-protobuf:${pw.binom.Versions.KOTLINX_SERIALIZATION_VERSION}"
                )
                api("pw.binom.io:sqlite:${pw.binom.Versions.BINOM_VERSION}")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
            }
        }
        val commonTest by getting {
            dependencies {
                api(project(":testing-tools"))
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
                api("pw.binom.io:testing:${pw.binom.Versions.BINOM_VERSION}")
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
//        useJUnitPlatform()
        testLogging.showStandardStreams = true
        testLogging.showCauses = true
        testLogging.showExceptions = true
        testLogging.showStackTraces = true
        testLogging.exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
