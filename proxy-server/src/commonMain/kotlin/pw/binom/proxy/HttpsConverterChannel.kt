package pw.binom.proxy

import pw.binom.DEFAULT_BUFFER_SIZE
import pw.binom.io.AsyncChannel
import pw.binom.io.Available
import pw.binom.io.ByteBuffer
import pw.binom.io.DataTransferSize
import pw.binom.io.socket.ssl.asyncChannel
import pw.binom.io.use
import pw.binom.ssl.EmptyKeyManager
import pw.binom.ssl.SSLContext
import pw.binom.ssl.SSLMethod
import pw.binom.ssl.TrustManager

class HttpsConverterChannel(
    source: AsyncChannel,
    val host: String,
    val port: Int,
    sslContext: SSLContext = SSLContext.getInstance(SSLMethod.TLSv1_2, EmptyKeyManager, TrustManager.TRUST_ALL),
    sslBufferSize: Int = DEFAULT_BUFFER_SIZE,
) : AsyncChannel {

    val sslSession = sslContext.clientSession(host = host, port = port)
    val sslChannel = sslSession.asyncChannel(channel = source, closeParent = true, bufferSize = sslBufferSize)

    override suspend fun asyncClose() {
        sslSession.use {
            sslChannel.asyncClose()
        }
    }

    override suspend fun write(data: ByteBuffer): DataTransferSize = sslChannel.write(data)

    override suspend fun flush() {
        sslChannel.flush()
    }

    override val available: Available
        get() = sslChannel.available

    override suspend fun read(dest: ByteBuffer): DataTransferSize =
        sslChannel.read(dest)

    override suspend fun write(
        data: ByteArray,
        offset: Int,
        length: Int
    ) = sslChannel.write(data, offset, length)

    override suspend fun read(
        dest: ByteArray,
        offset: Int,
        length: Int
    ) = sslChannel.read(dest, offset, length)

    override suspend fun readFully(dest: ByteArray, offset: Int, length: Int) =
        sslChannel.readFully(dest, offset, length)

    override suspend fun readFully(dest: ByteBuffer): Int = sslChannel.readFully(dest)

    override suspend fun writeFully(data: ByteBuffer): Int = sslChannel.writeFully(data)

    override suspend fun writeFully(data: ByteArray, offset: Int, length: Int) =
        sslChannel.writeFully(data, offset, length)

    override suspend fun skip(bytes: Long, bufferSize: Int) = sslChannel.skip(bytes, bufferSize)

    override suspend fun skip(bytes: Long, buffer: ByteBuffer) = sslChannel.skip(bytes, buffer)

    override suspend fun skipAll(bufferSize: Int) = sslChannel.skipAll(bufferSize)

    override suspend fun skipAll(buffer: ByteBuffer) = sslChannel.skipAll(buffer)
}
