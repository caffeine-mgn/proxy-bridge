import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.CompressionLevel
import net.lingala.zip4j.model.enums.EncryptionMethod
import pw.binom.*

plugins {
    kotlin("multiplatform")
    id("kotlinx-serialization")
    id("com.github.johnrengelman.shadow")
}
val nativeEntryPoint = "pw.binom.gateway.main"
kotlin {
    linuxX64 {
        binaries {
            executable {
                entryPoint = nativeEntryPoint
            }
        }
    }
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
                api(libs.binom.httpClient)
                api(libs.binom.strong.core)
                api(libs.binom.signal)
                api(libs.binom.logger)
                api(libs.binom.process)
                api(libs.binom.propertiesSerialization)
                api(libs.kotlinx.serialization.properties)
                api(project(":shared"))
                api(project(":sound"))
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(project(":testing-tools"))
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
                implementation(libs.binom.testing)
                implementation(libs.binom.coroutines)
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
    val jvmJar by getting(Jar::class)
    val shadowJar by creating(com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar::class) {
        from(jvmJar.archiveFile)
        group = "build"
        configurations = listOf(project.configurations["jvmRuntimeClasspath"])
        exclude {
            it.name.endsWith(".DSA") || it.name.endsWith(".SF") || it.name.endsWith(".kotlin_module")
        }
        exclude("META-INF/*.SF")
        exclude("META-INF/*.DSA")
        exclude("META-INF/*.RSA")
        exclude("META-INF/*.txt")
        manifest {
            attributes("Main-Class" to "pw.binom.gateway.MainJvm")
        }
    }
//    val linkMingw = this.getByName("linkReleaseExecutableMingwX64")
    register("deploy2", Copy::class.java) {
//        dependsOn(linkMingw)
        dependsOn(shadowJar)
        from(
//            file("build/bin/mingwX64/releaseExecutable"),
            file("build/libs/proxy-client.jar")
        )
        into(file("/home/subochev/Nextcloud/tmp/dddd"))
    }
    val batFile = buildDir.resolve("run-jvm.bat")
    val generateBatchFile by creating {
        outputs.file(batFile)
        doLast {
            batFile
                .writeText(
                    "C:\\Users\\SubochevAV\\jvms\\jdk-17.0.2\\bin\\java -jar proxy-client.jar"
                )
        }
    }

    val deployGermany by creating {
        inputs.file(shadowJar.archiveFile)
        dependsOn(shadowJar)
        doLast {
            SSH.run(
                ip = "10.200.196.2",
                user = "root",
                port = 7622,
            ) {
                put(
                    from = shadowJar.archiveFile.get().asFile,
                    to = "/opt/proxy/gateway.jar"
                )
            }
        }
    }

    val deploy by creating {
        group = "Deploy"
        dependsOn(shadowJar)
        dependsOn(generateBatchFile)
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
