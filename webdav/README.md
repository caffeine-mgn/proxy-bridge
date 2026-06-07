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

### 6. [ ] MountedFileSystem — агрегирующая ФС (`pw.binom.webdav.fs.mounted`)

Реализация `WebDavFileSystem`, которая умеет объединять несколько других `WebDavFileSystem`,
монтируя их в разные точки пути.

```
val mounted = MountedFileSystem().apply {
    mount("/", localFs)        // default — всё что не подошло
    mount("/remote", remoteFs) // /remote/... → remoteFs
    mount("/home/user", usbFs) // /home/user/... → usbFs, приоритетнее /home
}
```

Подзадачи:

- **6.1. Mount entry** — модель данных: точка монтирования (путь) + экземпляр `WebDavFileSystem`
- **6.2. Резолвинг** — по произвольному пути найти наиболее специфичную точку монтирования и вычислить относительный путь для делегирования. `/remote/test/file.txt` смонтирован на `/remote` → делегировать `fs.getMetadata("/test/file.txt")`.
- **6.3. Реализация методов `WebDavFileSystem`** — каждый метод ищет подходящий mount и делегирует вызов. Если mount не найден — `Result.failure`.
- **6.4. Mount/unmount** — `mount(path, fs)` и `unmount(path)`.
- **6.5. MOVE/COPY между разными mount'ами** — если source и destination в разных mount'ах, fallback через read+write+delete. Если в одном — прямой вызов.
- **6.6. Тесты** — юнит-тесты на резолвинг, делегирование, move/copy cross-mount, unmount.

## Технический долг

- **`lastModified` всегда 0** — `kotlinx.io.files.FileMetadata` (kotlinx-io 0.9.0) не хранит mtime. `LocalFileSystem` возвращает 0. Из-за этого `Last-Modified` не отправляется, а ETag считается от `"0-{size}"` и не меняется при изменении контента. Нужен отдельный слой метаданных или PR в kotlinx-io.
- **Кривой отступ в `WebDavModule.kt`** — код внутри `installWebDavHandlers` имеет отступ 12 пробелов (осталось от старой вложенности). Работоспособности не мешает.
- **Хардкод порта 18923 в тестах** — если порт занят, тесты упадут.
- **Диагностические маршруты `/ping` и `/dav/ping2` торчат в тестовом сервере** — не влияют на WebDAV, но лучше убрать.
- **`build.gradle.kts`** — мусор: неиспользуемая переменная `nativeEntryPoint`, закомментированные таргеты `linuxX64`/`mingwX64`, закомментированный `jvmTarget`.
