package pw.binom.config

import pw.binom.strong.Strong
import pw.binom.strong.bean
import pw.binom.subchannel.commands.BenchmarkCommand
import pw.binom.subchannel.commands.TcpConnectCommand

fun CommandsConfig()=Strong.config {
    it.bean { TcpConnectCommand() }
    it.bean { BenchmarkCommand() }
}
