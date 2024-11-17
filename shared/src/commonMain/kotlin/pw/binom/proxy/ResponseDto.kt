package pw.binom.proxy
/*

import kotlinx.serialization.Serializable
import pw.binom.ArgCounter
import pw.binom.proxy.serialization.ShortSerialization

@Serializable
data class ResponseDto(
    val ok: OK? = null,
    val unknownHost: UnknownHost? = null,
    val unknownError: UnknownError? = null,
    val channelNotFound: ChannelNotFound? = null,
    val channelExist: ChannelExist? = null,
) {
    fun toByteArray() =
        ShortSerialization.encodeByteArray(
            serializer(),
            this
        )

    init {
        ArgCounter.calc {
            add(ok, "ok")
            add(unknownHost, "unknownHost")
            add(unknownError, "unknownError")
            add(channelNotFound, "channelNotFound")
            add(channelExist, "channelExist")
        }
    }

    companion object {
        fun fromByteArray(array: ByteArray) = ShortSerialization.decodeByteArray(serializer(), array)

        fun ok() = ResponseDto(ok = OK)

        fun unknownHost() = ResponseDto(unknownHost = UnknownHost)

        fun channelNotFound() = ResponseDto(channelNotFound = ChannelNotFound)

        fun channelExist() = ResponseDto(channelExist = ChannelExist)

        fun unknownError(message: String?) = ResponseDto(unknownError = UnknownError(message = message))
    }

    val isOk
        get() = ok != null

    @Serializable
    object OK

    @Serializable
    object ChannelNotFound

    @Serializable
    object ChannelExist

    @Serializable
    object UnknownHost

    @Serializable
    class UnknownError(val message: String?)
}
*/
