plugins {
    kotlin("multiplatform")
    id("kotlinx-serialization")
    id("com.github.johnrengelman.shadow")
}
val nativeEntryPoint = "pw.binom.proxy.client.main"
kotlin {
    linuxX64 {
        binaries {
            executable {
                entryPoint = nativeEntryPoint
            }
        }
    }
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
                api("pw.binom.io:httpClient:${pw.binom.Versions.BINOM_VERSION}")
                api("pw.binom.io:strong:${pw.binom.Versions.BINOM_VERSION}")
                api("pw.binom.io:signal:${pw.binom.Versions.BINOM_VERSION}")
                api("pw.binom.io:logger:${pw.binom.Versions.BINOM_VERSION}")
                api("org.jetbrains.kotlinx:kotlinx-serialization-properties:${pw.binom.Versions.KOTLINX_SERIALIZATION_VERSION}")
                api(project(":shared"))
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }
    }
}
tasks {
    val jvmJar by getting(Jar::class)
    val shadowJar by creating(com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar::class) {
        from(jvmJar.archiveFile)
        group = "build"
        configurations = listOf(project.configurations["jvmRuntimeClasspath"])
        exclude {
            it.name.endsWith(".DSA") || it.name.endsWith(".SF") || it.name.endsWith(".kotlin_module")
        }
        manifest {
            attributes("Main-Class" to "pw.binom.proxy.client.MainJvm")
        }
    }
    val linkMingw = this.getByName("linkReleaseExecutableMingwX64")
    register("deploy", Copy::class.java) {
        dependsOn(linkMingw)
        dependsOn(shadowJar)
        from(
            file("build/bin/mingwX64/releaseExecutable"),
            file("build/libs/proxy-client.jar")
        )
        into(file("/media/subochev/BIG/Nextcloud/tmp/dddd"))
    }
}
