@file:Suppress("ktlint:standard:no-wildcard-imports")

package pw.binom.proxy

import kotlinx.coroutines.*
import pw.binom.*
import pw.binom.io.AsyncChannel
import pw.binom.io.AsyncCloseable
import pw.binom.io.http.websocket.WebSocketClosedException
import pw.binom.logger.Logger
import pw.binom.logger.debug
import pw.binom.logger.info
import pw.binom.logger.warn
import pw.binom.network.SocketClosedException
import pw.binom.proxy.io.copyTo
import kotlin.coroutines.cancellation.CancellationException

class ChannelBridge(
    val id: Int,
    private val local: AsyncChannel,
    private val remote: AsyncChannel,
    private val bufferSize: Int,
    private val logger: Logger?,
    private val localName: String,
    private val remoteName: String,
    private val controller: Controller?,
    private val scope: CoroutineScope,
) : AsyncCloseable {
    interface Controller {
        suspend fun created(channelBridge: ChannelBridge) {}

        suspend fun closed(channelBridge: ChannelBridge) {}

        suspend fun localClosed(channelBridge: ChannelBridge) {
            channelBridge.asyncClose()
        }

        suspend fun removeClosed(channelBridge: ChannelBridge) {
            channelBridge.asyncClose()
        }
    }

    companion object {
        suspend fun create(
            id: Int,
            local: AsyncChannel,
            remote: AsyncChannel,
            bufferSize: Int = DEFAULT_BUFFER_SIZE,
            logger: Logger? = null,
            localName: String = "local",
            remoteName: String = "remote",
            controller: Controller? = null,
            scope: CoroutineScope,
        ): ChannelBridge {
            val bridge =
                ChannelBridge(
                    local = local,
                    remote = remote,
                    bufferSize = bufferSize,
                    logger = logger,
                    localName = localName,
                    remoteName = remoteName,
                    controller = controller,
                    id = id,
                    scope = scope
                )
            bridge.start()
            return bridge
        }
    }

    private lateinit var wsToTcp: Job
    private lateinit var tcpToWs: Job

    suspend fun join() {
        wsToTcp.join()
        tcpToWs.join()
    }

    private suspend fun start() {
        wsToTcp =
            scope.launch(CoroutineName("wsToTcp")) {
                supervisorScope {
                    try {
                        logger?.info("Start coping.... $remoteName($remote)->$localName($local)}")
                        remote.copyTo(local, bufferSize = bufferSize) {
                            logger?.debug("$localName<-$remoteName $it")
                        }
                    } catch (e: SocketClosedException) {
                        logger?.info("SocketClosedException")
                        // Do nothing
                    } catch (e: CancellationException) {
                        logger?.info("CancellationException")
                        // Do nothing
                    } catch (e: WebSocketClosedException) {
                        logger?.info("WebSocketClosedException")
                        // Do nothing
                    } catch (e: Throwable) {
                        logger?.warn("Error on $localName<-$remoteName", exception = e)
                    } finally {
                        withContext(NonCancellable) {
                            println("Making close #1---wsToTcp")
                            asyncClose()
                        }
                    }
                }
            }
        tcpToWs =
            scope.launch(CoroutineName("tcpToWs")) {
                supervisorScope {
                    try {
                        logger?.info("Start coping.... $localName($local)->$remoteName($remote)")
                        local.copyTo(remote, bufferSize = bufferSize) {
                            logger?.debug("$localName->$remoteName $it")
                        }
                    } catch (e: SocketClosedException) {
                        // Do nothing
                    } catch (e: CancellationException) {
                        // Do nothing
                    } catch (e: Throwable) {
                        logger?.warn("Error on $localName->$remoteName", exception = e)
                    } finally {
                        withContext(NonCancellable) {
                            println("Making close #2---tcpToWs")
                            asyncClose()
                        }
                    }
                }
            }
    }

    override suspend fun asyncClose() {
        println("ChannelBridge:: Closing ClientConnection")
        println("ChannelBridge:: Closing #1")
        remote.asyncCloseAnyway()
        println("ChannelBridge:: Closing #2")
        local.asyncCloseAnyway()
        println("ChannelBridge:: Closing #3")
        wsToTcp.cancelAndJoin()
        println("ChannelBridge:: Closing #4")
        tcpToWs.cancelAndJoin()
        println("ChannelBridge:: Closing #5")
        println("ChannelBridge:: CLOSED!")
    }
}
