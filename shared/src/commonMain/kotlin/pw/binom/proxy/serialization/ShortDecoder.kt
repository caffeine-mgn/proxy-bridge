package pw.binom.proxy.serialization

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.modules.SerializersModule
import pw.binom.io.ByteArrayInput
import pw.binom.proxy.serialization.MessagePackConst.MP_INT16
import pw.binom.proxy.serialization.MessagePackConst.MP_INT32
import pw.binom.proxy.serialization.MessagePackConst.MP_INT8
import pw.binom.proxy.serialization.MessagePackConst.MP_UINT16
import pw.binom.proxy.serialization.MessagePackConst.MP_UINT32
import pw.binom.proxy.serialization.MessagePackConst.MP_UINT8

class ShortDecoder(
    override val serializersModule: SerializersModule,
    val input: ByteArrayInput,
    private val desc: SerialDescriptor,
) : CompositeDecoder, Decoder {
    private var cursor = -1

    override fun decodeBooleanElement(
        descriptor: SerialDescriptor,
        index: Int,
    ): Boolean = decodeBoolean()

    override fun decodeByteElement(
        descriptor: SerialDescriptor,
        index: Int,
    ): Byte = decodeByte()

    override fun decodeCharElement(
        descriptor: SerialDescriptor,
        index: Int,
    ): Char = decodeChar()

    override fun decodeDoubleElement(
        descriptor: SerialDescriptor,
        index: Int,
    ): Double = decodeDouble()

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        if (cursor + 1 >= descriptor.elementsCount) {
            return CompositeDecoder.DECODE_DONE
        }
        cursor++
        return cursor
    }

    override fun decodeFloatElement(
        descriptor: SerialDescriptor,
        index: Int,
    ): Float = decodeFloat()

    override fun decodeInlineElement(
        descriptor: SerialDescriptor,
        index: Int,
    ): Decoder = decodeInline(descriptor)

    override fun decodeIntElement(
        descriptor: SerialDescriptor,
        index: Int,
    ): Int = decodeInt()

    override fun decodeLongElement(
        descriptor: SerialDescriptor,
        index: Int,
    ): Long = decodeLong()

    @ExperimentalSerializationApi
    override fun <T : Any> decodeNullableSerializableElement(
        descriptor: SerialDescriptor,
        index: Int,
        deserializer: DeserializationStrategy<T?>,
        previousValue: T?,
    ): T? {
        val b = input.readByte()
        if (b == Consts.NIL) {
            return null
        }
        return decodeSerializableElement(
            descriptor = descriptor,
            index = index,
            deserializer = deserializer
        )
    }

    override fun <T> decodeSerializableElement(
        descriptor: SerialDescriptor,
        index: Int,
        deserializer: DeserializationStrategy<T>,
        previousValue: T?,
    ): T = deserializer.deserialize(beginStructure(descriptor))

    override fun decodeShortElement(
        descriptor: SerialDescriptor,
        index: Int,
    ): Short = decodeShort()

    override fun decodeStringElement(
        descriptor: SerialDescriptor,
        index: Int,
    ): String = decodeString()

    override fun endStructure(descriptor: SerialDescriptor) {
    }

    override fun beginStructure(descriptor: SerialDescriptor) =
        ShortDecoder(
            serializersModule = serializersModule,
            input = input,
            desc = descriptor
        )

    override fun decodeBoolean(): Boolean = decodeByte() == Consts.TRUE

    override fun decodeByte(): Byte = input.readByte()

    override fun decodeChar(): Char = decodeInt().toChar()

    override fun decodeDouble(): Double = input.readDouble()

    override fun decodeEnum(enumDescriptor: SerialDescriptor): Int = decodeInt()

    override fun decodeFloat(): Float = input.readFloat()

    override fun decodeInline(descriptor: SerialDescriptor): Decoder =
        ShortDecoder(
            serializersModule = serializersModule,
            input = input,
            desc = descriptor
        )

    override fun decodeInt(): Int {
        val firstByte = input.readByte()
        if ((firstByte.toInt() ushr 7) == 0) {
            return firstByte.toInt()
        }
        when (firstByte) {
            MP_UINT8, MP_INT8 -> return input.readByte().toInt()
            MP_UINT16, MP_INT16 -> return input.readShort().toInt()
            MP_UINT32, MP_INT32 -> return input.readInt()
            else -> TODO()
        }
    }

    override fun decodeLong(): Long = input.readLong()

    @ExperimentalSerializationApi
    override fun decodeNotNullMark(): Boolean = input.readByte() != Consts.NIL

    @ExperimentalSerializationApi
    override fun decodeNull(): Nothing? = null

    override fun decodeShort(): Short = input.readShort()

    override fun decodeString(): String {
        val size = decodeInt()
        return input.readBytes(size).decodeToString()
    }
}
