package pw.binom.webdav

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.io.Buffer
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
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
        val root = Path(System.getProperty("java.io.tmpdir"), "webdav-stream-integrity")
        SystemFileSystem.createDirectories(root)
        val testFile = Path(root, "stream_test.bin")

        // Тест раньше зависел от заранее созданного /tmp/stream_test.bin.
        // Теперь готовим данные сами: 10000 случайных байт.
        val payload = ByteArray(10_000) { (it * 37 + 11).toByte() }
        SystemFileSystem.sink(testFile).buffered().use { it.write(payload) }

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
        SystemFileSystem.delete(testFile, mustExist = false)
    }
}
