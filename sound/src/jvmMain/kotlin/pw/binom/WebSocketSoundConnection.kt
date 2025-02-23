package pw.binom

import pw.binom.io.http.websocket.WebSocketConnection
import pw.binom.sound.AsyncSoundInput
import pw.binom.sound.AsyncSoundOutput
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.Mixer
import pw.binom.io.http.websocket.WebSocketConnectionImpl as WebSocketConnectionImpl1


object WebSocketSoundConnection {
    fun create(
        formatInput: AudioFormat,
        deviceInput: Mixer.Info,
        formatOutput: AudioFormat,
        deviceOutput: Mixer.Info
    ): WebSocketConnection {
        val output = AsyncSoundOutput.create(format = formatOutput, mixerInfo = deviceOutput)
        val input = AsyncSoundInput.create(format = formatInput, mixerInfo = deviceInput)
        return WebSocketConnectionImpl1(
            _output = output,
            _input = input,
            masking = false,
            mainChannel = {
                output.asyncCloseAnyway()
                input.asyncCloseAnyway()
            }
        )
    }
}
