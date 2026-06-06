package pw.binom.properties

import org.koin.core.component.KoinComponent
import pw.binom.channel.OutcomeWrapperChannel
import pw.binom.multiplexer.DuplexChannel

class OutcomeWrapperService(
    override val name: String,
    val outcome: String,
) : OutcomeService, KoinComponent {

    private val outcomes by lazy { getKoin().getAll<OutcomeService>().associateBy { it.name } }

    override suspend fun createChannel(): DuplexChannel {
        val realOutcome = outcomes[outcome] ?: throw IllegalStateException("Outcome $outcome not found")
        val channel = realOutcome.createChannel()
        val result = OutcomeWrapperChannel.openChannel(
            channel = channel,
            remoteOutcomeName = outcome,
        )
        when (result) {
            OutcomeWrapperChannel.ChannelResult.SUCCESS -> return channel
            OutcomeWrapperChannel.ChannelResult.OUTCOME_NOT_FOUND -> throw IllegalStateException("Outcome $outcome not found")
            OutcomeWrapperChannel.ChannelResult.ERROR -> throw IllegalStateException("Can't open channel on remote outcome $outcome")
        }
    }
}
