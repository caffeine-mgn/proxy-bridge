package pw.binom.bootstrap;

import com.fazecast.jSerialComm.SerialPort;

import java.io.EOFException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

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
 * Работа с портом — через jSerialComm: библиотека сама подбирает нативный
 * бэкенд под ОС (Windows/Linux/macOS), поэтому код одинаковый везде.
 */
public final class Bootstrap {

    /** Маркер начала файла (Codes.START_FILE из старого бутстрапа). */
    private static final int START_FILE = 1;

    public static void main(String[] args) throws Exception {
        Settings settings = Settings.load(args.length > 0 ? args[0] : "settings.txt");

        String portName = settings.port;
        if (portName == null || portName.trim().isEmpty()) {
            System.out.println("Available ports: " + listPorts());
            System.out.print("Enter COM port (e.g. COM3, /dev/ttyACM0, /dev/cu.usbmodem14201): ");
            portName = Settings.readLine();
            if (portName == null || portName.trim().isEmpty()) {
                System.out.println("No port given — nothing to do.");
                return;
            }
        }
        portName = portName.trim();

        File workDir = new File(settings.workDir).getAbsoluteFile();
        if (!workDir.exists() && !workDir.mkdirs()) {
            throw new IOException("Cannot create work directory: " + workDir);
        }

        System.out.println("Bootstrap: port=" + portName + " baud=" + settings.baud);
        System.out.println("Bootstrap: work dir = " + workDir);
        System.out.println("Waiting for files... (Ctrl+C to stop)");

        SerialPort port = SerialPort.getCommPort(portName);
        port.setComPortParameters(settings.baud, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
        port.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, 60_000, 0);
        if (!port.openPort()) {
            throw new IOException("Cannot open port " + portName + ": " + port.getLastErrorCode()
                    + " " + port.getLastErrorLocation());
        }

        try {
            InputStream input = port.getInputStream();
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
        } finally {
            port.closePort();
        }
    }

    private static String listPorts() {
        StringBuilder builder = new StringBuilder();
        for (SerialPort port : SerialPort.getCommPorts()) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(port.getSystemPortName());
        }
        return builder.length() == 0 ? "(none)" : builder.toString();
    }

    private static void receiveOne(InputStream input, File workDir) throws IOException {
        int code = input.read();
        if (code < 0) {
            throw new EOFException();
        }
        if (code != START_FILE) {
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
        return new String(readFully(input, length), "UTF-8");
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
}
