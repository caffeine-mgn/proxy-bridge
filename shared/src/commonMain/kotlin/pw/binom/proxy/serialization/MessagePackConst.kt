package pw.binom.proxy.serialization

object MessagePackConst {
    const val MAX_4BIT = 0xf
    const val MAX_5BIT = 0x1f
    const val MAX_7BIT = 0x7f
    const val MAX_8BIT = 0xff
    const val MAX_15BIT = 0x7fff
    const val MAX_16BIT = 0xffff
    const val MAX_31BIT = 0x7fffffff
    const val MAX_32BIT = 0xffffffffL

// val BI_MIN_LONG: BigInteger = BigInteger.valueOf(Long.MIN_VALUE)
// val BI_MAX_LONG: BigInteger = BigInteger.valueOf(Long.MAX_VALUE)
// val BI_MAX_64BIT: BigInteger = BigInteger.valueOf(2).pow(64).subtract(BigInteger.ONE)

    // these values are from http://wiki.msgpack.org/display/MSGPACK/Format+specification
    const val MP_NULL = 0xc0.toByte()
    const val MP_FALSE = 0xc2.toByte()
    const val MP_TRUE = 0xc3.toByte()
    const val MP_BIN8 = 0xc4.toByte()
    const val MP_BIN16 = 0xc5.toByte()
    const val MP_BIN32 = 0xc6.toByte()

    const val MP_FLOAT = 0xca.toByte()
    const val MP_DOUBLE = 0xcb.toByte()

    const val MP_FIXNUM = 0x00.toByte() // last 7 bits is value

    const val MP_UINT8 = 0xcc.toByte() // 204 11001100
    const val MP_UINT16 = 0xcd.toByte()
    const val MP_UINT32 = 0xce.toByte()
    const val MP_UINT64 = 0xcf.toByte()

    const val MP_NEGATIVE_FIXNUM = 0xe0.toByte() // last 5 bits is value

    const val MP_NEGATIVE_FIXNUM_INT = 0xe0 //  /me wishes for signed numbers.
    const val MP_INT8 = 0xd0.toByte() // 208 11010000
    const val MP_INT16 = 0xd1.toByte()
    const val MP_INT32 = 0xd2.toByte()
    const val MP_INT64 = 0xd3.toByte()

    const val MP_FIXARRAY = 0x90.toByte() // last 4 bits is size
    const val MP_ARRAY1 = 0x91.toByte()
    const val MP_FIXEXT = 0xd8.toByte()
    const val MP_DECIMAL = 0x01.toByte()
    const val MP_UUID = 0x02.toByte()
    const val MP_FIXARRAY_INT = 0x90
    const val MP_ARRAY16 = 0xdc.toByte()
    const val MP_ARRAY32 = 0xdd.toByte()

    const val MP_FIXMAP = 0x80.toByte() // last 4 bits is size

    const val MP_FIXMAP_INT = 0x80
    const val MP_MAP16 = 0xde.toByte()
    const val MP_MAP32 = 0xdf.toByte()

    const val MP_FIXSTR = 0xa0.toByte() // last 5 bits is size

    const val MP_FIXSTR_INT = 0xa0
    const val MP_STR8 = 0xd9.toByte()
    const val MP_STR16 = 0xda.toByte()
    const val MP_STR32 = 0xdb.toByte()
}
