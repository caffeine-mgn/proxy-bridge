package ru.proxybridge.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class ConnectionInfo(
    val id: String,
    val name: String,
    val type: String,
    val status: String,
    val createdAt: String
)
