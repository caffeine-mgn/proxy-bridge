# Line-by-line review: multiplexer module

## Critical / Error

**Файл:** multiplexer/src/commonMain/kotlin/pw/binom/multiplexer/MultiplexerImpl.kt : 56
**Проблема:** Suspend-вызов в `finally` без `NonCancellable`
**Severity:** Error
**Описание:** `finally` блок корутины VirtualChannel вызывает `sendCloseChannel` (suspend) и `close()`/`cancel()` на outcome/income. Если корутина была отменена (через `job.cancel()`), все suspend-вызовы внутри finally выбросят `CancellationException`, и close-команда не дойдёт до удалённой стороны. Нужно обернуть в `withContext(NonCancellable) { ... }`.

**Файл:** multiplexer/src/commonMain/kotlin/pw/binom/multiplexer/MultiplexerImpl.kt : 94-98
**Проблема:** Spinlock удерживается во время suspend-вызова
**Severity:** Error
**Описание:** `pendingChannelsLock.lock()` (busy-wait spinlock) захвачен до вызова `sendRequestNewChannel` (suspend-функция). Если `output.send()` приостановится, lock остаётся захвачен, и любой другой поток, пытающийся взять этот же spinlock, будет бесконечно крутить CPU. Более того, если `sendRequestNewChannel` выбросит `ClosedSendChannelException` (а не `CancellationException`), catch не сработает, и lock никогда не будет отпущен — deadlock.

**Файл:** multiplexer/src/commonMain/kotlin/pw/binom/multiplexer/MultiplexerImpl.kt : 97-112
**Проблема:** Race condition при создании канала — данные могут прийти до регистрации в activeChannels
**Severity:** Error
**Описание:** После возобновления continuation (строка 152: `water.resume(Unit)`) код выходит из `suspendCancellableCoroutine` и лишь затем создаёт VirtualChannel и кладёт его в `activeChannels` (строка 114-116). Удалённая сторона, получив ACCEPT, может сразу начать слать DATA, которые придут раньше, чем channel окажется в `activeChannels` → handlerOnData сочтёт channelId неизвестным и пошлёт CLOSE.

**Файл:** multiplexer/src/commonMain/kotlin/pw/binom/multiplexer/DuplexChannel.kt : 48-50
**Проблема:** `cancel(cause: Throwable?)` игнорирует аргумент `cause`
**Severity:** Error
**Описание:** Метод `cancel(cause: Throwable?): Boolean` вызывает `income.cancel()` без передачи `cause`. Причина отмены теряется, что может затруднить отладку и нарушить контракт. В то же время `cancel(cause: CancellationException?)` (строка 57) cause передаёт — поведение перегруженных методов несогласованно.

**Файл:** multiplexer/src/commonMain/kotlin/pw/binom/multiplexer/MultiplexerImpl.kt : 77-79, 129-133
**Проблема:** Утечка каналов из activeChannels при локальном закрытии VirtualChannel
**Severity:** Error
**Описание:** VirtualChannel удаляется из `activeChannels` только в колбэке `channelClosed` (когда удалённая сторона инициирует закрытие). Если клиент вызывает `channel.close()`, то `job.cancel()` срабатывает, но запись в `activeChannels` остаётся навсегда. Со временем, при частом создании/закрытии каналов, HashMap неограниченно растёт. При глобальном `MultiplexerImpl.close()` карта очищается, но при долгоживущем мультиплексоре это real-world memory leak.

**Файл:** multiplexer/src/commonTest/kotlin/pw/binom/multiplexer/MultiplexerTest.kt : 116-118
**Проблема:** testCloseOutside ловит CancellationException, но send выбросит ClosedSendChannelException
**Severity:** Error
**Описание:** `ClosedSendChannelException` наследует `IllegalStateException`, а не `CancellationException`. После `outcome.close(e)` send на этом канале выбрасывает `ClosedSendChannelException`. catch-блок на стр.117 ловит только `CancellationException`, поэтому тест упадёт (exception не будет пойман). Ошибка подтверждена декомпиляцией класса `ClosedSendChannelException`.

## Warning

**Файл:** multiplexer/src/commonMain/kotlin/pw/binom/multiplexer/MultiplexerImpl.kt : 120
**Проблема:** supervisorScope не защищает от исключений в handler-ах
**Severity:** Warning
**Описание:** Весь readJob обёрнут в `supervisorScope`, который изолирует сбой корутины чтения. Но внутри handler-ов (requestChannel → `incomeChannels.send()`) исключение не обрабатывается. Если `incomeChannels` закрыт, `send()` выбросит `ClosedSendChannelException`, который убьёт supervisorScope и остановит чтение физического канала.

**Файл:** multiplexer/src/commonMain/kotlin/pw/binom/multiplexer/MultiplexerProtocol.kt : 99-135
**Проблема:** `physical.consumeEach` в `reading()` отменяет физический входной канал
**Severity:** Warning
**Описание:** `consumeEach` при завершении (даже по ошибке) вызывает `channel.cancel()` на `physical`. Это означает, что если MultiplexerProtocol.reading завершается (например, из-за malformed-пакета), физический входной канал безвозвратно закрывается, что может быть неожиданно для владельца мультиплексора.

