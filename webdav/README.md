# WebDAV сервер

Независимый подпроект. Реализует WebDAV сервер на Ktor + kotlinx.io.

## План работ

### 1. [x] Спроектировать и реализовать FS Interface (`pw.binom.webdav.fs`)

Абстракция файловой системы, не привязанная к JVM. Использует `kotlinx.io.files.Path`.

Методы:
- `list(path)` — листинг содержимого директории
- `readFile(path, range)` — чтение файла (с опциональным range)
- `writeFile(path, content, overwriteMode)` — запись/создание файла
- `createDirectory(path)` — MKCOL
- `delete(path)` — удаление файла/директории
- `move(src, dst)` — перемещение
- `copy(src, dst)` — копирование
- `getMetadata(path)` — размер, mtime, etag, isDirectory, isFile
- `exists(path)` — проверка существования

### 2. [x] Реализовать LocalFileSystem (`pw.binom.webdav.fs.local`)

Имплементация FS Interface через `kotlinx.io.files.SystemFileSystem`.

Без `java.io.File`, без JVM NIO2. Чистый kotlinx-io.

### 3. [x] Реализовать Ktor WebDAV Module (`pw.binom.webdav.server`)

Ktor-плагин (extension function на `Routing`), который принимает FS Interface и обрабатывает WebDAV методы:

- **PROPFIND** — получение свойств файлов/директорий (XML в формате WebDAV)
- **GET** — скачивание файла (с поддержкой Range, ETag, If-Modified-Since)
- **PUT** — загрузка файла (с поддержкой If-Match для optimistic locking)
- **DELETE** — удаление
- **MKCOL** — создание коллекции (директории)
- **MOVE** — перемещение/переименование
- **COPY** — копирование
- **LOCK / UNLOCK** — блокировки (можно заглушку, т.к. многие клиенты требуют)

### 4. [x] Добавить зависимости в `build.gradle.kts`

Модуль — библиотека, engine для запуска не нужен (предоставляется проектом-потребителем).

Подключено:
- `kotlinx-io-core` — для FS интерфейса и LocalFileSystem
- `ktor-server-core` — для Ktor модуля
- `ktor-server-test-host` — для интеграционных тестов

### 5. [x] Написать тесты

- Unit-тесты для LocalFileSystem (создание/удаление/чтение/запись файлов во временной директории) — 8 тестов
- Интеграционные тесты для Ktor модуля (запуск тестового сервера на Netty, HTTP-запросы WebDAV методов) — 11 тестов

## Технический долг

- **`lastModified` всегда 0** — `kotlinx.io.files.FileMetadata` (kotlinx-io 0.9.0) не хранит mtime. `LocalFileSystem` возвращает 0. Из-за этого `Last-Modified` не отправляется, а ETag считается от `"0-{size}"` и не меняется при изменении контента. Нужен отдельный слой метаданных или PR в kotlinx-io.
- **Кривой отступ в `WebDavModule.kt`** — код внутри `installWebDavHandlers` имеет отступ 12 пробелов (осталось от старой вложенности). Работоспособности не мешает.
- **Хардкод порта 18923 в тестах** — если порт занят, тесты упадут.
- **Диагностические маршруты `/ping` и `/dav/ping2` торчат в тестовом сервере** — не влияют на WebDAV, но лучше убрать.
- **`build.gradle.kts`** — мусор: неиспользуемая переменная `nativeEntryPoint`, закомментированные таргеты `linuxX64`/`mingwX64`, закомментированный `jvmTarget`.
