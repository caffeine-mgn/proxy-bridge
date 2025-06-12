package pw.binom

import com.fazecast.jSerialComm.SerialPort
import kotlinx.coroutines.*
import pw.binom.logger.Logger
import pw.binom.logger.info
import pw.binom.logger.infoSync
import pw.binom.strong.BeanLifeCycle
import kotlin.time.Duration.Companion.seconds

actual class ComClient actual constructor(port: String, speed: Int) {
    companion object {
        init {
            SerialPort.autoCleanupAtShutdown()
        }
    }

    private val logger by Logger.ofThisOrGlobal

    private var job: Job? = null

    init {
        logger.infoSync("Opening port \"$port\"...")
        val comPort = SerialPort.getCommPort(port)

        logger.infoSync("COM Port \"$port\" opened successfully")

        BeanLifeCycle.postConstruct {
            job = GlobalScope.launch {
                while (isActive) {
                    try {
                        val port = SerialPort.getCommPort(port)
                        comPort.baudRate = speed
                        if (comPort.openPort()) {
                            logger.info("Port $port opened successfully")
                        } else {
                            logger.info("Fail to open port $port")
                        }
                    } catch (e: Throwable) {
                        logger.info("Can't open: ${e.message ?: e.toString()}")
                        delay(10.seconds)
                    }
                }
            }
        }
        BeanLifeCycle.preDestroy {
            runCatching { comPort.closePort() }
        }
    }
}
