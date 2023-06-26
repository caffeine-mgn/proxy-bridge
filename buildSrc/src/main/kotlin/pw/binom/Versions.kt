package pw.binom

import BuildConfig
import java.util.*

object Versions {
    val JVM_VERSION = org.gradle.internal.jvm.Jvm.current().javaVersion!!.ordinal + 1
    const val BINOM_VERSION = BuildConfig.BINOM_VERSION
    const val KOTLIN_VERSION = BuildConfig.KOTLIN_VERSION
    const val SHADOW_VERSION = BuildConfig.SHADOW_VERSION
    const val KOTLINX_COROUTINES_VERSION = BuildConfig.KOTLINX_COROUTINES_VERSION
    const val KOTLINX_SERIALIZATION_VERSION = BuildConfig.KOTLINX_SERIALIZATION_VERSION
    val TL_VERSION = run {
        val tag = System.getenv("DRONE_TAG") ?: System.getenv("GITHUB_REF_NAME")
        if (tag != null) {
            return@run tag
        }
        val base = "${Date().year + 1900}.${Date().month + 1}"
        val buildNum =
            System.getenv("BUILD_NUMBER")
                ?: System.getenv("GO_TO_REVISION")
                ?: System.getenv("DRONE_BUILD_NUMBER")
        if (buildNum != null) {
            "$base.$buildNum"
        } else {
            base
        }
    }
}
