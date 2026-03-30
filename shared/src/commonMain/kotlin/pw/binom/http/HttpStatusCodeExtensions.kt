package pw.binom.http

import io.ktor.http.HttpStatusCode

private val privateConnectionEstablished = HttpStatusCode(200, "Connection Established")
val HttpStatusCode.Companion.ConnectionEstablished
    get() = privateConnectionEstablished
