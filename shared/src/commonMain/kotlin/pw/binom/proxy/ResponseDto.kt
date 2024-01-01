package pw.binom.proxy

import kotlinx.serialization.Serializable

@Serializable
class ResponseDto(
    val ok: OK? = null,
    val unknownHost: UnknownHost? = null,
    val unknownError: UnknownError? = null,
    val channelNotFound: ChannelNotFound? = null,
    val channelExist: ChannelExist? = null,
) {
    companion object {
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
