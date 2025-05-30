import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.hidetake.groovy.ssh.core.Remote
import pw.binom.*

plugins {
    kotlin("multiplatform")
    id("kotlinx-serialization")
    id("com.github.johnrengelman.shadow")
//    id("org.hidetake.ssh")// version "2.11.2"
//    id("pw.binom.strong") version "1.0.0-SNAPSHOT"
}
val nativeEntryPoint = "pw.binom.proxy.main"
description = "proxy-node"
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
                api(libs.binom.httpServer)
                api(libs.binom.httpClient)
                api(libs.binom.strong.core)
                api(libs.binom.signal)
                api(libs.binom.logger)
                api(libs.binom.propertiesSerialization)
                api(libs.kotlinx.serialization.json)
                api(libs.binom.prometheus)
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
        exclude("META-INF/*.SF")
        exclude("META-INF/*.DSA")
        exclude("META-INF/*.RSA")
        exclude("META-INF/*.txt")
        manifest {
            attributes("Main-Class" to "pw.binom.proxy.MainJvm")
        }
    }
}

//apply {
//    plugin(pw.binom.DockerPackNative::class.java)
//}

tasks {
    val linkLinuxRelease = this.getByName("linkReleaseExecutableLinuxX64")
    val linkLinuxDebug = this.getByName("linkDebugExecutableLinuxX64")
    val shadowJar by getting
    val deploy by creating {
        val shadowJar = shadowJar as ShadowJar
        group = "Deploy"
        inputs.file(shadowJar.archiveFile)
        dependsOn(shadowJar)
        doLast {
            SSH.run(
                ip = "192.168.88.116",
                user = "root",
            ) {
                put(
                    from = shadowJar.archiveFile.get().asFile,
                    to = "/opt/proxy/proxy-node.jar"
                )
            }
        }
    }
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
