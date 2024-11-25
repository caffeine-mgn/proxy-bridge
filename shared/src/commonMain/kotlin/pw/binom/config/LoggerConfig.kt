package pw.binom.config

import pw.binom.*
import pw.binom.io.file.File
import pw.binom.logging.*
import pw.binom.properties.LoggerProperties
import pw.binom.strong.Strong
import pw.binom.strong.bean

fun LoggerConfig(loggerProperties: LoggerProperties) = Strong.config {
    it.bean { LoggerSenderHandler(tags = mapOf("app" to "proxy-server")) }
    if (loggerProperties.db != null) {
        it.bean { SQLiteLogAppender(File(loggerProperties.db)) }
        it.bean { SQLiteLogSender() }
    }

//    InternalLog.replace {
//        InternalLoggerToLogger()
//    }

    if (loggerProperties.promtail != null) {
        it.bean { PromTailLogSender() }
    }
}
