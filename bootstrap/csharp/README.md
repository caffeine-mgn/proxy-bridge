# Бутстрап на C# — когда PowerShell запрещён

`Program.cs` компилируется **встроенным в Windows** компилятором
`csc.exe` (.NET Framework 4.x) в обычный `Bootstrap.exe`.

## Зачем

Политики безопасности Windows умеют запрещать именно **скрипты**:

- `ExecutionPolicy` → `AllSigned`/`Restricted` через GPO — `-Bypass` не
  помогает, потому что политика уровня GPO приоритетнее параметра;
- AppLocker / WDAC — может просто не дать запустить `powershell.exe`;
- Constrained Language Mode — запрещает `New-Object`, типы .NET, `[Console]::…`.

Скомпилированный `.exe` под эти механизмы не попадает: `csc.exe` есть в
`%WINDIR%\Microsoft.NET\Framework64\v4.0.30319\` на любой Windows с .NET
Framework 4.x (то есть на Windows 7 SP1 и новее, включая 10/11 «из коробки»).

## Запуск

```bat
build.bat
Bootstrap.exe settings.txt
```

## Что внутри

| Часть | Назначение |
|---|---|
| `Program.cs` | весь бутстрап: настройки, приём кадров, запись файла |
| `build.bat` | вызов `csc.exe` (без MSBuild, NuGet и сети) |
| `settings.txt` | `port=`, `baud=`, `workdir=` |

Порт — через `System.IO.Ports.SerialPort` (System.dll), тело пишется кусками
по 64 КБ, прогресс в консоль.

## Оговорка

Код написан по тому же протоколу и той же логике, что проверенные Java-варианты,
но **на Windows я его не запускал** (в этой среде нет Windows). Первый запуск —
за вами; при ошибке пришлите текст, поправлю.
