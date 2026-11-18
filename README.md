# proxy-bridge

Многопротокольный мост-туннель для передачи сетевого трафика через последовательный порт (RS-232 / USB CDC-ACM)
или TCP-соединение.

```
[Клиент] <--HTTP/SOCKS5--> [proxy-server на устройстве] <--COM/USB--> [proxy-server на ПК] <--TCP--> [Интернет/сеть]
```

Проект — Kotlin Multiplatform: один и тот же код собирается в JVM-джарник и в нативные исполняемые файлы
(Linux x64/arm64, Windows x64; macOS объявлен, но собирается только на Mac). Работа с COM-портом вынесена
в отдельную библиотеку [serial-kmp](https://github.com/caffeine-mgn/serial-kmp) — опубликована в Maven Central
как `pw.binom:serial` (собственное C-ядро + JNI/cinterop, без jSerialComm).

Устройство может прикидываться **одновременно COM-портом и USB-клавиатурой** — это используется для
первичной доставки программы на машину без сети (режим «фронт», см. ниже).

## Зачем?

Когда прямое сетевое соединение недоступно или ограничено, proxy-bridge позволяет организовать HTTP/SOCKS5-прокси,
TCP-форвардинг и доступ к удалённой файловой системе через физический канал связи — например, через USB-кабель между
Raspberry Pi и компьютером.

## Модули

| Модуль                 | Назначение                                                                     |
|------------------------|--------------------------------------------------------------------------------|
| `:multiplexer`         | Мультиплексирование виртуальных каналов поверх одного физического соединения    |
| `:webdav`              | Реализация WebDAV-сервера и файловых систем                                    |
| `:shared`              | Общая логика: протокол, конфигурация, обработчики каналов, serial-транспорт     |
| `:hid`                 | USB-клавиатура: имитация ввода (раскладка US, «человеческие» паузы)            |
| `:usb-gadget`          | Проверка и настройка USB-гаджета через configfs (COM + клавиатура)             |
| `:proxy-server`        | Приложение: сервисы, каналы, HTTP/SOCKS5/WebDAV/TCP-форвардинг                  |
| `:file-upload-service` | CLI для заливки файла через COM-порт (утилита сопровождения)                   |
| `:shared-models`       | Общие DTO для API                                                              |
| `:front`               | Заготовка веб-интерфейса (Kotlin/JS + Compose)                                 |

Модули `com`, `ble`, `sound` удалены: jSerialComm заменён на serial-kmp, Bluetooth выпилен.
Бутстрап для доставки на удалённую машину живёт в каталоге [`bootstrap/`](./bootstrap) —
пять вариантов под разные реальности (PowerShell, C#, Python, Java без зависимостей, Gradle/Maven-проект).

### Как это работает

1. **proxy-server** поднимает сервисы (HTTP-прокси, SOCKS5, TCP-форвардинг, WebDAV) и один или несколько транспортов.
2. Поверх транспорта (COM-порт или TCP) работает **мультиплексор** — он создаёт виртуальные каналы.
3. **ChannelSelector** направляет входящие данные обработчику по байту-идентификатору:
   - `TcpConnectChannel` (ID=1) — проксирование TCP-соединений;
   - `FileChannel` (ID=2) — удалённый доступ к файловой системе;
   - `OutcomeWrapperChannel` (ID=3) — обёртка цепочек outcome-сервисов.
4. Стороны соединяются симметрично: одна сторона описывает транспорт в `incomes`, другая — в `outcomes`.

## Требования

- JDK 21 (сборка и запуск JVM-таргета)
- Gradle wrapper (`./gradlew`)
- Для нативных таргетов — Kotlin/Native (скачивается Gradle'ом); Apple-таргеты собираются только на macOS

## Сборка

```bash
# JVM (fat jar)
./gradlew :proxy-server:jvmJar
# -> proxy-server/build/libs/proxy-server-jvm.jar

# Нативные исполняемые файлы
./gradlew :proxy-server:linkReleaseExecutableLinuxX64
./gradlew :proxy-server:linkReleaseExecutableLinuxArm64
./gradlew :proxy-server:linkReleaseExecutableMingwX64   # .exe, кросс-сборка с Linux

./gradlew :file-upload-service:jvmJar
```

Apple-таргеты (`macosX64`, `macosArm64`) объявлены, но кросс-компиляция с Linux невозможна — собирать на Mac.

### Зависимость serial-kmp

Используется `pw.binom:serial:1.0.0` из Maven Central
(исходники: [github.com/caffeine-mgn/serial-kmp](https://github.com/caffeine-mgn/serial-kmp)).
Если библиотеку правишь локально — публикуй в mavenLocal, проект резолвит его первым:

```bash
cd ../serial-kmp
./gradlew publishToMavenLocal -Pversion=1.0.1
# затем в shared/build.gradle.kts указать эту версию
```

## Конфигурация

Приложение читает `config.yaml` из рабочей директории. Стороны описываются одним и тем же файлом с разными секциями.

### Сторона устройства (например, Raspberry Pi, USB-gadget)

```yaml
# Транспорт наружу — COM-порт устройства
outcomes:
    com: !<com>
        port: "/dev/ttyGS0"     # на Windows: "COM4"
        speed: 115200

# Что поднять на устройстве
services:
    -   !<http-proxy>
        bind: "0.0.0.0"
        port: 8077
    -   !<socks5-proxy>
        bind: "0.0.0.0"
        port: 1080
    -   !<tcp-port-forward>
        bind: "0.0.0.0"
        localPort: 8070
        remotePort: 8080
        remoteHost: "127.0.0.1"
    -   !<webdav>
        bind: "0.0.0.0"
        port: 8075
        basePath: "/dav"
        fs: "remote"

# Куда отправлять исходящие соединения
trafficRoute:
    -   !<by-domain>
        host: "*.corp.example"
        rule: !<outcome>
            outcome: "com"
    -   !<always>
        rule: !<direct>

# Файловая система "remote" живёт на другой стороне (fs: local)
fileSystems:
    remote: !<remote>
        outcome: "com"
        fs: "local"
```

### Сторона ПК (шлюз с выходом в сеть)

```yaml
incomes:
    -   !<com>
        port: "/dev/ttyACM0"    # на Windows: "COM5"
        speed: 115200

trafficRoute:
    -   !<always>
        rule: !<direct>

fileSystems:
    local: !<local>
        root: "/home/user/dav-root"
```

Также поддерживается TCP-транспорт: `incomes: [{ !<tcp> { bind: 0.0.0.0, port: 9000 } }]`
(на противоположной стороне — `outcomes: { remote: !<tcp> { host: ..., port: 9000 } }`).

## Режим «фронт»: доставка на удалённую машину

Задача: на машине без сети (только USB-кабель) получить нашу программу.
Устройство представляется **одновременно** COM-портом и USB-клавиатурой,
поэтому файлы можно «печатать» в Блокнот.

```yaml
front:
  enabled: true          # включает режим и ВЫКЛЮЧАЕТ обычные сервисы
  bind: 0.0.0.0
  port: 8900
  bootstrapDir: bootstrap   # корень: подкаталоги = варианты бутстрапа
  settingsFile: settings.txt
  bootstrapExclude: []      # что не показывать (README* скрыт всегда)
  selfPath: null            # что отправлять кнопкой «отправить себя» (null = сам jar)
  serialPort: /dev/ttyGS0
  baudRate: 115200
  keyboardDevice: /dev/hidg0
  charDelayMs: 15
  jitterMs: 10
  holdMs: 8
  usbGadget:                 # самопроверка и настройка гаджета при старте
    configure: true          # требует root (configfs)
    name: g1
    udc: null                # null = первый из /sys/class/udc
```

### USB-гаджет настраивается сам

При старте в режиме фронта приложение проверяет, что устройство прикидывается
**одновременно** COM-портом и клавиатурой, и если нет — создаёт конфигурацию
через configfs (`:usb-gadget`), привязывает к UDC и ждёт появления
`/dev/ttyGS0` + `/dev/hidg0`. Пересоздание выполняется только когда что-то не
так: лишняя перепривязка меняет имя COM-порта на хосте (COM3 → COM4).

На странице фронта есть блок **«USB-гаджет»**: зелёная/красная плашка
(настроен / не настроен), факты (UDC, state, функции, устройства) и кнопка
«Настроить гаджет» — она делает то же, что автопроверка при старте, но по
требованию.

В логе видно состояние и причины:

```
USB gadget: supported=true udc=fe980000.usb state=not attached functions=[] serial=null keyboard=null
USB gadget: Гаджет g1 не создан
USB gadget: настраиваю…
USB gadget: готов (/dev/ttyGS0 + /dev/hidg0)
```

Права: запись в configfs идёт от root (systemd-юнит уже запускается от root).
Если ядро не в режиме peripheral (`dtoverlay=dwc2,dr_mode=peripheral`), это
чинится только перезагрузкой — приложение честно пишет об этом в problems.

Режимы **взаимоисключающие**: COM-порт и клавиатура нужны бутстрапу, поэтому
при `front.enabled: true` сервисы из `services`/`incomes` не поднимаются.

```
GET  /                        страница фронта (гаджет + группы вариантов)
GET  /api/gadget              состояние USB-гаджета: COM + клавиатура, проблемы
POST /api/gadget/setup        настроить гаджет (создать и привязать к UDC)
GET  /api/status              режим, число вариантов, отправляемый файл
GET  /api/bootstrap           варианты: файлы + команды запуска
POST /api/bootstrap/type      {"variant":"python","file":"bootstrap.py"} — напечатать файл
POST /api/bootstrap/launch    {"variant":"java","launch":"maven"} — напечатать команды
POST /api/bootstrap/send-self отправить программу по COM-порту
```

Варианты — подкаталоги `front.bootstrapDir` (`python/`, `csharp/`, `java/`,
`powershell/`, `standalone/`). Команды запуска лежат в `<вариант>/launch.txt`
(кнопка «запуск») и `<вариант>/launch-<имя>.txt` (кнопка с подписью `<имя>`;
у `java` их две — gradle и maven). После каждой строки нажимается Enter.

Порядок работы:

1. На удалённой машине открыть Блокнот (раскладка **US**).
2. По кнопке напечатать каждый файл бутстрапа и сохранить под показанным именем.
3. В `settings.txt` указать COM-порт (это осознанный шаг: порт на разных машинах разный).
4. Собрать и запустить бутстрап (`java Bootstrap.java`).
5. Нажать «Отправить себя» — бутстрап примет файл в свой рабочий каталог.
6. Выключить `front.enabled` на устройстве и запустить принятую программу
   на удалённой машине.

Кнопка «печатает» текст как настоящая клавиатура: паузы, джиттер, отпускание
клавиш. Раскладка удалённой машины должна быть US — если она другая, символы
придут «не теми» (это ответственность пользователя, как при живом вводе).

## Запуск

```bash
# JVM (любая ОС с JDK)
java -jar proxy-server/build/libs/proxy-server-jvm.jar

# Нативно
./proxy-server/build/bin/linuxX64/releaseExecutable/proxy-server.kexe
./proxy-server/build/bin/linuxArm64/releaseExecutable/proxy-server.kexe
proxy-server\build\bin\mingwX64\releaseExecutable\proxy-server.exe

# Ключ --tray (только JVM): иконка в системном трее
java -jar proxy-server-jvm.jar --tray
```

## Проверка на живом стенде

Стенд: Raspberry Pi 4 (Ubuntu, `192.168.76.108`), USB composite gadget (COM + клавиатура, создаётся
приложением через configfs), на ПК устройство видно как `/dev/ttyACM0` (`1d6b:0104`, драйвер `cdc_acm`).

```bash
# HTTP-прокси на устройстве
curl -x http://192.168.76.108:8077 http://example.com/
# SOCKS5
curl --socks5-hostname 192.168.76.108:1080 http://example.com/
# WebDAV (список / скачивание / загрузка)
curl -X PROPFIND -H "Depth: 1" http://192.168.76.108:8075/dav/
curl -O http://192.168.76.108:8075/dav/big.bin
curl -T big.bin http://192.168.76.108:8075/dav/uploaded.bin
```

## Подготовка Raspberry Pi

Пошаговая инструкция для «чистой» Ubuntu Server (включение режима device у
USB-контроллера, JDK, systemd-юнит, проверка гаджета, режим фронта, диагностика):

**→ [docs/raspberry-pi-setup.md](./docs/raspberry-pi-setup.md)**

Коротко:

```bash
./gradlew :proxy-server:copyToRaspberry        # кладёт jar в /opt/proxy
ssh root@<pi> systemctl restart proxy
```

Юнит `proxy.service` запускает `/opt/proxy/proxy-server-jvm.jar` из `/opt/proxy` (конфиг там же).

## Тестирование

```bash
./gradlew jvmTest                    # все JVM-тесты
./gradlew :multiplexer:jvmTest       # тесты конкретного модуля
./gradlew linuxX64Test               # тесты нативного таргета
```

Интеграционный тест serial-kmp через pty требует `socat`:

```bash
socat pty,raw,echo=0,link=/tmp/ttyA pty,raw,echo=0,link=/tmp/ttyB &
cd ../serial-kmp && ./gradlew jvmTest -Dserial.integration=true
```

Тесты фронта/бутстрапа (раскладка кадра, разбор вариантов):

```bash
./gradlew :proxy-server:jvmTest --tests "pw.binom.proxy.front.*"
./gradlew :hid:jvmTest
```

## Технологии

- **Kotlin Multiplatform** 2.4, kotlinx-coroutines, kotlinx-io, kotlinx-serialization
- **Ktor 3.4** (сервер CIO/Netty, клиент CIO, сетевые сокеты)
- **Koin 4.1** (DI)
- **kotaml** (YAML-конфигурация)
- **serial-kmp** (COM-порты: своё C-ядро, JNI + cinterop)
- **kotlin-logging** + slf4j-simple (только JVM)

## Лицензия

Apache 2.0
