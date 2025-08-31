package pw.binom.transport

import kotlinx.coroutines.suspendCancellableCoroutine
import javax.bluetooth.DeviceClass
import javax.bluetooth.DiscoveryAgent
import javax.bluetooth.DiscoveryListener
import javax.bluetooth.ServiceRecord
import javax.bluetooth.UUID
import kotlin.coroutines.resume

suspend fun DiscoveryAgent.asyncSearchServices(attrSet: IntArray, uuidSet: Array<UUID>, device: javax.bluetooth.RemoteDevice) =
    suspendCancellableCoroutine<List<ServiceRecord>> { con ->
        val tx = searchServices(attrSet, uuidSet, device, object : DiscoveryListener {
            override fun deviceDiscovered(btDevice: javax.bluetooth.RemoteDevice?, cod: DeviceClass?) {
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
