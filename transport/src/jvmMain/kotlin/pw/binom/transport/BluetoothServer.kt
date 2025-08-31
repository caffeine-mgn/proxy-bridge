package pw.binom.transport

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import pw.binom.io.useAsync
import pw.binom.logger.Logger
import pw.binom.logger.infoSync
import pw.binom.transport.io.AsyncInputStreamAdapter
import pw.binom.transport.io.AsyncOutputStreamAdapter
import pw.binom.transport.services.EchoService
import pw.binom.transport.services.SpeedTestService
import javax.bluetooth.LocalDevice
import javax.microedition.io.Connector
import javax.microedition.io.StreamConnection
import javax.microedition.io.StreamConnectionNotifier
import kotlin.time.Duration.Companion.seconds

object BluetoothServer {
    private val logger by Logger.ofThisOrGlobal
    fun start(
        serviceUuid: javax.bluetooth.UUID = BluetoothClient.SERVICE_UUID,//javax.bluetooth.UUID(0x1101),
        serviceName: String = "SPPServer",
    ) {
        val localDevice = LocalDevice.getLocalDevice()
        logger.infoSync("Адрес Bluetooth устройства: ${localDevice.bluetoothAddress}")
        logger.infoSync("Имя Bluetooth устройства: ${localDevice.friendlyName}")
        val uuid = serviceUuid.toString()
        val url = "btspp://localhost:$uuid;name=$serviceName"
        val streamConnectionNotifier = Connector.open(url) as StreamConnectionNotifier
        try {
            while (!Thread.currentThread().isInterrupted) {
                logger.infoSync("Waiting a client...")
                val input = streamConnectionNotifier.acceptAndOpen()
                logger.infoSync("Client connected")
                run(input)
//                val thread = ClientThread(input)
//                thread.start()
            }
        } finally {
            streamConnectionNotifier.close()
        }
    }

    private fun run(connection: StreamConnection) {
        val input = InputStreamImpl(connection.openInputStream())
        val output = OutputStreamImpl(connection.openOutputStream())
        val dispatcher = ThreadCoroutineDispatcher()

        CoroutineScope(dispatcher).launch(dispatcher) {
            try {
                AsyncInputStreamAdapter(
                    inputStream = input,
                    bufferSize = BluetoothClient.BUFFER_SIZE,
                ).useAsync { asyncInput ->
                    AsyncOutputStreamAdapter(
                        outputStream = output,
                        bufferSize = BluetoothClient.BUFFER_SIZE,
                    ).useAsync { asyncOutput ->
                        val manager = Manager.create(
                            input = asyncInput,
                            output = asyncOutput,
                            maxPackageSize = BluetoothClient.BUFFER_SIZE,
                            isServer = true,
                        ).onClose {
                            input.close()
                            output.close()
                            asyncInput.free()
                            asyncOutput.free()
                            connection.close()
                        }
                        manager.defineService(
                            serviceId = EchoService.ID,
                            EchoService(context = coroutineContext),
                        )
                        manager.defineService(
                            serviceId = SpeedTestService.ID,
                            SpeedTestService(context = coroutineContext),
                        )
                        while (isActive) {
                            delay(5.seconds)
                        }
                    }
                }
            } finally {
                input.close()
                output.close()
                connection.close()
                dispatcher.close()
            }
        }
    }
    /*
    class ClientThread(val connection: StreamConnection) : Thread() {
        private val input = connection.openInputStream()
        private val output = connection.openOutputStream()
        override fun run() {
            runBlocking {
                val asyncInput = AsyncInputStreamAdapter(
                    inputStream = input,
                    bufferSize = BluetoothClient.BUFFER_SIZE,
                ).useAsync { asyncInput ->
                    AsyncOutputStreamAdapter(
                        outputStream = output,
                        bufferSize = BluetoothClient.BUFFER_SIZE,
                    ).useAsync { asyncOutput ->
                        val manager = Manager.create(
                            input = asyncInput,
                            output = asyncOutput,
                            maxPackageSize = BUFFER_SIZE,
                            isServer = true,
                        ).onClose {
                            input.close()
                            output.close()
                            asyncInput.free()
                            asyncOutput.free()
                            connection.close()
                        }

                        while (!isInterrupted) {
                            sleep(1000)
                        }
                    }
                }
            }
        }
    }
    */
}
