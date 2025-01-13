@file:OptIn(ExperimentalSerializationApi::class)

package pw.binom.frame

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.protobuf.ProtoBuf
import pw.binom.*

private val protobuf = ProtoBuf

fun <T> FrameInput.readObject(k: KSerializer<T>) {
    val size = readInt()
    val data = readByteArray(size)
    protobuf.decodeFromByteArray(k, data)
}

fun <T> FrameOutput.writeObject(k: KSerializer<T>, value: T) {
    val data = protobuf.encodeToByteArray(k, value)
    writeInt(data.size)
    writeByteArray(data)
}

fun FrameSender.asOutput() = AsyncFrameOutput(this)
fun FrameReceiver.toInput() = AsyncFrameInput(this)
fun FrameChannel.toAsyncChannel() = FrameAsyncChannelAdapter(this)
