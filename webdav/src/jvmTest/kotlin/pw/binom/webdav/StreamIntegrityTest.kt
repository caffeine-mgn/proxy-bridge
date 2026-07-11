package pw.binom.webdav

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.io.Buffer
import kotlinx.io.files.Path
import org.junit.Test
import pw.binom.webdav.fs.local.LocalFileSystem
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

private fun testRun(block: suspend () -> Unit) {
    runBlocking { withTimeout(60.seconds) { block() } }
}

class StreamIntegrityTest {

    @Test
    fun `local file read produces no zero blocks`() = testRun {
        val root = Path("/tmp")
        val fs = LocalFileSystem(root)
        val source = fs.readFile(Path("stream_test.bin"), null)
        val buf = Buffer()
        var totalRead = 0L
        var zeroBlocks = 0
        var chunkCount = 0

        source.use { src ->
            while (true) {
                val read = src.readAtMostTo(buf, 8192)
                if (read <= 0) break
                chunkCount++
                totalRead += read
                val bytes = ByteArray(buf.size.toInt())
                buf.readAtMostTo(bytes, 0, bytes.size)

                val zeros = bytes.count { it == 0.toByte() }
                if (zeros > bytes.size / 2) {
                    zeroBlocks++
                    println("ZERO BLOCK #$zeroBlocks: chunk=$chunkCount, size=${bytes.size}, zeros=$zeros")
                }
            }
        }

        println("Total: $totalRead bytes in $chunkCount chunks, $zeroBlocks zero-heavy chunks")
        assertEquals(0, zeroBlocks, "Found $zeroBlocks zero-heavy chunks!")
        assertEquals(10000L, totalRead, "Total bytes mismatch")
    }
}
