# Multiplexer — проблемы и улучшения

> Результат полного ревью кода модуля `multiplexer` (июль 2025).

---

## ERROR

- [x] **#1** **Deadlock при close() — Mutex не reentrant**
  `activeChannelsMutex` захватывается в `MultiplexerImpl.close()`, затем вызывается `channel.close()` → `outcome.close()` → `income.invokeOnClose` → `job.cancel()` → `job.finally` блок пытается захватить тот же `activeChannelsMutex.withLock { activeChannels.remove(id) }`.  
  Mutex из kotlinx.coroutines НЕ reentrant — поток зависает навсегда.  
  Аналогичная проблема в `readJob.catch(e:Throwable)` — блоки ~189–195.  
  **Решение:** либо выносить `remove` за пределы Mutex, либо использовать Mutex с рекурсивной семантикой (отсутствует в kotlinx.coroutines).

- [x] **#2** **createChannel() — ручной lock/unlock без finally — утечка мьютекса при отмене**
  `pendingChannelsMutex.lock()` в строке 129, затем `unlock()` вручную внутри `suspendCancellableCoroutine`. Если корутина отменяется ДО вызова `unlock()`, мьютекс остаётся заблокированным навсегда.  
  **Решение:** использовать `withLock` либо гарантировать unlock через try/finally.

## WARNING

- [x] **#3** **AtomicBoolean spinlock (busy-wait) в pendingDataLock**
  `AtomicBoolean.lock()` реализует busy-wait spinlock без yield/park — сжигает CPU ядро при конкуренции.  
  Хотя в памяти предыдущего ревью сказано, что все spinlock'и заменены на Mutex, `pendingDataLock` остался на AtomicBoolean.  
  **Решение:** заменить на `Mutex` из kotlinx.coroutines.sync (как сделано для `activeChannelsMutex`).

- [x] **#4** **MultiplexerHolder.close() — busy-wait CAS spinlock**
  `while (true) { val m = instance.load(); if (m == null || instance.compareAndSet(m, null)) { m?.close(); break } }` — busy-wait loop.  
  **Решение:** заменить на Mutex или переписать проще (single-threaded предполагается).

- [x] **#5** **CoroutineScope не закрывается — утечка корутин**
  `MultiplexerImpl(...)` принимает `ioCoroutineScope`, но не сохраняет его для очистки.  
  При `close()` job'ы отменяются через `readJob.cancel()`, но сама дочерняя корутина `VirtualChannel.job` запускается через `ioCoroutineScope.launch` — после закрытия всех каналов scope не отменяется.  
  В перспективе может привести к утечке при пересоздании мультиплексоров.

- [ ] **#6** **`consumeEach` закрывает input при завершении — несоответствие поведения**  
  `MultiplexerProtocol.copyingLogicalToPhysical` использует `consumeEach`, который при завершении (нормальном или ошибке) закрывает `logical` (канал outcome).  
  В `VirtualChannel.job.finally` вызывается `outcome.close(e)`, который на уже закрытом канале — no-op. Работает, но семантика неочевидная.  
  **Решение:** использовать ручной `while`-цикл (по аналогии с `reading()`).

- [ ] **#7** **Buffer allocation на каждую команду/пакет**  
  `sendCommand()` (строка 17) создаёт новый `Buffer()` на каждую команду.  
  `wrapLogicalToPhysical()` (строка 65) создаёт новый `Buffer()` на каждый DATA-пакет.  
  При высокой нагрузке (сотни тысяч пакетов/сек) — избыточный GC pressure.  
  **Решение:** рассмотреть пул буферов или переиспользование при известном максимальном размере.

- [ ] **#8** **logger.info для каждого accept/createChannel/close — многословно в production**  
  `sendCloseChannel`, `sendRequestNewChannel`, `sendResponseNewChannel`, `handleChannelEvent` — все логируют `info` на каждое событие, включая `channelId`.  
  При 100+ каналов/сек это десятки лог-строк.  
  **Решение:** понизить до `debug` или сделать конфигурируемым.

## WEAK WARNING

- [ ] **#9** **readJob крашится с CancellationException — неоптимальный catch**  
  Строка ~175: `catch (e: CancellationException) { throw e }` перехватывает, логически ничего не делает и пробрасывает.  
  Можно просто не ловить — CancellationException не остановит supervisorScope.  
  **Решение:** удалить catch CancellationException или оставить комментарий.

- [ ] **#10** **Typo: `chanelJob` вместо `channelJob`**  
  `MultiplexerImpl.createChannel()` строка 147: `val chanelJob = VirtualChannel(...)`.  
  **Решение:** переименовать в `channelJob`.

- [ ] **#11** **Typo: `water` вместо `waiter`**  
  `MultiplexerImpl` строка 161: `val water = pendingChannelsMutex.withLock { ... }`.  
  **Решение:** переименовать в `waiter` (или `continuation`).

- [ ] **#12** **Dead code: `Leb.writeUnsignedLeb1282`**  
  Функция никогда не вызывается напрямую — `writeUnsignedLeb1282` используется только через `Sink.lebULong`, который в свою очередь не вызывается нигде в `commonMain`.  
  **Решение:** либо удалить, либо пометить `@PublishedApi internal`, если планируется экспорт.

