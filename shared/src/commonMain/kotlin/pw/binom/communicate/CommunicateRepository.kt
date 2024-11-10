package pw.binom.communicate

import pw.binom.strong.BeanLifeCycle
import pw.binom.strong.injectServiceList

@Deprecated(message = "Not use it")
class CommunicateRepository {
    private val pairs by injectServiceList<CommunicatePair<Any, Any>>()

    init {
        BeanLifeCycle.afterInit {
            val existCodes = HashSet<Short>()
            pairs.forEach {
                if (it.code in existCodes) {
                    throw IllegalStateException("Code ${it.code} already exist")
                }
                existCodes += it.code
            }
        }
    }

    fun getPair(code: Short): CommunicatePair<Any, Any>? = pairs.find { it.code == code }
}
