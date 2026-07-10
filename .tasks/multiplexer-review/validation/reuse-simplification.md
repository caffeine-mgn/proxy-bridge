# Validation: Reuse & Simplification Reviewer

## Находка 1: TP — Warning
**Файл:** multiplexer/src/commonMain/kotlin/pw/binom/multiplexer/Leb.kt : 73
**Вердикт:** TP (подтверждена)
**Обоснование:** `EncodeLeb128` — 0 usages в проекте. Параметр `len` затенён локальной переменной `PadTo = 10`. Функция не вызывается ниоткуда. Severity подтверждается: Warning — мёртвый код, 42 строки.

## Находка 2: TP — Warning
**Файл:** multiplexer/src/commonMain/kotlin/pw/binom/multiplexer/Leb.kt : 60
**Вердикт:** TP (подтверждена)
**Обоснование:** `writeUnsignedLeb128` — 0 usages в проекте. Используется только `writeUnsignedLeb1282` (1 usage в SinkExtensions.kt:9). Severity подтверждается: Warning.

## Находка 3: TP — Info
**Файл:** multiplexer/build.gradle.kts : 57-63
**Вердикт:** TP (подтверждена)
**Обоснование:** Закомментированный блок `runnableTest` sourceSet. Мёртвый код. Severity: Info (косметика).

## Находка 4: TP — WeakWarning
**Файл:** multiplexer/src/commonMain/kotlin/pw/binom/multiplexer/MultiplexerProtocol.kt : 18-54
**Вердикт:** TP (подтверждена)
**Обоснование:** Три send-функции имеют идентичную структуру: `Buffer() → writeByte(cmd) → lebInt(id) → physical.send()`. Различаются только константой команды и сообщением лога. Можно вынести в helper. Severity: WeakWarning (code smell, не баг).

## Находка 5: TP — WeakWarning
**Файл:** multiplexer/src/commonMain/kotlin/pw/binom/multiplexer/MultiplexerProtocol.kt : 101-121
**Вердикт:** TP (подтверждена)
**Обоснование:** Ветки CHANNEL_CLOSE, REQUEST_NEW_CHANNEL, ACCEPT_NEW_CHANNEL в `when(cmd)` выполняют `buffer.lebInt() → logger.info → handler.onEvent()`. DATA-ветка отличается (try-catch, другой handler). Три одинаковые ветки можно вынести. Severity: WeakWarning.

## Находка 6: TP — Warning
**Файл:** multiplexer/src/commonMain/kotlin/pw/binom/multiplexer/DuplexChannel.kt : 66-69
**Вердикт:** TP (подтверждена)
**Обоснование:** `cancel(cause: Throwable?)` вызывает `income.cancel()` без передачи `cause`, хотя рядом `cancel(cause: CancellationException?)` cause передаёт (строка 74). Причина отмены теряется. Severity: Warning — потеря диагностической информации.

## Находка 7: FP
**Файл:** multiplexer/src/commonMain/kotlin/pw/binom/multiplexer/RawSourceExtensions.kt
**Вердикт:** **FP (ложная)**
**Обоснование:** Ревьюер утверждает, что `RawSource.readFully` не используется в multiplexer-модуле. Это неверно. `MultiplexerProtocol.kt:87` вызывает `data.readFully(resultBuffer, data.size)`, где `data: Buffer`. Buffer → Source (sealed interface) → RawSource, поэтому `RawSource.readFully` extension доступен и используется. Usages tool подтверждает 1 usage внутри multiplexer-модуля. Идея перенести файл в `shared` имеет смысл (4 из 9 usage — в shared), но утверждение «не используется» ошибочно.

## Находка 8: TP — Info
**Файл:** multiplexer/src/commonTest/kotlin/pw/binom/multiplexer/MultiplexerEvent.kt
**Вердикт:** TP (подтверждена)
**Обоснование:** Тестовый файл оборачивает 4 handler'a `reading()` в `Channel<MultiplexerEvent>`. Это 44 строки вспомогательного кода для тестов. Сериализация событий полезна для тестов, но можно было использовать direct lambdas. Severity: Info — тестовый код, не влияет на production.

## Находка 9: TP — Info
**Файл:** multiplexer/src/commonMain/kotlin/pw/binom/multiplexer/DuplexChannel.kt : 66, 72, 75
**Вердикт:** TP (подтверждена)
**Обоснование:** Три перегрузки `cancel` — это обязательные override'ы из `ReceiveChannel`, а не произвольное размножение. `cancel(cause: Throwable?)` — deprecated-адаптер. Severity: Info — необходимое зло для совместимости.

## Находка 10: TP — Info
**Файл:** multiplexer/src/commonMain/kotlin/pw/binom/multiplexer/MultiplexerHolder.kt
**Вердикт:** TP (подтверждена)
**Обоснование:** MultiplexerHolder вручную делегирует 3 метода к `value.*`. При расширении интерфейса Multiplexer можно забыть обновить холдер. Severity: Info.

## Итог
- **TP:** 9
- **FP:** 1 (Finding 7 — RawSourceExtensions таки используется внутри модуля)
- **NeedMoreData:** 0
