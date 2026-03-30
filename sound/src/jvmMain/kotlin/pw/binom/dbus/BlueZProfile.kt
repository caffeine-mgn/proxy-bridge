package pw.binom.dbus

import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.annotations.DBusMemberName
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.types.UInt32
import org.freedesktop.dbus.types.Variant
import org.freedesktop.dbus.FileDescriptor

// org.bluez.Profile1 — интерфейс профиля [[63]]
@DBusInterfaceName("org.bluez.Profile1")
interface BlueZProfile : DBusInterface {
    fun Release()
    @DBusMemberName("NewConnection")
    fun NewConnection(
        device: DBusPath,
        fd: FileDescriptor,  // ← Не FileDescriptor!
        fdProperties: Map<String, Variant<*>>
    )
    fun RequestDisconnection(device: DBusPath)
}

