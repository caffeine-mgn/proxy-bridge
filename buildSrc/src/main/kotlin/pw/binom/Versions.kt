package pw.binom

import BuildConfig

object Versions {
    val JVM_VERSION = org.gradle.internal.jvm.Jvm.current().javaVersion!!.ordinal + 1
    const val BINOM_VERSION = BuildConfig.BINOM_VERSION
    const val KOTLIN_VERSION = BuildConfig.KOTLIN_VERSION
    const val SHADOW_VERSION = BuildConfig.SHADOW_VERSION
}
