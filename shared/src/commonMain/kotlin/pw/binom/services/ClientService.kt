package pw.binom.services

import pw.binom.strong.BeanLifeCycle
import pw.binom.strong.inject
import pw.binom.strong.injectServiceList
import pw.binom.subchannel.Command

class ClientService {
    private val virtualChannelService by inject<VirtualChannelService>()
    private val commands by injectServiceList<Command<Any?>>()

    init {
        BeanLifeCycle.postConstruct {
            val existCommands = HashMap<Byte, Command<Any?>>()
            commands.forEach {
                val exist = existCommands[it.cmd]
                if (exist != null) {
                    throw RuntimeException("Command with byte ${it.cmd} already exists: $exist")
                }
            }
        }
    }

    suspend fun <T> startServer(command: Command<T>): T {
        val channel = virtualChannelService.createChannel()
        channel
            .sendFrame { it.writeByte(command.cmd) }
            .valueOrNull
            ?: throw IllegalStateException("Channel closed")
        return try {
            command.startServer(channel)
        } catch (e: Throwable) {
            channel.asyncClose()
            throw e
        }
    }
}
