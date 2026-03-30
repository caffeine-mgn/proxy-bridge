package pw.binom.dbus

import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.annotations.DBusMemberName
import org.freedesktop.dbus.types.Variant

// ============================================================================
// org.bluez.Device1
// ============================================================================
@DBusInterfaceName("org.bluez.Device1")
interface BlueZDevice1 : DBusInterface {

    @DBusMemberName("Connect")
    fun Connect()

    @DBusMemberName("Disconnect")
    fun Disconnect()

    @DBusMemberName("ConnectProfile")
    fun ConnectProfile(uuid: String)

    @DBusMemberName("DisconnectProfile")
    fun DisconnectProfile(uuid: String)

    @DBusMemberName("Pair")
    fun Pair()

    @DBusMemberName("CancelPairing")
    fun CancelPairing()

    // Properties
    val Address: String
    val AddressType: String
    val Name: String
    val Alias: String
    val Class: UInt
    val Icon: String
    val Paired: Boolean
    val Bonded: Boolean
    val Trusted: Boolean
    val Blocked: Boolean
    val Connected: Boolean
    val UUIDs: Array<String>
    val Adapter: DBusPath
    val ManufacturerData: Map<UInt, ByteArray>
    val ServiceData: Map<String, ByteArray>
    val TxPower: Short
    val RSSI: Short
}
