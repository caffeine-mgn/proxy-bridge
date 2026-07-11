# Tasks — proxy-server (FS Proxy)

## Chunked streaming

- [x] 1. Изменить `WebDavFileSystem.readFile`
- [x] 2. Изменить `WebDavFileSystem.writeFile`
- [x] 3. Обновить `LocalFileSystem` под новые сигнатуры
- [x] 4. Обновить `MountedFileSystem` — пробросить новые сигнатуры
- [x] 5. Обновить `WebDavFileSystemService` — пробросить новые сигнатуры
- [x] 6. Обновить `FileChannel` — чанковый протокол
- [x] 7. Обновить `RemoteWebDavFileSystem`
- [x] 8. Обновить `WebDavModule` (веб-сервер)
- [x] 16. Обновить существующие тесты

## Мульти-FS и WebDAV

- [x] 9. Добавить поле `fsName` в протокол `FileChannel`
- [x] 10. `Merged` FS тип + `MountedFileSystem` композиция
- [x] 11. `Local`, `Remote`, `Merged` в `ConfigModule`
- [x] 12. `fileSystems` + `WebDav` сервис в `Configuration`
- [x] 13. `ConfigModule` — полная регистрация FS и WebDav
- [x] 14. `Main.kt` — убрать хардкод
- [x] 15. `WebDavServer` — новый на CIO + `WebDavFileSystem`
- [ ] 17. Удалить старый `WebDavServer` (закомментированный 300 строк)
- [ ] 18. Интеграционный тест: `RemoteWebDavFileSystem` через `FileChannel`
