package pw.binom.multiplexer

import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.io.Buffer

object MultiplexerProtocol {
    private const val DATA: Byte = 1
    private const val CHANNEL_CLOSE: Byte = 2
    private const val REQUEST_NEW_CHANNEL: Byte = 3
    private const val ACCEPT_NEW_CHANNEL: Byte = 4

    /**
     * Посылает запрос на закрытие канала [channelId]
     */
    suspend fun sendCloseChannel(
        channelId: Int,
        physical: SendChannel<Buffer>,
    ) {
        println("MultiplexerProtocol:: SEND CLOSING CHANNEL $channelId")
        val resultBuffer = Buffer()
        resultBuffer.writeByte(CHANNEL_CLOSE)
        resultBuffer.lebInt(channelId)
        physical.send(resultBuffer)
    }

    /**
     * Посылает команду открытия нового канала [channelId] в [physical]
     */
    suspend fun sendRequestNewChannel(
        channelId: Int,
        physical: SendChannel<Buffer>,
    ) {
        println("MultiplexerProtocol:: SEND REQUEST TO OPEN CHANNEL $channelId")
        val resultBuffer = Buffer()
        resultBuffer.writeByte(REQUEST_NEW_CHANNEL)
        resultBuffer.lebInt(channelId)
        physical.send(resultBuffer)
    }

    /**
     * Посылает ответ на запрос открытия нового канала
     * @param channelId
     * @param physical
     * @param accept `true` разрешить открытие канала. `false` запрет открытия нового канала
     */
    suspend fun sendResponseNewChannel(
        channelId: Int,
        physical: SendChannel<Buffer>,
    ) {
        println("MultiplexerProtocol:: SEND RESPONSE TO OPEN CHANNEL $channelId")
        val resultBuffer = Buffer()
        resultBuffer.writeByte(ACCEPT_NEW_CHANNEL)
        resultBuffer.lebInt(channelId)
        physical.send(resultBuffer)
    }

    /**
     * Копирует данные из [logical] в [physical] снабжая командой и номером канала.
     * Нужен чтобы логические данные правильно затолкать в физический канал
     */
    suspend fun coppingLogicalToPhysical(
        channelId: Int,
        logical: ReceiveChannel<Buffer>,
        physical: SendChannel<Buffer>,
    ) {
        logical.consumeEach { sourceBuffer ->
            println("MultiplexerProtocol:: SENDING ${sourceBuffer.size} bytes to $channelId")
            physical.send(
                wrapLogicalToPhysical(
                    channelId = channelId,
                    data = sourceBuffer,
                )
            )
        }
    }

    fun wrapLogicalToPhysical(
        channelId: Int,
        data: Buffer,
    ): Buffer {
        val resultBuffer = Buffer()
        resultBuffer.writeByte(DATA)
        resultBuffer.lebInt(channelId)
        data.readFully(resultBuffer, data.size)
        return resultBuffer
    }

    /**
     * Читает [physical]. В зависимости от команды вызывает соответствующий handler.
     * Нужен чтобы вычитать данные из физического канала
     */
    suspend fun reading(
        physical: ReceiveChannel<Buffer>,
        handlerOnData: HandlerOnData,
        channelClosed: HandlerOnChannel,
        requestChannel: HandlerOnChannel,
        newChannelAccepted: HandlerOnChannel,
    ) {
        try {
            physical.consumeEach { buffer ->
                val cmd = buffer.readByte()
                when (cmd) {
                    DATA -> {
                        val channelId = buffer.lebInt()
                        println("MultiplexerProtocol:: INCOME DATA on channel $channelId with ${buffer.size} bytes")
                        handlerOnData.onData(channelId = channelId, data = buffer)
                    }

                    CHANNEL_CLOSE -> {
                        val channelId = buffer.lebInt()
                        println("MultiplexerProtocol:: INCOME CLOSING channel $channelId")
                        channelClosed.onEvent(channelId)
                    }

                    REQUEST_NEW_CHANNEL -> {
                        val channelId = buffer.lebInt()
                        println("MultiplexerProtocol:: INCOME REQUEST_NEW_CHANNEL $channelId")
                        requestChannel.onEvent(channelId)
                    }

                    ACCEPT_NEW_CHANNEL -> {
                        val channelId = buffer.lebInt()
                        println("MultiplexerProtocol:: INCOME ACCEPT_NEW_CHANNEL $channelId")
                        newChannelAccepted.onEvent(channelId)
                    }
                }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        } finally {
            println("MultiplexerProtocol:: reading finished!")
        }
    }

    fun interface HandlerOnChannel {
        suspend fun onEvent(channel: Int)
    }

    fun interface HandlerOnData {
        suspend fun onData(channelId: Int, data: Buffer)
    }
}
