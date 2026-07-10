# Multiplexer Module — Review Tasks

## Error (6)

- [ ] **#1. Spinlock удерживается через suspend-вызов в createChannel() — гарантированный deadlock**
  - **Type:** Concurrency
  - **Severity:** Error
  - **Location:** `MultiplexerImpl.kt:89–106`
  - **Описание:** `pendingChannelsLock` захвачен ДО `sendRequestNewChannel` (suspend) и отпущен ПОСЛЕ. На однопоточном диспатчере — deadlock. При `ClosedSendChannelException` lock утекает навсегда.

- [x] **~~#2. Suspend в finally VirtualChannel без NonCancellable — close-команда теряется~~** ✅ FIXED
  - **Type:** Bug → **Concurrency**
  - **Severity:** ~~Error~~ → ~~Warning~~
  - **Location:** `MultiplexerImpl.kt:75–82`
  - **Описание:** `close()` вызывал `job.cancel()` → корутина отменялась → `finally` не мог выполнить suspend-вызов `sendCloseChannel`. **Фикс:** `close()` → `outcome.close(CancellationException(...))` — корутина завершается graceful через `consumeEach`, `finally` работает без CancellationException. Close-уведомление доходит.
  - **Тест покрывает:** `MultiplexerCyclesTest.test10Cycles` (10 create+close циклов), `MultiplexerRegressionTest.testCloseNotificationDelivered`

- [x] **~~#3. Race condition в createChannel() — данные могут прийти до регистрации в activeChannels~~** ✅ FIXED
  - **Type:** Concurrency
  - **Severity:** ~~Error~~ → ~~Fixed~~
  - **Location:** `MultiplexerImpl.kt:44–52, 119–130, 166–177`
  - **Описание:** DATA-пакет, пришедший ДО регистрации VirtualChannel в `activeChannels`, молча отбрасывался. **Фикс:** Добавлен `pendingData: HashMap<Int, MutableList<Buffer>>` с отдельным lock'ом. DATA для ещё не зарегистрированных каналов буферизуется. При `accept()`/`createChannel()` буфер сливается в `channel.income` через `trySend`.
  - **Тест покрывает:** `MultiplexerAcceptTest.testDataBeforeAccept`, `testMultipleDataBeforeAccept`, `testLargePayloadBeforeAccept`

- [ ] **#4. Утечка activeChannels при локальном закрытии VirtualChannel**
  - **Type:** Bug
  - **Severity:** Error
  - **Location:** `MultiplexerImpl.kt:77–81, 134–138`
  - **Описание:** VirtualChannel удаляется из `activeChannels` только при remote-close. Local `close()` оставляет запись навсегда. HashMap неограниченно растёт.

- [x] **~~#5. testCloseOutside ловит CancellationException, но send бросает ClosedSendChannelException~~** ✅ FIXED
  - **Type:** Bug
  - **Severity:** ~~Error~~ → ~~Fixed~~
  - **Location:** `MultiplexerTest.kt:116–118`
  - **Описание:** Исправлено в соседнем чате. Тест теперь проходит.

- [x] **~~#6. Тестовые зависимости (coroutines.test, kotlin test) объявлены в commonMain вместо commonTest~~** ✅ FIXED
  - **Type:** Bug
  - **Severity:** ~~Error~~ → ~~Fixed~~
  - **Location:** `build.gradle.kts`
  - **Описание:** Исправлено в соседнем чате. Зависимости перенесены в `commonTest`.

## Warning (11)

- [ ] **#7. DuplexChannel.cancel(cause: Throwable?) игнорирует параметр cause**
  - **Type:** Bug
  - **Severity:** Warning
  - **Location:** `DuplexChannel.kt:48–50`
  - **Описание:** `cancel(cause: Throwable?)` вызывает `income.cancel()` без передачи `cause`, теряя причину отмены.

- [ ] **#8. Неизвестная команда протокола молча игнорируется в when(cmd)**
  - **Type:** Security
  - **Severity:** Warning
  - **Location:** `MultiplexerProtocol.kt:101–123`
  - **Описание:** `when(cmd)` покрывает только 4 значения. Любая другая команда съедается, следующие байты читаются со смещением — возможна десинхронизация протокола.

