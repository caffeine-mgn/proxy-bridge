package pw.binom.properties

import kotlinx.serialization.Serializable
import pw.binom.properties.serialization.annotations.PropertiesPrefix

@PropertiesPrefix("file")
@Serializable
class FileSystemProperties(
    val root: String = ""
)
