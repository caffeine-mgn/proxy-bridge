package pw.binom.proxy.services

import org.koin.core.component.KoinComponent
import pw.binom.proxy.channel.OutcomeWrapperChannel
import pw.binom.multiplexer.DuplexChannel
import pw.binom.properties.OutcomeService

/**
 * [OutcomeWrapperService] — это сервис-обёртка над другим [pw.binom.properties.OutcomeService],
 * позволяющий мультиплексировать несколько логических outcome-соединений через
 * одно физическое транспортное соединение (например, TCP или COM-порт).
 *
 * Архитектурно класс решает задачу **проксирования/маршрутизации**:
 * 1. Класс получает имя целевого outcome (поле [outcome]) и находит настоящий
 *    [pw.binom.properties.OutcomeService] по этому имени в DI-контейнере Koin.
 * 2. Создаёт канал через найденный сервис (например, открывает TCP-соединение
 *    к удалённому хосту).
 * 3. Поверх этого канала отправляет протокольное сообщение (ID=3) с именем
 *    целевого outcome на удалённую сторону.
 * 4. Удалённая сторона ([OutcomeWrapperChannel.income]) получает запрос, находит
 *    у себя outcome по имени, создаёт канал к нему и соединяет (bridge) два канала
 *    — таким образом, инициатор получает прямой канал к нужному удалённому ресурсу.
 *
 * Типовая конфигурация (Configuration.Outcome.Wrapper):
 * ```yaml
 * outcomes:
 *   remote-link:
 *     type: tcp
 *     host: 10.0.0.1
 *     port: 1234
 *   my-logical-outcome:
 *     type: wrapper
 *     outcome: remote-link
 * ```
 * В этом примере `my-logical-outcome` — это ссылка на удалённый outcome,
 * доступный через TCP-соединение `remote-link`.
 *
 * @param name имя этого сервиса (используется для lookup в DI).
 * @param outcome имя целевого [pw.binom.properties.OutcomeService], к которому подключаться удалённо.
 */
class OutcomeWrapperService(
    override val name: String,
    val outcome: String,
) : OutcomeService, KoinComponent {

    private val outcomes by lazy { getKoin().getAll<OutcomeService>().associateBy { it.name } }

    override suspend fun createChannel(): DuplexChannel {
        val realOutcome = outcomes[outcome] ?: throw IllegalStateException("Outcome $outcome not found")
        val channel = realOutcome.createChannel()
        val result = OutcomeWrapperChannel.openChannel(
            channel = channel,
            remoteOutcomeName = outcome,
        )
        when (result) {
            OutcomeWrapperChannel.ChannelResult.SUCCESS -> return channel
            OutcomeWrapperChannel.ChannelResult.OUTCOME_NOT_FOUND -> throw IllegalStateException("Outcome $outcome not found")
            OutcomeWrapperChannel.ChannelResult.ERROR -> throw IllegalStateException("Can't open channel on remote outcome $outcome")
        }
    }
}
