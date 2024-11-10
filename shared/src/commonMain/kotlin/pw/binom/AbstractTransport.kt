package pw.binom

/*
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.protobuf.ProtoBuf
import pw.binom.communicate.CommunicateCommon
import pw.binom.communicate.CommunicateRepository
import pw.binom.io.AsyncChannel
import pw.binom.io.AsyncCloseable
import pw.binom.io.AsyncOutput
import pw.binom.io.ByteBuffer
import pw.binom.proxy.flushPackage
import pw.binom.proxy.writePackage
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class AbstractTransport(
    val channel: AsyncChannel,
    val communicateRepository: CommunicateRepository,
) : AsyncCloseable, FrameWriter {
    private val bufferRead = ByteBuffer(4)
    private val bufferWrite = ByteBuffer(4)
    private var current: CommunicateCommon? = null

    companion object {
        val CLIENT_CHANNEL_OPEN: Byte = 0
        val CLIENT_OPEN_RESPONSE: Byte = 1
        val CHANNEL_FRAME: Byte = 2


        val OK: Byte = 0
        val CHANNEL_BUSY: Byte = 1
        val UNKNOWN_CHANNEL_TYPE: Byte = 2
        val FAIL_OPEN_SERVER: Byte = 3
    }

    suspend fun read() {
        val cmd = channel.readByte(bufferRead)
        when (cmd) {
            CLIENT_CHANNEL_OPEN -> {
                bufferRead.reset(0, Int.SIZE_BYTES + Short.SIZE_BYTES + Int.SIZE_BYTES)
                channel.readFully(bufferRead)
                bufferRead.flip()
                val requestId = bufferRead.readInt()
                val channelType = bufferRead.readShort()
                val dataSize = bufferRead.readInt()

                if (current != null || channelConnection) {
                    channel.skip(dataSize.toLong(), bufferRead)
                    bufferRead.clear()
                    channel.writePackage(bufferRead, CLIENT_OPEN_RESPONSE)
                    channel.writePackage(bufferRead, requestId)
                    channel.writePackage(bufferRead, CHANNEL_BUSY)
                    channel.flushPackage(bufferRead)
                    return
                }

                val pair = communicateRepository.getPair(channelType)
                if (pair == null) {
                    bufferRead.clear()
                    channel.skip(dataSize.toLong(), bufferRead)
                    channel.writeByte(CLIENT_OPEN_RESPONSE, bufferRead)
                    channel.writeInt(requestId, bufferRead)
                    channel.writeByte(UNKNOWN_CHANNEL_TYPE, bufferRead)
                    channel.flushPackage(bufferRead)
                    return
                }
                val dataBytes = channel.readByteArray(dataSize, bufferRead)
                val data = ProtoBuf.decodeFromByteArray(pair.dataSerializer, dataBytes)
                val server = try {
                    pair.createServer(data)
                } catch (e: Throwable) {
                    bufferRead.clear()
                    channel.writePackage(bufferRead, CLIENT_OPEN_RESPONSE)
                    channel.writePackage(bufferRead, requestId)
                    channel.writePackage(bufferRead, FAIL_OPEN_SERVER)
                    channel.flushPackage(bufferRead)
                    return
                }
                try {
                    bufferRead.clear()
                    channel.writePackage(bufferRead, CLIENT_OPEN_RESPONSE)
                    channel.writePackage(bufferRead, requestId)
                    channel.writePackage(bufferRead, OK)
                    channel.flushPackage(bufferRead)
                    current = server
                } catch (e: Throwable) {
                    println("ERROR. Can't open success server. Can't send data to client")
                }
            }

            CLIENT_OPEN_RESPONSE -> {
                val requestId = channel.readInt(bufferRead)
                val water = waiterForOpenChannel.remove(requestId)
                if (water == null) {
                    println("ERROR! water not found")
                    return
                }
                val code = channel.readByte(bufferRead)
                when (code) {
                    OK -> water.resume(Unit)
                    CHANNEL_BUSY -> water.resumeWithException(RuntimeException("Channel busy"))
                    UNKNOWN_CHANNEL_TYPE -> water.resumeWithException(RuntimeException("Unknown channel type"))
                    FAIL_OPEN_SERVER -> water.resumeWithException(RuntimeException("Fail to open server"))
                }
            }
        }
    }

    private var channelConnection = false
    private var requestIdIt = 0
    suspend fun <T : Any> openClient(type: Short, data: T) {
        if (current != null) {
            TODO("Channel already busy")
        }
        val pair = communicateRepository.getPair(type) ?: TODO("Pair with code $type not found")
        channelConnection = true
        val client = try {
            pair.createClient(data)
        } catch (e: Throwable) {
            channelConnection = false
            throw e
        }

        val dataBytes = ProtoBuf.encodeToByteArray(pair.dataSerializer, data)

        val requestId = requestIdIt++
        bufferWrite.clear()
        channel.writePackage(bufferWrite, CLIENT_CHANNEL_OPEN)
        channel.writePackage(bufferWrite, requestId)
        channel.writePackage(bufferWrite, type)
        channel.writePackage(bufferWrite, dataBytes.size)
        channel.writePackage(bufferWrite, dataBytes)
        channel.flushPackage(bufferWrite)

        suspendCancellableCoroutine { con ->
            con.invokeOnCancellation {
                channelConnection = false
                waiterForOpenChannel.remove(requestId)
            }
            waiterForOpenChannel[requestId] = con
        }
        current = client
        channelConnection = false
    }

    private val waiterForOpenChannel = HashMap<Int, CancellableContinuation<Unit>>()

    override suspend fun asyncClose() {
        bufferRead.clear()
    }

    override fun writeFrame(): AsyncOutput {

        TODO("Not yet implemented")
    }
}
*/
