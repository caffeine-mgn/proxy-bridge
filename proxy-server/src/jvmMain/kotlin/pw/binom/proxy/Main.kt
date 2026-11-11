package pw.binom.proxy

import java.io.File

/**
 * JVM-точка входа. Вся общая логика — в [startProxy]/[loadConfiguration];
 * здесь только то, что специфично для JVM: трей, путь к собственному jar и
 * удержание процесса.
 */
object MainJvm {
    @JvmStatic
    @JvmName("main")
    fun mainJvm(args: Array<String>) {
        if (Cli.run(args)) return

        val config = loadConfiguration() ?: return
        startProxy(config, selfPath = detectSelfPath())

        if (args.contains("--tray")) {
            TrayManager.show {
                System.exit(0)
            }
        }

        while (true) {
            Thread.sleep(1_000)
        }
    }

    /**
     * Путь к файлу, из которого запущена программа (jar или каталог classes).
     * Нужен для кнопки «отправить себя»: именно этот файл уходит на удалённую
     * машину.
     */
    private fun detectSelfPath(): String? = runCatching {
        val location = MainJvm::class.java.protectionDomain?.codeSource?.location ?: return null
        File(location.toURI()).path.takeIf { File(it).isFile }
    }.getOrNull()
}
