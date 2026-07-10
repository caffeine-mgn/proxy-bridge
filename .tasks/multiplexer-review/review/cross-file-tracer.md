# Cross-File Tracer Review: Multiplexer Module

Проверка целостности контрактов между файлами модуля multiplexer.
Проверены: все 11 source-файлов + 3 test-файла + build.gradle.kts.

---

## Находка 1: close() vs close(cause) — разное поведение в VirtualChannel

**Файл:** `MultiplexerImpl.kt:62-115` (VirtualChannel) ↔ `DuplexChannel.kt:16-76`

**Severity:** Error

**Описание:**
VirtualChannel переопределяет `close()` (без аргументов) как `job.cancel()`, но НЕ переопределяет `close(cause: Throwable?)`, унаследованный из DuplexChannel. В результате:

- `channel.close()` → `job.cancel()` → агрессивная отмена корутины → finally-блок закрывает income + outcome и шлёт sendCloseChannel
- `channel.close(cause)` → `outcome.close(cause)` (дефолт DuplexChannel) → graceful-закрытие outcome → consumeEach завершается естественно → job завершается → finally-блок

Конечное состояние одинаковое, но механизмы разные. `close(cause)` ждёт завершения consumeEach-цикла, `close()` его прерывает. Вызывающий код, полагающийся на graceful-закрытие через `close(cause)`, получит неожиданно агрессивное поведение.

**Рекомендация:** VirtualChannel должен переопределить `close(cause: Throwable?)` для единообразия, либо явно задокументировать разницу.

---

## Находка 2: Race condition в MultiplexerImpl.close() — данные могут быть отправлены на уже удалённый канал

**Файл:** `MultiplexerImpl.kt:156-167`

**Severity:** Error

**Описание:**
`MultiplexerImpl.close()` вызывает `readJob.cancel()` первой строкой, затем очищает `activeChannels`. Но `readJob.cancel()` не останавливает выполнение немедленно — текущая итерация `consumeEach` в `reading()` завершается полностью. Если в этот момент `handlerOnData` ищет канал по ID в `activeChannels`, он может найти `null` (потому что `close()` уже очистил мапу) и отправить ложный `sendCloseChannel` для всё ещё живого канала.

```kotlin
val channel = activeChannelsLock.locking { activeChannels[channelId] }
if (channel == null) {
    MultiplexerProtocol.sendCloseChannel(channelId = channelId, physical = output) // ← ложный close
} else {
    channel.income.send(data)
}
```

**Рекомендация:** Либо очищать `activeChannels` ДО `readJob.cancel()`, либо сигнализировать чтению о shutdown через отдельный атомик.

---

## Находка 3: `consumeEach` в `reading()` закрывает внешний `input`-канал

**Файл:** `MultiplexerProtocol.kt:96` ↔ `MultiplexerImpl.kt:140-141`

**Severity:** Warning

**Описание:**
`reading()` использует `physical.consumeEach { ... }`. `consumeEach` — это terminal-операция, которая при завершении (нормальном или по ошибке) вызывает `cancel()` на канале. `physical` — это `input: ReceiveChannel<Buffer>`, переданный в конструктор MultiplexerImpl извне (например, `rawChannel.income` или `stub.input`). Таким образом, завершение `readJob` (в т.ч. через `close()`) попутно закрывает внешний канал, что может быть неожиданно для вызывающего кода.

**Рекомендация:** Заменить `consumeEach` на ручной цикл `while (isActive) { val buf = physical.receive(); ... }`, который не закрывает канал при завершении.

---

## Находка 4: DuplexChannel.invokeOnClose делегирует только outcome — асимметрия

**Файл:** `DuplexChannel.kt:19`

**Severity:** Warning

**Описание:**
`invokeOnClose(handler)` делегирует в `outcome.invokeOnClose(handler)`. Это значит, что handler срабатывает ТОЛЬКО при закрытии `outcome` (send-side). Если закрывается `income` (receive-side) — например, через `cancel()`, — handler не вызывается.

VirtualChannel обходит это, регистрируя handler напрямую на `income` в `init { income.invokeOnClose { job.cancel() } }`. Но любой код, использующий DuplexChannel через интерфейс (например, `channel.invokeOnClose { cleanup() }`), получит только половину событий закрытия.

**Рекомендация:** Документировать это поведение интерфейса, либо сделать `invokeOnClose` двусторонним.

---

## Находка 5: Тестовые зависимости в commonMain вместо commonTest

**Файл:** `multiplexer/build.gradle.kts:38-41`

**Severity:** Error

**Описание:**
`kotlinx.coroutines.test` и `kotlin("test")` объявлены в блоке `commonMain.dependencies`, хотя должны быть в `commonTest.dependencies`. Это приводит к включению test-биндингов в production-артефакты. Воспроизводится на текущем билде — сборка работает, но зависимости загрязнены.

---

## Находка 6: CancellationException логируется как ошибка

