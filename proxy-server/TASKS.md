# Tasks — proxy-server (FS Proxy)

## Chunked streaming

- [x] 1. Изменить `WebDavFileSystem.readFile` — сигнатура `suspend fun readFile(path, range, onChunk: suspend (ByteArray) -> Unit): Result<Unit>`
- [x] 2. Изменить `WebDavFileSystem.writeFile` — сигнатура `suspend fun writeFile(path, overwrite, nextChunk: suspend () -> ByteArray?): Result<Unit>`
- [x] 3. Обновить `LocalFileSystem` под новые сигнатуры (чтение/запись чанками через SystemFileSystem)
- [x] 4. Обновить `MountedFileSystem` — пробросить новые сигнатуры readFile/writeFile
- [x] 5. Обновить `WebDavFileSystemService` — пробросить новые сигнатуры
- [x] 6. Обновить `FileChannel`:
      - `readFile` client: шлёт запрос, читает чанки из `income`, дёргает `onChunk`
      - `writeFile` client: шлёт запрос, пишет чанки через `nextChunk` в `outcome`, завершает `lebInt(0)`
      - `handleReadFile` server: после ok/totalSize читает чанками из ФС, шлёт `[lebInt][data]`, завершает `lebInt(0)`
      - `handleWriteFile` server: после metadata читает чанки `[lebInt][data]` до `0`, пишет в ФС
- [x] 7. Обновить `RemoteWebDavFileSystem` — чанковый протокол через `createChannel`
- [x] 8. Обновить `WebDavModule` (веб-сервер) — `readFile` через `onChunk` (собирает чанки в ответ, или стримит), `writeFile` через `nextChunk` (читает из запроса чанками)
- [x] 16. Обновить существующие тесты `LocalFileSystemTest`, `MountedFileSystemTest` под новые сигнатуры

## Мульти-FS и маршрутизация

- [ ] 9. Добавить поле `fsName` в протокол `FileChannel`: все сообщения начинаются с `[lebString:fsName]` после `[ID=2]`. `income` ищет `WebDavFileSystem` по имени в DI.
- [ ] 10. Создать `FileSystemRouter` — имплементация `WebDavFileSystem`, маршрутизирует по правилам `ByPrefix` / `Always`, аналог `TcpConnectService`
- [ ] 11. Создать `FileSystemProvider` — sealed hierarchy: `Local(root)`, `Remote(outcome, fs)`, `Merged(layers)`
- [ ] 12. Добавить секции `fileSystems` и `fileSystemRoutes` в `Configuration`
- [ ] 13. Обновить `ConfigModule.createModule` — регистрация FS-компонентов в DI
- [ ] 14. Обновить `Main.kt` — подключить FS-модули

## Тесты

- [ ] 15. Написать тесты на `FileChannel` с чанковым протоколом
- [ ] 16. Обновить существующие тесты `LocalFileSystemTest`, `MountedFileSystemTest` под новые сигнатуры
- [ ] 17. Интеграционный тест: `RemoteWebDavFileSystem` через `FileChannel` — запись и чтение чанками
