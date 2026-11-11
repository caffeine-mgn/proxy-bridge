# Бутстрап для Windows без Java

`bootstrap.ps1` — самодостаточный приёмник файлов по COM-порту. Использует
`System.IO.Ports.SerialPort` из .NET, который встроен в Windows: ни Java, ни
Gradle, ни сети не требуется.

## Запуск

```bat
powershell -NoProfile -ExecutionPolicy Bypass -File bootstrap.ps1 settings.txt
```

Если политика разрешает скрипты — просто `.\bootstrap.ps1 settings.txt`.

## Настройки (`settings.txt`)

```
port=COM3          # пусто = спросит при запуске и покажет список портов
baud=115200
workdir=incoming
```

## Как читается поток

- маркер и все поля читаются из `$port.BaseStream` — один поток, без смешивания
  с внутренним буфером `SerialPort.ReadByte()`;
- `Read-Exactly` крутит цикл, пока не наберёт запрошенное число байт:
  `Stream.Read` вправе вернуть меньше;
- `Read-Int32` собирает big-endian вручную (`-shl`/`-bor`), потому что
  `BitConverter` на Windows little-endian;
- тело пишется кусками по 64 КБ прямо в `FileStream`, прогресс — в консоль.

## Ограничение

Если политика (AppLocker / ExecutionPolicy) запрещает и `-Bypass`, остаётся
`standalone/` — Java-версия без зависимостей.
