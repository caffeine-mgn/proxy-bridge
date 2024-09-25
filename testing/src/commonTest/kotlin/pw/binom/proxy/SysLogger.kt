package pw.binom.proxy

import pw.binom.*
import pw.binom.logger.Logger
import pw.binom.logger.infoSync

object SysLogger : InternalLog {
    val logger by Logger.ofThisOrGlobal
    override fun log(level: InternalLog.Level, file: String?, line: Int?,method: String?, text: () -> String) {
        logger.infoSync(text = "$file::$method ${text()}")
    }
}
