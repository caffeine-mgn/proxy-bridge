package pw.binom

import kotlinx.coroutines.*
import pw.binom.atomic.AtomicBoolean
import pw.binom.strong.BeanLifeCycle
import pw.binom.thread.Thread
import kotlin.time.Duration.Companion.seconds

class GCService {
    private var job: Job? = null

    init {
        BeanLifeCycle.postConstruct {
            job = GlobalScope.launch {
                while (isActive) {
                    System.gc()
                    try {
                        delay(5.seconds)
                    } catch (e: CancellationException) {
                        break
                    }
                }
            }
        }

        BeanLifeCycle.preDestroy {
            job?.cancelAndJoin()
        }
    }
}