- [ ] **#13** **Dead code: `SourceExtensions.lebULong/lebUInt/lebLong/boolean/lebString/list/nullable`**  
  Все эти функции не используются в `commonMain`.  
  **Решение:** удалить или переместить в отдельный файл для внешнего использования.

- [ ] **#14** **Dead code: `SinkExtensions.lebUInt/lebULong/lebLong/lebString/boolean/list/nullable`**  
  Аналогично #13 — не используются в `commonMain`.  
  **Решение:** удалить или переместить.

- [ ] **#15** **Dead code: `RawSourceExtensions.kt`**  
  Весь файл `RawSourceExtensions.kt` (функция `readFully`) не используется нигде в модуле.  
  **Решение:** удалить или перенести туда, где используется.

- [ ] **#16** **`MultiplexerProtocol.HandlerOnChannel` — functional interface не нужен**  
  `fun interface HandlerOnChannel` и `HandlerOnData` — используются как обычные SAM-интерфейсы.  
  Лучше заменить на простые suspend-лямбды типа `suspend (channelId: Int) -> Unit`, убрав лишние интерфейсы.

- [ ] **#17** **`MultiplexerImpl.readJob` — supervisorScope оборачивает всю reading(), включая CancellationException**  
  supervisorScope не передаёт CancellationException детям, что правильно. Но в `reading()` CancellationException ловится и swallowing не происходит.  
  В итоге `reading()` выходит по CancellationException → supervisorScope завершается нормально → readJob завершается.  
  Потенциально запутанная цепочка — стоит упростить.

- [x] **#18** **`VirtualChannel.job.finally` отправляет `sendCloseChannel` после закрытия output**
  `outcome.close(e)` вызывается до `sendCloseChannel(channelId, physical=output)`. Если outcome — это тот же Physical канал, то send по закрытому каналу упадёт.  
  Хотя есть `catch (_: Throwable)` — это best-effort. Лучше сначала отправить close, потом чистить локальные каналы.

- [ ] **#19** **`drainPendingData` использует `trySend` вместо `send`**  
  `trySend` для UNLIMITED канала всегда успешен, так что разницы нет.  
  Но семантически `send` (suspend) точнее — если когда-то канал перестанет быть UNLIMITED, `trySend` молча потеряет данные.  
  **Решение:** использовать `send`.

- [ ] **#20** **Нет проверки на переполнение Int при LEB128-декодинге**  
  `Source.lebInt()` использует `maxBits = Int.SIZE_BITS = 32`. LEB128-поток может содержать значение > 2³¹-1 (signed) — в этом случае результат обрежется до Int silently.  
  **Решение:** добавить валидацию диапазона или проверять в MultiplexerProtocol.

## INFO

- [ ] **#21** **`DuplexChannel` — `@Suppress` для `DEPRECATION_ERROR`, `INVISIBLE_MEMBER` и др.**  
  Подавление ошибок компиляции для переопределения методов `offer`, `poll`, `onReceiveOrNull` — очень хрупко.  
  При обновлении kotlinx.coroutines может перестать компилироваться.  
  **Решение:** задокументировать, на какой версии корутин работает, и мониторить при обновлениях.

- [ ] **#22** **Тесты используют `newSingleThreadContext` + `runBlocking` для изоляции — дублирование**  
  `MultiplexerRegressionTest`, `MultiplexerAcceptTest`, `MultiplexerCyclesTest` — везде копипаста вспомогательной функции.  
  **Решение:** вынести общий хелпер в `Utils.kt`.

- [ ] **#23** **Интеграционные тесты без `withTimeout`**  
  `MultiplexerIntegrationTest` запускает все тесты в `runBlocking` без `withTimeout`.  
  При deadlock'е (см. #1) тест зависает навсегда и не падает с таймаутом.  
  **Решение:** добавить `withTimeout`.

- [ ] **#24** **LEB128-декодинг дочитывает continuation-байты при overflow**  
  `readUnsigned` и `readSigned` при превышении `maxBytes` продолжают читать байты, чтобы сохранить выравнивание.  
  Если данные повреждены (бесконечный поток continuation-байтов), цикл не остановится.  
  **Решение:** добавить лимит на количество дочитываемых байт.

- [ ] **#25** **`MultiplexerImpl` принимает `Channel<Buffer>`, но протокол использует `ReceiveChannel`/`SendChannel`**  
  Конструктор принимает конкретную реализацию `Channel<Buffer>`, хотя поля `input`/`output` типизированы как интерфейсы.  
  **Решение:** принимать интерфейсы, а не конкретную реализацию.

- [ ] **#26** **Двойное логирование в `handleChannelEvent` и `reading`**  
  В `reading()` для DATA: логирование через `logger.error(e)` при ошибке.  
  В `handleChannelEvent`: то же самое. При ошибке CHANNEL_CLOSE/REQUEST/ACCEPT будет два лога — один внутри `handleChannelEvent`, второй... нет, `handleChannelEvent` не в try-catch вызывающего. Так что только один.  
  Но для DATA есть try-catch в вызывающем коде + внутри handler — избыточно.

- [ ] **#27** **Нет тестов для MultiplexerHolder**  
  `MultiplexerHolder` — важный infrastructure класс, но не покрыт тестами.  
  **Решение:** добавить unit-тесты.
