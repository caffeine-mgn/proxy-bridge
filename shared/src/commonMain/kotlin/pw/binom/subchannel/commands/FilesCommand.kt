package pw.binom.subchannel.commands

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import pw.binom.config.LOCAL_FS
import pw.binom.frame.FrameChannel
import pw.binom.frame.FrameInput
import pw.binom.frame.FrameOutput
import pw.binom.frame.writeObject
import pw.binom.io.AsyncInput
import pw.binom.io.FileSystem
import pw.binom.serialization.PathSerializer
import pw.binom.services.ClientService
import pw.binom.strong.inject
import pw.binom.subchannel.AbstractCommandClient
import pw.binom.subchannel.Command
import pw.binom.subchannel.commands.TcpConnectCommand.Connected
import pw.binom.url.Path

class FilesCommand : Command<FilesCommand.FilesClient> {
    private val fs by inject<FileSystem>(name = LOCAL_FS)
    private val clientService by inject<ClientService>()

    suspend fun client() = clientService.startServer(this)

    @OptIn(ExperimentalSerializationApi::class)
    @Serializable
    sealed interface Command {

        @Serializable
        class GetDir(@Serializable(PathSerializer::class) val path: Path) : Command

        @Serializable
        class ReadFile(@Serializable(PathSerializer::class) val path: Path) : Command

        @Serializable
        class PutFile(@Serializable(PathSerializer::class) val path: Path) : Command

        @Serializable
        class DeleteFile(@Serializable(PathSerializer::class) val path: Path) : Command

        @Serializable
        class Mkdir(@Serializable(PathSerializer::class) val path: Path) : Command

        @Serializable
        class Move(
            @Serializable(PathSerializer::class) val from: Path,
            @Serializable(PathSerializer::class) val to: Path,
            val overwrite: Boolean
        ) : Command

        @Serializable
        class Copy(
            @Serializable(PathSerializer::class) val from: Path,
            @Serializable(PathSerializer::class) val to: Path,
            val overwrite: Boolean
        ) : Command
    }

    class FilesClient(override val channel: FrameChannel) : AbstractCommandClient() {
//        suspend fun getDir(path: Path): List<String> {
//            asClosed {
//                channel.sendFrame {
//                    it.writeObject(Command.serializer(), Command.GetDir(path))
//                }
//            }
//        }

        fun readFile(path: Path, offset: ULong, length: ULong?) {

        }

        fun putFile(path: Path, input: AsyncInput) {

        }

        fun delete(path: Path) {

        }

        fun mkdir(path: Path) {
        }

        fun move(from: Path, path: Path, overwrite: Boolean) {}
        fun copy(from: Path, path: Path, overwrite: Boolean) {}
    }

    override val cmd: Byte
        get() = pw.binom.subchannel.Command.FS

    override suspend fun startClient(channel: FrameChannel) {
        TODO("Not yet implemented")
    }

    override suspend fun startServer(channel: FrameChannel): FilesClient =
        FilesClient(channel)
}
