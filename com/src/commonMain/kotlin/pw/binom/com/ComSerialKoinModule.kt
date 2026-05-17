package pw.binom.com

import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.dsl.onClose
import pw.binom.*

fun comSerialKoinModule(serialName: Lazy<String>, baudRate: Lazy<Int> = lazyOf(115200)) = module {
    single(createdAtStart = true) {
        SerialConnectionAcceptor(
            serialName.value,
            baudRate.value
        )
    }
        .onClose { it?.close() }
        .bind(ConnectionAcceptor::class)
}
