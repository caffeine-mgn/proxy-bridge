import java.io.BufferedReader;
import java.io.EOFException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

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
 * Сборка и запуск (Java 11+ умеет запускать одиночный файл без сборки):
 *
 * <pre>
 *   javac Bootstrap.java SerialLink.java      // вариант с явной сборкой
 *   java Bootstrap
 *
 *   java Bootstrap.java                        // без сборки (Java 11+)
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
            System.out.print("Enter COM port (e.g. COM3 or /dev/ttyACM0): ");
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
        FileOutputStream output = new FileOutputStream(target);
        try {
            while (remaining > 0) {
                int read = input.read(buffer, 0, Math.min(buffer.length, remaining));
                if (read < 0) {
                    throw new EOFException();
                }
                output.write(buffer, 0, read);
                remaining -= read;
                if (size > 1024 * 1024 && (size - remaining) % (1024 * 1024) < buffer.length) {
                    System.out.println("  " + ((size - remaining) * 100L / size) + "%");
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
            BufferedReader reader = new BufferedReader(new InputStreamReader(new java.io.FileInputStream(file), "UTF-8"));
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
