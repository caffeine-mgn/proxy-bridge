package pw.binom.properties

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import pw.binom.ConnectionAcceptor
import pw.binom.channel.ChannelSelector
import pw.binom.com.SerialConnectionAcceptor
import pw.binom.multiplexer.DuplexChannel
import pw.binom.multiplexer.Multiplexer
import pw.binom.multiplexer.MultiplexerImpl
import kotlin.use

class SerialIncomeService(
    private val serialName: String,
    private val baudRate: Int,
    private val channelSelector: ChannelSelector,
    val idOdd: Boolean,
    override val name: String,
) : IncomeService, Multiplexer, ConnectionAcceptor, OutcomeService {
    private val acc = SerialConnectionAcceptor(
        serialName = serialName,
        baudRate = baudRate,
    )

    override suspend fun connection(): DuplexChannel = acc.connection()

    private var currentMultiplexer: Multiplexer? = null

    private val job = CoroutineScope(Dispatchers.IO + CoroutineName("Serial Main")).launch {
        while (isActive) {
            acc.connection().use { rawChannel ->
                MultiplexerImpl(
                    channel = rawChannel,
                    idOdd = idOdd,
                    ioCoroutineScope = CoroutineScope(Dispatchers.IO)
                ).use { multiplexer ->
                    try {
                        currentMultiplexer = multiplexer
                        while (isActive) {
                            val con = multiplexer.accept()
                            CoroutineScope(Dispatchers.IO)
                                .launch(CurrentMultiplexer(multiplexer)+CoroutineName("Serial Main Processing")) {
                                    con.use { channel ->
                                        channelSelector.processing(
                                            connection = channel,
                                        )
                                    }
                                }
                        }
                    } finally {
                        currentMultiplexer = null
                    }
                }
            }
        }
    }

    override fun close() {
        job.cancel()
        acc.close()
    }

    private fun getMultiplexer() =
        currentMultiplexer ?: throw IllegalStateException("Multiplexer not ready")

    override suspend fun accept(): DuplexChannel =
        getMultiplexer().accept()

    override suspend fun createChannel(): DuplexChannel =
        getMultiplexer().createChannel()

}
