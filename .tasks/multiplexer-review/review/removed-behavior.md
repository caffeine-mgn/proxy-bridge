# Removed-Behavior Review: multiplexer module

Found issues — missing error handling, validation, cleanup, and guards in `multiplexer/src/commonMain/kotlin/pw/binom/multiplexer/`.

---

## Error 1: Spinlock held across suspension in `createChannel()` — guaranteed deadlock

**Файл:** `MultiplexerImpl.kt : 73–89`
**Severity:** Error

`pendingChannelsLock.lock()` (строка 75) захватывает спин-блокировку, затем строкой 77 вызывается `MultiplexerProtocol.sendRequestNewChannel(...)`, которая внутри вызывает `physical.send(resultBuffer)` — **suspend-функцию**. На однопоточном диспатчере (или при переиспользовании потока пулом) другой корутине, пытающейся захватить тот же `pendingChannelsLock` через `.locking{}`, достанется тот же поток, и он навечно зациклится в `lock()` (строка 9 `AtomicBooleanExtensions.kt`), потому что holder висит на той же нити в suspend. Это **гарантированный дедлок**.

Рекомендация: перенести `sendRequestNewChannel` за пределы блокировки — либо отправлять запрос уже после освобождения лока, либо использовать неблокирующую очередь.

---

## Error 2: Race window in `createChannel()` — ACCEPT может прийти до регистрации continuation

**Файл:** `MultiplexerImpl.kt : 77–89`
**Severity:** Error

После вызова `sendRequestNewChannel` (строка 77) код входит в `suspendCancellableCoroutine` и регистрирует continuation в `pendingChannels`. Между этими двумя точками есть окно, за которое remote-сторона успевает ответить `ACCEPT_NEW_CHANNEL`. Ответный хендлер `newChannelAccepted` (строка 107) не найдёт запись в `pendingChannels`, пошлёт `sendCloseChannel` (ложное закрытие), а continuation останется навечно висеть в map и никогда не возобновится.

Рекомендация: регистрировать continuation **до** отправки запроса, или сделать атомарную операцию «отправил + зарегистрировал».

---

## Error 3: Unknown protocol command silently ignored

**Файл:** `MultiplexerProtocol.kt : 97–130`
**Severity:** Error

`when(cmd)` покрывает только 4 значения (DATA=1, CHANNEL_CLOSE=2, REQUEST_NEW_CHANNEL=3, ACCEPT_NEW_CHANNEL=4). При получении любой другой команды (битый протокол, злонамеренный пакет, будущая версия) отсечка `when` ничего не делает и не логирует — байт просто отбрасывается. Отсутствует валидация протокола на уровне чтения.

Рекомендация: добавить `else`-ветку с `logger.warn { "Unknown command: $cmd" }`.

---

## Error 4: `VirtualChannel.job.finally` может выбросить в cancelled-корутине

**Файл:** `MultiplexerImpl.kt : 60–68`
**Severity:** Error

Блок `finally` (строка 62) вызывает suspend-функцию `MultiplexerProtocol.sendCloseChannel(...)`. Если `job` был отменён (через `income.invokeOnClose -> job.cancel()`), выполнение `finally` находится в cancelled-корутине, и `sendCloseChannel` (содержащая `physical.send()`) выбросит `CancellationException` на первой же suspension point. Таким образом, `sendCloseChannel` может никогда не дойти до physical-канала, и remote-сторона не узнает о закрытии логического канала.

Рекомендация: обернуть весь `finally` в `withContext(NonCancellable) { ... }`.

---

## Error 5: `close()` не защищён от повторного вызова

**Файл:** `MultiplexerImpl.kt : 153–167`
**Severity:** Error

`close()` не имеет `closed`-флага. При повторном вызове:
- `readJob.cancel()` — OK (иденпотентно)
- `activeChannelsLock.locking { ... }` — может наткнуться на уже очищенный `HashMap` (safe для `clear()`, но `values.forEach` на пустом — тоже safe)
- `pendingChannelsLock.locking { ... }` — пойдёт по той же логике
- `incomeChannels.cancel()` — OK

Однако ConcurrentModificationException невозможен благодаря блокировке. Основная проблема — повторный вызов может выполнить лишнюю работу и усложнить отладку. Рекомендуется добавить `private val closed = AtomicBoolean(false)`.

---

