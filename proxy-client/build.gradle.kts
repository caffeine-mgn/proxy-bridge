import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.CompressionLevel
import net.lingala.zip4j.model.enums.EncryptionMethod

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
//        compilations.all {
//            kotlinOptions.jvmTarget = "1.8"
//        }
    }
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(kotlin("stdlib"))
                api("pw.binom.io:httpClient:${pw.binom.Versions.BINOM_VERSION}")
                api("pw.binom.io:strong:${pw.binom.Versions.BINOM_VERSION}")
                api("pw.binom.io:signal:${pw.binom.Versions.BINOM_VERSION}")
                api("pw.binom.io:logger:${pw.binom.Versions.BINOM_VERSION}")
                api("pw.binom.io:process:${pw.binom.Versions.BINOM_VERSION}")
                api(
                    "org.jetbrains.kotlinx:kotlinx-serialization-properties:${pw.binom.Versions.KOTLINX_SERIALIZATION_VERSION}"
                )
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
    register("deploy2", Copy::class.java) {
        dependsOn(linkMingw)
        dependsOn(shadowJar)
        from(
            file("build/bin/mingwX64/releaseExecutable"),
            file("build/libs/proxy-client.jar")
        )
        into(file("/home/subochev/Nextcloud/tmp/dddd"))
    }
    val batFile = buildDir.resolve("run-jvm.bat")
    val genBat =
        register("genBat") {
            outputs.file(batFile)
            doLast {
                batFile
                    .writeText(
                        "C:\\Users\\SubochevAV\\jvms\\jdk-17.0.2\\bin\\java -jar proxy-client.jar -Durl=http://r2d3.entry.binom.pw -Dproxy.address=webproxy.isb:8080 -Dproxy.auth.basicAuth.user=subochevav -Dproxy.auth.basicAuth.password=droVosek3192 -DtransportType=WS"
                    )
            }
        }

    register("deploy") {
        dependsOn(shadowJar)
        dependsOn(genBat)
        inputs.file(shadowJar.archiveFile)
        inputs.file(batFile)
        doLast {
            val zipParameters = ZipParameters()
            zipParameters.isEncryptFiles = true
            zipParameters.compressionLevel = CompressionLevel.HIGHER
            zipParameters.encryptionMethod = EncryptionMethod.AES

            val zipFile = ZipFile("/home/subochev/Nextcloud/tmp/dddd/proxy-client.zip", "1".toCharArray())
            zipFile.addFile(shadowJar.archiveFile.get().asFile, zipParameters)
            zipFile.addFile(batFile, zipParameters)
        }
    }
}
