package pw.binom.transport

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import pw.binom.io.AsyncInput
import pw.binom.io.AsyncOutput
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

object Manager {
    fun create(
        input: AsyncInput,
        output: AsyncOutput,
        maxPackageSize: Int,
        isServer: Boolean,
        scope: CoroutineScope = GlobalScope,
        context: CoroutineContext = EmptyCoroutineContext,
    ): VirtualManagerImpl {
        val multiplexer =
            Multiplexer(
                input = input,
                output = output,
                serverMode = isServer,
                maxPackageSize = maxPackageSize,
            )
        return VirtualManagerImpl(
            multiplexer = multiplexer,
            scope = scope,
            context = context,
        )
    }
}
