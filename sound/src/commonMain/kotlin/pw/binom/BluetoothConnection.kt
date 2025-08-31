package pw.binom

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import pw.binom.io.*
import pw.binom.logger.Logger
import pw.binom.logger.info
import pw.binom.logger.infoSync
import pw.binom.logger.warn
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration.Companion.seconds

class BluetoothConnection(
    private val income: SendChannel<ByteBuffer>,
    private val outcome: ReceiveChannel<ByteBuffer>,
    private val nativeChannel: AsyncChannel
) : AsyncCloseable {
    private val logger by Logger.ofThisOrGlobal
    private var jobRead: Job? = null
    private var jobWrite: Job? = null
    suspend fun processing() {
        logger.info("Start processing")
        try {
            jobRead = GlobalScope.launch(coroutineContext) {
                logger.info("Reading processing")
                try {
                    readProcessing()
                } finally {
                    jobWrite?.cancel(message = "Read process finished!")
                }
            }
            jobWrite = GlobalScope.launch(coroutineContext) {
                logger.info("Writing processing")
                try {
                    writeProcessing()
                } finally {
                    jobRead?.cancel(message = "Write process finished!")
                }
            }
            jobRead?.join()
            jobWrite?.join()
        } finally {
            logger.info("Stop processing")
        }
    }

    suspend fun readProcessing() {
        while (coroutineContext.isActive) {
            try {
                logger.info("Reading package size...")
                val size = nativeChannel.readInt()
                logger.info("Package size: $size. Reading package...")
                val buf = ByteArray(size)
                nativeChannel.readFully(buf)
                logger.info("Buffer was read success! Try push to virtual manager")
                timeoutChecker(timeout = 5.seconds, onTimeout = {
                    logger.infoSync("Timeout pushing data to virtual channel...")
                }) {
                    income.send(buf.wrap())
                }
                logger.info("Package read success!")
            } catch (_: ClosedReceiveChannelException) {
                break
            } catch (e: CancellationException) {
                logger.warn(text = "Reading processing cancelled", exception = e)
                break
            } catch (e: Exception) {
                logger.info(text = "Error on reading processing", exception = e)
                break
            }
        }
    }

    suspend fun writeProcessing() {
        while (coroutineContext.isActive) {
            try {
                outcome.receive().use { buf ->
                    logger.info("writing package size ${buf.remaining}")
                    nativeChannel.writeInt(buf.remaining)
                    logger.info("writing frame data")
                    nativeChannel.writeFully(buf)
                    logger.info("frame write success")
                }
                nativeChannel.flush()
            } catch (_: ClosedReceiveChannelException) {
                break
            } catch (e: CancellationException) {
                logger.warn(text = "Writing processing cancelled", exception = e)
                break
            } catch (e: Exception) {
                logger.info(text = "Error on writing processing", exception = e)
                break
            }
        }
    }

    override suspend fun asyncClose() {
        jobRead?.cancelAndJoin()
        jobWrite?.cancelAndJoin()
        nativeChannel.asyncClose()
    }

}
