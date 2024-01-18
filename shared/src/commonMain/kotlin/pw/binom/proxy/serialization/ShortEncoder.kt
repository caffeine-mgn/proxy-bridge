package pw.binom.proxy.serialization

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule
import pw.binom.io.ByteArrayOutput

class ShortEncoder(
    val out: ByteArrayOutput,
    override val serializersModule: SerializersModule,
) : Encoder, CompositeEncoder {
    override fun encodeBooleanElement(
        descriptor: SerialDescriptor,
        index: Int,
        value: Boolean,
    ) {
        encodeBoolean(value)
    }

    override fun encodeByteElement(
        descriptor: SerialDescriptor,
        index: Int,
        value: Byte,
    ) {
        encodeByte(value)
    }

    override fun encodeCharElement(
        descriptor: SerialDescriptor,
        index: Int,
        value: Char,
    ) {
        encodeChar(value)
    }

    override fun encodeDoubleElement(
        descriptor: SerialDescriptor,
        index: Int,
        value: Double,
    ) {
        encodeDouble(value)
    }

    override fun encodeFloatElement(
        descriptor: SerialDescriptor,
        index: Int,
        value: Float,
    ) {
        encodeFloat(value)
    }

    override fun encodeInlineElement(
        descriptor: SerialDescriptor,
        index: Int,
    ): Encoder = this

    override fun encodeIntElement(
        descriptor: SerialDescriptor,
        index: Int,
        value: Int,
    ) {
        encodeInt(value)
    }

    override fun encodeLongElement(
        descriptor: SerialDescriptor,
        index: Int,
        value: Long,
    ) {
        encodeLong(value)
    }

    @ExperimentalSerializationApi
    override fun <T : Any> encodeNullableSerializableElement(
        descriptor: SerialDescriptor,
        index: Int,
        serializer: SerializationStrategy<T>,
        value: T?,
    ) {
        if (value == null) {
            encodeNull()
        } else {
            out.writeByte(MessagePackConst.MP_FALSE)
            encodeSerializableElement(
                descriptor = descriptor,
                index = index,
                serializer = serializer,
                value = value
            )
        }
    }

    override fun <T> encodeSerializableElement(
        descriptor: SerialDescriptor,
        index: Int,
        serializer: SerializationStrategy<T>,
        value: T,
    ) {
        serializer.serialize(this, value)
    }

    override fun encodeShortElement(
        descriptor: SerialDescriptor,
        index: Int,
        value: Short,
    ) {
        encodeShort(value)
    }

    override fun encodeStringElement(
        descriptor: SerialDescriptor,
        index: Int,
        value: String,
    ) {
        encodeString(value)
    }

    override fun endStructure(descriptor: SerialDescriptor) {
    }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder = this

    override fun encodeBoolean(value: Boolean) {
        encodeByte(if (value) MessagePackConst.MP_TRUE else MessagePackConst.MP_FALSE)
    }

    override fun encodeByte(value: Byte) {
        out.writeByte(value)
    }

    override fun encodeChar(value: Char) {
        encodeInt(value.code)
    }

    override fun encodeDouble(value: Double) {
        out.writeDouble(value)
    }

    override fun encodeEnum(
        enumDescriptor: SerialDescriptor,
        index: Int,
    ) = encodeInt(index)

    override fun encodeFloat(value: Float) {
        out.writeFloat(value)
    }

    override fun encodeInline(descriptor: SerialDescriptor): Encoder = this

    override fun encodeInt(value: Int) {
        if (value >= 0) {
            when {
                value <= MessagePackConst.MAX_7BIT -> {
                    out.writeByte((value.toInt() or MessagePackConst.MP_FIXNUM.toInt()).toByte())
                }

                value <= MessagePackConst.MAX_8BIT -> {
                    out.writeByte(MessagePackConst.MP_UINT8)
                    out.writeByte(value.toByte())
                }

                value <= MessagePackConst.MAX_16BIT -> {
                    out.writeByte(MessagePackConst.MP_UINT16)
                    out.writeShort(value.toShort())
                }

                value <= MessagePackConst.MAX_32BIT -> {
                    out.writeByte(MessagePackConst.MP_UINT32)
                    out.writeInt(value.toInt())
                }

                else -> {
                    out.writeByte(MessagePackConst.MP_UINT64)
                    out.writeLong(value.toLong())
                }
            }
        } else {
            when {
                value >= -(MessagePackConst.MAX_5BIT + 1) -> {
                    out.writeByte((value.toInt() and 0xff).toByte())
                }

                value >= -(MessagePackConst.MAX_7BIT + 1) -> {
                    out.writeByte(MessagePackConst.MP_INT8)
                    out.writeByte(value.toByte())
                }

                value >= -(MessagePackConst.MAX_15BIT + 1) -> {
                    out.writeByte(MessagePackConst.MP_INT16)
                    out.writeShort(value.toShort())
                }

                value >= -(MessagePackConst.MAX_31BIT + 1) -> {
                    out.writeByte(MessagePackConst.MP_INT32)
                    out.writeInt(value.toInt())
                }

                else -> {
                    out.writeByte(MessagePackConst.MP_INT64)
                    out.writeLong(value.toLong())
                }
            }
        }
    }

    override fun encodeLong(value: Long) {
        out.writeLong(value)
    }

    @ExperimentalSerializationApi
    override fun encodeNull() {
        out.writeByte(MessagePackConst.MP_NULL)
    }

    override fun encodeShort(value: Short) {
        out.writeShort(value)
    }

    override fun encodeString(value: String) {
        encodeInt(value.length)
        out.write(value.encodeToByteArray())
    }
}
