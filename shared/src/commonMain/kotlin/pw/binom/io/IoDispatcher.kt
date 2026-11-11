package pw.binom.io

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Диспетчер для блокирующих операций (сокеты, serial, файлы).
 *
 * В common-коде `Dispatchers.IO` недоступен (на Kotlin/Native он internal),
 * поэтому JVM использует настоящий IO-пул, а нативные таргеты — эластичный
 * Default.
 */
expect val ioDispatcher: CoroutineDispatcher