- [ ] **#9. Нет cleanup при падении readJob — мультиплексор зависает**
  - **Type:** Bug
  - **Severity:** Warning
  - **Location:** `MultiplexerImpl.kt:152–196`
  - **Описание:** Если handler в readJob бросает non-CancellationException (напр. `ClosedSendChannelException`), readJob падает, а активные/pending каналы не очищены.

- [ ] **#10. consumeEach в reading() закрывает внешний input-канал как side effect**
  - **Type:** Maintainability
  - **Severity:** Warning
  - **Location:** `MultiplexerProtocol.kt:96–99`
  - **Описание:** `consumeEach` при завершении вызывает `cancel()` на physical-канале. Для внешнего владельца это неожиданно.

- [ ] **#11. Неравномерная обработка ошибок между командами протокола**
  - **Type:** Bug
  - **Severity:** Warning
  - **Location:** `MultiplexerProtocol.kt:109–127`
  - **Описание:** DATA-ветка обёрнута в `try-catch(Throwable)`, CHANNEL_CLOSE/REQUEST/ACCEPT — нет. Битый пакет в них убьёт весь read-цикл.

- [ ] **#12. DuplexChannel.cancel()/close() затрагивают только одну сторону**
  - **Type:** Bug
  - **Severity:** Warning
  - **Location:** `DuplexChannel.kt:64–81`
  - **Описание:** `cancel()` → `income.cancel()`, `close()` → `outcome.close()`. Пользователь, вызывающий `cancel()`, ожидает полную остановку, но outcome остаётся открыт.

- [ ] **#13. CancellationException логируется как READ FINISHED WITH ERROR**
  - **Type:** Maintainability
  - **Severity:** Warning
  - **Location:** `MultiplexerProtocol.kt:131–134`
  - **Описание:** `catch(Throwable)` перехватывает `CancellationException` при нормальной отмене и логгирует как ошибку.

- [ ] **#14. Busy-wait spinlock без backoff в корутинном контексте**
  - **Type:** Performance
  - **Severity:** Warning
  - **Location:** `AtomicBooleanExtensions.kt:7–13`
  - **Описание:** `while(true) { CAS }` блокирует поток диспатчера. При contention все потоки сжигают CPU. Рекомендуется `Mutex`.

- [ ] **#15. Race: close() очищает activeChannels до завершения readJob**
  - **Type:** Concurrency
  - **Severity:** Warning
  - **Location:** `MultiplexerImpl.kt:192–206`
  - **Описание:** `close()` вызывает `readJob.cancel()` (не мгновенно) затем `clear()`. readJob может успеть обработать DATA и отправить ложный `sendCloseChannel`.

- [x] **~~#16. VirtualChannel.close() vs close(cause) — разное поведение~~** ✅ FIXED
  - **Type:** Maintainability
  - **Severity:** ~~Warning~~ → ~~Fixed~~
  - **Описание:** `close()` теперь вызывает `outcome.close()` (graceful), а не `job.cancel()`. Поведение унифицировано с `close(cause)` из DuplexChannel.

- [ ] **#17. channelClosed handler — race между remove() и channel.close()**
  - **Type:** Concurrency
  - **Severity:** Warning
  - **Location:** `MultiplexerImpl.kt:138–143`
  - **Описание:** `activeChannels.remove(id)` под lock, `channel?.close()` без lock. Между ними возможен конфликт при переполнении Int.

## WeakWarning (10)

- [ ] **#18. Две мёртвые функции LEB128 в Leb.kt**
  - **Type:** Maintainability
  - **Severity:** WeakWarning
  - **Location:** `Leb.kt:60–114`
  - **Описание:** `EncodeLeb128` (42 строки) и `writeUnsignedLeb128` нигде не используются. `EncodeLeb128` имеет неиспользуемый параметр `len`.

- [ ] **#19. Typo: chanelJob вместо channelJob**
  - **Type:** Maintainability
  - **Severity:** WeakWarning
  - **Location:** `MultiplexerImpl.kt:35, 115`
  - **Описание:** Пропущена буква 'l' в имени переменной (дважды: `accept` и `createChannel`).

- [ ] **#20. Typo: coppingLogicalToPhysical вместо copyingLogicalToPhysical**
  - **Type:** Maintainability
  - **Severity:** WeakWarning
  - **Location:** `MultiplexerProtocol.kt:65`
  - **Описание:** Две 'p' в copping вместо одной — опечатка в публичном методе.

