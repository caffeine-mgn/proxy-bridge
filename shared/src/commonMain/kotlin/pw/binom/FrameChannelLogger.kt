package pw.binom
/*
import pw.binom.io.ByteBuffer
import pw.binom.io.holdState
import pw.binom.logger.Logger
import pw.binom.logger.info
import pw.binom.logger.infoSync
import kotlin.math.log

@OptIn(ExperimentalStdlibApi::class)
class FrameChannelLogger(
    val logger: Logger,
    val other: FrameChannel,
) : FrameChannel {
    override val bufferSize: PackageSize
        get() = other.bufferSize

    init {
        logger.infoSync("Started")
    }

    override suspend fun <T> sendFrame(func: (ByteBuffer) -> T): FrameResult<T> {
        var bytes: ByteArray? = null
        val l = other.sendFrame {
            val result = func(it)
            it.holdState {
                bytes = it.toByteArray()
                logger.infoSync("Success sent ${bytes!!.toHexString()}")
            }
            result
        }
        if (l.isClosed) {
            if (bytes == null) {
                logger.info("Can't send data")
            } else {
                logger.info("Can't send ${bytes.toHexString()}")
            }
        }
        return l
    }

    override suspend fun <T> readFrame(func: (FrameInput) -> T): FrameResult<T> {
        var bytes: ByteArray? = null
        val l = other.readFrame {
            val pos = it.position
            val len = it.remaining
            val result = func(it)
            it.holdState {
                it.reset(position = pos, length = len)
                bytes = it.toByteArray()
                logger.infoSync("Success read ${bytes.toHexString()}")
            }
            result
        }
        if (l.isClosed) {
            if (bytes == null) {
                logger.info("Can't read data")
            } else {
                logger.info("Can't read ${bytes.toHexString()}")
            }
        }
        return l
    }

    override suspend fun asyncClose() {
        logger.info("Closing")
        other.asyncClose()
    }
}

fun FrameChannel.withLogger(logger: Logger) = FrameChannelLogger(
    logger = logger,
    other = this,
)

fun FrameChannel.withLogger(name: String) =
    withLogger(
        logger = Logger.getLogger(name)
    )
*/
