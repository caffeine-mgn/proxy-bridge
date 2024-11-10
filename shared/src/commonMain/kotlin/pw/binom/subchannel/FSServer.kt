package pw.binom.subchannel

import pw.binom.frame.FrameChannel
import pw.binom.copyTo
import pw.binom.io.FileSystem
import pw.binom.io.IOException
import pw.binom.io.useAsync
import pw.binom.url.toPath

object FSServer {
    private interface Income {
        data class GetFile(val file: String) : Income
        data class GetList(val file: String) : Income
        data class PutList(val file: String) : Income
        data class MakeDirectory(val file: String) : Income
        data object UnSupported : Income
    }

    suspend fun processing(channel: FrameChannel, fileSystem: FileSystem) {
        val cmd = channel.readFrame {
            when (val cmd = it.readByte()) {
                FSClient.MK_DIR -> Income.MakeDirectory(it.readString())
                FSClient.GET_FILE_LIST -> Income.GetList(it.readString())
                FSClient.GET_FILE -> Income.GetFile(it.readString())
                FSClient.PUT_FILE -> Income.PutList(it.readString())
                else -> Income.UnSupported
            }
        }.valueOrNull ?: return

        when (cmd) {
            is Income.MakeDirectory -> {
                fileSystem.mkdirs(cmd.file.toPath)
                channel.sendFrame {
                    it.writeByte(FSClient.CREATED)
                }
            }

            is Income.GetList -> {
                val files = fileSystem.getDir(cmd.file.toPath)
                if (files == null) {
                    channel.sendFrame { it.writeByte(FSClient.NOT_EXIST) }
                } else {
                    channel.sendFrame {
                        it.writeByte(FSClient.EXIST)
                    }
                    channel.sendFrame {
                        it.writeInt(files.size)
                        files.forEach { entity ->
                            it.writeString(entity.name)
                            it.writeBoolean(entity.isFile)
                        }
                    }
                }
            }

            is Income.GetFile -> {
                val file = fileSystem.get(cmd.file.toPath)
                when {
                    file == null -> channel.sendFrame { it.writeByte(FSClient.NOT_EXIST) }
                    file.isFile -> {
                        val stream = file.read()
                        if (stream == null) {
                            channel.sendFrame { it.writeByte(FSClient.NOT_EXIST) }
                        } else {
                            channel.sendFrame { it.writeByte(FSClient.EXIST) }
                            stream.useAsync { stream ->
                                stream.copyTo(channel)
                            }
                        }
                    }

                    !file.isFile -> channel.sendFrame { it.writeByte(FSClient.IS_NOT_FILE) }
                }
            }

            is Income.PutList -> {
                val file = try {
                    fileSystem.new(cmd.file.toPath)
                } catch (_: FileSystem.FileNotFoundException) {
                    channel.sendFrame {
                        it.writeByte(FSClient.NOT_EXIST)
                    }
                    return
                } catch (e: IOException) {
                    channel.sendFrame {
                        it.writeByte(FSClient.ERROR)
                    }
                    return
                }
                channel.sendFrame {
                    it.writeByte(FSClient.EXIST)
                }
                file.useAsync { file ->
                    channel.copyTo(file)
                }
            }
        }
    }
}
