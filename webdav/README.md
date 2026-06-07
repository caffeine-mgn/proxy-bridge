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

### 2. [ ] Реализовать LocalFileSystem (`pw.binom.webdav.fs.local`)

Имплементация FS Interface через `kotlinx.io.files.SystemFileSystem`.

Без `java.io.File`, без JVM NIO2. Чистый kotlinx-io.

### 3. [ ] Реализовать Ktor WebDAV Module (`pw.binom.webdav.server`)

Ktor-плагин (extension function на `Routing`), который принимает FS Interface и обрабатывает WebDAV методы:

- **PROPFIND** — получение свойств файлов/директорий (XML в формате WebDAV)
- **GET** — скачивание файла (с поддержкой Range, ETag, If-Modified-Since)
- **PUT** — загрузка файла (с поддержкой If-Match для optimistic locking)
- **DELETE** — удаление
- **MKCOL** — создание коллекции (директории)
- **MOVE** — перемещение/переименование
- **COPY** — копирование
- **LOCK / UNLOCK** — блокировки (можно заглушку, т.к. многие клиенты требуют)

### 4. [ ] Добавить зависимости в `build.gradle.kts`

- `kotlinx-io` (уже есть в проекте, но в webdav не подключён)
- `ktor-server-core` (уже есть)
- `ktor-server-cio` или `ktor-server-netty` для запуска
- `kotlinx-serialization-xml` — для формирования WebDAV XML ответов (PROPFIND)

### 5. [ ] Написать тесты

- Unit-тесты для LocalFileSystem (создание/удаление/чтение/запись файлов во временной директории)
- Интеграционные тесты для Ktor модуля (запуск тестового сервера, HTTP-запросы WebDAV методов)
