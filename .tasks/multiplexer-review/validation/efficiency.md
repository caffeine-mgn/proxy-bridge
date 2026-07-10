# Validation Results: Efficiency Reviewer

## Находка 1: TP — Warning
**Файл:** AtomicBooleanExtensions.kt:7-13
**Вердикт:** TP (подтверждена)
**Обоснование:** Busy-wait spinlock без backoff в coroutine-контексте блокирует поток. Критические секции короткие, но при contention поток сжигает CPU впустую. Замена на Mutex или хотя бы `Thread.yield()` оправдана. Severity: Warning.

## Находка 2: TP — Warning
**Файл:** MultiplexerImpl.kt:89-106
**Вердикт:** TP (подтверждена)
**Обоснование:** `pendingChannelsLock` захвачен ДО и отпущен ПОСЛЕ suspend-вызова `sendRequestNewChannel`. Смягчающий фактор: `output` — UNLIMITED канал, так что `send()` не приостанавливается на практике. Однако lock семантически удерживается через suspend-точку, что неправильно. Severity: Warning.

## Находка 3: TP — Warning
**Файл:** MultiplexerProtocol.kt:80-89
**Вердикт:** TP (подтверждена)
**Обоснование:** Каждый вызов `wrapLogicalToPhysical` аллоцирует новый Buffer и копирует все данные через `readFully`. O(n) аллокация + копирование на каждое сообщение. Для больших payload — значительный overhead. Severity: Warning.

## Находка 4: TP — WeakWarning
**Файл:** Utils.kt:7-13
**Вердикт:** TP (подтверждена)
**Обоснование:** `bytes.forEach { buffer.writeByte(it) }` — N вызовов вместо одного `buffer.write(bytes, 0, bytes.size)`. Тестовый код, не hot-path. Severity: WeakWarning.

## Находка 5: TP — WeakWarning
**Файл:** Leb.kt:73
**Вердикт:** TP (подтверждена)
**Обоснование:** Параметр `len: Int = 10` объявлен, но тело функции использует локальную `var PadTo = 10`, игнорируя параметр. Впрочем, вся функция `EncodeLeb128` мёртвая. Severity: WeakWarning.

## Находка 6: TP — WeakWarning
**Файл:** MultiplexerImpl.kt:37, 55-56
**Вердикт:** TP (подтверждена)
**Обоснование:** `Channel(UNLIMITED)` для incomeChannels, VirtualChannel.income и VirtualChannel.outcome — отсутствие backpressure. Осознанный выбор для избежания deadlock'ов в I/O конвейере. Риск OOM при перегрузке. Severity: WeakWarning.

## Находка 7: TP — Info (понижено с Warning)
**Файл:** MultiplexerImpl.kt:121, 134-136, 144
**Вердикт:** TP (подтверждена) с понижением severity
**Обоснование:** `activeChannelsLock.locking {}` и `pendingChannelsLock.locking {}` вызываются на каждый входящий пакет. Однако readJob — единственный читатель; contention с accept/createChannel/close минимален. CAS на uncontended AtomicBoolean — единицы наносекунд. Severity: Info.

## Находка 8: FP (ложная)
**Файл:** RawSourceExtensions.kt:7-16
**Вердикт:** FP (ложная)
**Обоснование:** В kotlinx-io 0.9.0 `RawSource` имеет только `readAtMostTo()`. Встроенного `readFully` нет. Ручная реализация неизбежна и корректна.

## Находка 9: TP — Info
**Файл:** MultiplexerImpl.kt:32
**Вердикт:** TP (подтверждена)
**Обоснование:** `idGenerator.addAndFetch(2)` использует половину ID-space. Осознанный дизайн для разделения чётных/нечётных ID между peer'ами. 2³¹ ID на экземпляр достаточно. Severity: Info.

---

## Итог
- **TP:** 8
- **FP:** 1 (Finding 8)
- **NeedMoreData:** 0
