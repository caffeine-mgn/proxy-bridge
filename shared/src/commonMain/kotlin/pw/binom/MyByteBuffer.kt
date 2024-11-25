package pw.binom

import pw.binom.concurrency.SpinLock
import pw.binom.concurrency.synchronize
import pw.binom.io.ByteBuffer
import pw.binom.thread.Thread
/*
class MyByteBuffer : ByteBuffer {
    companion object {

        private val map = HashMap<String, Int>()
        private val lock = SpinLock()

        private val thread = Thread {
            while (true) {
                Thread.sleep(5000)
                System.gc()
                lock.synchronize {
                    map.forEach { (stack, counter) ->
                        println("--==$counter==--")
                        println(stack)
                        println("--==$counter==--")
                    }
                }
            }
        }
        init {
            thread.start()
        }

        private fun inc(stack: String) {
            lock.synchronize {
                map[stack] = map.getOrElse(stack) { 0 } + 1
            }
        }

        private fun dec(stack: String) {
            lock.synchronize {
                var e = map[stack] ?: return
                e--
                if (e == 0) {
                    map.remove(stack)
                } else {
                    map[stack] = e
                }
            }
        }
    }

    private val stack = Throwable()
        .stackTraceToString()
        .lines()
        .drop(1)
        .joinToString(separator = "\n")

    init {
        inc(stack)
    }

    constructor(size: Int) : super(size)
    constructor(array: ByteArray) : super(array)


    override fun close() {
        dec(stack)
        super.close()
    }
}
*/

fun byteBuffer(size: Int) = ByteBuffer(size)
fun byteBuffer(array: ByteArray) = ByteBuffer(array)
