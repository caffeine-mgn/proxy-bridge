package pw.binom.frame

import kotlinx.serialization.Serializable
import pw.binom.*
import kotlin.jvm.JvmInline

@JvmInline
@Serializable
value class PackageSize(val raw: Short) {
    companion object {
        const val SIZE_BYTES = Short.SIZE_BYTES
        const val MAX_VALUE = Short.MAX_VALUE.toInt()
        val MAX = PackageSize(Short.MAX_VALUE)
    }

    constructor(array: ByteArray) : this(Short.fromBytes(array))
    constructor(size: Int) : this(size.toUShort().toShort()) {
        require(size > 0) { "Invalid size $size. Size should be great than 0" }
        require(size <= UShort.MAX_VALUE.toInt()) { "Invalid size $size. Size should be less or equal ${UShort.MAX_VALUE}" }
    }

    val asInt: Int
        get() = raw.toUShort().toInt()

    val asShort
        get() = raw
    val asUShort
        get() = raw.toUShort()

    val isZero
        get() = raw == 0.toShort()

    override fun toString(): String = "PackageSize($asInt)"

    fun toByteArray(dest: ByteArray) {
        raw.toByteArray(dest)
    }

    fun toByteArray(): ByteArray {
        val data = ByteArray(SIZE_BYTES)
        toByteArray(data)
        return data
    }

    operator fun plus(other: Int) = PackageSize(asInt + other)
    operator fun minus(other: Int) = PackageSize(asInt - other)
    operator fun compareTo(other: PackageSize): Int = (raw - other.raw).toInt()
    operator fun compareTo(other: Int): Int = asInt - other
}
