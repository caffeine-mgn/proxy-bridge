package pw.binom

import dev.bluefalcon.core.BlueFalcon
import dev.bluefalcon.engine.rpi.RpiEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

suspend fun main() {
    val blueFalcon = BlueFalcon {
        this.engine = RpiEngine()
    }

    val job = CoroutineScope(Dispatchers.IO).launch {
        blueFalcon.peripherals.collect { list ->
            list.forEach {
                println("-->${it.name} ${it.mtuSize} ${it.services} ${it.uuid} ${it.characteristics}")
            }
        }
    }
    blueFalcon.scan()
    delay(10.seconds)
    blueFalcon.stopScanning()
    job.cancel()
    println("FINISH!")
}
