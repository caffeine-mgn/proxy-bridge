# Бутстрап без зависимостей (один файл)

`Bootstrap.java` — вся программа в одном файле: нет библиотек, нет Gradle, нет
сети. Нужен только JRE 8+ (а для `java Bootstrap.java` — JDK 11+).

Проверено на стенде: принял 15 МБ за ~1 с, md5 совпал.

## Запуск

```bash
java Bootstrap.java                 # без сборки (Java 11+)
sh build.sh && java -jar bootstrap.jar   # Linux/macOS
build.bat                                # Windows
```

## Почему один файл

Лаунчер `java Bootstrap.java` компилирует **ровно один** файл, поэтому второй
исходник он не увидит. Заодно меньше печатать в Блокнот.

## Как читается COM-порт без библиотек

- **Linux** — порт это файл устройства: `stty -F … raw -echo`, затем
  `FileInputStream` (raw обязателен: иначе терминал съест 0x0A/0x0D и байты
  со старшим битом);
- **macOS** — то же, но `stty -f …` и имя вида `/dev/cu.usbmodem14201`;
- **Windows** — Win32 API из Java недоступен, поэтому запускается встроенный
  PowerShell с `System.IO.Ports.SerialPort`, который отдаёт сырые байты в свой
  stdout, а Java читает этот поток.
