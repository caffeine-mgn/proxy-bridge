package pw.binom.proxy.channel

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.io.Buffer
import org.koin.core.component.KoinComponent
import pw.binom.multiplexer.DuplexChannel
import pw.binom.multiplexer.lebString
import pw.binom.properties.OutcomeService
import pw.binom.proxy.utils.connect
import pw.binom.proxy.utils.send

/**
 * [OutcomeWrapperChannel] — протокольный обработчик для проброса (bridge)
 * удалённого outcome через транспортное соединение.
 *
 * ## Как это работает (полный цикл проброса)
 *
 * ### Сценарий: клиент хочет подключиться к COM-порту на сервере
 *
 * ```yaml
 * # Конфигурация СЕРВЕРА (рядом с COM-портом)
 * outcomes:
 *   temperature-sensor:
 *     type: com
 *     port: /dev/ttyUSB0
 *     speed: 115200
 *
 * # Конфигурация КЛИЕНТА (удалённая машина)
 * outcomes:
 *   tunnel:
 *     type: tcp
 *     host: server.com
 *     port: 9999
 *   sensor:
 *     type: wrapper
 *     outcome: tunnel       # имя транспорта, по которому стучаться к серверу
 * ```
 *
 * Последовательность шагов:
 *
 * 1. Клиент вызывает `OutcomeWrapperService("sensor").createChannel()`.
 * 2. [OutcomeWrapperService] находит в Koin `OutcomeService` с именем `"tunnel"`,
 *    открывает через него канал (TCP-соединение к server.com:9999).
 * 3. [OutcomeWrapperService] вызывает [openChannel]: отправляет по этому каналу
 *    протокольное сообщение `[ID=3, "temperature-sensor"]` и ждёт ответ.
 * 4. На сервере мультиплексор получает канал с ID=3 и маршрутизирует его
 *    в [OutcomeWrapperChannel.income].
 * 5. [income] читает имя outcome (`"temperature-sensor"`), находит у себя
 *    сервис с таким именем, создаёт через него канал к `/dev/ttyUSB0`.
 * 6. [income] отправляет клиенту байт результата (`SUCCESS` / `OUTCOME_NOT_FOUND` / `ERROR`).
 * 7. При успехе [income] соединяет (bridge) два канала:
 *      канал клиента (TCP) ↔ канал COM-порта
 *    — все данные потекли в обе стороны прозрачно.
 *
 * Итог: клиентский код работает с `DuplexChannel`, не подозревая,
 * что данные проходят через TCP-туннель → сервер → физический COM-порт.
 */
class OutcomeWrapperChannel : KoinComponent, ChannelHandler {

    enum class ChannelResult {
        /** Сервер успешно подключил запрошенный outcome */
        SUCCESS,
        /** На сервере нет outcome с таким именем */
        OUTCOME_NOT_FOUND,
        /** Не удалось открыть канал к outcome на сервере */
        ERROR,
    }

    companion object {
        /** Идентификатор протокола в мультиплексоре */
        private const val ID: Byte = 3

        private const val ERROR: Byte = 0
        private const val OUTCOME_NOT_FOUND: Byte = 1
        private const val SUCCESS: Byte = 2

        /**
         * Открывает проброс (bridge) к удалённому outcome через已有的 канал [channel].
         *
         * 1. Отправляет по [channel] протокольное сообщение с именем целевого outcome.
         * 2. Ждёт ответ от сервера (SUCCESS / ERROR / NOT_FOUND).
         *
         * При [ChannelResult.SUCCESS] канал [channel] уже соединён мостом
         * с удалённым outcome — в него можно писать и читать.
         */
        suspend fun openChannel(channel: DuplexChannel, remoteOutcomeName: String): ChannelResult {
            channel.send {
                writeByte(ID)
                lebString(remoteOutcomeName)
            }
            return when (val code = channel.receive().readByte()) {
                ERROR -> ChannelResult.ERROR
                OUTCOME_NOT_FOUND -> ChannelResult.OUTCOME_NOT_FOUND
                SUCCESS -> ChannelResult.SUCCESS
                else -> error("Unknown result: $code")
            }
        }
    }

    private val logger = KotlinLogging.logger { }
    override val id: Byte
        get() = ID

    private val outcomes by lazy { getKoin().getAll<OutcomeService>().associateBy { it.name } }

    override suspend fun income(channel: DuplexChannel, buffer: Buffer) {
        val outcomeName = buffer.lebString()
        val outcome = outcomes[outcomeName]
        if (outcome == null) {
            channel.send {
                writeByte(OUTCOME_NOT_FOUND)
            }
            return
        }
        val newChannel = try {
            outcome.createChannel()
        } catch (e: Throwable) {
            logger.warn(e) { "Can't create channel on outcome $outcomeName" }
            channel.send {
                writeByte(ERROR)
            }
            return
        }
        channel.send {
            writeByte(SUCCESS)
        }
        connect(
            outcome = channel.outcome,
            income = channel.income,
            a = newChannel.outcome,
            b = newChannel.income,
        )
    }
}
