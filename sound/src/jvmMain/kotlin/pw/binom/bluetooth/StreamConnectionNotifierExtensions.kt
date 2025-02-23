package pw.binom.bluetooth

import pw.binom.executeCoroutineInThread
import javax.microedition.io.StreamConnectionNotifier

suspend fun StreamConnectionNotifier.asyncAcceptAndOpen() =
    executeCoroutineInThread {
        acceptAndOpen()
    }
