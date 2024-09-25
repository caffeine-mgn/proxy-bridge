package pw.binom.proxy

import kotlinx.coroutines.*
import pw.binom.*
import pw.binom.concurrency.SpinLock
import pw.binom.concurrency.synchronize
import pw.binom.io.*
import pw.binom.logger.Logger
import pw.binom.logger.info
import pw.binom.logger.infoSync
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.seconds

object StreamBridge {
    val logger by Logger.ofThisOrGlobal

    enum class ReasonForStopping {
        LEFT,
        RIGHT,
        ;

        operator fun not() = when (this) {
            LEFT -> RIGHT
            RIGHT -> LEFT
        }
    }

    suspend inline fun copy(
        left: AsyncInput,
        right: AsyncOutput,
        bufferSize: Int = DEFAULT_BUFFER_SIZE,
        sizeProvider: ((ULong) -> Unit) = {},
        logger: Logger = StreamBridge.logger,
    ) = ByteBuffer(bufferSize).use { buffer ->
        copy(
            left = left,
            right = right,
            buffer = buffer,
            sizeProvider = sizeProvider,
            logger = logger,
        )
    }

    @OptIn(ExperimentalStdlibApi::class)
    suspend inline fun copy(
        left: AsyncInput,
        right: AsyncOutput,
        buffer: ByteBuffer,
        sizeProvider: ((ULong) -> Unit) = {},
        logger: Logger = StreamBridge.logger,
    ): ReasonForStopping {
//        logger.info("$left->$right")
        var length = 0uL
        while (coroutineContext.isActive) {
            buffer.clear()
            val r = try {
                left.read(buffer)
            } catch (e: Throwable) {
                sizeProvider(length)
                logger.info("left finish by exception: $e")
                return ReasonForStopping.LEFT
            }
            if (r.isClosed) {
                sizeProvider(length)
                logger.info("left finish by close")
                return ReasonForStopping.LEFT
            }
            if (r.isEof) {
                sizeProvider(length)
                logger.info("left finish by eof")
                return ReasonForStopping.LEFT
            }
//            logger.info("left read ok ${left.hashCode()}")
            buffer.flip()
//            logger.info("READ ${buffer.remaining} ${r} ${buffer.toByteArray().toHexString()}")
            try {
//                delay(0.1.seconds)
                val wroteSize = right.writeFully(buffer)
                length += wroteSize.toULong()
                logger.info("send $wroteSize")
//                logger.info("right write ok ${right.hashCode()}")
            } catch (e: Throwable) {
                sizeProvider(length)
                logger.info("right finish by exception: $e")
                return ReasonForStopping.RIGHT
            }
        }
        TODO()
    }

    suspend fun copy(
        left: AsyncChannel,
        right: AsyncChannel,
        bufferSize: Int,
        leftProvider: ((Deferred<ReasonForStopping>) -> Unit)? = null,
        rightProvider: ((Deferred<ReasonForStopping>) -> Unit)? = null,
        leftToRightSizeProvider: ((ULong) -> Unit)? = null,
        rightToLeftSizeProvider: ((ULong) -> Unit)? = null,
        logger: Logger = StreamBridge.logger,
    ) = coroutineScope {
        val bufferLeftToRight = ByteBuffer(bufferSize)
        val bufferRightToLeft = ByteBuffer(bufferSize)
        try {
            var rightToLeft: Deferred<ReasonForStopping>? = null
            var water: CancellableContinuation<ReasonForStopping>? = null
            val lock = SpinLock()
            val leftToRight = async {
                val r = copy(
                    left = left,
                    right = right,
                    buffer = bufferLeftToRight,
                    sizeProvider = { size ->
                        leftToRightSizeProvider?.invoke(size)
                    },
                    logger = Logger.getLogger("${logger.pkg} $left->$right"),
                )
                rightToLeft?.cancel()
                lock.synchronize {
                    val w = water
                    water = null
                    w
                }?.resume(r)
                r
            }
            leftProvider?.invoke(leftToRight)
            rightToLeft = async {
                val r = copy(
                    left = right,
                    right = left,
                    buffer = bufferRightToLeft,
                    sizeProvider = { size ->
                        rightToLeftSizeProvider?.invoke(size)
                    },
                    logger = Logger.getLogger("${logger.pkg} $right->$left"),
                )
                leftToRight.cancel()
                lock.synchronize {
                    val w = water
                    water = null
                    w
                }?.resume(!r)
                r
            }
            rightProvider?.invoke(rightToLeft)
            suspendCancellableCoroutine<ReasonForStopping> {
                water = it
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            throw e
        } finally {
            bufferLeftToRight.close()
            bufferRightToLeft.close()
        }
    }
}
