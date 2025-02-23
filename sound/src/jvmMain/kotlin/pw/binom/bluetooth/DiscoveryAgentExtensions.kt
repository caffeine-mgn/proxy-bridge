package pw.binom.bluetooth

import kotlinx.coroutines.suspendCancellableCoroutine
import javax.bluetooth.*
import javax.microedition.io.Connector
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

suspend fun DiscoveryAgent.asyncSearchServices(attrSet: IntArray, uuidSet: Array<UUID>, device: RemoteDevice) =
    suspendCancellableCoroutine<List<out ServiceRecord>> { con ->
        val tx = searchServices(attrSet, uuidSet, device, object : DiscoveryListener {
            override fun deviceDiscovered(btDevice: RemoteDevice?, cod: DeviceClass?) {
            }

            override fun servicesDiscovered(transID: Int, servRecord: Array<out ServiceRecord>?) {
                con.resume(servRecord?.toList() ?: emptyList())
            }

            override fun serviceSearchCompleted(transID: Int, respCode: Int) {
            }

            override fun inquiryCompleted(discType: Int) {
            }
        })
        con.invokeOnCancellation {
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
