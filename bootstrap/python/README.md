# Бутстрап на Python

`bootstrap.py` — один файл, самая короткая версия (~200 строк, весь протокол).

## Запуск

```bash
python bootstrap.py settings.txt
python bootstrap.py --port COM3 settings.txt     # порт вручную
```

Прогресс виден сразу, файл пишется в `workdir` из `settings.txt`.

## Откуда берётся COM-порт

1. Если установлен **pyserial** — используется он:
   ```bash
   pip install pyserial
   ```
   Это единственный путь на Windows (в Python нет доступа к Win32 COM из stdlib).

2. Если pyserial нет, но это **Linux/macOS** — порт открывается как файл
   устройства, raw-режим ставится модулем `tty` из стандартной библиотеки.
   Принудительно: `python bootstrap.py --stdlib settings.txt`.

3. Windows без pyserial — скрипт скажет прямо и предложит варианты:
   `pip install pyserial` либо `../csharp` / `../powershell`.

## Проверено

Оба режима (pyserial и `--stdlib` на `/dev/ttyACM1`): приняли 15 МБ, md5 совпал.

## Список портов

С pyserial — `serial.tools.list_ports`; без него — перебор `/dev` по префиксам
`ttyACM*`, `ttyUSB*`, `cu.*`, `tty.usbserial*`.

## Отличие от Java-варианта

Тот же протокол и та же логика чтения (маркер → длина имени → имя → длина тела
→ тело кусками по 64 КБ), но не нужен ни JDK, ни сборка: Python-скрипт
запускается как есть.