**Файл:** multiplexer/src/commonMain/kotlin/pw/binom/multiplexer/MultiplexerProtocol.kt : 109-111
**Проблема:** Неравномерная обработка ошибок между командами протокола
**Severity:** Warning
**Описание:** Обработка команды DATA обёрнута в try-catch(Throwable) (строка 108-111), что даёт устойчивость к единичному битому пакету. Команды CHANNEL_CLOSE, REQUEST_NEW_CHANNEL, ACCEPT_NEW_CHANNEL такой защиты не имеют — если `lebInt()` упадёт, чтение остановится целиком. Это либо баг, либо осознанное решение — но различие стоит задокументировать.

**Файл:** multiplexer/src/commonMain/kotlin/pw/binom/multiplexer/MultiplexerImpl.kt : 64-66
**Проблема:** invokeOnClose на income не гарантирует отмену job при любой поломке
**Severity:** Warning
**Описание:** `income.invokeOnClose` вызывает `job.cancel()` только когда income закрывается напрямую (через cancel/close). Если job падает сам по себе (исключение в coppingLogicalToPhysical), его finally блок закроет income, что вызовет invokeOnClose — круговой вызов. Это не баг, но хрупкий цикл зависимостей.

## WeakWarning

**Файл:** multiplexer/src/commonMain/kotlin/pw/binom/multiplexer/MultiplexerImpl.kt : 36, 114
**Проблема:** Опечатка "chanelJob" вместо "channelJob"
**Severity:** WeakWarning
**Описание:** Дважды — в `accept()` и `createChannel()` — переменная названа `chanelJob` (пропущена буква 'l').

**Файл:** multiplexer/src/commonMain/kotlin/pw/binom/multiplexer/MultiplexerProtocol.kt : 70
**Проблема:** Опечатка в имени функции "coppingLogicalToPhysical"
**Severity:** WeakWarning
**Описание:** Должно быть "copyingLogicalToPhysical" (с одной 'p').

**Файл:** multiplexer/src/commonMain/kotlin/pw/binom/multiplexer/Leb.kt : 35, 49, 55
**Проблема:** Мёртвый код: две неиспользуемые реализации LEB128
**Severity:** WeakWarning
**Описание:** `writeUnsignedLeb1282` используется, а `writeUnsignedLeb128` (строка 49) и `EncodeLeb128` (строка 55) нигде не вызываются. Загромождают код.

**Файл:** multiplexer/src/commonMain/kotlin/pw/binom/multiplexer/DuplexChannel.kt : 7
**Проблема:** Тяжёлое подавление инкапсуляции внутренних API
**Severity:** WeakWarning
**Описание:** `@Suppress("DEPRECATION_ERROR", "INVISIBLE_REFERENCE", "INVISIBLE_MEMBER", "CANNOT_OVERRIDE_INVISIBLE_MEMBER")` отключает видимые ошибки инкапсуляции для override непубличных/экспериментальных методов. Это хрупко — при обновлении корутин интерфейс может сломаться.

**Файл:** multiplexer/src/commonTest/kotlin/pw/binom/multiplexer/MultiplexerTest.kt : 76-85
**Проблема:** testReceive отправляет DATA до вызова accept() — потенциальная гонка
**Severity:** WeakWarning
**Описание:** DATA-пакет посылается до того, как accept() зарегистрирует VirtualChannel в activeChannels. Если readJob обработает DATA раньше, чем accept() выполнит регистрацию, handlerOnData сочтёт channelId неизвестным. Тест может быть flaky при другом планировщике/нагрузке.

## Info

**Файл:** multiplexer/src/commonMain/kotlin/pw/binom/multiplexer/MultiplexerImpl.kt : 54-55
**Проблема:** Канал 0 и 1 не используются idGenerator
**Severity:** Info
**Описание:** idGenerator начинает счёт с 0/1 и инкрементит на 2, поэтому channel ID 0 и 1 никогда не будут созданы локально (хотя могут прийти от удалённой стороны). Не баг, но 2 из 2^32 возможных ID потеряны.

**Файл:** multiplexer/src/commonMain/kotlin/pw/binom/multiplexer/MultiplexerImpl.kt : 89-90
**Проблема:** Потенциальное переполнение Int при генерации ID
**Severity:** Info
**Описание:** `idGenerator.addAndFetch(2)` при достижении `Int.MAX_VALUE` даст отрицительное значение, а затем снова вырастет. В переполненном состоянии возможна коллизия с существующим каналом. Вероятность крайне мала при 2·10⁹ операций.

**Файл:** multiplexer/src/commonMain/kotlin/pw/binom/multiplexer/Leb.kt : 4-19
**Проблема:** readUnsigned не консьюмит "лишние" байты при превышении maxBytes
**Severity:** Info
**Описание:** Если LEB128-поток содержит больше байт, чем `maxBytes` (например, битый/зловредный пакет с бесконечной continuation), лишние байты не читаются — последующие данные интерпретируются со смещением. Для защищённого протокола стоило бы вычитать и проигнорировать лишние байты, либо упасть с ошибкой.
