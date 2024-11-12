package pw.binom.logging

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonObject
import pw.binom.date.DateTime
import pw.binom.date.iso8601
import pw.binom.io.http.Headers
import pw.binom.io.httpClient.HttpClient
import pw.binom.io.httpClient.addHeader
import pw.binom.io.useAsync
import pw.binom.network.NetworkManager
import pw.binom.strong.BeanLifeCycle
import pw.binom.strong.inject
import pw.binom.url.toURL

class PromTailLogSender : LogSender {
    private val httpClient by inject<HttpClient>()
    private val networkManager by inject<NetworkManager>()
    private val sendChannel = Channel<Record>()

    private val baseUrl = "http://192.168.88.58:3100/loki/api/v1/push".toURL()
    private var job: Job? = null

    private data class Record(
        val tags: Map<String, String>,
        val message: String?,
        val exception: Throwable?,
        val loggerName: String,
    )

    init {
        BeanLifeCycle.postConstruct {
            job = GlobalScope.launch(networkManager) {
                while (isActive) {
                    try {
                        val r = sendChannel.receive()
                        var url = baseUrl
                        val tags = r.tags
                        val sb = StringBuilder()
                        if (r.message != null) {
                            sb.append(r.message)
                        }
                        if (r.exception != null) {
                            if (r.message != null) {
                                sb.append("\n")
                            }
                            sb.append(r.exception.stackTraceToString())
                        }

                        val now = DateTime.now
                        val nanoSeconds = now.milliseconds * 1000000

                        val el = buildJsonObject {
                            putJsonObject("stream") {
                                tags.forEach { (key, value) ->
                                    put(key, JsonPrimitive(value))
                                }
                            }
                            put("entries", buildJsonArray {
                                add(buildJsonObject {
                                    put("timestamp", JsonPrimitive(now.iso8601()))
                                    put("line", JsonPrimitive(sb.toString()))
                                    tags.forEach { (key, value) ->
                                        put(key, JsonPrimitive(value))
                                    }
                                })
                                add(buildJsonObject {
                                    tags.forEach { (key, value) ->
                                        put(key, JsonPrimitive(value))
                                    }
                                })
                            })
                            put("values", buildJsonArray {
                                add(buildJsonArray {
                                    add(JsonPrimitive(nanoSeconds.toString()))
                                    add(JsonPrimitive(sb.toString()))
                                    add(buildJsonObject {
                                        tags.forEach { (key, value) ->
                                            put(key, JsonPrimitive(value))
                                        }
                                    })
                                })
                            })
                        }
                        val el2 = buildJsonObject {
                            put("streams", buildJsonArray {
                                add(el)
                            })
                        }
                        val json = Json.encodeToString(JsonElement.serializer(), el2)
                        httpClient.connect(method = "POST", uri = url).useAsync {
                            it.addHeader(Headers.CONTENT_TYPE, "application/json")
                            it.send(json)
                            it.getResponse().asyncClose()
                        }
                    } catch (e: Throwable) {
                        println("Error on logging: $e\n${e.stackTraceToString()}")
                    }
                }
            }
        }
        BeanLifeCycle.preDestroy {
            job?.cancelAndJoin()
        }
    }

    override fun send(
        tags: Map<String, String>,
        message: String?,
        exception: Throwable?,
        loggerName: String,
    ) {
        sendChannel.trySend(
            Record(
                tags = tags,
                message = message,
                exception = exception,
                loggerName = loggerName,
            )
        )
    }
}
