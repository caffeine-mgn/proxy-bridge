package pw.binom

import kotlinx.coroutines.*
import pw.binom.concurrency.SpinLock
import pw.binom.concurrency.synchronize
import pw.binom.io.*
import pw.binom.logger.Logger
import pw.binom.logger.info
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume

object StreamBridge {
    @PublishedApi
    internal val logger by Logger.ofThisOrGlobal

    class ChannelBreak(msg: String? = null) : CancellationException(msg)

    enum class ReasonForStopping {
        LEFT,
        RIGHT,
        ;

        operator fun not() = when (this) {
            LEFT -> RIGHT
            RIGHT -> LEFT
        }
    }

    @PublishedApi
    internal suspend inline fun copy(
        left: AsyncInput,
        right: AsyncOutput,
        bufferSize: Int = DEFAULT_BUFFER_SIZE,
        sizeProvider: ((ULong) -> Unit) = {},
        logger: Logger = StreamBridge.logger,
    ) = byteBuffer(bufferSize).use { buffer ->
        copy(
            left = left,
            right = right,
            buffer = buffer,
            sizeProvider = sizeProvider,
            logger = logger,
        )
    }

    @PublishedApi
    internal suspend inline fun copy(
        left: AsyncInput,
        right: AsyncOutput,
        buffer: ByteBuffer,
        sizeProvider: ((ULong) -> Unit) = {},
        transferProvider: ((Int) -> Unit) = {},
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
//                logger.info("left finish by exception: $e")
                return ReasonForStopping.LEFT
            }
            if (r.isClosed) {
                sizeProvider(length)
//                logger.info("left finish by close")
                return ReasonForStopping.LEFT
            }
            if (r.isEof) {
                sizeProvider(length)
//                logger.info("left finish by eof")
                return ReasonForStopping.LEFT
            }
//            logger.info("left read ok ${left.hashCode()}")
            buffer.flip()
//            logger.info("READ ${buffer.remaining} ${r} ${buffer.toByteArray().toHexString()}")
            try {
//                delay(0.1.seconds)
                val wroteSize = right.writeFully(buffer)
                right.flush()
                length += wroteSize.toULong()
                transferProvider(wroteSize)
//                logger.info("send $wroteSize")
//                logger.info("right write ok ${right.hashCode()}")
            } catch (e: Throwable) {
                sizeProvider(length)
//                logger.info("right finish by exception: $e")
                return ReasonForStopping.RIGHT
            }
        }
//        println("Cancelling copy $left->$right")
        TODO("Cancelled coping $left->$right")
    }

    suspend fun sync(
        left: AsyncChannel,
        right: AsyncChannel,
        bufferSize: Int,
        leftProvider: ((Deferred<ReasonForStopping>) -> Unit)? = null,
        rightProvider: ((Deferred<ReasonForStopping>) -> Unit)? = null,
        leftToRightSizeProvider: ((ULong) -> Unit)? = null,
        rightToLeftSizeProvider: ((ULong) -> Unit)? = null,
        syncStarted: (() -> Unit)? = null,
        exceptionHappened: (() -> Unit)? = null,
        logger: Logger = StreamBridge.logger,
        transferToLeft: ((Int) -> Unit)? = null,
        transferToRight: ((Int) -> Unit)? = null,
    ) = coroutineScope {
        val bufferLeftToRight = byteBuffer(bufferSize)
        val bufferRightToLeft = byteBuffer(bufferSize)
        try {
            var rightToLeft: Deferred<ReasonForStopping>? = null
            var water: CancellableContinuation<ReasonForStopping>? = null
            val lock = SpinLock()
            try {
                val leftToRight = async {
                    val r = copy(
                        left = left,
                        right = right,
                        buffer = bufferLeftToRight,
                        sizeProvider = { size ->
                            leftToRightSizeProvider?.invoke(size)
                        },
                        logger = Logger.getLogger("${logger.pkg} $left->$right"),
                        transferProvider = {
                            transferToRight?.invoke(it)
                        }
                    )
                    rightToLeft?.cancel(ChannelBreak("Finish copy $left->$right"))
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
                        transferProvider = {
                            transferToLeft?.invoke(it)
                        }
                    )
                    leftToRight.cancel(ChannelBreak("Finish copy $right->$left"))
                    lock.synchronize {
                        val w = water
                        water = null
                        w
                    }?.resume(!r)
                    r
                }
                rightProvider?.invoke(rightToLeft)
                syncStarted?.invoke()
            } catch (e: Throwable) {
                exceptionHappened?.invoke()
                throw e
            }
            suspendCancellableCoroutine {
                water = it
            }
        } finally {
            bufferLeftToRight.close()
            bufferRightToLeft.close()
        }
    }
}
