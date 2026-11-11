package pw.binom.proxy.front

/** Один файл бутстрапа, который можно «напечатать» на удалённой машине. */
data class BootstrapFile(
    val id: Int,
    val name: String,
    val size: Long,
)

/**
 * Протокол передачи файла на удалённую машину через COM-порт.
 *
 * Формат — тот же, что в исходном бутстрапе проекта (`Codes.START_FILE`),
 * чтобы работали уже написанные приёмники:
 *
 * ```
 * START_FILE  1 байт   1 = начало файла
 * nameLen     4 байта  длина имени, big-endian
 * name        nameLen  имя файла (UTF-8)
 * size        4 байта  размер тела, big-endian
 * payload     size     тело
 * ```
 */
object BootstrapProtocol {
    const val START_FILE: Int = 1

    fun frame(name: String, payload: ByteArray): ByteArray {
        val nameBytes = name.encodeToByteArray()
        require(nameBytes.size in 1..4096) { "file name must be 1..4096 bytes, got ${nameBytes.size}" }
        val header = ByteArray(1 + 4 + nameBytes.size + 4)
        var i = 0
        header[i++] = START_FILE.toByte()
        writeIntBe(header, i, nameBytes.size)
        i += 4
        nameBytes.forEach { header[i++] = it }
        writeIntBe(header, i, payload.size)
        return header + payload
    }

    private fun writeIntBe(target: ByteArray, offset: Int, value: Int) {
        target[offset] = ((value ushr 24) and 0xFF).toByte()
        target[offset + 1] = ((value ushr 16) and 0xFF).toByte()
        target[offset + 2] = ((value ushr 8) and 0xFF).toByte()
        target[offset + 3] = (value and 0xFF).toByte()
    }
}
