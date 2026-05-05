import org.hidetake.groovy.ssh.Ssh
import org.hidetake.groovy.ssh.connection.AllowAnyHosts
import org.hidetake.groovy.ssh.core.Remote
import kotlin.collections.hashMapOf

//import net.lingala.zip4j.ZipFile
//import net.lingala.zip4j.model.ZipParameters
//import net.lingala.zip4j.model.enums.CompressionLevel
//import net.lingala.zip4j.model.enums.EncryptionMethod
//import pw.binom.*

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.shadow)
}
val nativeEntryPoint = "pw.binom.gateway.main"
kotlin {
//    linuxX64 {
//        binaries {
//            executable {
//                entryPoint = nativeEntryPoint
//            }
//        }
//    }
//    mingwX64 {
//        binaries {
//            executable {
//                entryPoint = nativeEntryPoint
//            }
//        }
//    }
    jvm {
        binaries {
        }
//        compilations.all {
//            kotlinOptions.jvmTarget = "1.8"
//        }
    }
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(kotlin("stdlib"))
                api(libs.kotlinx.serialization.properties)
                api(libs.ktor.server.core)
                api(project(":shared"))
                api(project(":sound"))
                api(project(":com"))
                api(libs.slf4j.simple)
                api(libs.koin.core)
//                ksp(libs.koin.compiler)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
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

tasks.named<Jar>("jvmJar") {
    manifest {
        attributes["Main-Class"] = "pw.binom.gateway.MainJvm"
    }
    from({
        configurations["jvmMainRuntimeClasspath"].map { if (it.isDirectory) it else zipTree(it) }
    })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    exclude("META-INF/*.SF")
    exclude("META-INF/*.DSA")
    exclude("META-INF/*.RSA")
}


tasks {
    val jvmJar by getting(Jar::class)


//    val linkMingw = this.getByName("linkReleaseExecutableMingwX64")
    register("deploy2", Copy::class.java) {
//        dependsOn(linkMingw)
        dependsOn(jvmJar)
        from(
//            file("build/bin/mingwX64/releaseExecutable"),
            file("build/libs/proxy-client.jar")
        )
        into(file("/home/subochev/Nextcloud/tmp/dddd"))
    }
    val copyToRaspberry by registering {
        dependsOn(jvmJar)
        inputs.file(jvmJar.archiveFile)
        doLast {
            val remote = Remote(
                hashMapOf<String, Any?>(
                    "host" to "192.168.76.108",
                    "user" to "root",
                    "port" to 22,
                    "identity" to File("/home/subochev/.ssh/id_rsa"),
                    "knownHosts" to AllowAnyHosts.instance
                )
            )
            val ssh = Ssh.newService()
            ssh.run(delegateClosureOf<org.hidetake.groovy.ssh.core.RunHandler> {
                session(remote, delegateClosureOf<org.hidetake.groovy.ssh.session.SessionHandler> {
                    put(
                        hashMapOf(
                            "from" to jvmJar.archiveFile.get().asFile,
                            "into" to "/opt/proxy-gateway"
                        )
                    )
                })
            })
        }
    }

    val copyToWorkPc by registering {
        dependsOn(copyToRaspberry)
//        inputs.files(copyToRaspberry.get().outputs.files)
        doLast {
            val remote = Remote(
                hashMapOf<String, Any?>(
                    "host" to "192.168.76.108",
                    "user" to "root",
                    "port" to 22,
                    "identity" to File("/home/subochev/.ssh/id_rsa"),
                    "knownHosts" to AllowAnyHosts.instance
                )
            )
            val ssh = Ssh.newService()
            ssh.run(delegateClosureOf<org.hidetake.groovy.ssh.core.RunHandler> {
                session(remote, delegateClosureOf<org.hidetake.groovy.ssh.session.SessionHandler> {
                    execute(listOf("/usr/bin/java","-jar","/opt/uploader/file-upload-service-jvm.jar","-p","/dev/ttyGS0", "-f", "/opt/proxy-gateway/proxy-gateway-jvm.jar"))
                })
            })
        }
    }
//    val deployGermany by creating {
//        inputs.file(shadowJar.archiveFile)
//        dependsOn(shadowJar)
//        doLast {
//            SSH.run(
//                ip = "10.200.196.2",
//                user = "root",
//                port = 7622,
//            ) {
//                put(
//                    from = shadowJar.archiveFile.get().asFile,
//                    to = "/opt/proxy/gateway.jar"
//                )
//            }
//        }
//    }

//    val deploy by creating {
//        group = "Deploy"
//        dependsOn(shadowJar)
//        dependsOn(generateBatchFile)
//        inputs.file(shadowJar.archiveFile)
//        inputs.file(batFile)
//        doLast {
//            val zipParameters = ZipParameters()
//            zipParameters.isEncryptFiles = true
//            zipParameters.compressionLevel = CompressionLevel.HIGHER
//            zipParameters.encryptionMethod = EncryptionMethod.AES
//
//            val zipFile = ZipFile("/home/subochev/Nextcloud/tmp/dddd/proxy-client.zip", "1".toCharArray())
//            zipFile.addFile(shadowJar.archiveFile.get().asFile, zipParameters)
//            zipFile.addFile(batFile, zipParameters)
//        }
//    }
}
