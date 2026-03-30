package pw.binom.gateway

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.prepareQuery
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.request
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.cio.encodeChunked
import io.ktor.http.content.OutgoingContent
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.resourceClasspathResource
import io.ktor.server.request.httpMethod
import io.ktor.server.request.uri
import io.ktor.server.response.header
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.response.respondText
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.copyTo
import io.ktor.utils.io.readLineStrict
import io.ktor.utils.io.reader
import io.ktor.utils.io.writeStringUtf8
import io.ktor.utils.io.writer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.IOException
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.exceptions.DBusException
import org.freedesktop.dbus.types.Variant
import pw.binom.*
import pw.binom.atomic.AtomicBoolean
import pw.binom.config.BluetoothConfig
import pw.binom.config.CommandsConfig
import pw.binom.config.FileSystemConfig
import pw.binom.config.LoggerConfig
import pw.binom.dbus.BlueZProfileManager
import pw.binom.dbus.SPP_UUID
import pw.binom.dbus.SppProfileHandler
import pw.binom.frame.PackageSize
import pw.binom.io.ByteBufferFactory
import pw.binom.io.http.BasicAuth
import pw.binom.io.http.BearerAuth
import pw.binom.io.httpClient.HttpClient
import pw.binom.io.httpClient.HttpProxyConfig
import pw.binom.io.httpClient.create
import pw.binom.io.use
import pw.binom.network.*
import pw.binom.pool.GenericObjectPool
import pw.binom.gateway.properties.GatewayRuntimeProperties
import pw.binom.gateway.services.OneConnectService
import pw.binom.gateway.services.TcpConnectionFactoryImpl
import pw.binom.http.ConnectionEstablished
import pw.binom.http.HttpProxy
import pw.binom.io.file.*
import pw.binom.logger.Logger
import pw.binom.logger.WARNING
import pw.binom.logging.PromTailLogSender
import pw.binom.logging.LoggerSenderHandler
import pw.binom.multiplexer.DuplexChannel
import pw.binom.multiplexer.Multiplexer
import pw.binom.properties.*
import pw.binom.properties.serialization.PropertiesDecoder
import pw.binom.services.ClientService
import pw.binom.services.VirtualChannelServiceImpl2
import pw.binom.services.VirtualChannelServiceIncomeService
import pw.binom.signal.Signal
import pw.binom.strong.*
import pw.binom.thread.Thread
import java.util.concurrent.TimeUnit
import kotlin.jvm.java
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.use

suspend fun startProxyClient(
    properties: GatewayRuntimeProperties,
    networkManager: NetworkManager,
    loggerProperties: LoggerProperties,
    pingProperties: PingProperties,
    fileConfig: FileSystemProperties,
    bluetooth: BluetoothProperties,
): Strong {
    val baseConfig =
        Strong.config {
            it.bean { networkManager }
            it.bean {
                LocalFileSystem(
                    root = Environment.workDirectoryFile,
                    byteBufferPool = GenericObjectPool(factory = ByteBufferFactory(DEFAULT_BUFFER_SIZE))
                )
            }
            it.bean {
                val proxyConfig =
                    properties.proxy?.let { proxyConfig ->
                        HttpProxyConfig(
                            address = proxyConfig.address,
                            auth =
                                proxyConfig.auth?.let { auth ->
                                    when {
                                        auth.basicAuth != null ->
                                            BasicAuth(
                                                login = auth.basicAuth.user,
                                                password = auth.basicAuth.password
                                            )

                                        auth.bearerAuth != null -> BearerAuth(token = auth.bearerAuth.token)
                                        else -> null
                                    }
                                }
                        )
                    }
                val nm = inject<NetworkManager>()
                HttpClient.create(networkDispatcher = nm.asInstance(), proxy = proxyConfig)
            }
//            it.bean { ChannelService() }
            it.bean { TcpConnectionFactoryImpl() }
            it.bean { properties }
            it.bean { pingProperties }
            it.bean { loggerProperties }
            it.bean { GCService() }
            it.bean { VirtualChannelServiceIncomeService() }
//            it.bean { ProxyClientService() }
            it.bean { BinomMetrics }
            it.bean { ClientService() }
            it.bean { LocalEventSystem() }
//            it.bean { GatewayControlService() }
//            it.bean { TcpCommunicatePair() }
            if (properties.enableWebSocket) {
                it.bean { OneConnectService() }
            }
//            it.bean { CommunicateRepository() }
//            it.bean { VirtualChannelServiceImpl(PackageSize(properties.bufferSize)) }
            it.bean {
                VirtualChannelServiceImpl2(
                    bufferSize = PackageSize(properties.bufferSize),
                    serverMode = false,
                    networkManager = inject()
                )
            }
            val pool = ByteBufferPool(size = DEFAULT_BUFFER_SIZE)
            it.bean { pool }
            if (loggerProperties.promtail != null) {
                it.bean { PromTailLogSender() }
            }
            if (loggerProperties.isCustomLogger) {
                it.bean { LoggerSenderHandler(tags = mapOf("app" to "proxy-client")) }
            }
            it.bean(name = "LOCAL_FS") { LocalFileSystem(root = File("/"), byteBufferPool = pool) }
        }
    return Strong.create(
        baseConfig,
        CommandsConfig(),
        LoggerConfig(loggerProperties),
        FileSystemConfig(fileConfig),
        BluetoothConfig(bluetooth),
    )
}

