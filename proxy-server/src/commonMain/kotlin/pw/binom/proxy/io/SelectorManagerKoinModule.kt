package pw.binom.proxy.io

import pw.binom.io.ioDispatcher
import io.ktor.network.selector.SelectorManager
import kotlinx.coroutines.Dispatchers
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.dsl.onClose

val SelectorManagerKoinModule = module {
    single {
        SelectorManager(ioDispatcher)
    } onClose {
        it?.close()
    } bind SelectorManager::class
}
