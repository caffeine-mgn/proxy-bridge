@file:Suppress("ktlint:standard:no-wildcard-imports")

package pw.binom.proxy

import kotlinx.coroutines.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import pw.binom.*
import pw.binom.atomic.AtomicInt
import pw.binom.atomic.AtomicLong
import pw.binom.concurrency.SpinLock
import pw.binom.concurrency.synchronize
import pw.binom.io.AsyncCloseable
import pw.binom.io.ByteBuffer
import pw.binom.io.ClosedException
import pw.binom.io.http.websocket.MessageType
import pw.binom.io.http.websocket.WebSocketClosedException
import pw.binom.io.http.websocket.WebSocketConnection
import pw.binom.io.socket.UnknownHostException
import pw.binom.io.use
import pw.binom.logger.Logger
import pw.binom.logger.info
import pw.binom.network.NetworkManager
import pw.binom.proxy.ControlClient.BaseHandler
import pw.binom.proxy.exceptions.ChannelExistException
import pw.binom.proxy.exceptions.ChannelNotFoundException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration

@OptIn(ExperimentalSerializationApi::class)
class ControlClient(
    val connection: WebSocketConnection,
    networkManager: NetworkManager,
    private val pingInterval: Duration,
    private val logger: Logger,
) : AsyncCloseable {
    @Suppress("FUN_INTERFACE_WITH_SUSPEND_FUNCTION")
    fun interface BaseHandler {
        companion object {
            private const val NOT_SUPPORTED_MSG = "Not supported"
            val NOT_SUPPORTED =
                BaseHandler {
                    ResponseDto.unknownError(NOT_SUPPORTED_MSG)
                }

            fun composite(
                connect: suspend (host: String, port: Int, channelId: Int) -> Unit = { _, _, _ ->
                    throw RuntimeException(
                        NOT_SUPPORTED_MSG
                    )
                },
                emmitChannel: suspend (channelId: Int) -> Unit = { throw RuntimeException(NOT_SUPPORTED_MSG) },
                destroyChannel: suspend (channelId: Int) -> Unit = { throw RuntimeException(NOT_SUPPORTED_MSG) },
                setIdle: suspend (channelId: Int) -> Unit = { throw RuntimeException(NOT_SUPPORTED_MSG) },
                setProxy: suspend (host: String, port: Int, channelId: Int) -> Unit = { _, _, _ ->
                    throw RuntimeException(
                        NOT_SUPPORTED_MSG
                    )
                },
            ) = BaseHandler {
                when {
                    it.connect != null -> {
                        try {
                            connect(it.connect.host, it.connect.port, it.connect.channelId)
                            ResponseDto.ok()
                        } catch (e: UnknownHostException) {
                            ResponseDto.unknownHost()
                        }
                    }

                    it.emmitChannel != null -> {
                        try {
                            emmitChannel(it.emmitChannel.channelId)
                            ResponseDto.ok()
                        } catch (e: ChannelExistException) {
                            ResponseDto.channelExist()
                        }
                    }

                    it.destroyChannel != null -> {
                        try {
                            destroyChannel(it.destroyChannel.channelId)
                            ResponseDto.ok()
                        } catch (e: ChannelNotFoundException) {
                            ResponseDto.channelNotFound()
                        }
                    }

                    it.setProxy != null -> {
                        try {
                            setProxy(it.setProxy.host, it.setProxy.port, it.setProxy.channelId)
                            ResponseDto.ok()
                        } catch (e: UnknownHostException) {
                            ResponseDto.unknownHost()
                        } catch (e: ChannelNotFoundException) {
                            ResponseDto.channelNotFound()
                        }
                    }

                    it.setIdle != null -> {
                        try {
                            setIdle(it.setIdle.channelId)
                            ResponseDto.ok()
                        } catch (e: ChannelNotFoundException) {
                            ResponseDto.channelNotFound()
                        }
                    }

                    else -> ResponseDto.unknownError("Unknown request. Full object: $it")
                }
            }
        }

        suspend fun income(request: RequestDto): ResponseDto
    }

    companion object {
        private val proto = ProtoBuf
        private const val MAX_PING_BYTES = Long.SIZE_BYTES
    }

    private val pingWaitersLock = SpinLock()
    private val pingWaiters = HashMap<Long, CancellableContinuation<Unit>>()
    private var pingCounter = AtomicLong(0)
    private val waiters = HashMap<Int, CancellableContinuation<ResponseDto>>()
    private val waitersLock = SpinLock()
    private val requestIterator = AtomicInt(0)

    private var pingJob: Job? = null

    private suspend fun startPing()  {
        if (pingInterval > Duration.ZERO) {
            GlobalScope.launch(currentCoroutineContext() + CoroutineName("${logger.pkg}-PING")) {
                supervisorScope {
                    while (isActive) {
                        try {
                            delay(pingInterval)
                            ping()
                        } catch (e: ClosedException) {
                            break
                        } catch (e: CancellationException) {
                            println("PING JOB Cancelled!!!!!!")
                            break
                        }
                    }
                }
            }
        } else {
            null
        }
    }

    suspend fun ping() {
        val pingId = pingCounter.addAndGet(1)
        connection.write(MessageType.PING).use { out ->
            ByteBuffer(MAX_PING_BYTES).use { buffer ->
                buffer.writeLong(pingId)
                buffer.flip()
                out.writeFully(buffer)
            }
        }
        suspendCancellableCoroutine { con ->
            con.invokeOnCancellation {
                pingWaitersLock.synchronize {
                    pingWaiters.remove(pingId)
                }
            }
            pingWaitersLock.synchronize {
                pingWaiters[pingId] = con
            }
        }
    }

    suspend fun connect(
        host: String,
        port: Int,
        channelId: Int,
    ) {
        val resp =
            sendRequest(RequestDto(connect = RequestDto.Connect(host = host, port = port, channelId = channelId)))
        if (resp.isOk) {
            return
        }
        if (resp.unknownHost != null) {
            throw UnknownHostException(host)
        }
        elseCheck(resp)
    }

    private fun elseCheck(resp: ResponseDto) {
        if (resp.unknownError != null) {
            throw RuntimeException(resp.unknownError.message)
        }
        throw RuntimeException("Unknown response: $resp")
    }

    suspend fun emmitChannel(
        channelId: Int,
        ignoreExist: Boolean,
    ) {
        val resp = sendRequest(RequestDto(emmitChannel = RequestDto.EmmitChannel(channelId)))
        if (resp.isOk) {
            return
        }
        if (resp.channelExist != null) {
            if (ignoreExist) {
                return
            } else {
                throw ChannelExistException(channelId)
            }
        }
        elseCheck(resp)
    }

    suspend fun destroyChannel(
        channelId: Int,
        ignoreNotExist: Boolean = false,
    ) {
        val resp = sendRequest(RequestDto(destroyChannel = RequestDto.DestroyChannel(channelId)))
        if (resp.isOk) {
            return
        }
        if (resp.channelNotFound != null) {
            if (ignoreNotExist) {
                return
            } else {
                throw ChannelNotFoundException(channelId)
            }
        }
        elseCheck(resp)
    }

    private suspend fun sendRequest(dto: RequestDto): ResponseDto {
        val id = requestIterator.addAndGet(1)
        val array = dto.toByteArray()
        logger.info("Send request $dto (${array.size} bytes)   ${dto.toByteArray().size}")
        logger.info(
            "STUB: ${
                RequestDto(
                    connect =
                        RequestDto.Connect(
                            host = "123",
                            port = 11,
                            channelId = 22
                        )
                ).toByteArray().size
            }"
        )
        sendBytes(
            code = Codes.REQUEST,
            id = id,
            bytes = array
        )
        return wait(id)
    }

    private suspend fun sendBytes(
        code: Byte,
        id: Int,
        bytes: ByteArray,
    ) {
        ByteBuffer(Byte.SIZE_BYTES + Int.SIZE_BYTES + Int.SIZE_BYTES + bytes.size).use { buffer ->
            buffer.put(code)
            buffer.writeInt(id)
            buffer.writeInt(bytes.size)
            buffer.write(bytes)
            buffer.clear()
            connection.write(MessageType.BINARY).use { msg ->
                msg.write(buffer)
            }
        }
    }

    suspend fun runClient(handler: BaseHandler) {
        startPing()
        try {
            ByteBuffer(1024).use { buffer ->
                while (true) {
                    try {
                        logger.info("ControlClient::runClient Waiting new message...")
                        connection.read().use MSG@{ msg ->
                            logger.info("ControlClient::runClient Message got! msg.type=${msg.type}")
                            when (msg.type) {
                                MessageType.CLOSE -> return
                                MessageType.PING -> {
                                    buffer.clear()
                                    buffer.reset(position = 0, length = Long.SIZE_BYTES)
                                    msg.readFully(buffer)
                                    buffer.flip()
                                    connection.write(MessageType.PONG).use { out ->
                                        out.writeFully(buffer)
                                    }
                                    return@MSG
                                }

                                MessageType.PONG -> {
                                    buffer.reset(position = 0, length = Long.SIZE_BYTES)
                                    msg.readFully(buffer)
                                    buffer.flip()
                                    if (buffer.remaining != MAX_PING_BYTES) {
                                        val pingId = buffer.readLong()
                                        pingWaitersLock.synchronize {
                                            pingWaiters.remove(pingId)?.resume(Unit)
                                        }
                                    }
                                    return@MSG
                                }

                                MessageType.BINARY, MessageType.TEXT -> {
                                    val byte = msg.readByte(buffer)
                                    when (byte) {
                                        Codes.REQUEST -> {
                                            val id = msg.readInt(buffer)
                                            val size = msg.readInt(buffer)
                                            val bytes = msg.readByteArray(size = size, buffer = buffer)
                                            val req =
                                                try {
                                                    RequestDto.fromByteArray(bytes)
                                                } catch (e: Throwable) {
                                                    logger.info(text = "Can't decode income request", exception = e)
                                                    sendBytes(
                                                        code = Codes.RESPONSE,
                                                        id = id,
                                                        bytes =
                                                            ResponseDto.unknownError("Can't decode income request")
                                                                .toByteArray()
                                                    )
                                                    return@MSG
                                                }
                                            logger.info("Income request $req (${bytes.size} bytes). size=$size")
                                            val resp =
                                                try {
                                                    handler.income(req)
                                                } catch (e: Throwable) {
                                                    logger.info(
                                                        text = "Unknown exception during request processing",
                                                        exception = e
                                                    )
                                                    ResponseDto.unknownError(e.toString())
                                                }
                                            sendBytes(
                                                code = Codes.RESPONSE,
                                                id = id,
                                                bytes = resp.toByteArray()
                                            )
                                        }

                                        Codes.RESPONSE -> {
                                            val id = msg.readInt(buffer)
                                            val size = msg.readInt(buffer)
                                            val bytes = msg.readByteArray(size = size, buffer = buffer)
                                            val con =
                                                waitersLock.synchronize {
                                                    waiters.remove(id)
                                                }
                                            if (con == null) {
                                                logger.info("Can't find request water for request $id")
                                                return@MSG
                                            }
                                            val resp =
                                                try {
                                                    ResponseDto.fromByteArray(bytes)
                                                } catch (e: Throwable) {
                                                    con.resumeWithException(
                                                        RuntimeException(
                                                            "Can't decode income response",
                                                            e
                                                        )
                                                    )
                                                    return@MSG
                                                }
                                            con.resume(resp)
                                        }

                                        else -> TODO("Unknown cmd $byte and ${msg.readByte(buffer).toUByte()}")
                                    }
                                }

                                MessageType.CONTINUATION -> {
                                    return
                                }
                            }
                        }
                    } catch (e: CancellationException) {
                        withContext(NonCancellable) {
                            logger.info(text = "ControlClient::runClient STOP PROCESSING #1")
                            asyncCloseAnyway()
                        }
                        return
                    } catch (e: WebSocketClosedException) {
                        withContext(NonCancellable) {
                            asyncCloseAnyway()
                        }
                        return
                    } catch (e: Throwable) {
                        logger.info(text = "ControlClient::runClient STOP PROCESSING #2", exception = e)
                        throw e
                    }
                }
            }
        } catch (e: Throwable) {
            println("EROROROROR!!!!!!!!")
            e.printStackTrace()
            throw e
        } finally {
            logger.info("ControlClient::runClient STOP PROCESSING")
        }
    }

    private suspend fun wait(id: Int): ResponseDto =
        suspendCancellableCoroutine {
            it.invokeOnCancellation {
                waitersLock.synchronize {
                    waiters.remove(id)
                }
            }
            waitersLock.synchronize {
                waiters[id] = it
            }
        }

    override suspend fun asyncClose() {
        logger.info("ControlClient::asyncClose Closing web socket connection")
        connection.asyncCloseAnyway()
        logger.info("ControlClient::asyncClose web socket connection closed")
        waitersLock.synchronize {
            ArrayList(waiters.values)
        }.forEach {
            it.resumeWithException(ClosedException("1"))
        }
        pingWaitersLock.synchronize {
            ArrayList(pingWaiters.values)
        }.forEach {
            it.resumeWithException(ClosedException("2"))
        }
        val pingJob = pingJob
        if (pingJob != null) {
            if (pingJob.isActive && !pingJob.isCompleted && !pingJob.isCancelled) {
                try {
                    pingJob.cancelAndJoin()
                    logger.info("PING JOB CANCELLED")
                } catch (e: Throwable) {
                    println("ERROR ON CLOSE CONTROL CLIENT: $e")
                }
            }
        }
    }
}
