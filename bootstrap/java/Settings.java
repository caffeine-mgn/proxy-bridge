package pw.binom.bootstrap;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Настройки бутстрапа: {@code key=value}, по строке на параметр.
 * Строки, начинающиеся с {@code #}, игнорируются.
 */
final class Settings {

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

    static String readLine() throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        return reader.readLine();
    }
}
