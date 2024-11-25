package pw.binom

import org.gradle.kotlin.dsl.delegateClosureOf
import org.hidetake.groovy.ssh.Ssh
import org.hidetake.groovy.ssh.core.Remote
import org.hidetake.groovy.ssh.session.SessionHandler
import java.io.File

class SSH private constructor(private val handler: SessionHandler) {
    companion object {
        private val service by lazy { Ssh.newService() }
        fun run(
            ip: String,
            user: String,
            port: Int = 22,
            func: SSH.() -> Unit
        ) {
            val ssh = service
            val remote = Remote(
                hashMapOf<String, Any?>(
                    "host" to ip,
                    "user" to user,
                    "port" to port,
                    "identity" to File("/home/subochev/.ssh/id_rsa"),
                    "knownHosts" to org.hidetake.groovy.ssh.connection.AllowAnyHosts.instance
                )
            )

            ssh.run(delegateClosureOf<org.hidetake.groovy.ssh.core.RunHandler> {
                session(remote, delegateClosureOf<org.hidetake.groovy.ssh.session.SessionHandler> {
                    func(SSH(this))
                })
            })

        }
    }

    fun put(from: File, to: String) {
        handler.put(
            hashMapOf(
                "from" to from,
                "into" to to
            )
        )
    }

    fun execute(cmd: List<String>) {
        handler.execute(ArrayList(cmd))
    }
}
