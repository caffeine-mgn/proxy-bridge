# Review: Reuse & Simplification

## Finding 1 — Dead function `EncodeLeb128`

**Файл:** `multiplexer/src/commonMain/kotlin/pw/binom/multiplexer/Leb.kt` : 73

**Проблема:** Функция `EncodeLeb128(value: Long, len: Int = 10, write: (Byte) -> Unit)` (42 строки, строки 73–114) не вызывается нигде в проекте. Её задачу выполняет `writeSignedLeb128` (строки 102–117).

**Severity:** Warning

**Описание:** Удалить мёртвую функцию `EncodeLeb128`. Вместе с ней убрать неиспользуемый параметр `len` (имеет дефолт `10`, нигде не переопределён).

---

## Finding 2 — Dead function `writeUnsignedLeb128`

**Файл:** `multiplexer/src/commonMain/kotlin/pw/binom/multiplexer/Leb.kt` : 60

**Проблема:** Функция `writeUnsignedLeb128(value: ULong, write: (Byte) -> Unit)` (строки 60–70) не вызывается нигде в проекте. Её полностью замещает `writeUnsignedLeb1282` (строки 47–57), которая и используется в `SinkExtensions.lebULong`.

**Severity:** Warning

**Описание:** Удалить `writeUnsignedLeb128`. Поскольку остаётся только одна unsigned-функция, переименовать `writeUnsignedLeb1282` в `writeUnsignedLeb128` (убрать суффикс `2`).

---

## Finding 3 — Dead (закомментированный) sourceSet `runnableTest`

**Файл:** `multiplexer/build.gradle.kts` : 57–63

**Проблема:** Закомментированный блок sourceSet `runnableTest` на 6 строк.

**Severity:** Info

**Описание:** Удалить закомментированный блок, если он не планируется к восстановлению.

---

## Finding 4 — Pattern duplication: send-команды

**Файл:** `multiplexer/src/commonMain/kotlin/pw/binom/multiplexer/MultiplexerProtocol.kt` : 18–54

**Проблема:** Три функции `sendCloseChannel`, `sendRequestNewChannel`, `sendResponseNewChannel` имеют идентичную структуру (создать Buffer, записать байт команды, записать channelId, отправить). Отличается только константа команды.

**Severity:** WeakWarning

**Описание:** Вынести общий код в приватную inline-функцию:
```kotlin
private suspend inline fun sendCommand(cmd: Byte, channelId: Int, physical: SendChannel<Buffer>) {
    val resultBuffer = Buffer()
    resultBuffer.writeByte(cmd)
    resultBuffer.lebInt(channelId)
    physical.send(resultBuffer)
}
```
Каждая из трёх функций сокращается до:
```kotlin
suspend fun sendCloseChannel(channelId: Int, physical: SendChannel<Buffer>) {
    logger.info { "SEND CLOSING CHANNEL $channelId" }
    sendCommand(CHANNEL_CLOSE, channelId, physical)
}
```

---

## Finding 5 — Pattern duplication: `when`-ветки в `reading`

**Файл:** `multiplexer/src/commonMain/kotlin/pw/binom/multiplexer/MultiplexerProtocol.kt` : 101–121

**Проблема:** Три ветки `when` для `CHANNEL_CLOSE`, `REQUEST_NEW_CHANNEL`, `ACCEPT_NEW_CHANNEL` выполняют идентичную последовательность: `val channelId = buffer.lebInt()`, логгирование, вызов handler.

**Severity:** WeakWarning

**Описание:** Вынести повторяющийся код в небольшую inline-функцию:
```kotlin
private inline fun readChannelId(buffer: Buffer, logMessage: String, handler: HandlerOnChannel) {
    val channelId = buffer.lebInt()
    logger.info { logMessage(channelId) }
    handler.onEvent(channelId)
}
```
Ветки `reading` сокращаются. Ветка `DATA` остаётся отдельно (у неё другой handler).

---

