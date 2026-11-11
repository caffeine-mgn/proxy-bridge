#!/usr/bin/env python3
"""Бутстрап для удалённой машины: принимает файлы от proxy-bridge по COM-порту.

Один файл, без обязательных зависимостей:

* если установлен ``pyserial`` — используется он (работает и на Windows);
* иначе на Linux/macOS порт открывается как файл устройства и переводится
  в raw-режим средствами стандартной библиотеки (``tty``/``os``).

Протокол (общий со всеми вариантами бутстрапа):

    START_FILE  1 байт   1 = начало файла
    nameLen     4 байта  длина имени, big-endian
    name        nameLen  имя файла, UTF-8
    size        4 байта  размер тела, big-endian
    payload     size     тело

Запуск:

    python bootstrap.py settings.txt
    python bootstrap.py --port COM3          # порт вручную
    python bootstrap.py --stdlib settings.txt # без pyserial (только POSIX)
"""

import os
import sys

# Прогресс должен быть виден сразу, даже если stdout перенаправлен в файл.
try:
    sys.stdout.reconfigure(line_buffering=True)
except AttributeError:
    pass

START_FILE = 1
CHUNK = 64 * 1024


def load_settings(path):
    settings = {"port": "", "baud": 115200, "workdir": "incoming"}
    if not os.path.exists(path):
        print("Settings file not found: %s (using defaults)" % path)
        return settings
    with open(path, "r", encoding="utf-8") as file:
        for raw in file:
            line = raw.strip()
            if not line or line.startswith("#"):
                continue
            if "=" not in line:
                continue
            key, value = line.split("=", 1)
            key = key.strip().lower()
            value = value.strip()
            if key == "port":
                settings["port"] = value
            elif key == "baud":
                settings["baud"] = int(value)
            elif key == "workdir":
                settings["workdir"] = value
    return settings


def list_ports():
    try:
        from serial.tools import list_ports as pyserial_ports
        return [p.device for p in pyserial_ports.comports()]
    except Exception:
        pass
    ports = []
    if os.path.isdir("/dev"):
        for name in sorted(os.listdir("/dev")):
            if name.startswith(("ttyACM", "ttyUSB", "cu.", "tty.usbserial")):
                ports.append("/dev/" + name)
    return ports


class PosixStream:
    """Порт как файл устройства: raw-режим средствами stdlib."""

    def __init__(self, path):
        import tty
        fd = os.open(path, os.O_RDWR | os.O_NOCTTY)
        tty.setraw(fd)
        self._file = os.fdopen(fd, "rb", buffering=0)

    def read(self, size):
        return self._file.read(size)

    def close(self):
        self._file.close()


class PyserialStream:
    def __init__(self, path, baud):
        import serial
        self._port = serial.Serial(path, baud, timeout=None)

    def read(self, size):
        return self._port.read(size)

    def close(self):
        self._port.close()


def open_stream(port, baud, use_pyserial=True):
    if use_pyserial:
        try:
            return PyserialStream(port, baud)
        except ImportError:
            if os.name == "nt":
                sys.exit(
                    "pyserial is not installed.\n"
                    "Either run: pip install pyserial\n"
                    "or use the C#/PowerShell bootstrap."
                )
            print("pyserial not found, falling back to raw device access")
    return PosixStream(port)


def read_exactly(stream, size):
    buffer = bytearray()
    while len(buffer) < size:
        chunk = stream.read(size - len(buffer))
        if not chunk:
            raise EOFError("port closed while reading")
        buffer.extend(chunk)
    return bytes(buffer)


def read_int32(stream):
    b = read_exactly(stream, 4)
    return (b[0] << 24) | (b[1] << 16) | (b[2] << 8) | b[3]


def receive_one(stream, workdir):
    # Синхронизация по маркеру: после шума/обрыва читаем дальше.
    while True:
        marker = stream.read(1)
        if not marker:
            raise EOFError("port closed")
        if marker[0] == START_FILE:
            break

    name_length = read_int32(stream)
    if name_length <= 0 or name_length > 4096:
        raise ValueError("bad name length: %d" % name_length)
    name = read_exactly(stream, name_length).decode("utf-8")

    size = read_int32(stream)
    if size < 0 or size > 512 * 1024 * 1024:
        raise ValueError("bad file size: %d" % size)

    safe_name = os.path.basename(name) or "received.bin"
    target = os.path.join(workdir, safe_name)

    print("Receiving %s (%d bytes)..." % (safe_name, size))
    remaining = size
    printed = 0
    with open(target, "wb") as output:
        while remaining > 0:
            chunk = stream.read(min(CHUNK, remaining))
            if not chunk:
                raise EOFError("port closed while receiving %s" % safe_name)
            output.write(chunk)
            remaining -= len(chunk)
            percent = (size - remaining) * 100 // size
            if percent >= printed + 10:
                printed = percent - percent % 10
                print("  %d%%" % printed)
    print("Saved %s (%d bytes)" % (os.path.abspath(target), os.path.getsize(target)))


def main(argv):
    use_pyserial = "--stdlib" not in argv
    args = [a for a in argv[1:] if not a.startswith("--")]

    settings_path = args[0] if args else "settings.txt"
    settings = load_settings(settings_path)

    port = settings["port"]
    if "--port" in argv:
        port = argv[argv.index("--port") + 1]
    if not port:
        print("Available ports: %s" % (", ".join(list_ports()) or "(none)"))
        port = input("Enter COM port (e.g. COM3, /dev/ttyACM0): ").strip()
        if not port:
            print("No port given - nothing to do.")
            return 1

    workdir = os.path.abspath(settings["workdir"])
    os.makedirs(workdir, exist_ok=True)

    print("Bootstrap: port=%s baud=%d" % (port, settings["baud"]))
    print("Bootstrap: work dir = %s" % workdir)
    print("Waiting for files... (Ctrl+C to stop)")

    stream = open_stream(port, settings["baud"], use_pyserial)
    try:
        while True:
            try:
                receive_one(stream, workdir)
            except EOFError as error:
                print("Port closed (%s), exiting." % error)
                return 0
            except (ValueError, OSError) as error:
                print("Error: %s - retrying" % error)
                import time
                time.sleep(1)
    except KeyboardInterrupt:
        return 0
    finally:
        stream.close()


if __name__ == "__main__":
    sys.exit(main(sys.argv))