## Error 6: `readJob` не запускает cleanup при не-отменной ошибке

**Файл:** `MultiplexerImpl.kt : 93–120`
**Severity:** Error

`readJob` (строка 93) запущен в `ioCoroutineScope` без какого-либо механизма восстановления. Если `reading()` выбросит исключение (кроме CancellationException), `readJob` упадёт, `supervisorScope` не перезапустит его, и мультиплексор останется в полужизненном состоянии: `activeChannels` не очищены, `pendingChannels` брошены, `incomeChannels` висит, а `VirtualChannel.job`-корутины продолжают писать в `output`. При этом `close()` никто не вызвал, и leak-детекции нет.

Рекомендация: добавить `readJob.invokeOnCompletion { cause -> if (cause != null) close() }`, либо перезапускать `readJob` через `SupervisorJob` + retry-логику.

---

## Warning 1: `handlerOnData` ловит только `CancellationException`

**Файл:** `MultiplexerImpl.kt : 97–101`
**Severity:** Warning

В `handlerOnData`: `channel.income.send(data)` завернут в `try-catch (e: CancellationException)`. Любой другой exception (например, из-за закрытого `income`-канала, который может кинуть `ClosedSendChannelException`) **не ловится**, и `readJob` падает.

Рекомендация: либо расширить catch до `e: Throwable`, либо, если канал закрыт, удалять его из `activeChannels` в catch-блоке.

---

## Warning 2: `DuplexChannel.cancel()` не закрывает `outcome`

**Файл:** `DuplexChannel.kt : 64–71`
**Severity:** Warning

`override fun cancel(cause: Throwable?): Boolean` вызывает только `income.cancel()`, не трогая `outcome`. Если пользователь вызывает `cancel()` на DuplexChannel (ожидая полную остановку), send-side остаётся открытым. Аналогично, `close()` (строка 76) закрывает только `outcome`, а `income` висит.

Рекомендация: `cancel()` должен закрывать оба направления: `income.cancel(cause); outcome.close(cause)`. `close()` должен тоже закрывать оба.

---

## Warning 3: `reading()` не логирует команды без data — при ошибке данных обрывается чтение

**Файл:** `MultiplexerProtocol.kt : 96–138`
**Severity:** Warning

Только DATA-обработчик (строка 100) защищён try-catch. Если `CHANNEL_CLOSE`, `REQUEST_NEW_CHANNEL` или `ACCEPT_NEW_CHANNEL` хендлеры выбросят исключение (например, `incomeChannels.send()` в `requestChannel` упадёт, если `incomeChannels` закрыт), `physical.consumeEach` упадёт, и весь `reading` цикл завершится.

Рекомендация: обернуть каждую ветку `when` в индивидуальный try-catch, либо добавить общий catch вокруг `when`.

---

## Warning 4: `readJob` не сигнализирует о завершении с ошибкой

**Файл:** `MultiplexerImpl.kt : 93`
**Severity:** Warning

`readJob` после завершения (в т.ч. из-за ошибки) не уведомляет внешний код. Метод `reading()` (строка 132) ловит `Throwable` и пишет в лог, но возвращается нормально. `readJob` завершается успешно, даже если физический канал был аварийно закрыт. Внешний код этого не видит.

Рекомендация: `readJob` должен либо перебрасывать исключение (без `supervisorScope`), либо сохранять причину завершения в поле `closeCause`.

---

## Warning 5: `handlerOnData` при unknown channel шлёт `sendCloseChannel` без валидации

**Файл:** `MultiplexerImpl.kt : 95–96`
**Severity:** Warning

Если data-пакет приходит для несуществующего channelId, код отправляет `sendCloseChannel` обратно. Это корректное поведение, но без какого-либо rate-limiting это может быть DoS-вектором: злоумышленник может генерировать множество data-пакетов с невалидными channelId, заставляя мультиплексор отправлять ответные close-пакеты.

---

## Warning 6: `close()` не отменяет `ioCoroutineScope`

**Файл:** `MultiplexerImpl.kt : 153–167`
**Severity:** Warning

После `close()` в `ioCoroutineScope` могут остаться активные корутины (`VirtualChannel.job`), если они не были захвачены `activeChannels.forEach { it.close() }` (например, если `createChannel` был выполнен асинхронно между итерацией и cleanup). Scope передаётся снаружи и не отменяется.