val closed = AtomicBoolean(false)

suspend fun handleConnectMethod(call: ApplicationCall) {
    val host = call.request.local.remoteHost
    val port = (call.request.headers["Host"]?.substringAfter(":", "443") ?: "443").toInt()

    println("🔒 CONNECT to $host:$port")

    // Устанавливаем туннель
    call.response.status(HttpStatusCode.SwitchingProtocols)
    call.response.header("Proxy-Connection", "keep-alive")
    call.response.header("Connection", "keep-alive")

    // Для полноценной поддержки HTTPS нужно работать с сырыми сокетами
    // Это упрощённая версия - в продакшене нужна более сложная логика
    val input = call.request.receiveChannel()
    val output = call.respondBytesWriter {
    }

    TODO()
}

suspend fun connectProcessing(
    socketInput: ByteReadChannel,
    socketOutput: ByteWriteChannel,
) {

}


fun main(args: Array<String>) {
    println("START HTTP SERVER ON PORT 8088")
    runBlocking {
        val client = io.ktor.client.HttpClient(io.ktor.client.engine.cio.CIO)
        SelectorManager(Dispatchers.IO).use { selector ->
            HttpProxy(
                port = 8088,
                selector = selector,
                onConnect = onConnect@{ host, port, context ->
                    val remoteSocket = try {
                        withTimeoutOrNull(5.seconds) {
                            aSocket(selector).tcp().connect(hostname = host, port = port)
                        }
                    } catch (e: IOException) {
                        context.notAvailable()
                        return@onConnect
                    } catch (e: Throwable) {
                        context.ioError()
                        return@onConnect
                    }
                    if (remoteSocket == null) {
                        context.timeout()
                        return@onConnect
                    }
                    val (read, write) = context.ok()
                    remoteSocket.use { socket ->
                        val j1 = launch {
                            remoteSocket.openReadChannel().copyTo(write)
                        }

                        val j2 = launch {
                            read.copyTo(remoteSocket.openWriteChannel())
                        }
                        j1.join()
                        j2.join()
                    }
                },
                onHttp = { url, method, headers, context ->
                    val input = context.readRequest()
                    val bodyContent = object : OutgoingContent.ReadChannelContent() {
                        override val contentType: ContentType? = headers["Content-Type"]?.let { ContentType.parse(it) }
                        override val contentLength: Long? = headers["Content-Length"]?.toLongOrNull()
                        override fun readFrom(): ByteReadChannel = input
                    }
                    val hasBody = headers["Content-Length"] != null || headers["Transfer-Encoding"] != null

                    val m = client.request {
                        this.method = method
                        this.url(url)
                        if (hasBody) {
                            this.setBody(bodyContent)
                        }
                        headers.forEach { key, values ->
                            values.forEach { value ->
                                this.header(key, value)
                            }
                        }
                    }
                    val responseBody = context.sendResponse(m.status, m.headers)
                    m.bodyAsChannel().copyTo(responseBody)
                }
            ).join()
        }
    }
    return
    runBlocking {
        SelectorManager(Dispatchers.IO).use { selector ->
            val server = aSocket(selector).tcp()
                .bind(hostname = "0.0.0.0", port = 8088)
            while (isActive) {
                val client = server.accept()
                launch {
                    client.use {
                        val read = client.openReadChannel()
                        val write = client.openWriteChannel(autoFlush = false)
                        val request = read.readLineStrict() ?: return@use

                        val headers = mutableMapOf<String, String>()
                        while (true) {
                            val line = read.readLineStrict() ?: break
                            if (line.isEmpty()) break
                            val parts = line.split(": ", limit = 2)
                            if (parts.size == 2) headers[parts[0]] = parts[1]
                        }

                        val items = request.split(" ")
                        val method = items[0]
                        val path = items[1]

                        if (method == "CONNECT") {
                            val hostItems = path.split(':', limit = 2)
                            val host = hostItems[0]
                            val port = hostItems.getOrNull(1)?.toIntOrNull() ?: 443
                            try {
                                val remoteSocket = aSocket(selector).tcp().connect(hostname = host, port = port)
                                write.writeStringUtf8("HTTP/1.1 ${HttpStatusCode.ConnectionEstablished.value} ${HttpStatusCode.ConnectionEstablished.description}\r\n")
                                write.writeStringUtf8("Content-Length: 0\r\n")
                                write.writeStringUtf8("Proxy-Connection: keep-alive\r\n")
                                write.writeStringUtf8("\r\n")
                                write.flush()
                                remoteSocket.use { socket ->
                                    val j1 = launch {
                                        remoteSocket.openReadChannel().copyTo(write)
                                    }

                                    val j2 = launch {
                                        read.copyTo(remoteSocket.openWriteChannel())
                                    }

                                    j1.join()
                                    j2.join()
                                }
                            } catch (e: IOException) {
                                write.writeStringUtf8("HTTP/1.1 ${HttpStatusCode.ServiceUnavailable.value} ${HttpStatusCode.ServiceUnavailable.description}\r\n")
                                write.writeStringUtf8("Content-Length: 0\r\n")
                                write.writeStringUtf8("\r\n")
                                write.flush()
                                e.printStackTrace()
                            } catch (e: Throwable) {
                                write.writeStringUtf8("HTTP/1.1 ${HttpStatusCode.BadGateway.value} ${HttpStatusCode.BadGateway.description}\r\n")
                                write.writeStringUtf8("Content-Length: 0\r\n")
                                write.writeStringUtf8("\r\n")
                                write.flush()
                                e.printStackTrace()
                            }
                        }
                        println("request: $request")
                        println("Headers: $headers")
                    }
                }
            }
        }
    }
    return
    embeddedServer(CIO, host = "0.0.0.0", port = 8088) {
        intercept(ApplicationCallPipeline.Call) {
            println("REQUEST! ${call.request.httpMethod} \"${call.request.uri}\"")
            println("Headers: ${call.request.headers.entries()}")

            if (call.request.httpMethod.value == "CONNECT") {
                val items = call.request.uri.split(":", limit = 2)
                val host = items[0]
                val port = items.getOrNull(1)?.toInt() ?: 443
                call.response.status(HttpStatusCode.SwitchingProtocols)
                println("Try connect to $host:$port")
                val socket = aSocket(SelectorManager(Dispatchers.IO)).tcp().connect(hostname = host, port = port)
                println("Connected!")
                socket.use { socket ->
                    println("#1")
                    val j1 = CoroutineScope(Dispatchers.IO).launch {
                        socket.writer {
                            println("Coping socket->http")
                            call.request.receiveChannel().copyTo(this.channel)
                        }
                    }
                    println("#2")
                    val j2 = CoroutineScope(Dispatchers.IO).launch(Dispatchers.IO) {
                        socket.reader {
                            println("Coping http->socket #1")
                            call.respondBytesWriter {
                                println("Coping http->socket #2")
                                channel.copyTo(this)
                            }
                        }
                    }
                    println("#3")
                    j1.join()
                    j2.join()
                }
            } else {
                call.respondText(text = "OK", status = HttpStatusCode.Conflict)
            }

        }
    }.start(wait = true)
}

