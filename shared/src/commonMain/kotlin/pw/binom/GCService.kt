package pw.binom

import pw.binom.atomic.AtomicBoolean
import pw.binom.strong.BeanLifeCycle
import pw.binom.thread.Thread

class GCService {
    private val closed = AtomicBoolean(false)
    private val thread = Thread {
        while (!closed.getValue()) {
            Thread.sleep(5000)
            System.gc()
        }
    }

    init {
        BeanLifeCycle.postConstruct {
            thread.start()
        }

        BeanLifeCycle.preDestroy {
            closed.setValue(true)
            thread.join()
        }
    }
}
