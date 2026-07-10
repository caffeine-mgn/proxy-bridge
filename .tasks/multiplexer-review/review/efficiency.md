# Efficiency Review: Multiplexer Module

## Finding 1: CPU-wasteful spinlock in coroutine context

**Файл:** `multiplexer/src/commonMain/kotlin/pw/binom/multiplexer/AtomicBooleanExtensions.kt` : 7-13

**Проблема:** Busy-wait spinlock на AtomicBoolean в coroutine-контексте

**Severity:** Warning

**Описание:** `AtomicBoolean.lock()` реализован как `while(true) { if (compareAndSet(false, true)) break }` — классический spinlock. В coroutine-среде это блокирует целый поток исполнения (worker thread) на время ожидания, не давая планировщику запустить на нём другую корутину. Если корутина, захватившая lock, приостанавливается (suspend), удерживая lock, остальные корутины будут вхолостую сжигать 100% CPU, пока оригинальная корутина не возобновится и не отпустит lock.

**Исправление:** Заменить на `kotlinx.coroutines.sync.Mutex` с `withLock {}`. Если spinlock необходим (например, в performance-критичном разделе с гарантированно микросекундным удержанием), заменить busy-wait на хотя бы `Thread.yield()` или `parkNanos(1)`.

---

## Finding 2: Lock удерживается через suspend-точку

**Файл:** `multiplexer/src/commonMain/kotlin/pw/binom/multiplexer/MultiplexerImpl.kt` : 89-106

**Проблема:** `pendingChannelsLock` захвачен на входе (строка 89), затем внутри критической секции вызывается `sendRequestNewChannel` (строка 91-93) — suspend-функция, и только затем lock отпускается (строка 106). Учитывая, что lock — spinlock (Finding 1), все корутины, пытающиеся войти в критическую секцию, будут busy-wait'ить, пока оригинальная корутина находится в подвешенном состоянии.

**Severity:** Warning

**Описание:** `createChannel()` делает: `pendingChannelsLock.lock()` → `sendRequestNewChannel(output.send(...))` → `suspendCancellableCoroutine { ... pendingChannelsLock.unlock() }`. Lock удерживается через suspend. Даже если поменять spinlock на `Mutex`, удержание блокировки через await-точку повышает contention и увеличивает latency.

**Исправление:** Переписать логику: захватывать lock только на время записи в `pendingChannels`, а `sendRequestNewChannel` вынести до захвата или после отпускания lock'а.

---

## Finding 3: Избыточная аллокация Buffer и копирование данных в каждой отправке

**Файл:** `multiplexer/src/commonMain/kotlin/pw/binom/multiplexer/MultiplexerProtocol.kt` : 80-89

**Проблема:** Каждое логическое сообщение копируется целиком через промежуточный Buffer

**Severity:** Warning

**Описание:** `wrapLogicalToPhysical` создаёт новый `Buffer()`, пишет туда 1 байт команды, затем кодирует channelId (lebInt), и наконец делает `data.readFully(resultBuffer, data.size)` — то есть читает все байты из `data` в `resultBuffer`. Аллокация + O(n) копирование на каждое сообщение. Для крупных payload (мегабайты) это может быть значительным: удвоение памяти и пропускной способности памяти.

**Исправление:** Альтернативы: (a) использовать `Buffer.clone()` и затем `writeByte/lebInt` в начало (если API позволяет вставку), (б) перейти на zero-copy протокол, где заголовок и данные передаются раздельными сообщениями, (в) на JVM можно использовать `Unsafe` / direct buffers для header-prepend без копирования тела. Наиболее практичное решение для ktor-okio: отправлять два буфера (header и data) через `SendChannel` — если канал поддерживает batching, это может быть zero-copy.

---

## Finding 4: `bufferOf` пишет ByteArray побайтово вместо bulk write

**Файл:** `multiplexer/src/commonTest/kotlin/pw/binom/multiplexer/Utils.kt` : 7-13

**Проблема:** Итерирование ByteArray с вызовом `writeByte` на каждый элемент

**Severity:** WeakWarning

**Описание:** `bufferOf(bytes: ByteArray)` делает `bytes.forEach { buffer.writeByte(it) }` — N отдельных вызовов `writeByte` вместо одного `buffer.write(bytes)`. Для массива из 500 байт (как в тестах) это 500 виртуальных вызовов вместо одного. На hot-path (тест `testReceive` и `testSend` отправляют 500 байт) это лишняя аллокация stack frame'ов и bounds checks.

**Исправление:** Заменить `bytes.forEach { buffer.writeByte(it) }` на `buffer.write(bytes)`. Для `vararg`-версии — тоже.

---

## Finding 5: Неиспользуемый параметр `len` в `EncodeLeb128`

**Файл:** `multiplexer/src/commonMain/kotlin/pw/binom/multiplexer/Leb.kt` : 73

