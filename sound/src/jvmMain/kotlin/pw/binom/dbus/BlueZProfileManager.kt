package pw.binom.dbus

import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.types.Variant

// org.bluez.ProfileManager1 — менеджер профилей [[35]]
@DBusInterfaceName("org.bluez.ProfileManager1")
interface BlueZProfileManager : DBusInterface {
    fun RegisterProfile(profile: DBusPath, uuid: String, options: Map<String, Variant<*>>)
    fun UnregisterProfile(profile: DBusPath)
}
