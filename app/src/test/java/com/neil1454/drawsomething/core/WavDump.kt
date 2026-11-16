package com.neil1454.drawsomething.core

import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

class WavDump {
    private fun wav(pcm: ShortArray): ByteArray {
        val data = ByteArray(pcm.size * 2)
        for (i in pcm.indices) { data[2 * i] = (pcm[i].toInt() and 0xFF).toByte(); data[2 * i + 1] = (pcm[i].toInt() shr 8).toByte() }
        val o = ByteArrayOutputStream()
        fun w32(v: Int) { o.write(v and 0xFF); o.write((v shr 8) and 0xFF); o.write((v shr 16) and 0xFF); o.write((v shr 24) and 0xFF) }
        fun w16(v: Int) { o.write(v and 0xFF); o.write((v shr 8) and 0xFF) }
        o.write("RIFF".toByteArray()); w32(36 + data.size); o.write("WAVEfmt ".toByteArray()); w32(16); w16(1); w16(1); w32(Synth.RATE); w32(Synth.RATE * 2); w16(2); w16(16)
        o.write("data".toByteArray()); w32(data.size); o.write(data)
        return o.toByteArray()
    }

    @Test fun dump() {
        val dir = File("/tmp/claude-0/-home-user/b86e8ce0-8a07-5e9f-848a-4d4f3fa346cb/scratchpad/previews")
        for (s in listOf(Sound.VOICE, Sound.RING, Sound.BOX_OPEN, Sound.APPLAUSE, Sound.GROAN, Sound.CASH, Sound.DRUMROLL, Sound.BANKER, Sound.CHEER, Sound.JACKPOT))
            File(dir, "${s.name.lowercase()}.wav").writeBytes(wav(Synth.render(s)))
        File(dir, "deal_music.wav").writeBytes(wav(Synth.renderDealMusic()))
    }
}