suspend fun main3(args: Array<String>) {
    withContext(Dispatchers.IO) {
        val adapter = pw.binom.bluetooth.BluetoothAdapter.getAdapters().first()
        adapter.listenSPP().use { server ->
            while (coroutineContext.isActive) {
                println("Wait a client")
                val newClient = server.accept()
                println("Client connected!")
                launch {
                    Multiplexer(
                        channel = newClient,
                        idOdd = true,
                        ioCoroutineScope = CoroutineScope(Dispatchers.IO)
                    ).use { multiplexer ->
                        while (isActive) {
                            val newClient = multiplexer.accept()
                            CoroutineScope(Dispatchers.IO).launch {
                                clientProcessing(newClient)
                            }
                        }
                    }
                    clientProcessing(newClient)
                }
                println("Client connected")
            }
        }
//        BluetoothServer().use { server ->
//            while (coroutineContext.isActive) {
//                println("Wait a client")
//                val newClient = server.accept()
//                println("Client connected!")
//                launch {
//                    clientProcessing(newClient)
//                }
//                println("Client connected")
//            }
//        }
    }
}

suspend fun clientProcessing(connection: DuplexChannel) {
    TODO()
//    connection.use {
//        ThroughputTest.server(channel = it, ack = true)
//    }
}

suspend fun main1(args: Array<String>) {
    try {
        println("🚀 Starting SPP/RFCOMM service registration...")

        // 1. Подключение к system bus (BlueZ работает там)
        val connection: DBusConnection = DBusConnectionBuilder
            .forSystemBus()
            .build()

        // 2. Запрос уникального имени на шине
//        connection.requestBusName(BUS_NAME)
//        println("✅ Bus name requested: $BUS_NAME")

        // 3. Экспорт объекта профиля
        val handler = SppProfileHandler()
        connection.exportObject(handler.getObjectPath(), handler)
        println("✅ Profile exported at: ${handler.getObjectPath()}")

        // 4. Получение ссылки на ProfileManager1
        val profileManager = connection.getRemoteObject(
            "org.bluez",
            "/org/bluez",
            BlueZProfileManager::class.java
        )
        val serviceName = "SPPServer"
        // 5. Опции регистрации (RFCOMM channel, аутентификация и т.д.) [[35]]
        val options = mapOf<String, Variant<*>>(
            "Name" to Variant(serviceName),
            "Channel" to Variant(22u.toUShort().toShort()), // RFCOMM channel 22
            "RequireAuthentication" to Variant(false),
            "RequireAuthorization" to Variant(false),
            "AutoConnect" to Variant(true)
        )

        // 6. Регистрация профиля в BlueZ
        profileManager.RegisterProfile(
            DBusPath(handler.getObjectPath()),
            SPP_UUID,
            options
        )
        println("✅ SPP service registered with UUID: $SPP_UUID")
        println("   RFCOMM channel: 22")
        println("   🔍 Теперь устройство видно для подключения")

        // 7. Sleep — сервис работает
        println("⏳ Service running... (sleep 30 seconds)")
        try {
            TimeUnit.SECONDS.sleep(120)
        } finally {
            // 8. Удаление профиля
            println("🗑️  Unregistering profile...")
            profileManager.UnregisterProfile(DBusPath(handler.getObjectPath()))
            println("✅ Profile unregistered")

            // 9. Очистка
            connection.unExportObject(handler.getObjectPath())
            connection.close()
            println("👋 Connection closed. Done.")
        }


    } catch (e: DBusException) {
        java.lang.System.err.println("❌ D-Bus error: ${e.message}")
        e.printStackTrace()
    } catch (e: InterruptedException) {
        java.lang.System.err.println("⚠️  Interrupted")
    } catch (e: Exception) {
        java.lang.System.err.println("❌ Unexpected error: ${e.message}")
        e.printStackTrace()
    } finally {

    }
}

