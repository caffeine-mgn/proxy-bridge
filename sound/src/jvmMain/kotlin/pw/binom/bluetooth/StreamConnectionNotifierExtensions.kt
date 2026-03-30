package pw.binom.bluetooth

import pw.binom.utils.executeCoroutineInThread
import javax.microedition.io.StreamConnectionNotifier

suspend fun StreamConnectionNotifier.asyncAcceptAndOpen() =
    executeCoroutineInThread {
        acceptAndOpen()
    }
