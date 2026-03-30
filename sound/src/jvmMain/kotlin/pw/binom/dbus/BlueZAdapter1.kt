package pw.binom.dbus

import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.annotations.DBusMemberName
import org.freedesktop.dbus.types.Variant

// ============================================================================
// org.bluez.Adapter1
// ============================================================================
@DBusInterfaceName("org.bluez.Adapter1")
interface BlueZAdapter1 : DBusInterface {

    @DBusMemberName("StartDiscovery")
    fun StartDiscovery()

    @DBusMemberName("StopDiscovery")
    fun StopDiscovery()

    @DBusMemberName("RemoveDevice")
    fun RemoveDevice(device: DBusPath)

    // Properties
    val Address: String
    val AddressType: String
    val Name: String
    val Alias: String
    val Class: UInt
    val Powered: Boolean
    val Discoverable: Boolean
    val DiscoverableTimeout: UInt
    val Pairable: Boolean
    val PairableTimeout: UInt
    val Discovering: Boolean
    val UUIDs: Array<String>
}
