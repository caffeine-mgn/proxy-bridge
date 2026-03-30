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
            val ssh =Ssh.newService()
            ssh.run(delegateClosureOf<org.hidetake.groovy.ssh.core.RunHandler> {
                session(remote, delegateClosureOf<org.hidetake.groovy.ssh.session.SessionHandler> {
                    put(
                        hashMapOf(
                            "from" to jvmJar.archiveFile.get().asFile,
                            "into" to "/opt/gateway"
                        )
                    )
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
