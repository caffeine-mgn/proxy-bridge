plugins {
    kotlin("multiplatform")
    id("kotlinx-serialization")
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
        val commonMain by getting {
            dependencies {
                api(kotlin("stdlib"))
                api(libs.kotlinx.serialization.core)
                api(libs.kotlinx.serialization.json)
                api(libs.kotlinx.serialization.protobuf)
                api(libs.kotlinx.coroutines.core)
                api(libs.kotlinx.io.core)
                api("io.ktor:ktor-server-core:${libs.versions.ktor.get()}")
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
                implementation(libs.ktor.server.test.host)
                implementation(libs.ktor.server.netty)
                implementation(libs.ktor.client.cio)
            }
        }
    }
}
