package pw.binom.channel

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.io.Buffer
import kotlinx.io.readString
import kotlinx.io.writeString
import org.koin.core.component.KoinComponent
import pw.binom.multiplexer.DuplexChannel
import pw.binom.multiplexer.lebString
import pw.binom.properties.OutcomeService
import pw.binom.utils.connect
import pw.binom.utils.send

class OutcomeWrapperChannel : KoinComponent, ChannelHandler {

    enum class ChannelResult {
        SUCCESS,
        OUTCOME_NOT_FOUND,
        ERROR,
    }

    companion object {
        private const val ID: Byte = 3
        private const val ERROR: Byte = 0
        private const val OUTCOME_NOT_FOUND: Byte = 1
        private const val SUCCESS: Byte = 2
        suspend fun openChannel(channel: DuplexChannel, remoteOutcomeName: String): ChannelResult {
            channel.send {
                writeByte(ID)
                lebString(remoteOutcomeName)
            }
            return when (val code = channel.receive().readByte()) {
                ERROR -> ChannelResult.ERROR
                OUTCOME_NOT_FOUND -> ChannelResult.OUTCOME_NOT_FOUND
                SUCCESS -> ChannelResult.SUCCESS
                else -> error("Unknown result: $code")
            }
        }
    }

    private val logger = KotlinLogging.logger { }
    override val id: Byte
        get() = ID

    private val outcomes by lazy { getKoin().getAll<OutcomeService>().associateBy { it.name } }

    override suspend fun income(channel: DuplexChannel, buffer: Buffer) {
        val outcomeName = buffer.lebString()
        val outcome = outcomes[outcomeName]
        if (outcome == null) {
            channel.send {
                writeByte(OUTCOME_NOT_FOUND)
            }
            return
        }
        val newChannel = try {
            outcome.createChannel()
        } catch (e: Throwable) {
            logger.warn(e) { "Can't create channel on outcome $outcomeName" }
            channel.send {
                writeByte(ERROR)
            }
            return
        }
        channel.send {
            writeByte(SUCCESS)
        }
        connect(
            outcome = channel.outcome,
            income = channel.income,
            a = newChannel.outcome,
            b = newChannel.income,
        )
    }
}
