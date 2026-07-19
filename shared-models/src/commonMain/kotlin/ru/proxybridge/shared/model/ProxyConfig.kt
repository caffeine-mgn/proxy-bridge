package ru.proxybridge.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class ProxyConfig(
    val name: String? = null,
    val transportType: String? = null,
    val port: Int? = null,
    val targetHost: String? = null,
    val targetPort: Int? = null,
    val bleDeviceName: String? = null,
    val bleServiceUuid: String? = null
)