**Проблема:** Параметр `len: Int = 10` не используется — тело функции оперирует жёстко закодированным `PadTo = 10`

**Severity:** WeakWarning

**Описание:** Сигнатура `EncodeLeb128(value: Long, len: Int = 10, ...)` принимает `len`, но первая же строка тела переопределяет `var PadTo = 10`, полностью игнорируя `len`. Это dead code / misleading API. На производительность не влияет (компилятор, скорее всего, выбрасывает неиспользуемый параметр), но создаёт ложное впечатление настраиваемости и увеличивает когнитивную нагрузку.

**Исправление:** Убрать параметр `len: Int = 10` из сигнатуры, если он не нужен, или использовать `var PadTo = len` если задумывалась конфигурируемость.

---

## Finding 6: Неограниченные буферы Channel(UNLIMITED)

**Файл:** `multiplexer/src/commonMain/kotlin/pw/binom/multiplexer/MultiplexerImpl.kt` : 37, 55-56

**Проблема:** Каналы VirtualChannel.income, VirtualChannel.outcome, incomeChannels используют `Channel.UNLIMITED`

**Severity:** WeakWarning

**Описание:** `Channel(Channel.UNLIMITED)` — это unbounded linked-list буфер (на JVM — ConcurrentLinkedQueue). При превышении скорости записи над чтением буфер растёт без ограничений, потребляя всю доступную память вплоть до OOM. Для сетевого мультиплексора, где пакеты могут поступать быстрее, чем их обрабатывает приложение, это потенциальная точка отказа.

**Исправление:** Использовать канал с ограниченной ёмкостью (например, `Channel(256)` или `Channel(BUFFERED)`) для создания backpressure. Если UNLIMITED необходим для предотвращения deadlock'ов, добавить явный мониторинг размера буфера или `onUndeliveredElement` для отбрасывания старых сообщений.

---

## Finding 7: Синхронизация через `locking {}` в hot-path readJob

**Файл:** `multiplexer/src/commonMain/kotlin/pw/binom/multiplexer/MultiplexerImpl.kt` : 121, 134-136, 144

**Проблема:** Callback'и readJob обращаются к `activeChannels` и `pendingChannels` через spinlock-обёртку `locking {}` на каждом входящем пакете

**Severity:** Info

**Описание:** readJob — единственная корутина, исполняющая MultiplexerProtocol.reading. При каждом входящем DATA/CLOSE/ACCEPT пакете происходит захват spinlock через `activeChannelsLock.locking { ... }` или `pendingChannelsLock.locking { ... }`. Даже если contention нет, сам цикл CAS (compareAndSet) имеет ненулевую стоимость. С `Mutex` стоимость была бы ниже, но в readJob всё равно нет конкуренции за эти map'ы, кроме коллизий с `accept()` / `createChannel()` / `close()`.

**Исправление:** Рассмотреть использование `ConcurrentHashMap` (если доступен на целевых платформах) или однопоточную диспетчеризацию всех операций с каналами через actor. Это сняло бы overhead синхронизации в readJob entirely.

---

## Finding 8: `rawSource.readFully` — ручная реализация с побайтовым чтением

**Файл:** `multiplexer/src/commonMain/kotlin/pw/binom/multiplexer/RawSourceExtensions.kt` : 7-16

**Проблема:** Реализация циклически вызывает `readAtMostTo(dst, remaining)` — документированно может возвращать -1 (EOF) итерациями по кускам

**Severity:** Info

**Описание:** `readFully` — ручная обёртка над `RawSource.readAtMostTo`. Корректно, но может быть менее эффективно, чем нативная реализация `RawSource.readFully` из okio/kotlinx-io (если она существует на целевой платформе). При каждом вызове `readAtMostTo` происходит накладные расходы на JNI/native bridge.

**Исправление:** Если `kotlinx.io.RawSource` предоставляет built-in `readFully(dst, byteCount)`, использовать его — вероятно, нативная реализация быстрее.

---

## Finding 9: `idGenerator.addAndFetch(2)` — только половина ID space используется (minor)

**Файл:** `multiplexer/src/commonMain/kotlin/pw/binom/multiplexer/MultiplexerImpl.kt` : 32

**Проблема:** Шаг генерации ID равен 2, при этом чётные/нечётные ID делятся между двумя экземплярами мультиплексора

**Severity:** Info

**Описание:** `idGenerator = AtomicInt(if (idOdd) 1 else 0)` с инкрементом по 2. Один экземпляр использует только нечётные ID, другой — только чётные. Хотя 2³¹ каналов на экземпляр практически избыточно, это неэффективное использование диапазона, и при очень большом количестве каналов (миллиарды) ID могут переполниться раньше времени.

**Исправление:** Документировать как осознанное решение для разделения ID-пространства между peer'ами. Если нужна полная ёмкость — использовать общий AtomicLong или другой протокол handshake.
