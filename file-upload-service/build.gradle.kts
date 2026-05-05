import org.hidetake.groovy.ssh.Ssh
import org.hidetake.groovy.ssh.connection.AllowAnyHosts
import org.hidetake.groovy.ssh.core.Remote

//import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
//import org.hidetake.groovy.ssh.core.Remote
//import pw.binom.*

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.shadow)
//    id("org.hidetake.ssh")// version "2.11.2"
//    id("pw.binom.strong") version "1.0.0-SNAPSHOT"
}
val nativeEntryPoint = "pw.binom.proxy.main"
description = "proxy-node"
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
            executable {
                mainClass = "pw.binom.MainJvm"
            }
        }
//        compilations.all {
//            kotlinOptions.jvmTarget = "1.8"
//        }
    }
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("com.github.ajalt.clikt:clikt:5.1.0")
                api(kotlin("stdlib"))
                api(libs.kotlinx.serialization.json)
                api(libs.ktor.server.negotiation)
                api(libs.ktor.serialization.json)
                api(libs.kotlinx.serialization.properties)
                api(project(":shared"))
                api(project(":sound"))
                api(project(":com"))
                api(libs.slf4j.simple)
                api(libs.koin.core)
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

tasks.named<Jar>("jvmJar") {
    manifest {
        attributes["Main-Class"] = "pw.binom.MainJvm"
    }
    from({
        configurations["jvmMainRuntimeClasspath"].map { if (it.isDirectory) it else zipTree(it) }
    })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    exclude("META-INF/*.SF")
    exclude("META-INF/*.DSA")
    exclude("META-INF/*.RSA")
}

//apply {
//    plugin(pw.binom.DockerPackNative::class.java)
//}

tasks {
    val jvmJar by getting(Jar::class)
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
                            "into" to "/opt/uploader"
                        )
                    )
                })
            })
        }
    }
//    val linkLinuxRelease = this.getByName("linkReleaseExecutableLinuxX64")
//    val linkLinuxDebug = this.getByName("linkDebugExecutableLinuxX64")
//    val shadowJar by getting
//    val deploy by creating {
//        val shadowJar = shadowJar as ShadowJar
//        group = "Deploy"
//        inputs.file(shadowJar.archiveFile)
//        dependsOn(shadowJar)
//        doLast {
//            SSH.run(
//                ip = "192.168.88.116",
//                user = "root",
//            ) {
//                put(
//                    from = shadowJar.archiveFile.get().asFile,
//                    to = "/opt/proxy/proxy-node.jar"
//                )
//            }
//        }
//    }
//    register("deploy") {
//        dependsOn(shadowJar)
//        doLast {
//            ssh.run(
//                delegateClosureOf<org.hidetake.groovy.ssh.core.RunHandler> {
//                    val remote =
//                        Remote(
//                            hashMapOf<String, Any?>(
//                                "host" to "192.168.88.116",
//                                "user" to "root",
//                                "identity" to file("/home/subochev/.ssh/id_rsa"),
//                                "knownHosts" to org.hidetake.groovy.ssh.connection.AllowAnyHosts.instance
//                            )
//                        )
//                    println("remote=$remote")
//                    val jvmFile = layout.buildDirectory.file("libs/proxy-server.jar")
//                    val linuxReleaseFile = layout.buildDirectory.file("bin/linuxX64/releaseExecutable/proxy-node.kexe")
//                    session(
//                        remote,
//                        delegateClosureOf<org.hidetake.groovy.ssh.session.SessionHandler> {
//                            put(
//                                hashMapOf(
//                                    "from" to jvmFile.get().asFile,
////                                    "from" to file("build/bin/linuxX64/debugExecutable/proxy-node.kexe"),
//                                    "into" to "/opt/proxy/proxy-node.jar"
//                                )
//                            )
//                        }
//                    )
//                }
//            )
//        }
//    }
}
