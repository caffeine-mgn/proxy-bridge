package pw.binom.proxy


/**
 * JVM-точка входа. Вся общая логика — в [startProxy]/[loadConfiguration];
 * здесь только то, что специфично для JVM: трей и удержание процесса.
 */
object MainJvm {
    @JvmStatic
    @JvmName("main")
    fun mainJvm(args: Array<String>) {
        if (Cli.run(args)) return

        val config = loadConfiguration() ?: return
        startProxy(config)

        if (args.contains("--tray")) {
            TrayManager.show {
                System.exit(0)
            }
        }

        while (true) {
            Thread.sleep(1_000)
        }
    }
}
