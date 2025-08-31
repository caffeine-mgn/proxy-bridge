plugins {
    kotlin("multiplatform")
    id("kotlinx-serialization")
    id("org.jetbrains.kotlinx.kover") version "0.9.1"
    id("com.google.devtools.ksp") version "2.0.21-1.0.28"
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
                api(libs.koin.core)
                api(libs.koin.annotations)
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

//                ksp("io.insert-koin:koin-ksp-compiler:${libs.versions.koin.annotations.get()}")
            }
        }
        val commonTest by getting {
            dependencies {
                api(libs.koin.test)
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
