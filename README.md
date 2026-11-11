# proxy-bridge

Многопротокольный мост-туннель для передачи сетевого трафика через последовательный порт (RS-232 / USB CDC-ACM)
или TCP-соединение.

```
[Клиент] <--HTTP/SOCKS5--> [proxy-server на устройстве] <--COM/USB--> [proxy-server на ПК] <--TCP--> [Интернет/сеть]
```

Проект — Kotlin Multiplatform: один и тот же код собирается в JVM-джарник и в нативные исполняемые файлы
(Linux x64/arm64, Windows x64). Работа с COM-портом вынесена в отдельную библиотеку
[serial-kmp](https://git.binom.pw/subochev/serial-kmp) (собственное C-ядро + JNI/cinterop, без jSerialComm).

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
| `:proxy-server`        | Приложение: сервисы, каналы, HTTP/SOCKS5/WebDAV/TCP-форвардинг                  |
| `:file-upload-service` | CLI для заливки файла через COM-порт (утилита сопровождения)                   |
| `:shared-models`       | Общие DTO для API                                                              |
| `:front`               | Заготовка веб-интерфейса (Kotlin/JS + Compose)                                 |

Модули `com`, `ble`, `sound`, `bootstrap` удалены: jSerialComm заменён на serial-kmp, Bluetooth выпилен.
Бутстрап для доставки на удалённую машину теперь живёт в каталоге
[`bootstrap/`](./bootstrap) (Java, без зависимостей).

### Как это работает

1. **proxy-server** поднимает сервисы (HTTP-прокси, SOCKS5, TCP-форвардинг, WebDAV) и один или несколько транспортов.
2. Поверх транспорта (COM-порт или TCP) работает **мультиплексор** — он создаёт виртуальные каналы.
3. **ChannelSelector** направляет входящие данные обработчику по байту-идентификатору:
   - `TcpConnectChannel` (ID=1) — проксирование TCP-соединений;
   - `FileChannel` (ID=2) — удалённый доступ к файловой системе;
   - `OutcomeWrapperChannel` (ID=3) — обёртка цепочек outcome-сервисов.
4. Стороны соединяются симметрично: одна сторона описывает транспорт в `incomes`, другая — в `outcomes`.

## Требования

- JDK 17+ (сборка JVM-таргета)
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

Используется опубликованная версия `pw.binom.serial:serial-kmp:2` из Nexus (`caffeine`).
Если библиотеку правишь локально — публикуй в mavenLocal, проект резолвит его первым:

```bash
cd ../serial-kmp
./gradlew publishToMavenLocal -Pversion=2
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
Устройство представляется **одновременно** COM-портом и USB-клавиатурой
(`deploy/usb-gadget/setup.sh`), поэтому файлы можно «печатать» в Блокнот.

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
```

Режимы **взаимоисключающие**: COM-порт и клавиатура нужны бутстрапу, поэтому
при `front.enabled: true` сервисы из `services`/`incomes` не поднимаются.

```
GET  /                        страница фронта (группы по вариантам)
GET  /api/status              режим, число вариантов, отправляемый файл
GET  /api/bootstrap           варианты: файлы + команды запуска
POST /api/bootstrap/type      {"variant":"python","file":"bootstrap.py"} — напечатать файл
POST /api/bootstrap/launch    {"variant":"java","launch":"maven"} — напечатать команды
POST /api/bootstrap/send-self отправить программу по COM-порту
```

Варианты — подкаталоги `front.bootstrapDir` (`python/`, `csharp/`, `java/`,
`powershell/`, `standalone/`). Команды запуска для кнопки лежат в
`<вариант>/launch.txt` (по строке на команду, после каждой нажимается Enter).

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

Стенд: Raspberry Pi 4 (Ubuntu, `192.168.76.108`), USB CDC-ACM gadget (`cdc-acm.service` создаёт `/dev/ttyGS0`),
на ПК устройство видно как `/dev/ttyACM0` (`1d6b:0104`, драйвер `cdc_acm`).

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

## Развёртывание на Raspberry Pi

```bash
./gradlew :proxy-server:copyToRaspberry        # кладёт jar в /opt/proxy
ssh root@192.168.76.108 systemctl restart proxy
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
./gradlew :shared:jvmTest            # тесты транспортной логики
cd ../serial-kmp && ./gradlew jvmTest -Dserial.integration=true
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
