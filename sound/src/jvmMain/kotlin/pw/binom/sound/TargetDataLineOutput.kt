package pw.binom.sound

import pw.binom.io.ByteBuffer
import pw.binom.io.DataTransferSize
import pw.binom.io.Input
import javax.sound.sampled.TargetDataLine

class TargetDataLineOutput(val out: TargetDataLine):Input {
    override fun close() {
        out.close()
    }

    override fun read(dest: ByteBuffer): DataTransferSize {
        TODO("Not yet implemented")
    }
}
