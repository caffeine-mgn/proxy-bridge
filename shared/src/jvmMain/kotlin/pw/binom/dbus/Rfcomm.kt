package pw.binom.dbus

import java.io.File
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import com.github.hypfvieh.bluetooth.wrapper.BluetoothDevice

@OptIn(ExperimentalAtomicApi::class)
class Rfcomm(
    private val number: Int,
    val device: BluetoothDevice,
) : AutoCloseable {
    companion object {
        fun resetMode(number: Int) {
            val exitCode =
                ProcessBuilder("stty", "-F", "/dev/rfcomm$number", "sane").start().waitFor()
            check(exitCode == 0) { "Can't reset /dev/rfcomm$number: invalid exit code: $exitCode" }
        }

        fun disableEcho(number: Int) {
            val exitCode =
                ProcessBuilder("stty", "-F", "/dev/rfcomm$number", "raw", "-echo", "-icanon").start().waitFor()
            check(exitCode == 0) { "Can't disable echo /dev/rfcomm$number: invalid exit code: $exitCode" }
        }
    }

    private val closed = AtomicBoolean(false)
    val file
        get() = File("/dev/rfcomm$number")

    fun isConnected(): Boolean {
        val process = ProcessBuilder("rfcomm", "show", number.toString()).start()
        val text = process.inputStream.bufferedReader().use { it.readText() }
        if ("connected" in text) {
            return true
        }
        return true
    }

    private fun release() {
        resetMode(number)
        val exitCode =
            ProcessBuilder("rfcomm", "release", "/dev/rfcomm$number").start().waitFor()
        check(exitCode == 0) { "Can't unbind rfcomm: invalid exit code: $exitCode" }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            release()
        }
    }
}
