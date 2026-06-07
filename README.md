# proxy-bridge

Многопротокольный мост-туннель для передачи сетевого трафика через последовательный порт (RS-232), Bluetooth (
RFCOMM/SPP) или TCP-соединение.

## Зачем?

Когда прямое сетевое соединение недоступно или ограничено, proxy-bridge позволяет организовать HTTP/SOCKS5-прокси,
TCP-форвардинг и доступ к удалённой файловой системе через физический канал связи — например, через UART между Raspberry
Pi и шлюзом.

```
[Клиент] <--HTTP/SOCKS5--> [proxy-server] <--Serial/BT/TCP--> [proxy-gateway] <--TCP--> [Интернет/сеть]
```

- **proxy-server** — запускается на удалённом устройстве, принимает HTTP/SOCKS5-запросы от локальных клиентов.
- **proxy-gateway** — запускается на машине-шлюзе, соединяется с удалённым устройством через COM-порт, Bluetooth или
  TCP.

## Архитектура

Проект написан на **Kotlin Multiplatform** и состоит из модулей:

| Модуль                 | Назначение                                                                   |
|------------------------|------------------------------------------------------------------------------|
| `:shared`              | Общая логика, протоколы, конфигурация, обработчики каналов                   |
| `:multiplexer`         | Мультиплексирование виртуальных каналов поверх одного физического соединения |
| `:com`                 | Работа с последовательным портом (jSerialComm)                               |
| `:proxy-server`        | Серверная часть (запускается на удалённом устройстве)                        |
| `:proxy-gateway`       | Шлюзовая часть (запускается на машине с выходом в сеть)                      |
| `:bootstrap`           | Утилиты для транспорта                                                       |
| `:ble`                 | Bluetooth Low Energy                                                         |
| `:sound`               | Аудио-модуль (интеграция с BlueZ через DBus)                                 |
| `:file-upload-service` | CLI-утилита для загрузки файлов через COM-порт                               |

### Как это работает

1. **proxy-server** поднимает HTTP-прокси и/или SOCKS5-прокси на удалённом устройстве.
2. **proxy-gateway** подключается к серверу через COM-порт, Bluetooth или TCP.
3. Поверх физического транспорта работает **мультиплексор** — он создаёт несколько виртуальных каналов на одном
   соединении.
4. Каждый канал обрабатывает свой тип трафика: TCP-подключения, файловые запросы, обёртки outcome-сервисов.
5. **ChannelSelector** направляет входящие данные нужному обработчику по байту-идентификатору.

### Обработчики каналов

- **TcpConnectChannel** (ID=1) — проксирование TCP-соединений
- **FileChannel** (ID=2) — удалённый доступ к файловой системе
- **OutcomeWrapperChannel** (ID=3) — обёртка для цепочек outcome-сервисов

## Требования

- JDK 17+
- Gradle (используется wrapper — `./gradlew`)

## Сборка

```bash
# Собрать всё
./gradlew build

# Собрать fat JAR для сервера или шлюза
./gradlew :proxy-server:jvmJar
./gradlew :proxy-gateway:jvmJar

# Собрать bootstrap
./gradlew :bootstrap:shadowJar
```

## Конфигурация

Создайте `config.yaml` в рабочей директории:

```yaml
# Транспорт (со стороны сервера)
income:
    com:
        port: "/dev/ttyACM0"   # или "COM4" на Windows
        speed: 115200
    # Или TCP:
    # tcp:
    #   bind: "0.0.0.0"
    #   port: 9000

# Каналы наружу (со стороны шлюза)
outcomes:
    internet:
        tcp:
            host: "8.8.8.8"
            port: 53
    # Или COM:
    # com:
    #   port: "/dev/ttyGS0"
    #   speed: 115200

# Прокси, которые запускает сервер
proxies:
    -   type: http
        bind: "0.0.0.0"
        port: 8080
    -   type: sock5
        bind: "0.0.0.0"
        port: 1080

# Статический TCP-форвардинг
tcpForwarding:
    -   localPort: 2222
        remoteHost: "example.com"
        remotePort: 22

# Сервисы и фильтрация хостов
services:
    -   tcp-connect:
            hosts:
                -   hosts: [ "*" ]
                    filterMode: include
```

## Запуск

```bash
# Сервер (на удалённом устройстве)
java -jar proxy-server/build/libs/proxy-server-jvm.jar

# Шлюз (на машине с выходом в сеть)
java -jar proxy-gateway/build/libs/proxy-gateway-jvm.jar

# Загрузка файла через COM-порт
java -jar file-upload-service/build/libs/file-upload-service-jvm.jar -p /dev/ttyGS0 -f /path/to/file
```

## Тестирование

```bash
./gradlew jvmTest          # JVM-тесты
./gradlew allTests         # Тесты на всех платформах
./gradlew :multiplexer:jvmTest  # Тесты конкретного модуля
```

Перед запуском интеграционных тестов требуется `tinyproxy`:

```bash
tinyproxy -c testing/tinyproxy.conf
```

## Развёртывание на Raspberry Pi

В Gradle есть таски для копирования JAR на Raspberry Pi (192.168.76.108):

```bash
./gradlew :proxy-server:copyToRaspberry
./gradlew :proxy-gateway:copyToRaspberry
```

## Docker

```bash
docker-compose up -d   # WebDAV-сервер для тестов на порту 8011
```

## Технологии

- **Kotlin** 2.3 + Kotlin Multiplatform
- **Ktor** 3.4 (сервер, клиент, CIO)
- **Koin** 4.1 (DI)
- **kotlinx-serialization** (JSON, Protobuf, YAML)
- **kotlinx-coroutines** + **kotlinx-io**
- **Clikt** (CLI)
- **jSerialComm** (COM-порты)
- **BlueCove** / **blue-falcon** / **bluez-dbus** (Bluetooth)

## Лицензия

Apache 2.0
