import pw.binom.*

plugins {
    kotlin("multiplatform")
    id("kotlinx-serialization")
//    id("com.bmuschko.docker-remote-api") version "9.4.0"
}
kotlin {
    linuxX64()
//    mingwX64()
    jvm {
//        compilations.all {
//            kotlinOptions.jvmTarget = "1.8"
//        }
    }
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(kotlin("stdlib"))
                api(libs.kotlinx.coroutines.core)
                api(libs.kotlinx.serialization.core)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(project(":proxy-gateway"))
                implementation(project(":proxy-server"))
                api(libs.binom.testing)
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

fun TaskContainer.eachKotlinTest(func: (Task) -> Unit) {
    this.mapNotNull { it as? org.jetbrains.kotlin.gradle.tasks.KotlinTest }
        .forEach(func)
    this.mapNotNull { it as? org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmTest }
        .forEach(func)
}

//val proxy = DockerUtils.dockerContainer(
//    project = this,
//    image = "",
//    tcpPorts = listOf(11 to 11),
//    suffix = ""
//)

//tasks {
//    eachKotlinTest {
//        proxy.dependsOn(it)
//    }
//}


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
