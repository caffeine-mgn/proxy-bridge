@file:JvmName("MainJvm")

package pw.binom.bootstrap

import java.io.File

fun main(args: Array<String>) {
    BootstrapBluetoothClient.start(
        file = File("/home/subochev/projects/WORK/proxy-bridge/transport/build/libs/transport.jar"),
        addressAndChannel = "105FADED5516",
    )
}
