package pw.binom.bluetooth

import io.klogging.logger
import io.klogging.noCoLogger
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.bluetooth.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private val logger = noCoLogger("DiscoveryAgentExtensions")
suspend fun DiscoveryAgent.asyncSearchServices(
    attrSet: IntArray,
    uuidSet: Array<UUID>,
    device: RemoteDevice
) =
    suspendCancellableCoroutine<List<out ServiceRecord>> { con ->
        println("Start searching service on device ${device.bluetoothAddress}")
        val tx = searchServices(attrSet, uuidSet, device, object : DiscoveryListener {
            var resumed = false
            override fun deviceDiscovered(btDevice: RemoteDevice?, cod: DeviceClass?) {
                println("DiscoveryAgent-->deviceDiscovered #1")
            }

            override fun servicesDiscovered(transID: Int, servRecord: Array<out ServiceRecord>?) {
                println("DiscoveryAgent-->servicesDiscovered #2")
                if (!resumed) {
                    resumed = true
                    con.resume(servRecord?.toList() ?: emptyList())
                }
            }

            override fun serviceSearchCompleted(transID: Int, respCode: Int) {
                println("DiscoveryAgent-->serviceSearchCompleted #3 transID=$transID, respCode=$respCode")
                if (!resumed) {
                    resumed = true
                    con.resume(emptyList())
                }
            }

            override fun inquiryCompleted(discType: Int) {
                println("DiscoveryAgent-->inquiryCompleted #4")
            }


        })
        con.invokeOnCancellation {
            println("DiscoveryAgent-->cancelServiceSearch")
            cancelServiceSearch(tx)
        }
    }

suspend fun DiscoveryAgent.asyncDiscover(): Collection<RemoteDevice> =
    suspendCancellableCoroutine { con ->
        val listener = object : DiscoveryListener {
            val result = HashSet<RemoteDevice>()
            override fun deviceDiscovered(btDevice: RemoteDevice?, cod: DeviceClass?) {
                btDevice ?: return
                result += btDevice
            }

            override fun servicesDiscovered(transID: Int, servRecord: Array<out ServiceRecord>?) {
            }

            override fun serviceSearchCompleted(transID: Int, respCode: Int) {
            }

            override fun inquiryCompleted(discType: Int) {
                con.resume(result)
            }
        }
        val started = startInquiry(DiscoveryAgent.GIAC, listener)
        if (!started) {
            con.resumeWithException(IllegalStateException("Can't start discover"))
        }
        con.invokeOnCancellation {
            cancelInquiry(listener)
        }
    }
