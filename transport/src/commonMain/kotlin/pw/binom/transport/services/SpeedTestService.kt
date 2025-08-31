package pw.binom.transport.services

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import pw.binom.io.ByteBuffer
import pw.binom.io.nextBytes
import pw.binom.io.use
import pw.binom.io.using
import pw.binom.logger.Logger
import pw.binom.logger.info
import pw.binom.transport.MultiplexSocket
import pw.binom.transport.Service
import pw.binom.transport.VirtualManager
import pw.binom.transport.VirtualManagerImpl
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

class SpeedTestService(
    val scope: CoroutineScope = GlobalScope,
    val context: CoroutineContext = EmptyCoroutineContext,
) : Service {
    companion object {
        private val logger = Logger.getLogger("SpeedTestService")
        const val ID = 10
        private const val UPLOAD: Byte = 1
        private const val DOWNLOAD: Byte = 2
        suspend fun upload(manager: VirtualManager, time: Duration) = manager.connect(ID).use { socket ->
            logger.info("Start Upload Test")
            socket.output.writeByte(UPLOAD)
//            socket.output.flush()
            socket.output.writeLong(time.inWholeMilliseconds)
            socket.output.flush()
            var bytes = 0L
            ByteBuffer(DEFAULT_BUFFER_SIZE).using { buffer ->
                Random.nextBytes(buffer)
                var now = TimeSource.Monotonic.markNow()
                while (coroutineContext.isActive && now.elapsedNow() < time) {
                    buffer.clear()
                    val l = socket.output.write(buffer)
                    if (l.isClosed) {
                        break
                    }
                    bytes += l.length
                }
            }
            bytes
        }

        suspend fun download(manager: VirtualManager, time: Duration) = manager.connect(ID).use { socket ->
            logger.info("Start Download Test")
            socket.output.writeByte(DOWNLOAD)
            socket.output.flush()
            socket.output.writeLong(time.inWholeMilliseconds)
            socket.output.flush()
            var bytes = 0L
            var now = TimeSource.Monotonic.markNow()
            ByteBuffer(DEFAULT_BUFFER_SIZE).using { buffer ->
                while (coroutineContext.isActive && now.elapsedNow() < time) {
                    buffer.clear()
                    logger.info("Reading...")
                    val l = socket.input.read(buffer)
                    logger.info("Was read $l")
                    if (l.isClosed) {
                        break
                    }
                    bytes += l.length
                }
            }
            bytes
        }
    }

    override fun income(socket: MultiplexSocket) {
        scope.launch(context) {
            socket.use { socket ->
                logger.info("Starting service SpeedTest")
                logger.info("Reading cmd...")
                val cmd = socket.input.readByte()
                logger.info("cmd: $cmd")
                logger.info("Reading time...")
                val time = socket.input.readLong().milliseconds
                logger.info("time: $time")
                var now = TimeSource.Monotonic.markNow()
                ByteBuffer(DEFAULT_BUFFER_SIZE).using { buffer ->
                    Random.nextBytes(buffer)
                    when (cmd) {
                        UPLOAD -> {
                            logger.info("Start Upload Test")
                            while (now.elapsedNow() < time) {
                                buffer.clear()
                                socket.input.read(buffer)
                            }
                        }

                        DOWNLOAD -> {
                            logger.info("Start Download Test")
                            while (now.elapsedNow() < time) {
                                buffer.clear()
                                logger.info("Writing...")
                                val wrote = socket.output.write(buffer)
                                logger.info("Wrote $wrote")
                                socket.output.flush()
                            }
                        }
                    }
                }
            }
        }
    }
}
