package pw.binom
/*
import org.gradle.api.Project
import org.gradle.api.tasks.Copy
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeLink
import org.jetbrains.kotlin.konan.target.KonanTarget
import java.io.File

abstract class DockerPackNative : BasicDockerPlugin() {
    val useValgrind = false
    override fun apply(project: Project) {
        super.apply(project)
        val nativeTargets = project.tasks
            .mapNotNull { it as? KotlinNativeLink }
            .filter { it.binary.debuggable }
        val linuxTarget = nativeTargets.filter { it.binary.target.konanTarget == KonanTarget.LINUX_X64 }.firstOrNull()
        val linuxTask = linuxTarget ?: TODO("Can't find LinuxBuild task")
        val copyBinaryToDockerTask = project.tasks.create("copyBinaryToDocker", Copy::class.java).apply {
            dependsOn(linuxTask)
            group = "build"
            from(linuxTask.outputFile.get())
            destinationDir = File(project.buildDir, "docker")
        }

        createDockerfileTask.configure {
            it.from("debian:latest")
            it.copyFile(linuxTask.outputFile.get().name, "/app/binary")
            it.exposePort(80)
            it.workingDir("/app")
            it.runCommand("chmod 777 /app/binary")
            if (useValgrind) {
                it.runCommand("apt-get update && apt install valgrind -y")
                it.defaultCommand("valgrind", "--leak-check=full", "/app/binary", "-docker")
            } else {
                it.defaultCommand("/app/binary", "-docker")
            }
        }
        buildDockerBuildImageTask.configure {
            it.dependsOn(copyBinaryToDockerTask)
        }
    }
}
*/
