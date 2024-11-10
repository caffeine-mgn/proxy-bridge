package pw.binom

import pw.binom.frame.PackageSize
import kotlin.test.Test

class PackageSizeTest {
    @Test
    fun test() {
        val size = 32767 + 100

        val ps = PackageSize(size)

        val sizeShort = size.toShort()
        val sizeUShort = sizeShort.toUShort()
        val sizeShortToInt = sizeShort.toInt()
        val sizeUShortToInt = sizeUShort.toInt()
        println("size=$size ${size.toString(2)}")
        println("sizeShort=$sizeShort ${sizeShort.toString(2)}")
        println("sizeUShort=$sizeUShort ${sizeUShort.toString(2)}")
        println("sizeShortToInt=$sizeShortToInt ${sizeShortToInt.toString(2)}")
        println("sizeUShortToInt=$sizeUShortToInt ${sizeUShortToInt.toString(2)}")
        println("ps=$ps ${ps.asInt}")
    }
}