fun main2(args: Array<String>) {
    Logger.getLogger("Strong.Starter").level = Logger.WARNING
//    Thread {
//        Thread.sleep(7.hours)
//        println("Goodbay. time to die")
//        InternalLog.info(file = "main") { "Goodbay. time to die" }
//        closed.setValue(true)
//        exitProcess(0)
//    }.start()
    Thread {
        while (!closed.getValue()) {
            Thread.sleep(5.minutes)
            InternalLog.info(file = "main") { "Making GC" }
            System.gc()
        }
    }.start()
//    val date = "yyyy-MM-dd-HH-mm".toDatePattern().toString(DateTime.now, DateTime.systemZoneOffset)
//    InternalLog.default = GLog(File(Environment.currentExecutionPath).parent!!.relative("$date.glog"))
//    InternalLog.default = SysLogger
    val argMap = HashMap<String, String?>()
    (args
        .filter { it.startsWith("-D") }
        .map { it.removePrefix("-D") }
            +
            (Environment.workDirectoryFile.relative("config.ini")
                .takeIfFile()
                ?.readText()
                ?.lines() ?: emptyList())
            )
        .filter { it.isNotBlank() }
        .filter { !it.startsWith("#") }
        .filter { !it.startsWith(";") }
        .forEach {
            val items = it.split('=', limit = 2)
            val key = items[0]
            val value = items[1]
            argMap[key] = value
        }
    val configRoot = IniParser.parseMap(argMap)
    val properties = PropertiesDecoder.parse(
        GatewayRuntimeProperties.serializer(),
        configRoot,
    )
    val loggerProperties = PropertiesDecoder.parse(LoggerProperties.serializer(), configRoot)
    val pingProperties = PropertiesDecoder.parse(PingProperties.serializer(), configRoot)
    val fileConfig = PropertiesDecoder.parse(FileSystemProperties.serializer(), configRoot)
    val bluetoothProperties = PropertiesDecoder.parse(BluetoothProperties.serializer(), configRoot)
    runBlocking {
        MultiFixedSizeThreadNetworkDispatcher(Environment.availableProcessors).use { networkManager ->
            val strong = startProxyClient(
                properties = properties,
                networkManager = networkManager,
                loggerProperties = loggerProperties,
                pingProperties = pingProperties,
                fileConfig = fileConfig,
                bluetooth = bluetoothProperties,
            )
            val mainCoroutineContext = coroutineContext
            Signal.handler {
                if (it.isInterrupted) {
                    if (!strong.isDestroying && !strong.isDestroyed) {
                        GlobalScope.launch(mainCoroutineContext) {
                            println("destroying...")
                            strong.destroy()
                            println("destroyed!!!")
                        }
                    }
                }
            }
            strong.awaitDestroy()
            println("Main finished!")
        }
    }
}
