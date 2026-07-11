package pw.binom.multiplexer

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.io.Buffer

object MultiplexerProtocol {
    private const val DATA: Byte = 1
    private const val CHANNEL_CLOSE: Byte = 2
    private const val REQUEST_NEW_CHANNEL: Byte = 3
    private const val ACCEPT_NEW_CHANNEL: Byte = 4
    private val logger = KotlinLogging.logger { }

    private suspend fun sendCommand(cmd: Byte, channelId: Int, physical: SendChannel<Buffer>) {
        val resultBuffer = Buffer()
        resultBuffer.writeByte(cmd)
        resultBuffer.lebInt(channelId)
        physical.send(resultBuffer)
    }

    /**
     * Посылает запрос на закрытие канала [channelId]
     */
    suspend fun sendCloseChannel(
        channelId: Int,
        physical: SendChannel<Buffer>,
    ) {
        logger.info { "SEND CLOSING CHANNEL $channelId" }
        sendCommand(CHANNEL_CLOSE, channelId, physical)
    }

    /**
     * Посылает команду открытия нового канала [channelId] в [physical]
     */
    suspend fun sendRequestNewChannel(
        channelId: Int,
        physical: SendChannel<Buffer>,
    ) {
        logger.info { "SEND REQUEST TO OPEN CHANNEL $channelId" }
        sendCommand(REQUEST_NEW_CHANNEL, channelId, physical)
    }

    /**
     * Посылает ответ на запрос открытия нового канала
     * @param channelId
     * @param physical
     */
    suspend fun sendResponseNewChannel(
        channelId: Int,
        physical: SendChannel<Buffer>,
    ) {
        logger.info { "SEND RESPONSE TO OPEN CHANNEL $channelId" }
        sendCommand(ACCEPT_NEW_CHANNEL, channelId, physical)
    }

    /**
     * Копирует данные из [logical] в [physical] снабжая командой и номером канала.
     * Нужен чтобы логические данные правильно затолкать в физический канал
     */
    suspend fun copyingLogicalToPhysical(
        channelId: Int,
        logical: ReceiveChannel<Buffer>,
        physical: SendChannel<Buffer>,
    ) {
        logical.consumeEach { sourceBuffer ->
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
        resultBuffer.transferFrom(data)
        return resultBuffer
    }

    private suspend inline fun handleChannelEvent(
        buffer: Buffer,
        logPrefix: String,
        handler: suspend (channelId: Int) -> Unit,
    ) {
        try {
            val channelId = buffer.lebInt()
            logger.info { "$logPrefix $channelId" }
            handler(channelId)
        } catch (e: Throwable) {
            logger.error(e) { "Error on $logPrefix" }
        }
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
            while (true) {
                val buffer = physical.receiveCatching().getOrNull() ?: break
                val cmd = buffer.readByte()
                when (cmd) {
                    DATA -> {
                        try {
                            val channelId = buffer.lebInt()
                            handlerOnData.onData(channelId = channelId, data = buffer)
                        } catch (e: Throwable) {
                            logger.error(e) { "Error on reading data" }
                        }
                    }

                    CHANNEL_CLOSE ->
                        handleChannelEvent(buffer, "INCOME CLOSING channel") {
                            channelClosed.onEvent(it)
                        }

                    REQUEST_NEW_CHANNEL ->
                        handleChannelEvent(buffer, "INCOME REQUEST_NEW_CHANNEL") {
                            requestChannel.onEvent(it)
                        }

                    ACCEPT_NEW_CHANNEL ->
                        handleChannelEvent(buffer, "INCOME ACCEPT_NEW_CHANNEL") {
                            newChannelAccepted.onEvent(it)
                        }

                    else -> {
                        logger.warn { "Unknown protocol command: $cmd" }
                    }
                }
            }
        } catch (e: CancellationException) {
            // Normal shutdown, not an error
        } catch (e: Throwable) {
            logger.error(e) { "READ FINISHED WITH ERROR!!!" }
        } finally {
            logger.info { "reading finished!" }
        }
    }

    fun interface HandlerOnChannel {
        suspend fun onEvent(channel: Int)
    }

    fun interface HandlerOnData {
        suspend fun onData(channelId: Int, data: Buffer)
    }
}
