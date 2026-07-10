# Validation: Removed-Behavior Review

## Находка 1: [TP] — Error
**Файл:** MultiplexerImpl.kt:89-97
**Вердикт:** Spinlock удерживается через suspend-вызов sendRequestNewChannel. На однопоточном диспатчере — deadlock. На многопоточном — риск при высокой конкуренции.
**Обоснование:** `pendingChannelsLock.lock()` (строка 89) → suspend `output.send()` (строка 93) → lock не отпущен. `locking{}` блокирует busy-wait.

## Находка 2: [FP]
**Файл:** MultiplexerImpl.kt:91-103
**Вердикт:** Race window отсутствует. Continuation регистрируется в `pendingChannels` ДО отпускания lock'а, внутри синхронного лямбда-вызова `suspendCancellableCoroutine`.
**Обоснование:** Порядок: `pendingChannels[newChannelId] = cont` (строка 102) → `pendingChannelsLock.unlock()` (строка 103). Другая корутина не может войти в `locking{}`, пока lock не отпущен.

## Находка 3: [TP] — Error → Warning (понижено)
**Файл:** MultiplexerProtocol.kt:101-123
**Вердикт:** Отсутствует `else`-ветка в `when(cmd)`. Неизвестная команда молча отбрасывается, вызывая десинхронизацию протокола (съеден байт, следующие пакеты читаются со смещением).
**Обоснование:** `when(cmd)` неexhaustive для Byte. Любое значение кроме [1,2,3,4] игнорируется. Снижено до Warning, т.к. это не баг в нормальном потоке, а отсутствие защиты от некорректных данных.

## Находка 4: [TP] — Error
**Файл:** MultiplexerImpl.kt:57-63
**Вердикт:** `finally` блок отменённой корутины вызывает suspend `sendCloseChannel`. CancellationException прерывает отправку close-уведомления на remote-сторону.
**Обоснование:** `job.cancel()` → cancelled-корутина → finally выполняется → `physical.send()` выбрасывает CancellationException на suspension point → remote не получает close.

## Находка 5: [FP] — не Error
**Файл:** MultiplexerImpl.kt:153-166
**Вердикт:** Повторный close() безопасен: cancel(), clear(), values.forEach на пустом — все операции идемпотентны. Нет сценария поломки.
**Обоснование:** `readJob.cancel()` идемпотентно. `clear()` на пустом HashMap safe. `cancel()` на закрытом Channel safe. Не Error, максимум WeakWarning.

## Находка 6: [TP] — Error
**Файл:** MultiplexerImpl.kt:119-152
**Вердикт:** handler-ы в `reading()` (особенно `requestChannel` → `incomeChannels.send()`) могут бросить не-CancellationException при закрытом канале → readJob падает без cleanup.
**Обоснование:** `incomeChannels.send()` при закрытом канале → `ClosedSendChannelException`. Не ловится в `reading()` (только DATA-ветка защищена). Убивает `readJob`.

## Находка 7: [TP] — Warning
**Файл:** MultiplexerImpl.kt:122-127
**Вердикт:** `catch (e: CancellationException)` не ловит `ClosedSendChannelException`, который может выбросить `channel.income.send(data)` на закрытом канале.
**Обоснование:** `ClosedSendChannelException` наследует `IllegalStateException`, не `CancellationException`. Exception всплывает из handlerOnData в readJob.

## Находка 8: [TP] — Warning
**Файл:** DuplexChannel.kt:66-72, 80
**Вердикт:** `cancel()` затрагивает только income, `close()` затрагивает только outcome. Пользователь, вызывающий `cancel()` в ожидании полной остановки, оставляет outcome открытым.
**Обоснование:** cancel → income.cancel(), close → outcome.close(). Ни один метод не закрывает оба направления симметрично.

