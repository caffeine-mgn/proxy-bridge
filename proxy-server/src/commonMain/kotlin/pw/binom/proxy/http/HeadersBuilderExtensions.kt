package pw.binom.proxy.http

import io.ktor.http.HeadersBuilder

fun HeadersBuilder.contentLength(value: Long) {
    append("Content-Length", value.toString())
}
