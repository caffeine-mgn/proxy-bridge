package pw.binom.io

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * На Kotlin/Native публичного `Dispatchers.IO` нет; Default (эластичный пул
 * воркеров) подходит для блокирующих вызовов не хуже.
 */
actual val ioDispatcher: CoroutineDispatcher = Dispatchers.Default
