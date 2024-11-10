package pw.binom.subchannel

import pw.binom.frame.FrameChannel
import pw.binom.atomic.AtomicBoolean
import pw.binom.copyTo
import pw.binom.io.AsyncInput
import pw.binom.io.AsyncOutput
import pw.binom.url.Path

class FSClient(val channel: FrameChannel) {
    companion object {
        const val GET_FILE_LIST: Byte = 0x1
        const val GET_FILE: Byte = 0x2
        const val PUT_FILE: Byte = 0x3
        const val MK_DIR: Byte = 0x4
        const val NOT_EXIST: Byte = 0x5
        const val IS_NOT_FILE: Byte = 0x6
        const val EXIST: Byte = 0x7
        const val CREATED: Byte = 0x8
        const val ERROR: Byte = 0x9
    }

    private val close = AtomicBoolean(false)

    suspend fun makeDirectory(path: Path) {
        try {
            channel.sendFrame {
                it.writeByte(MK_DIR)
                it.writeString(path.raw)
            }.ensureNotClosed()
            val resp = channel.readFrame { it.readByte() }.valueOrNull
            if (resp != CREATED) {
                TODO()
            }
        } finally {
            close.setValue(true)
            channel.asyncCloseAnyway()
        }
    }

    class Entity(
        val name: String,
        val isFile: Boolean,
    )

    suspend fun getList(path: Path): List<Entity>? {
        channel.sendFrame {
            it.writeByte(GET_FILE_LIST)
            it.writeString(path.raw)
        }.ensureNotClosed()
        val cmd = channel.readFrame {
            it.readByte()
        }.valueOrNull ?: return null
        when (cmd) {
            NOT_EXIST -> return null
            EXIST -> {
                return channel.readFrame {
                    val size = it.readInt()
                    val out = ArrayList<Entity>(size)
                    repeat(size) { _ ->
                        out += Entity(
                            name = it.readString(),
                            isFile = it.readBoolean()
                        )
                    }
                    out
                }.ensureNotClosed()
            }

            else -> TODO()
        }
    }

    enum class GetFileResult {
        FILE_NOT_FOUND,
        IS_NOT_FILE,
        CLOSED,
        OK,
    }

    suspend fun getFile(file: Path, out: () -> AsyncOutput): GetFileResult {
        channel.sendFrame { it.writeByte(GET_FILE) }.valueOrNull ?: return GetFileResult.CLOSED
        val resp = channel.readFrame { it.readByte() }.valueOrNull ?: return GetFileResult.CLOSED
        when (resp) {
            EXIST -> {
                channel.copyTo(out())
                return GetFileResult.OK
            }

            IS_NOT_FILE -> return GetFileResult.IS_NOT_FILE
            NOT_EXIST -> return GetFileResult.FILE_NOT_FOUND
            else -> TODO()
        }
    }

    suspend fun putFile(file: Path, input: AsyncInput): Boolean {
        channel.sendFrame {
            it.writeByte(PUT_FILE)
            it.writeString(file.raw)
        }
        val cmd = channel.readFrame { it.readByte() }.valueOrNull ?: return false
        when (cmd) {
            NOT_EXIST -> return false
            ERROR -> return false
            EXIST -> {
                input.copyTo(channel)
                return true
            }

            else -> return false
        }


    }
}
