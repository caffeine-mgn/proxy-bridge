package ru.proxybridge.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class LogEntry(
    val level: String,
    val message: String,
    val timestamp: String,
    val source: String? = null
)
