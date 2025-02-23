package pw.binom

import javax.bluetooth.DeviceClass
import javax.bluetooth.DiscoveryListener
import javax.bluetooth.RemoteDevice
import javax.bluetooth.ServiceRecord

interface DiscoveryListener2: DiscoveryListener {
    override fun deviceDiscovered(btDevice: RemoteDevice?, cod: DeviceClass?) {
    }

    override fun servicesDiscovered(transID: Int, servRecord: Array<out ServiceRecord>?) {
    }

    override fun serviceSearchCompleted(transID: Int, respCode: Int) {
    }

    override fun inquiryCompleted(discType: Int) {
    }
}
