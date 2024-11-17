import org.hidetake.groovy.ssh.core.Remote

plugins {
    kotlin("multiplatform")
    id("kotlinx-serialization")
    id("com.github.johnrengelman.shadow")
    id("org.hidetake.ssh") version "2.11.2"
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
                api("pw.binom.io:httpServer:${pw.binom.Versions.BINOM_VERSION}")
                api("pw.binom.io:httpClient:${pw.binom.Versions.BINOM_VERSION}")
                api("pw.binom.io:strong:${pw.binom.Versions.BINOM_VERSION}")
                api("pw.binom.io:signal:${pw.binom.Versions.BINOM_VERSION}")
                api("pw.binom.io:logger:${pw.binom.Versions.BINOM_VERSION}")
                api("pw.binom.io:properties-serialization:${pw.binom.Versions.BINOM_VERSION}")
                api("org.jetbrains.kotlinx:kotlinx-serialization-json:${pw.binom.Versions.KOTLINX_SERIALIZATION_VERSION}")
                api("pw.binom.io:prometheus:${pw.binom.Versions.BINOM_VERSION}")
                api(
                    "org.jetbrains.kotlinx:kotlinx-serialization-properties:${pw.binom.Versions.KOTLINX_SERIALIZATION_VERSION}"
                )
                api(project(":shared"))
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

apply {
    plugin(pw.binom.DockerPackNative::class.java)
}

tasks {
    val linkLinuxRelease = this.getByName("linkReleaseExecutableLinuxX64")
    val linkLinuxDebug = this.getByName("linkDebugExecutableLinuxX64")
    val shadowJar by getting
    register("deploy") {
        dependsOn(shadowJar)
        doLast {
            ssh.run(
                delegateClosureOf<org.hidetake.groovy.ssh.core.RunHandler> {
                    val remote =
                        Remote(
                            hashMapOf<String, Any?>(
                                "host" to "192.168.88.116",
                                "user" to "root",
                                "identity" to file("/home/subochev/.ssh/id_rsa"),
                                "knownHosts" to org.hidetake.groovy.ssh.connection.AllowAnyHosts.instance
                            )
                        )
                    println("remote=$remote")
//                    println("ssh.remotes=${ssh.remotes}")
//                    val remote = (ssh.remotes as Map<*,*>)["linux-test"] as Remote
                    val jvmFile = layout.buildDirectory.file("libs/proxy-server.jar")
                    val linuxReleaseFile = layout.buildDirectory.file("bin/linuxX64/releaseExecutable/proxy-node.kexe")
                    session(
                        remote,
                        delegateClosureOf<org.hidetake.groovy.ssh.session.SessionHandler> {
                            put(
                                hashMapOf(
                                    "from" to jvmFile.get().asFile,
//                                    "from" to file("build/bin/linuxX64/debugExecutable/proxy-node.kexe"),
                                    "into" to "/opt/proxy/proxy-node.jar"
                                )
                            )
                        }
                    )
                }
            )
        }
    }
}
