# Бутстрап

Маленькая программа для **удалённой машины**: принимает файл от `proxy-bridge`
(малина) по USB-COM и сохраняет его в рабочий каталог. Дальше вы запускаете
принятую программу — и туннель работает уже в обратную сторону.

Пять вариантов, один протокол. Выбор — по тому, что реально есть на машине.

| Вариант | ОС | Что нужно на машине | Как доставить |
|---|---|---|---|
| [`powershell/`](./powershell) | Windows | PowerShell + `FullLanguage` | напечатать текст |
| [`csharp/`](./csharp) | Windows | только `csc.exe` из .NET Framework | напечатать текст, `build.bat` |
| [`python/`](./python) | Windows / Linux / macOS | Python 3 (+ `pyserial` на Windows) | напечатать текст |
| [`standalone/`](./standalone) | Windows / Linux / macOS | JRE 8+ (**без** библиотек, Gradle, сети) | напечатать текст |
| [`java/`](./java) | Windows / Linux / macOS | JDK + Gradle + **сеть** (тянет jSerialComm) | напечатать текст, затем `gradle build` |

Важно: в реальной ситуации канал доставки — **клавиатура** (в Блокноте), то
есть доехать может только текст. Поэтому:

- **Windows** → `powershell/`; если политика/GPO/AppLocker мешает запускать
  скрипты — `csharp/` (обычный `.exe`, скриптовые политики на него не действуют).
- **Linux / macOS** → `standalone/` (только JRE) или `python/` (Python 3 без
  зависимостей). Оба проверены на стенде: 15 МБ, md5 совпал.
- **Есть Python на Windows** → `python/` с `pip install pyserial`.
- **`java/`** — «честный» Gradle-проект с библиотекой `jSerialComm`. Удобен,
  когда машина с Gradle и сетью (или когда бутстрап доставляется не клавиатурой,
  а, например, из вашего репозитория при сборке образа).

## Протокол (общий для всех вариантов)

```
START_FILE  1 байт   1 = начало файла
nameLen     4 байта  длина имени, big-endian
name        nameLen  имя файла, UTF-8
size        4 байта  размер тела, big-endian
payload     size     тело
```

Формат взят из исходного бутстрапа проекта (`Codes.START_FILE`), никаких
новых полей не добавлено — стороны совместимы.

## Как это выглядит в работе

1. На малине: `front.enabled: true`, открыть `http://<малина>:8900/`.
2. На удалённой машине открыть Блокнот (раскладка **US**).
3. Напечатать кнопкой нужные файлы выбранного варианта и сохранить:
   - PowerShell: `bootstrap.ps1`, `run.cmd`, `settings.txt`;
   - C#: `Program.cs`, `build.bat`, `settings.txt`;
   - Python: `bootstrap.py`, `settings.txt`;
   - standalone: `Bootstrap.java`, `settings.txt`;
   - java: `build.gradle.kts`, `settings.gradle.kts`, `Bootstrap.java`, `Settings.java`, `settings.txt`.
4. В `settings.txt` вписать COM-порт (это делается руками: на каждой машине он свой).
5. Запустить:

   ```bat
   run.cmd settings.txt
   ```

   ```bat
   build.bat && Bootstrap.exe settings.txt
   ```

   ```bash
   python bootstrap.py settings.txt
   ```

   ```bash
   java Bootstrap.java          # standalone, без сборки (Java 11+)
   # или: sh build.sh && java -jar bootstrap.jar
   ```

   ```bash
   gradle run     # собрать и запустить сразу; settings.txt берётся по умолчанию
   ```

6. На странице фронта нажать «Отправить себя через bootstrap» — в консоли
   бутстрапа пойдёт прогресс, файл окажется в `workdir` (`incoming`).

## Каталог фронта

`front.bootstrapDir` указывает **корень**, внутри которого каждый подкаталог —
отдельный вариант бутстрапа. Фронт показывает их группами: файлы варианта и
кнопку «Напечатать команду запуска» (команды берутся из `<вариант>/launch.txt`).

```yaml
front:
  bootstrapDir: bootstrap      # внутри: python/, csharp/, java/, powershell/, standalone/
```

`README*` и `launch.txt` в список «сохранить как» не попадают; лишнее можно
скрыть через `front.bootstrapExclude`.

Команды запуска (печатаются по кнопке, после каждой строки — Enter):

| Вариант | `launch.txt` |
|---|---|
| `powershell` | `run.cmd settings.txt` |
| `csharp` | `build.bat` + `Bootstrap.exe settings.txt` |
| `python` | `python bootstrap.py settings.txt` |
| `standalone` | `java Bootstrap.java settings.txt` |
| `java` | `gradle run` |
