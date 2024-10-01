package pw.binom.exceptions

class TimeoutException : IllegalStateException {
    constructor(message: String) : super(message)
    constructor() : super()
}
