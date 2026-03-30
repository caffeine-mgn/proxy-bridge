package pw.binom.dbus

import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.annotations.DBusMemberName
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.errors.UnknownObject
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.types.Variant

@DBusInterfaceName("org.freedesktop.DBus.Properties")
interface DBusProperties : DBusInterface {

    @DBusMemberName("Get")
    fun <T> Get(interfaceName: String, propertyName: String): Variant<T>

    @DBusMemberName("Set")
    fun <T> Set(interfaceName: String, propertyName: String, value: Variant<T>)

    @DBusMemberName("GetAll")
    fun GetAll(interfaceName: String): Map<String, Variant<*>>
}

data class AdapterInfo(
    val path: String,
    val address: String,
    val name: String
)


fun getAvailableAdapters(connection: DBusConnection): List<AdapterInfo> {
    val adapters = mutableListOf<AdapterInfo>()

    // BlueZ обычно создаёт адаптеры по пути /org/bluez/hciX
    for (i in 0..Int.MAX_VALUE) {
        val adapterPath = "/org/bluez/hci$i"
        try {
            // Проверяем, существует ли объект через Properties
            val props = connection.getRemoteObject(
                "org.bluez",
                adapterPath,
                DBusProperties::class.java
            )

            // Пытаемся прочитать свойство "Address" — если есть, это адаптер
            val addressVariant = props.Get<String>("org.bluez.Adapter1", "Address")
            val address = addressVariant.value as String
            val nameVariant = props.Get<String>("org.bluez.Adapter1", "Name")
            val name = nameVariant.value as String

            adapters.add(AdapterInfo(adapterPath, address, name))
            println("✅ Found adapter: $name ($address) at $adapterPath")

        } catch (e: UnknownObject) {
            break
            // Объект не существует или нет доступа — пропускаем
        }
    }

    return adapters
}
