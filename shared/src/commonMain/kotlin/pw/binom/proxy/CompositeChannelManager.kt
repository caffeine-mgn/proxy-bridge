package pw.binom.proxy

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import pw.binom.concurrency.SpinLock
import pw.binom.concurrency.synchronize
import pw.binom.io.AsyncChannel
import pw.binom.io.AsyncInput
import pw.binom.io.AsyncOutput
import pw.binom.logger.Logger
import pw.binom.logger.info
import pw.binom.logger.infoSync
import kotlin.coroutines.resume

class CompositeChannelManager<T> {
    private val logger by Logger.ofThisOrGlobal

    suspend fun useChannel(id: T, channel: AsyncChannel) {
        logger.info("Channel $id connected: $channel")
        suspendCancellableCoroutine {
            channelsLock.synchronize {
                channels[id] = it to channel
            }
            val water = channelLock.synchronize { channelWater[id] }
            if (water == null) {
                logger.infoSync("Channel water for $id not found")
            } else {
                logger.infoSync("Channel water for $id found!")
                water.useChannel(channel)
            }
        }
    }

    suspend fun useInput(id: T, input: AsyncInput) {
        logger.info("Input connected: $input")
        suspendCancellableCoroutine {
            inputLock.synchronize {
                inputs[id] = it to input
            }
            channelLock.synchronize { channelWater[id] }?.useInput(input)
        }
    }

    suspend fun useOutput(id: T, output: AsyncOutput) {
        logger.info("Output connected: $output")
        suspendCancellableCoroutine {
            outputLock.synchronize {
                outputs[id] = it to output
            }
            channelLock.synchronize { channelWater[id] }?.useOutput(output)
        }
    }

    private val channels = HashMap<T, Pair<CancellableContinuation<Unit>, AsyncChannel>>()
    private val inputs = HashMap<T, Pair<CancellableContinuation<Unit>, AsyncInput>>()
    private val outputs = HashMap<T, Pair<CancellableContinuation<Unit>, AsyncOutput>>()

    private var inputLock = SpinLock()
    private var channelsLock = SpinLock()
    private var outputLock = SpinLock()
    private var channelLock = SpinLock()

    private val channelWater = HashMap<T, Record>()

    private inner class Record(val id: T, val water: CancellableContinuation<AsyncChannel>) {
        var input: AsyncInput? = null
        var output: AsyncOutput? = null
        fun useChannel(channel: AsyncChannel) {
            channelLock.synchronize { channelWater.remove(id) }
            water.resume(channel)
        }

        fun useInput(input: AsyncInput) {
            this.input = input
            checkResume()
        }

        fun useOutput(output: AsyncOutput) {
            this.output = output
            checkResume()
        }

        private fun checkResume() {
            val input = input
            val output = output
            if (input != null && output != null) {
                val inputWater = inputLock.synchronize { inputs.remove(id) }?.first
                val outputWater = outputLock.synchronize { outputs.remove(id) }?.first
                val channel = AsyncChannel.create(
                    input = input,
                    output = output,
                ) {
                    inputWater?.resume(Unit)
                    outputWater?.resume(Unit)
                }

                channelLock.synchronize { channelWater.remove(id) }
                water.resume(channel)
            }
        }
    }

    suspend fun getChannel(id: T) = suspendCancellableCoroutine {
        val r = Record(id = id, water = it)
        channelLock.synchronize { channelWater[id] = r }
        inputLock.synchronize { inputs.remove(id) }?.second?.let { r.useInput(it) }
        outputLock.synchronize { outputs.remove(id) }?.second?.let { r.useOutput(it) }
        channelsLock.synchronize { channels.remove(id) }?.second?.let { r.useChannel(it) }
    }
}
