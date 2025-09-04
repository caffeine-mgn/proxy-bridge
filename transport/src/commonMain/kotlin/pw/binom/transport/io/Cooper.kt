package pw.binom.transport.io

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import pw.binom.DEFAULT_BUFFER_SIZE
import pw.binom.copyTo
import pw.binom.io.AsyncChannel
import pw.binom.io.AsyncInput
import pw.binom.io.AsyncOutput
import pw.binom.io.ByteBuffer
import pw.binom.io.use
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

object Cooper {
    suspend fun AsyncInput.copyTo2(dest: AsyncOutput, bufferSize: Int = DEFAULT_BUFFER_SIZE): Long =
        ByteBuffer(bufferSize).use { buffer ->
            copyTo2(dest = dest, buffer = buffer)
        }

    suspend fun AsyncInput.copyTo2(dest: AsyncOutput, buffer: ByteBuffer): Long {
        var totalLength = 0L
        while (true) {
            buffer.clear()
            println("AsyncInput.copyTo2:: Reading from $this to $dest ${buffer.remaining}....")
            val length = read(buffer)
            println("AsyncInput.copyTo2:: was read $length")
            if (length.isNotAvailable) {
                break
            }
            totalLength += length.length.toLong()
            buffer.flip()
            println("AsyncInput.copyTo2:: sending to dest ${buffer.remaining} bytes")
            dest.writeFully(buffer)
            dest.flush()
        }
        return totalLength
    }

    fun exchange(
        first: AsyncChannel,
        second: AsyncChannel,
        scope: CoroutineScope = GlobalScope,
        context: CoroutineContext = EmptyCoroutineContext,
    ): Job {
        println("Cooper::exchange start exchange between $first and $second")
        var job2: Job? = null
        val job1 = scope.launch(context) {
            try {
                first.copyTo2(second)
            } finally {
                job2?.cancel()
            }
        }
        job2 = scope.launch(context) {
            try {
                second.copyTo2(first)
            } finally {
                job1.cancel()
            }
        }
        return scope.launch(context) { listOf(job1, job2).joinAll() }
    }
}
