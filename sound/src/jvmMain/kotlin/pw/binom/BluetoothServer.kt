package pw.binom

import pw.binom.bluetooth.asyncAcceptAndOpen
import pw.binom.bluetooth.create
import pw.binom.io.BluetoothConnection
import javax.bluetooth.LocalDevice
import javax.microedition.io.Connector
import javax.microedition.io.StreamConnectionNotifier

actual class BluetoothServer actual constructor() : AutoCloseable {
    private val localDevice = LocalDevice.getLocalDevice()
    private val uuid = "0000110100002000800000805f9b34fb"
    private val url = "btspp://localhost:$uuid;name=SPPServer"
    private val streamConnectionNotifier = Connector.open(url) as StreamConnectionNotifier

    suspend fun accept(): BluetoothConnection =
        BluetoothConnection.create(streamConnectionNotifier.asyncAcceptAndOpen())


    override fun close() {
        streamConnectionNotifier.close()
    }

    init {
        /*
        BeanLifeCycle.preDestroy {
            streamConnectionNotifier?.close()
            job?.cancelAndJoin()
//            threadCoroutineDispatcher.close()
        }
        BeanLifeCycle.postConstruct {
            logger.info("Адрес Bluetooth устройства: ${localDevice.bluetoothAddress}")
            logger.info("Имя Bluetooth устройства: ${localDevice.friendlyName}")

            logger.info("Сервис SPP опубликован: btspp://${localDevice.bluetoothAddress}:1;authenticate=false;encrypt=false;master=false")
            job = GlobalScope.launch {
                while (isActive) {
                    val connection = try {
                        logger.info("Wait a client...")
                        streamConnectionNotifier!!.asyncAcceptAndOpen()
                    } catch (e: InterruptedException) {
                        logger.info("Client is interrupted")
                        break
                    }
                    logger.info("Client connected: $connection")
                    val channel = BluetoothAsyncChannel(connection)
                    val clientConnection = BluetoothConnection(
                        income = virtualChannelService.income,
                        outcome = virtualChannelService.outcome,
                        nativeChannel = channel,
                    )
                    GlobalScope.launch {
                        try {
                            channel.useAsync {
                                clientConnection.processing()
                            }
                        } finally {
                            logger.info("Client processing finished: $clientConnection")
                        }
                    }
                }
            }
        }
        */
    }
}
