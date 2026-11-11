// Бутстрап для Windows БЕЗ PowerShell и БЕЗ Java.
//
// Компилируется встроенным в Windows компилятором C# (.NET Framework 4.x):
//   build.bat   ->  Bootstrap.exe
//   Bootstrap.exe settings.txt
//
// Зачем нужен этот вариант: политики ExecutionPolicy/AppLocker/Constrained
// Language Mode могут запретить выполнение .ps1, а csc.exe и обычный .exe —
// нет: это родной механизм Windows, никаких скриптовых политик.
//
// Протокол (общий со всеми вариантами):
//   START_FILE (1 байт)
//   длина имени (4 байта, big-endian)
//   имя файла (UTF-8)
//   размер файла (4 байта, big-endian)
//   содержимое файла

using System;
using System.IO;
using System.IO.Ports;
using System.Text;

namespace Bootstrap
{
    internal static class Program
    {
        private const int StartFile = 1;

        private static int Main(string[] args)
        {
            Console.OutputEncoding = Encoding.UTF8;

            string settingsPath = args.Length > 0 ? args[0] : "settings.txt";
            Settings settings = Settings.Load(settingsPath);

            string portName = settings.Port;
            if (string.IsNullOrWhiteSpace(portName))
            {
                string[] ports = SerialPort.GetPortNames();
                Console.WriteLine("Available ports: " + (ports.Length == 0 ? "(none)" : string.Join(", ", ports)));
                Console.Write("Enter COM port (e.g. COM3): ");
                portName = (Console.ReadLine() ?? string.Empty).Trim();
                if (portName.Length == 0)
                {
                    Console.WriteLine("No port given - nothing to do.");
                    return 1;
                }
            }

            string workDir = Path.GetFullPath(settings.WorkDir);
            if (!Directory.Exists(workDir)) Directory.CreateDirectory(workDir);

            Console.WriteLine("Bootstrap: port=" + portName + " baud=" + settings.Baud);
            Console.WriteLine("Bootstrap: work dir = " + workDir);
            Console.WriteLine("Waiting for files... (Ctrl+C to stop)");

            using (SerialPort port = new SerialPort(portName, settings.Baud, Parity.None, 8, StopBits.One))
            {
                port.ReadTimeout = 60000;
                port.Open();

                while (true)
                {
                    try
                    {
                        ReceiveOne(port.BaseStream, workDir);
                    }
                    catch (TimeoutException)
                    {
                        // Ничего не пришло за минуту — ждём дальше.
                    }
                    catch (EndOfStreamException)
                    {
                        Console.WriteLine("Port closed, exiting.");
                        return 0;
                    }
                    catch (Exception ex)
                    {
                        Console.WriteLine("Error: " + ex.Message + " - retrying");
                        System.Threading.Thread.Sleep(1000);
                    }
                }
            }
        }

        private static void ReceiveOne(Stream stream, string workDir)
        {
            // Маркер: после шума/обрыва просто читаем дальше.
            int code;
            do
            {
                code = stream.ReadByte();
                if (code < 0) throw new EndOfStreamException();
            } while (code != StartFile);

            string name = ReadString(stream);
            int size = ReadInt32(stream);
            if (size < 0 || size > 512 * 1024 * 1024)
            {
                throw new IOException("Bad file size: " + size);
            }

            string safeName = Path.GetFileName(name);
            if (string.IsNullOrEmpty(safeName)) safeName = "received.bin";
            string target = Path.Combine(workDir, safeName);

            Console.WriteLine("Receiving " + safeName + " (" + size + " bytes)...");
            byte[] buffer = new byte[64 * 1024];
            int remaining = size;
            int printed = 0;
            using (FileStream output = File.Create(target))
            {
                while (remaining > 0)
                {
                    int read = stream.Read(buffer, 0, Math.Min(buffer.Length, remaining));
                    if (read <= 0) throw new EndOfStreamException();
                    output.Write(buffer, 0, read);
                    remaining -= read;
                    int percent = (int)((long)(size - remaining) * 100 / size);
                    if (percent >= printed + 10)
                    {
                        printed = percent - percent % 10;
                        Console.WriteLine("  " + printed + "%");
                    }
                }
            }
            Console.WriteLine("Saved " + target + " (" + new FileInfo(target).Length + " bytes)");
        }

        private static string ReadString(Stream stream)
        {
            int length = ReadInt32(stream);
            if (length < 0 || length > 4096) throw new IOException("Bad name length: " + length);
            return Encoding.UTF8.GetString(ReadExactly(stream, length));
        }

        private static int ReadInt32(Stream stream)
        {
            byte[] b = ReadExactly(stream, 4);
            return (b[0] << 24) | (b[1] << 16) | (b[2] << 8) | b[3];
        }

        private static byte[] ReadExactly(Stream stream, int count)
        {
            byte[] buffer = new byte[count];
            int offset = 0;
            while (offset < count)
            {
                int read = stream.Read(buffer, offset, count - offset);
                if (read <= 0) throw new EndOfStreamException();
                offset += read;
            }
            return buffer;
        }
    }

    internal sealed class Settings
    {
        public string Port = "";
        public int Baud = 115200;
        public string WorkDir = "incoming";

        public static Settings Load(string path)
        {
            Settings settings = new Settings();
            if (!File.Exists(path))
            {
                Console.WriteLine("Settings file not found: " + path + " (using defaults)");
                return settings;
            }
            foreach (string raw in File.ReadAllLines(path))
            {
                string line = raw.Trim();
                if (line.Length == 0 || line[0] == '#') continue;
                int idx = line.IndexOf('=');
                if (idx <= 0) continue;
                string key = line.Substring(0, idx).Trim().ToLowerInvariant();
                string value = line.Substring(idx + 1).Trim();
                if (key == "port") settings.Port = value;
                else if (key == "baud") settings.Baud = int.Parse(value);
                else if (key == "workdir") settings.WorkDir = value;
            }
            return settings;
        }
    }
}
