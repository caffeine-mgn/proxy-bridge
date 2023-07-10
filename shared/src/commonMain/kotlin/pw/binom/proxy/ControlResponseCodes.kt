package pw.binom.proxy

enum class ControlResponseCodes(val code: Byte) {
    OK(code = 0),
    UNKNOWN_ERROR(code = 1),
    UNKNOWN_HOST(code = 2);

    companion object {
        val byCode = values().associateBy { it.code }
        fun getByCode(code: Byte) =
            byCode[code] ?: throw IllegalArgumentException("Can't find response with code $code")
    }
}
