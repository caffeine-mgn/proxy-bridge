package pw.binom.dbus

import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.annotations.DBusMemberName
import org.freedesktop.dbus.types.Variant

// ============================================================================
// org.freedesktop.DBus.ObjectManager
// ============================================================================
@DBusInterfaceName("org.freedesktop.DBus.ObjectManager")
interface DBusObjectManager : DBusInterface {

    @DBusMemberName("GetManagedObjects")
    fun GetManagedObjects(): Map<DBusPath, Map<String, Map<String, Variant<*>>>>
}
