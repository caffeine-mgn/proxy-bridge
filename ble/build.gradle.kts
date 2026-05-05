plugins {
    kotlin("multiplatform")
    id("kotlinx-serialization")
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
            api("dev.bluefalcon:blue-falcon:3.1.1")
            api(libs.kotlinx.coroutines.core)
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
//        useJUnitPlatform()
        testLogging.showStandardStreams = true
        testLogging.showCauses = true
        testLogging.showExceptions = true
        testLogging.showStackTraces = true
        testLogging.exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
