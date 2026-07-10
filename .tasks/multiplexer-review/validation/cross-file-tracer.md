# Validation: Cross-File Tracer Review

## Находка 1: close() vs close(cause) — разное поведение в VirtualChannel
**Вердикт:** TP — Warning
**Обоснование:** VirtualChannel переопределяет `close()` как `job.cancel()` (MultiplexerImpl.kt:80), но наследует `close(cause: Throwable?)` из DuplexChannel.kt:17, который делегирует в `outcome.close(cause)`. Разные пути: `close()` агрессивно отменяет корутину (finally очищает обе стороны), `close(cause)` graceful-закрывает outcome (consumeEach завершается, finally делает то же самое). Конечное состояние идентично, но поведение разное. Не баг-корректность, но неконсистентность интерфейса. Severity снижена до Warning, т.к. утечки/потери данных нет.

## Находка 2: Race в MultiplexerImpl.close() — spurious sendCloseChannel
**Вердикт:** TP — Warning
**Обоснование:** close() вызывает `readJob.cancel()` (шаг 1), затем `activeChannels.clear()` (шаг 2). `readJob.cancel()` не останавливает текущую итерацию `consumeEach`. Если lambda handler'а читает `activeChannels[channelId]` после clear, находит null и отправляет ложный `sendCloseChannel`. Окно узкое (только пока handler исполняется), и spurious close уходит в output, который тоже скоро закроется, но race реален. Severity: Warning (не Error, т.к. обе стороны скоро закроются).

## Находка 3: consumeEach закрывает внешний input-канал
**Вердикт:** TP — Warning
**Обоснование:** `physical.consumeEach { ... }` в MultiplexerProtocol.reading() при завершении вызывает `cancel()` на канале. Это side effect — внешний канал `input`, переданный в MultiplexerImpl, безвозвратно закрывается. В текущей реализации это ожидаемо (Multiplexer владеет input), но при повторном использовании канала или shared-канале это может быть сюрпризом. Severity: Warning.

## Находка 4: DuplexChannel.invokeOnClose только для outcome
**Вердикт:** TP — WeakWarning
**Обоснование:** `invokeOnClose` существует только на `SendChannel`, не на `ReceiveChannel`, так что делегирование `outcome.invokeOnClose(handler)` — это вынужденное архитектурное решение API корутин. VirtualChannel обходит это через `income.invokeOnClose { ... }` в init. Проблема существует, но это ограничение корутинного API, а не баг в коде. Severity снижена до WeakWarning.

## Находка 5: Тестовые зависимости в commonMain вместо commonTest
**Вердикт:** TP — Error
**Обоснование:** build.gradle.kts строки 38-41: `commonMain.dependencies { implementation(libs.kotlinx.coroutines.test); implementation(kotlin("test")) }`. Copy-paste ошибка — должно быть `commonTest.dependencies`. Test-зависимости попадают в production-артефакты. Серьёзная проблема сборки.

## Находка 6: CancellationException логируется как ошибка
**Вердикт:** TP — Warning
**Обоснование:** MultiplexerProtocol.reading() ловит `Throwable` (строка 131) и логирует "READ FINISHED WITH ERROR!!!" CancellationException — подкласс Throwable — тоже попадает сюда. При нормальной отмене через close() это вводит в заблуждение. Severity: Warning.

## Находка 7: Мёртвый код в Leb.kt
**Вердикт:** TP — WeakWarning
**Обоснование:** `writeUnsignedLeb128` (строка 49) и `EncodeLeb128` (строка 55) не используются нигде. SinkExtensions использует `writeUnsignedLeb1282` и `writeSignedLeb128`. dead code. Severity: WeakWarning.

## Находка 8: Неиспользуемые UInt-расширения LEB128
**Вердикт:** TP — Info
**Обоснование:** `Sink.lebUInt()` и `Source.lebUInt()` не вызываются внутри модуля multiplexer. `Source.lebInt()` используется. Severity: Info — мёртвый код только для unsigned-версий.

## Находка 9: Утечка lock в createChannel() при неожиданном исключении
**Вердикт:** TP — Warning
**Обоснование:** try-catch ловит только `CancellationException`. Если `sendRequestNewChannel` выбросит `ClosedSendChannelException` (IllegalStateException) — lock никогда не отпустится. `ClosedSendChannelException extends IllegalStateException`, не CancellationException. Реальный deadlock при закрытом output-канале. Severity: Warning.

## Находка 10: Race: pendingChannels vs readJob при close()
**Вердикт:** TP — WeakWarning
**Обоснование:** close() очищает pendingChannels (шаг 3) после readJob.cancel() (шаг 1). Если readJob обрабатывает ACCEPT в момент между cancel и очисткой, `newChannelAccepted` handler найдёт null в pendingChannels и пошлёт ложный `sendCloseChannel`. Окно крайне мало, последствия мягкие (close на уже закрывающемся канале). Severity: WeakWarning.

## Итог
- TP: 10/10
- FP: 0
- NeedMoreData: 0
- Error: 1 (Finding 5)
- Warning: 4 (Findings 1, 2, 3, 6, 9)
- WeakWarning: 3 (Findings 4, 7, 10)
- Info: 1 (Finding 8)

Все 10 находок подтверждены. Наиболее критичная — Finding 5 (test dependencies в commonMain).