- [ ] **#21. bufferOf пишет ByteArray побайтово вместо bulk write**
  - **Type:** Performance
  - **Severity:** WeakWarning
  - **Location:** `Utils.kt:7–13`
  - **Описание:** `bytes.forEach { buffer.writeByte(it) }` — N вызовов вместо `buffer.write(bytes)`.

- [ ] **#22. Избыточная аллокация + копирование в wrapLogicalToPhysical**
  - **Type:** Performance
  - **Severity:** WeakWarning
  - **Location:** `MultiplexerProtocol.kt:80–89`
  - **Описание:** Каждая отправка аллоцирует новый Buffer и копирует payload через `readFully`. O(n) overhead на каждое сообщение.

- [ ] **#23. Channel(UNLIMITED) — отсутствие backpressure, риск OOM при перегрузке**
  - **Type:** Performance
  - **Severity:** WeakWarning
  - **Location:** `MultiplexerImpl.kt:39, 57–58`
  - **Описание:** `VirtualChannel.income`, `outcome` и `incomeChannels` — все UNLIMITED. При перегрузке буфер растёт без ограничений.

- [ ] **#24. Pattern duplication: три send-команды имеют идентичную структуру**
  - **Type:** Maintainability
  - **Severity:** WeakWarning
  - **Location:** `MultiplexerProtocol.kt:18–54`
  - **Описание:** `sendCloseChannel` / `sendRequestNewChannel` / `sendResponseNewChannel` — Buffer → writeByte → lebInt → send. Можно вынести в helper.

- [ ] **#25. Three when-ветки выполняют одинаковую последовательность**
  - **Type:** Maintainability
  - **Severity:** WeakWarning
  - **Location:** `MultiplexerProtocol.kt:114–121`
  - **Описание:** CHANNEL_CLOSE/REQUEST/ACCEPT: `buffer.lebInt` → log → handler. Можно вынести в inline-функцию.

- [ ] **#26. MultiplexerHolder.close() — race между load() и close()**
  - **Type:** Concurrency
  - **Severity:** WeakWarning
  - **Location:** `MultiplexerHolder.kt:27–29`
  - **Описание:** `instance.load()?.close()` — другой поток может вызвать `remove()` между load и close.

- [ ] **#27. invokeOnClose делегирует только outcome, игнорируя income**
  - **Type:** Design
  - **Severity:** WeakWarning
  - **Location:** `DuplexChannel.kt:19`
  - **Описание:** `invokeOnClose(handler)` срабатывает только при закрытии outcome.

## Info (4)

- [ ] **#28. Int overflow при 2 млрд созданий каналов**
  - **Type:** Maintainability
  - **Severity:** Info
  - **Location:** `MultiplexerImpl.kt:32`
  - **Описание:** `idGenerator.addAndFetch(2)` переполнится после ~2B каналов. Вероятность ничтожна.

- [ ] **#29. LEB128 readUnsigned не дочитывает лишние байты при превышении maxBits**
  - **Type:** Security
  - **Severity:** Info
  - **Location:** `Leb.kt:4–19`
  - **Описание:** При битом LEB128 с бесконечной continuation лишние байты не дочитываются — смещение.

- [ ] **#30. MultiplexerEvent.kt дублирует сигнатуры handler'ов reading()**
  - **Type:** Maintainability
  - **Severity:** Info
  - **Location:** `MultiplexerEvent.kt:1–44`
  - **Описание:** 44 строки тестового кода оборачивают handler'ы в `Channel<MultiplexerEvent>`.

- [ ] **#31. Тяжёлое @Suppress — хрупкость при обновлении корутин**
  - **Type:** Maintainability
  - **Severity:** WeakWarning
  - **Location:** `DuplexChannel.kt:7`
  - **Описание:** `@Suppress("DEPRECATION_ERROR", "INVISIBLE_REFERENCE", ...)` отключает инкапсуляцию.

---

## Статус

| Категория | Всего | Осталось | Исправлено |
|-----------|-------|----------|------------|
| Error | 6 | 2 (#1, #4) | 4 (#2, #3, #5, #6) |
| Warning | 11 | 10 | 1 (#16) |
| WeakWarning | 10 | 10 | 0 |
| Info | 4 | 4 | 0 |
| **Всего** | **31** | **26** | **5** |

**Тестов:** 21, все проходят.
