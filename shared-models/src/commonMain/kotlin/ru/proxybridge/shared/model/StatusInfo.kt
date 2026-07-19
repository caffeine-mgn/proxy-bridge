package ru.proxybridge.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class StatusInfo(
    val running: Boolean,
    val uptime: String? = null,
    val activeConnections: Int = 0,
    val version: String? = null
)
