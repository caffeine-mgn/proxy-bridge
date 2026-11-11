import java.io.BufferedReader;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Бутстрап для удалённой машины: принимает файлы от proxy-bridge по COM-порту
 * (USB CDC-ACM) и складывает их в рабочий каталог.
 *
 * Протокол — тот же, что был в Bluetooth-бутстрапе проекта:
 *
 * <pre>
 *   START_FILE (1 байт)
 *   длина имени (4 байта, big-endian)
 *   имя файла (UTF-8)
 *   размер файла (4 байта, big-endian)
 *   содержимое файла
 * </pre>
 *
 * Всё в одном файле намеренно: Java 11+ запускает одиночный файл без сборки
 * ({@code java Bootstrap.java}), а лаунчер компилирует ровно один файл — второй
 * исходник он уже не увидит.
 *
 * <pre>
 *   java Bootstrap.java        // без сборки (Java 11+)
 *   javac Bootstrap.java && java Bootstrap
 * </pre>
 *
 * Если порт не задан в settings.txt — программа покажет список доступных портов
 * и спросит имя.
 */
public final class Bootstrap {

    /** Маркер начала файла (Codes.START_FILE из старого бутстрапа). */
    private static final int START_FILE = 1;

    public static void main(String[] args) throws Exception {
        Settings settings = Settings.load(args.length > 0 ? args[0] : "settings.txt");

        String port = settings.port;
        if (port == null || port.trim().isEmpty()) {
            System.out.println("Available ports: " + SerialLink.listPorts());
            System.out.print("Enter COM port (e.g. COM3, /dev/ttyACM0, /dev/cu.usbmodem14201): ");
            port = readLine();
            if (port == null || port.trim().isEmpty()) {
                System.out.println("No port given — nothing to do.");
                return;
            }
        }
        port = port.trim();

        File workDir = new File(settings.workDir).getAbsoluteFile();
        if (!workDir.exists() && !workDir.mkdirs()) {
            throw new IOException("Cannot create work directory: " + workDir);
        }

        System.out.println("Bootstrap: port=" + port + " baud=" + settings.baud);
        System.out.println("Bootstrap: work dir = " + workDir);
        System.out.println("Waiting for files... (Ctrl+C to stop)");

        InputStream input = SerialLink.open(port, settings.baud);
        while (true) {
            try {
                receiveOne(input, workDir);
            } catch (EOFException e) {
                System.out.println("Port closed, exiting.");
                return;
            } catch (IOException e) {
                System.out.println("Error: " + e.getMessage() + " — retrying");
                Thread.sleep(1000);
            }
        }
    }

    private static void receiveOne(InputStream input, File workDir) throws IOException {
        int code = input.read();
        if (code < 0) {
            throw new EOFException();
        }
        if (code != START_FILE) {
            // Не наш маркер — ждём следующий.
            return;
        }

        String name = readString(input);
        int size = readInt(input);
        if (size < 0 || size > 512 * 1024 * 1024) {
            throw new IOException("Bad file size: " + size);
        }

        String safeName = new File(name).getName();
        if (safeName.isEmpty()) {
            safeName = "received.bin";
        }
        File target = new File(workDir, safeName);

        System.out.println("Receiving " + safeName + " (" + size + " bytes)...");
        byte[] buffer = new byte[64 * 1024];
        int remaining = size;
        int printedPercent = 0;
        FileOutputStream output = new FileOutputStream(target);
        try {
            while (remaining > 0) {
                int read = input.read(buffer, 0, Math.min(buffer.length, remaining));
                if (read < 0) {
                    throw new EOFException();
                }
                output.write(buffer, 0, read);
                remaining -= read;
                int percent = (int) ((size - remaining) * 100L / size);
                if (percent >= printedPercent + 10) {
                    printedPercent = percent - percent % 10;
                    System.out.println("  " + printedPercent + "%");
                }
            }
        } finally {
            output.close();
        }
        System.out.println("Saved " + target.getAbsolutePath() + " (" + target.length() + " bytes)");
    }

    private static String readString(InputStream input) throws IOException {
        int length = readInt(input);
        if (length < 0 || length > 4096) {
            throw new IOException("Bad name length: " + length);
        }
        byte[] bytes = readFully(input, length);
        return new String(bytes, "UTF-8");
    }

    private static int readInt(InputStream input) throws IOException {
        byte[] b = readFully(input, 4);
        return ((b[0] & 0xFF) << 24) | ((b[1] & 0xFF) << 16) | ((b[2] & 0xFF) << 8) | (b[3] & 0xFF);
    }

    private static byte[] readFully(InputStream input, int length) throws IOException {
        byte[] buffer = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = input.read(buffer, offset, length - offset);
            if (read < 0) {
                throw new EOFException();
            }
            offset += read;
        }
        return buffer;
    }

    private static String readLine() throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        return reader.readLine();
    }

    // Настройки: key=value, по строке на параметр; '#' — комментарий.
    private static final class Settings {
        String port = "";
        int baud = 115200;
        String workDir = "incoming";

        static Settings load(String path) throws IOException {
            Settings settings = new Settings();
            File file = new File(path);
            if (!file.exists()) {
                System.out.println("Settings file not found: " + path + " (using defaults)");
                return settings;
            }
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(file), "UTF-8"));
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.charAt(0) == '#') {
                        continue;
                    }
                    int idx = line.indexOf('=');
                    if (idx <= 0) {
                        continue;
                    }
                    String key = line.substring(0, idx).trim().toLowerCase();
                    String value = line.substring(idx + 1).trim();
                    if (key.equals("port")) {
                        settings.port = value;
                    } else if (key.equals("baud")) {
                        settings.baud = Integer.parseInt(value);
                    } else if (key.equals("workdir")) {
                        settings.workDir = value;
                    }
                }
            } finally {
                reader.close();
            }
            return settings;
        }
    }
}

/**
 * Доступ к последовательному порту без сторонних библиотек.
 *
 * В стандартной Java нет API для COM-портов, поэтому:
 * <ul>
 *   <li>Linux/macOS: порт — обычный файл устройства. Настраиваем линию через
 *       {@code stty} и читаем {@link FileInputStream}. Raw-режим обязателен:
 *       иначе терминал съедает 0x0A/0x0D и байты со старшим битом.</li>
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
                // Linux: ttyACM*/ttyUSB*; macOS: cu.*/tty.usbserial*.
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
        // Linux: stty -F <port> ...
        if (runStty(new String[] {
                "stty", "-F", port, String.valueOf(baud), "raw", "-echo", "clocal", "cread",
                "cs8", "-parenb", "-cstopb",
        })) {
            return;
        }
        // macOS/BSD: stty -f <port> ...
        runStty(new String[] {"stty", "-f", port, String.valueOf(baud), "raw", "-echo"});
    }

    private static boolean runStty(String[] command) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            process.waitFor();
            return process.exitValue() == 0;
        } catch (Exception ignored) {
            return false;
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