## Finding 6 — DuplexChannel.cancel(cause: Throwable?) игнорирует параметр

**Файл:** `multiplexer/src/commonMain/kotlin/pw/binom/multiplexer/DuplexChannel.kt` : 66–69

**Проблема:** Переопределение `cancel(cause: Throwable?): Boolean` игнорирует переданный `cause` и вызывает `income.cancel()` без причины. Рядом (строка 74) есть корректная перегрузка `cancel(cause: CancellationException?) = income.cancel(cause)`.

**Severity:** Warning

**Описание:** Исправить на делегирование с параметром:
```kotlin
override fun cancel(cause: Throwable?): Boolean {
    income.cancel(cause as? CancellationException)
    return true
}
```
Либо убрать эту перегрузку, если она является устаревшей и не вызывается.

---

## Finding 7 — RawSourceExtensions.kt находится не в том модуле

**Файл:** `multiplexer/src/commonMain/kotlin/pw/binom/multiplexer/RawSourceExtensions.kt` : 1–16

**Проблема:** Функция `RawSource.readFully` не используется ни в одном файле самого multiplexer-модуля (единственное использование `readFully` в `MultiplexerProtocol.wrapLogicalToPhysical` — это `Buffer.readFully`, не `RawSource.readFully`). Все 4 потребителя находятся в модуле `shared`.

**Severity:** Info

**Описание:** Перенести `RawSourceExtensions.kt` в модуль `shared` (например, `shared/src/commonMain/kotlin/pw/binom/utils/`) и убрать из multiplexer.

---

## Finding 8 — MultiplexerEvent.kt: дублирование handler-сигнатур из reading()

**Файл:** `multiplexer/src/commonTest/kotlin/pw/binom/multiplexer/MultiplexerEvent.kt` : 1–44

**Проблема:** Функция `readEvent` и sealed-интерфейс `MultiplexerEvent` оборачивают 4 коллбэка `MultiplexerProtocol.reading()` в один `Channel<MultiplexerEvent>`. Это добавляет 44 строки тестового кода, которые по сути дублируют сигнатуры хендлеров. Тест мог бы использовать `reading` напрямую с лямбдами.

**Severity:** Info

**Описание:** Упростить тесты: вместо `MultiplexerEvent` и `readEvent` использовать прямые вызовы `reading()` с in-line лямбдами, как это уже делает `MultiplexerImpl`. Если всё же нужен канал событий, заменить sealed-иерархию на один тип с enum tag:
```kotlin
data class MultiplexerEvent(val type: Type, val channelId: Int, val data: Buffer? = null) {
    enum class Type { DATA, CLOSED, REQUEST, ACCEPTED }
}
```

---

## Finding 9 — DuplexChannel: избыточная перегрузка cancel

**Файл:** `multiplexer/src/commonMain/kotlin/pw/binom/multiplexer/DuplexChannel.kt` : 66, 72, 75

**Проблема:** Три переопределения `cancel` (с `Throwable?`, с `CancellationException?`, без параметров). Перегрузка с `Throwable?` дублируется с `CancellationException?`.

**Severity:** Info

**Описание:** Если `cancel(cause: Throwable?)` — deprecated-адаптер, оставить его, но передавать cause. Если он не вызывается — удалить.

---

## Finding 10 — MultiplexerHolder: делегирование вручную

**Файл:** `multiplexer/src/commonMain/kotlin/pw/binom/multiplexer/MultiplexerHolder.kt`

**Проблема:** `MultiplexerHolder` реализует `Multiplexer` и вручную делегирует `accept()` / `createChannel()` / `close()` к `value.*`. При добавлении новых методов интерфейса `Multiplexer` легко забыть обновить холдер.

**Severity:** Info

**Описание:** Abstract class delegation (например, `Lazy<Multiplexer>` + forwarding) — альтернативное решение. Для текущего размера (3 метода) допустимо, но стоит зафиксировать в комментарии, что при расширении Multiplexer нужно обновлять холдер.
