package pw.binom.frame

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@JvmInline
@Serializable
value class PackageSize(val raw: Short) {
    constructor(size: Int) : this(size.toUShort().toShort()) {
        require(size > 0) { "Invalid size $size. Size should be great than 0" }
        require(size <= UShort.MAX_VALUE.toInt()) { "Invalid size $size. Size should be less or equal ${UShort.MAX_VALUE}" }
    }

    val asInt
        get() = raw.toUShort().toInt()

    val asShort
        get() = raw
    val asUShort
        get() = raw.toUShort()

    val isZero
        get() = raw == 0.toShort()

    override fun toString(): String = "PackageSize($asInt)"

    operator fun plus(other: Int) = PackageSize(asInt + other)
    operator fun minus(other: Int) = PackageSize(asInt - other)
    operator fun compareTo(other: PackageSize): Int = (raw - other.raw).toInt()
    operator fun compareTo(other: Int): Int = asInt - other
}
