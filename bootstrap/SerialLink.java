import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Доступ к последовательному порту без сторонних библиотек.
 *
 * В стандартной Java нет API для COM-портов, поэтому:
 * <ul>
 *   <li>Linux/macOS: порт — обычный файл устройства. Настраиваем линию через
 *       {@code stty} и читаем {@link FileInputStream}. Байты CDC-ACM приходят
 *       как есть, скорость для USB-гейджета роли не играет, но raw-режим обязателен:
 *       иначе терминал съедает 0x0A/0x0D и байты с старшим битом.</li>
 *   <li>Windows: порт доступен только через Win32 API, поэтому читаем через
 *       PowerShell ({@code System.IO.Ports.SerialPort}), который пишет сырые
 *       байты в свой stdout, а мы читаем этот поток.</li>
 * </ul>
 */
final class SerialLink {

    private SerialLink() {
    }

    static InputStream open(String port, int baud) throws IOException {
        if (isWindows()) {
            return openWindows(port, baud);
        }
        return openPosix(port, baud);
    }

    static List<String> listPorts() {
        List<String> ports = new ArrayList<String>();
        File dir = new File("/dev");
        String[] names = dir.list();
        if (names != null) {
            for (String name : names) {
                if (name.startsWith("ttyACM") || name.startsWith("ttyUSB")
                        || name.startsWith("cu.") || name.startsWith("tty.usbserial")) {
                    ports.add("/dev/" + name);
                }
            }
        }
        if (ports.isEmpty() && isWindows()) {
            ports.add("COM1..COM20 (see Device Manager)");
        }
        return ports;
    }

    private static InputStream openPosix(String port, int baud) throws IOException {
        configureLine(port, baud);
        return new FileInputStream(port);
    }

    /**
     * Переводит линию в raw-режим. Ошибку игнорируем: на некоторых системах
     * stty нет, а порт уже настроен пользователем.
     */
    private static void configureLine(String port, int baud) {
        String[] candidates = {
                "stty", "-F", port, String.valueOf(baud), "raw", "-echo", "clocal", "cread",
                "cs8", "-parenb", "-cstopb",
        };
        try {
            Process process = new ProcessBuilder(candidates).redirectErrorStream(true).start();
            process.waitFor();
            if (process.exitValue() == 0) {
                return;
            }
        } catch (Exception ignored) {
            // нет stty — пробуем BSD-вариант ниже
        }
        // macOS: stty -f <port> ...
        try {
            Process process = new ProcessBuilder(
                    "stty", "-f", port, String.valueOf(baud), "raw", "-echo").redirectErrorStream(true).start();
            process.waitFor();
        } catch (Exception ignored) {
            // остаётся надежда, что терминал уже настроен
        }
    }

    private static InputStream openWindows(String port, int baud) throws IOException {
        String script =
                "$ErrorActionPreference='Stop';"
                        + "$p=New-Object System.IO.Ports.SerialPort('" + port + "'," + baud + ",'None',8,'One');"
                        + "$p.ReadTimeout=60000;"
                        + "$p.Open();"
                        + "$o=[Console]::OpenStandardOutput();"
                        + "$b=New-Object byte[] 65536;"
                        + "while($true){$n=$p.Read($b,0,$b.Length);$o.Write($b,0,$n);$o.Flush()}";
        ProcessBuilder builder = new ProcessBuilder(
                "powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", script);
        builder.redirectError(ProcessBuilder.Redirect.INHERIT);
        Process process = builder.start();
        return process.getInputStream();
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