**Файл:** `MultiplexerProtocol.kt:131-132`

**Severity:** Warning

**Описание:**
В `reading()` блок `catch (e: Throwable)` перехватывает `CancellationException` (который является `Throwable`) и логирует "READ FINISHED WITH ERROR!!!". При нормальной отмене `readJob.cancel()` это не ошибка — это штатное завершение. Лог вводит в заблуждение.

**Рекомендация:** Отделять `CancellationException` до `Throwable`:
```kotlin
} catch (e: CancellationException) {
    // нормальное завершение
} catch (e: Throwable) {
    logger.error(e) { "READ FINISHED WITH ERROR!!!" }
}
```

---

## Находка 7: Мёртвый код в Leb.kt

**Файл:** `Leb.kt:60` (writeUnsignedLeb128), `Leb.kt:73` (EncodeLeb128)

**Severity:** WeakWarning

**Описание:**
Функции `writeUnsignedLeb128` и `EncodeLeb128` нигде не используются. `SinkExtensions.kt` вызывает только `writeUnsignedLeb1282` и `writeSignedLeb128`. Это увеличивает поддерживаемый код без пользы. Также `EncodeLeb128` нарушает Kotlin naming convention (должно быть `encodeLeb128`).

---

## Находка 8: Неиспользуемые LEB128-расширения для UInt

**Файл:** `SinkExtensions.kt:5`, `SourceExtensions.kt:15`

**Severity:** Info

**Описание:**
`Sink.lebUInt()`, `Source.lebUInt()`, `Source.lebInt()` присутствуют, но ни один из них не вызывается в multiplexer-модуле. Потенциально используются в других модулях проекта, но внутри модуля мёртвый код.

---

## Находка 9: Утечка lock'а в createChannel() при неожиданном исключении

**Файл:** `MultiplexerImpl.kt:96-107`

**Severity:** Warning

**Описание:**
`pendingChannelsLock.lock()` вручную, а unlock происходит в двух местах: catch CancellationException и в lambda `suspendCancellableCoroutine`. Если lambda выбросит не-CancellationException (например, OOM), lock никогда не будет отпущен — deadlock.

На практике HashMap.put и invokeOnCancellation не выбрасывают, но паттерн хрупкий.

**Рекомендация:** Переписать через `pendingChannelsLock.locking { ... }` с передачей continuation наружу:
```kotlin
val cont = pendingChannelsLock.locking {
    val c = suspendCancellableCoroutine<Unit> { ... }
    pendingChannels[newChannelId] = c
    c
}
// теперь cont — сохранённая continuation
```

---

## Находка 10: Последовательность закрытия в MultiplexerImpl.close() — pendingChannels могут умереть раньше readJob

**Файл:** `MultiplexerImpl.kt:156-167`

**Severity:** Info

**Описание:**
`close()` сначала отменяет `readJob`, потом чистит `activeChannels`, потом `pendingChannels`, потом `incomeChannels`. Если в момент между `activeChannels.clear()` и `pendingChannels.clear()` readJob всё ещё обрабатывает последний элемент и приходит `ACCEPT_NEW_CHANNEL`, то `newChannelAccepted` handler попытается резолвить continuation из `pendingChannels`, который уже может быть отменён или очищен. Итог: `water == null` → `sendCloseChannel` для канала, который уже создаётся.

**Рекомендация:** Установить атомарный флаг «shutting down» и проверять его в handler'ах чтения перед доступом к pendingChannels.

---

## Summary

| # | Severity | Тип | Файлы |
|---|----------|-----|-------|
| 1 | **Error** | close() vs close(cause) — разное поведение | MultiplexerImpl.kt ↔ DuplexChannel.kt |
| 2 | **Error** | Race: readJob.cancel → activeChannels очищены → spurious sendCloseChannel | MultiplexerImpl.kt |
| 3 | **Warning** | consumeEach закрывает внешний input-канал | MultiplexerProtocol.kt → MultiplexerImpl.kt |
| 4 | **Warning** | invokeOnClose только для outcome | DuplexChannel.kt |
| 5 | **Error** | test-зависимости в commonMain | build.gradle.kts |
| 6 | **Warning** | CancellationException логируется как ошибка | MultiplexerProtocol.kt |
| 7 | **WeakWarning** | Мёртвый код в Leb.kt | Leb.kt |
| 8 | **Info** | Неиспользуемые UInt-расширения | SinkExtensions.kt, SourceExtensions.kt |
| 9 | **Warning** | Утечка lock при неожиданном исключении | MultiplexerImpl.kt |
| 10 | **Info** | Race: pendingChannels vs readJob при close() | MultiplexerImpl.kt |

Итого: **3 Error**, **4 Warning**, **1 WeakWarning**, **2 Info**.

Наиболее критичные: (1) и (2) — могут приводить к некорректному закрытию каналов и race-условиям в production. (5) — загрязнение production-артефактов тестовыми зависимостями.
