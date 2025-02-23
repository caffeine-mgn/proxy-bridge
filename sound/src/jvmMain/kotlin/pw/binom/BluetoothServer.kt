package pw.binom

import kotlinx.coroutines.*
import pw.binom.bluetooth.asyncAcceptAndOpen
import pw.binom.io.useAsync
import pw.binom.logger.Logger
import pw.binom.logger.info
import pw.binom.services.VirtualChannelService
import pw.binom.strong.BeanLifeCycle
import pw.binom.strong.inject
import javax.bluetooth.LocalDevice
import javax.microedition.io.Connector
import javax.microedition.io.StreamConnectionNotifier

actual class BluetoothServer actual constructor() {
    private val virtualChannelService by inject<VirtualChannelService>()

    //    private val threadCoroutineDispatcher = ThreadCoroutineDispatcher("Wating new clients")
    private var job: Job? = null
    private val logger by Logger.ofThisOrGlobal
    private var streamConnectionNotifier: StreamConnectionNotifier? = null

    init {
        BeanLifeCycle.preDestroy {
            streamConnectionNotifier?.close()
            job?.cancelAndJoin()
//            threadCoroutineDispatcher.close()
        }
        BeanLifeCycle.postConstruct {
            val localDevice = LocalDevice.getLocalDevice()
            logger.info("Адрес Bluetooth устройства: ${localDevice.bluetoothAddress}")
            logger.info("Имя Bluetooth устройства: ${localDevice.friendlyName}")
            val uuid = "0000110100002000800000805f9b34fb"
            val url = "btspp://localhost:$uuid;name=SPPServer"
            streamConnectionNotifier = Connector.open(url) as StreamConnectionNotifier
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
    }
}
