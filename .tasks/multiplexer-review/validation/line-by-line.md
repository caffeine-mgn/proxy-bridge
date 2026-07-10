# Validation: Line-by-line reviewer

## Находка 1: TP — Error
**Файл:** MultiplexerImpl.kt:56
**Вердикт:** TP
**Обоснование:** `sendCloseChannel` — suspend fun (вызывает `physical.send()`). В `finally` отменённой корутины первый suspend выбросит CancellationException. Remote не получит уведомление о закрытии.

## Находка 2: TP — Error
**Файл:** MultiplexerImpl.kt:94-98
**Вердикт:** TP
**Обоснование:** `pendingChannelsLock` (spinlock) захвачен до suspend-вызова `sendRequestNewChannel`. Блокировка удерживается через точку приостановки. Другие корутины на всех потоках будут бесконечно крутить CAS. Если `output.send()` бросит `ClosedSendChannelException` (не CancellationException) — lock утекает навсегда.

## Находка 3: TP — Error
**Файл:** MultiplexerImpl.kt:97-112
**Вердикт:** TP
**Обоснование:** `water.resume(Unit)` возвращает управление корутине createChannel, которая ещё НЕ зарегистрировала VirtualChannel в activeChannels (регистрация на 4 строки ниже). readJob в это же время (тот же consumeEach-цикл) может обработать следующий DATA-пакет → activeChannels[channelId] == null → ложный sendCloseChannel + потеря данных.

## Находка 4: TP — Warning (понижено с Error)
**Файл:** DuplexChannel.kt:48-50
**Вердикт:** TP
**Обоснование:** `cancel(cause: Throwable?)` вызывает `income.cancel()` без cause. Перегрузка `cancel(cause: CancellationException?)` на строке 74 cause передаёт. Несогласованность есть, но runtime-поведение не ломается — теряется контекст отладки.

## Находка 5: TP — Error
**Файл:** MultiplexerImpl.kt:77-79, 129-133
**Вердикт:** TP
**Обоснование:** VirtualChannel удаляется из `activeChannels` только в `channelClosed` (remote close). При локальном `channel.close()` → `job.cancel()` запись остаётся. HashMap растёт, объекты не GC (inner class держит ссылку на MultiplexerImpl). Кумулятивно с Finding 1 (close не доходит до remote) — реальная утечка.

## Находка 6: TP — Error
**Файл:** MultiplexerTest.kt:116-118
**Вердикт:** TP
**Обоснование:** `outcome.close(e)` → `send()` на закрытом канале бросает `ClosedSendChannelException` (extends `IllegalStateException`), а не `CancellationException`. Тестовый catch не поймает exception → тест падает.

## Находка 7: TP — Warning
**Файл:** MultiplexerImpl.kt:120
**Вердикт:** TP
**Обоснование:** `requestChannel` handler (`incomeChannels.send()`) не имеет try-catch. Если `incomeChannels` закрыт, `ClosedSendChannelException` убивает `reading()` → read-цикл останавливается навсегда. Верхний catch в `reading()` ловит и логирует, но не восстанавливает чтение.

## Находка 8: TP — Warning
**Файл:** MultiplexerProtocol.kt:99-135
**Вердикт:** TP
**Обоснование:** `consumeEach` использует `consume { }`, который в `finally { cancel(cause) }` всегда отменяет physical-канал. В текущем сценарии использования единственный владелец — MultiplexerImpl — так что OK, но это недокументированный side effect.

## Находка 9: TP — Warning
**Файл:** MultiplexerProtocol.kt:109-111
**Вердикт:** TP
**Обоснование:** DATA-ветка обёрнута в try-catch(Throwable), CHANNEL_CLOSE/REQUEST/ACCEPT — нет. Битый пакет в CHANNEL_CLOSE убьёт весь read-цикл, хотя DATA смог бы пережить.

## Находка 10: FP
**Файл:** MultiplexerImpl.kt:64-66
**Вердикт:** FP
**Обоснование:** `income.cancel(e)` — non-suspend. `invokeOnClose` → `job.cancel()` — non-suspend. `Job.cancel()` на уже завершающейся job — no-op. Циклическая рекурсия отсутствует.

## Находка 11: TP — Typo
**Файл:** MultiplexerImpl.kt:36, 114
**Вердикт:** TP
**Обоснование:** Дважды `chanelJob` вместо `channelJob`.

## Находка 12: TP — Typo
**Файл:** MultiplexerProtocol.kt:65
**Вердикт:** TP
**Обоснование:** `coppingLogicalToPhysical` (с двумя 'p'), должно быть `copyingLogicalToPhysical`.

## Находка 13: TP — WeakWarning
**Файл:** Leb.kt:60, 73
**Вердикт:** TP
**Обоснование:** `writeUnsignedLeb128` — 0 usages, `EncodeLeb128` — 0 usages. Мёртвый код.

## Находка 14: TP — WeakWarning
**Файл:** DuplexChannel.kt:7
**Вердикт:** TP
**Обоснование:** Тяжёлый @Suppress необходим для реализации конфликтующих ReceiveChannel/SendChannel. Хрупко при обновлении корутин, альтернативы нет.

## Находка 15: TP — WeakWarning
**Файл:** MultiplexerTest.kt:76-85
**Вердикт:** TP
**Обоснование:** DATA отправлен до вызова accept(). Если readJob (Dispatchers.Default) обработает DATA раньше чем accept() зарегистрирует канал — данные потеряны. Потенциально flaky.

## Находка 16: TP — Info
**Файл:** MultiplexerImpl.kt:54-55
**Вердикт:** TP
**Обоснование:** addAndFetch(2) пропускает один ID из двух. Тривиально, 2³¹ каналов на peer достаточно.

## Находка 17: TP — Info
**Файл:** MultiplexerImpl.kt:89-90
**Вердикт:** TP
**Обоснование:** После 2·10⁹ созданий каналов Int переполнится. Вероятность ничтожна.

## Находка 18: TP — Info
**Файл:** Leb.kt:4-19
**Вердикт:** TP
**Обоснование:** Недочитанные LEB128-байты остаются в Buffer, следующий readByte() прочитает их как данные/команду. Для корректного remote-кода не проблема, для robustness стоит дочитать/упасть.

---

## Итог
- TP: 17 (6 Error, 5 Warning, 4 WeakWarning, 2 Info, 2 Typo)
- FP: 1 (Finding 10)
- NeedMoreData: 0
