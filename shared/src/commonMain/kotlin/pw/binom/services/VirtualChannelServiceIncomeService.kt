package pw.binom.services

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import pw.binom.Environment
import pw.binom.logger.Logger
import pw.binom.logger.info
import pw.binom.logger.infoSync
import pw.binom.logger.severeSync
import pw.binom.network.NetworkManager
import pw.binom.strong.BeanLifeCycle
import pw.binom.strong.inject
import pw.binom.strong.injectServiceList
import pw.binom.strong.map
import pw.binom.subchannel.Command
import pw.binom.timeoutChecker
import kotlin.coroutines.suspendCoroutine
import kotlin.time.Duration.Companion.seconds

class VirtualChannelServiceIncomeService {
    private val virtualChannelService: VirtualChannelService by inject()
    private val networkManager: NetworkManager by inject()
    private val commands by injectServiceList<Command<Any?>>().map {
        it.associateBy { it.cmd }
    }

    private var job: Job? = null

    private val logger by Logger.ofThisOrGlobal

    init {
        BeanLifeCycle.postConstruct {
            job = networkManager.launch {
                try {
                    while (isActive) {
                        var state = 0
                        var cmd: Byte? = null
                        timeoutChecker(timeout = 10.seconds, onTimeout = {
                            logger.infoSync("accept timeout. state=$state, cmd=$cmd")
                        }) {
                            try {
                                state = 1
                                logger.info("Accept new connect....")
                                val newChannel = virtualChannelService.accept()
                                logger.info("Accepted!!!")
                                state = 2
                                logger.info("Reading connection CMD...")
                                cmd = newChannel.readFrame { it.readByte() }.valueOrNull
                                logger.info("Connection CMD: $cmd")
                                state = 3
                                if (cmd == null) {
                                    logger.info("Cmd not found. Channel was closed.")
                                    state = 4
                                    newChannel.asyncClose()
                                    return@timeoutChecker
                                }
                                state = 5
                                val command = commands[cmd]
                                logger.info("Found CMD: $command")
                                state = 6
                                if (command == null) {
                                    state = 7
                                    newChannel.asyncClose()
                                    logger.info("Cmd not found. Closing channel")
                                } else {
                                    logger.info("Cmd found: $command")
                                    state = 8
                                    logger.info("Starting commend")
                                    GlobalScope.launch(networkManager) {
                                        command.startClient(newChannel)
                                    }
                                    logger.info("Command started!")
                                }
                            } catch (e: Throwable) {
                                logger.severeSync(exception = e, text = "Can't processing income")
                                e.printStackTrace()
                            }
                        }
                    }
                } finally {
                    repeat(30) {
                        logger.severeSync("FINISHED accept!")
                    }
                }
            }
        }
        BeanLifeCycle.preDestroy {
            job?.cancel()
        }
    }
}
