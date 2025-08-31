package pw.binom.transport

import javax.bluetooth.ServiceRecord

val ServiceRecord.type
    get() = getAttributeValue(256)?.value as String?

val ServiceRecord.url: String?
    get() = getConnectionURL(ServiceRecord.NOAUTHENTICATE_NOENCRYPT, false)