Рекомендация: документировать, что `ioCoroutineScope` должен быть отменён вызывающей стороной. Альтернативно — создать дочерний scope внутри `MultiplexerImpl` и отменять его.

---

## Warning 7: `AtomicBoolean.lock()` — busy-wait без backoff

**Файл:** `AtomicBooleanExtensions.kt : 7–13`
**Severity:** Warning

`lock()` выполняет `while (true) { if (compareAndSet(false, true)) break }` — tight spinlock без `yield()`, `sleep()` или exponential backoff. Если lock удерживается долго, процессорное ядро сжигается впустую. При одновременном конкурентном доступе >2 потоков CAS-конкуренция растёт квадратично.

Рекомендация: добавить `Thread.yield()` в тело цикла или экспоненциальный backoff.

---

## Warning 8: `channelClosed` handler не удаляет VirtualChannel из `activeChannels` атомарно с закрытием job

**Файл:** `MultiplexerImpl.kt : 103–106`
**Severity:** Warning

В `channelClosed`-хендлере `activeChannels.remove(channelId)` и `channel?.close()` выполняются без единой блокировки: `remove` под lock, а `close` (который вызывает `job.cancel()`) — уже без него. Между remove и close другой код может создать новый VirtualChannel с тем же ID (через `accept()`). Хотя ID теоретически уникальны (инкремент по 2), при переполнении Int ID могут совпасть.

Рекомендация: перенести `channel.close()` внутрь `locking{}`, или проверять, что канал всё ещё актуален.

---

## WeakWarning 1: `MultiplexerHolder.close()` — race с `set()`/`remove()`

**Файл:** `MultiplexerHolder.kt : 27`
**Severity:** WeakWarning

`close()` вызывает `instance.load()?.close()`, но между `load()` и `.close()` другой поток может вызвать `remove()` или `set()`. Корректно для текущего использования, но стоило бы атомарно сравнивать и закрывать через CAS + compare-and-swap (например, `AtomicReference.getAndSet(null)?.close()`).

---

## WeakWarning 2: `incomeChannels` — отсутствие backpressure

**Файл:** `MultiplexerImpl.kt : 39`
**Severity:** WeakWarning

`incomeChannels = Channel(UNLIMITED)` может расти без ограничений, если вызывающая сторона редко вызывает `accept()`. Remote-сторона может слать `REQUEST_NEW_CHANNEL` быстрее, чем local-сторона их принимает, приводя к неограниченному росту памяти.

Рекомендация: добавить `Channel(Channel.BUFFERED)` с капасити или документировать требование своевременного вызова `accept()`.

---

## WeakWarning 3: `Leb.kt` — дублирующие неиспользуемые функции

**Файл:** `Leb.kt : 60, 73`
**Severity:** WeakWarning

Функции `writeUnsignedLeb128` (строка 60) и `EncodeLeb128` (строка 73) не используются нигде в проекте. Они только загромождают код и создают путаницу с `writeUnsignedLeb1282` и `writeSignedLeb128`.

---

## WeakWarning 4: `ReadFile` не обрабатывает `EOFException` мягко

**Файл:** `RawSourceExtensions.kt : 9–15`
**Severity:** WeakWarning

`readFully` бросает `EOFException` при недочтении. В контексте сетевого чтения это ожидаемо, но было бы надёжнее проверять `len == -1L` после каждого вызова `readAtMostTo` не только для аккуратного сообщения, но и для возможности graceful retry при частичном чтении.

---

## Summary

Найдено **19 проблем**: 6 Error, 8 Warning, 4 WeakWarning.

Ключевые критические ошибки:

1. **Дедлок** в `createChannel()`: spinlock удерживается через suspend-функцию `sendRequestNewChannel` — гарантированный deadlock на ограниченном пуле потоков.
2. **Race condition** в `createChannel()`: ACCEPT может прийти до регистрации continuation — потерянный ответ и навсегда висящий канал.
3. **Отсутствует валидация протокола**: неизвестные команды в `when(cmd)` молча игнорируются.
4. **sendCloseChannel теряется в `finally`**: suspend в `finally` отменённой корутины выбрасывает `CancellationException`.
5. **Нет cleanup при падении readJob**: мультиплексор зависает в неконсистентном состоянии.
6. **close() не идемпотентен**: двойной вызов может привести к проблемам.