## Находка 9: [TP] — Warning
**Файл:** MultiplexerProtocol.kt:100-127
**Вердикт:** Только DATA-ветка защищена try-catch(Throwable). Остальные три ветки (CHANNEL_CLOSE, REQUEST_NEW_CHANNEL, ACCEPT_NEW_CHANNEL) при исключении handler'а убьют reading().
**Обоснование:** Различие в обработке ошибок между командами протокола — неконсистентно.

## Находка 10: [TP] — Warning
**Файл:** MultiplexerImpl.kt:119
**Вердикт:** `reading()` ловит Throwable внутри и возвращается нормально. `readJob` завершается успешно даже при аварийном закрытии физического канала. Внешний код не может отличить норму от ошибки.
**Обоснование:** Нет observable error state у readJob. Нет invokeOnCompletion для сигнализации.

## Находка 11: [FP]
**Файл:** MultiplexerImpl.kt:121-123
**Вердикт:** Стандартное протокольное поведение. Rate-limiting не является ответственностью мультиплексора.
**Обоснование:** Close-команда на неизвестный channelId — ожидаемая защита протокола. DoS-защита должна быть на транспортном уровне.

## Находка 12: [FP]
**Файл:** MultiplexerImpl.kt:25-28
**Вердикт:** Мультиплексор не должен отменять внешний scope — это нарушило бы инверсию контроля. VirtualChannel.job-корутины отменяются через `activeChannels.forEach { it.close() }`.
**Обоснование:** `ioCoroutineScope` передан конструктором. Его отмена — ответственность создателя.

## Находка 13: [TP] — Warning
**Файл:** AtomicBooleanExtensions.kt:7-13
**Вердикт:** Tight spinlock без yield/backoff сжигает CPU при конкуренции. В корутинном контексте блокирует поток диспатчера.
**Обоснование:** `while(true) { if (compareAndSet(false, true)) break }` — ни одной паузы между CAS-попытками.

## Находка 14: [TP] — Warning
**Файл:** MultiplexerImpl.kt:129-133
**Вердикт:** `activeChannels.remove(id)` под lock, `channel?.close()` — без. Между ними другой поток может создать VirtualChannel с тем же ID и затем быть отменён.
**Обоснование:** Race между remove и close. Смягчено шагом генерации ID=2, но при переполнении Int возможна коллизия.

## Находка 15: [TP] — WeakWarning
**Файл:** MultiplexerHolder.kt:27
**Вердикт:** `instance.load()?.close()` — между load и close другой поток может вызвать remove(). Низкая вероятность в реальном использовании.
**Обоснование:** Теоретический race при конкурентном shutdown + set/remove. На практике однократный вызов close() при остановке.

## Находка 16: [TP] — WeakWarning
**Файл:** MultiplexerImpl.kt:39
**Вердикт:** `incomeChannels = Channel(UNLIMITED)` может расти без ограничений при медленном accept().
**Обоснование:** Нет backpressure на входящие channel-запросы.

## Находка 17: [TP] — WeakWarning
**Файл:** Leb.kt:60, 73
**Вердикт:** `writeUnsignedLeb128` и `EncodeLeb128` не используются. Мёртвый код.
**Обоснование:** Поиск по проекту подтвердил — ни одного вызова, кроме собственного определения.

## Находка 18: [FP]
**Файл:** RawSourceExtensions.kt:9-15
**Вердикт:** EOFException на недочтении — стандартный контракт readFully. Нет проблемы.
**Обоснование:** readFully по определению должен прочитать ровно N байт или упасть. EOFException — корректный способ сигнализации.

---

## Итог

- **TP (подтверждено):** 12 (1 Error, 4 Warning, 4 WeakWarning = исходные; Error1→Error, Error3→Warning, Error4→Error, Error6→Error, Warning1→Warning, Warning2→Warning, Warning3→Warning, Warning4→Warning, Warning7→Warning, Warning8→Warning, WeakWarning1→WeakWarning, WeakWarning2→WeakWarning, WeakWarning3→WeakWarning)
- **FP (ложные):** 5 (Error2, Error5, Warning5, Warning6, WeakWarning4)
- **NeedMoreData:** 0
