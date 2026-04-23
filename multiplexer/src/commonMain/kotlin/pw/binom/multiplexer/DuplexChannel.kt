package pw.binom.multiplexer

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.ChannelIterator
import kotlinx.coroutines.channels.ChannelResult
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.selects.SelectClause1
import kotlinx.coroutines.selects.SelectClause2
import kotlinx.io.Buffer

@Suppress("DEPRECATION_ERROR", "INVISIBLE_REFERENCE", "INVISIBLE_MEMBER", "CANNOT_OVERRIDE_INVISIBLE_MEMBER")
interface DuplexChannel : ReceiveChannel<Buffer>, SendChannel<Buffer>, AutoCloseable {
    val income: ReceiveChannel<Buffer>
    val outcome: SendChannel<Buffer>

    override fun close(cause: Throwable?): Boolean = outcome.close(cause)

    override fun invokeOnClose(handler: (cause: Throwable?) -> Unit) = outcome.invokeOnClose(handler)


    override fun offer(element: Buffer): Boolean = outcome.offer(element)

    override suspend fun send(element: Buffer) = outcome.send(element)

    override fun trySend(element: Buffer): ChannelResult<Unit> = outcome.trySend(element)

    @DelicateCoroutinesApi
    override val isClosedForSend: Boolean
        get() = outcome.isClosedForSend
    override val onSend: SelectClause2<Buffer, SendChannel<Buffer>>
        get() = outcome.onSend

    override val onReceiveOrNull: SelectClause1<Buffer?>
        get() = income.onReceiveOrNull
    override val onReceiveCatching: SelectClause1<ChannelResult<Buffer>>
        get() = income.onReceiveCatching
    override val onReceive: SelectClause1<Buffer>
        get() = income.onReceive

    @ExperimentalCoroutinesApi
    override val isEmpty: Boolean
        get() = income.isEmpty

    @DelicateCoroutinesApi
    override val isClosedForReceive: Boolean
        get() = income.isClosedForReceive

    override fun tryReceive(): ChannelResult<Buffer> = income.tryReceive()

    override suspend fun receiveOrNull(): Buffer? = income.receiveOrNull()


    override suspend fun receiveCatching(): ChannelResult<Buffer> = income.receiveCatching()

    override suspend fun receive(): Buffer = income.receive()

    override fun poll(): Buffer? = income.poll()

    override fun iterator(): ChannelIterator<Buffer> = income.iterator()

    override fun cancel(cause: Throwable?): Boolean {
        cancel(cause)
        return true
    }

    override fun cancel() {
        cancel()
    }

    override fun cancel(cause: CancellationException?) = income.cancel(cause)

    override fun close() {
        outcome.close()
    }
}
