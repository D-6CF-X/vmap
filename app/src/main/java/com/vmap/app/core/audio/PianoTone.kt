package com.vmap.app.core.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * 极简钢琴音源：现场合成一段带泛音衰减的琴音 PCM，用 AudioTrack 播放。
 *
 * 巡演动画里每点亮一座城就弹一个音，音高逐级上行（从低到高），
 * 比 ToneGenerator 的滴滴声更像"钢琴"。
 *
 * 合成思路：基频 + 5 个泛音，每个泛音的衰减速度不同（越高的泛音衰减越快，
 * 这是钢琴音色发亮但很快变柔的关键），再叠 4ms 起音和尾部淡出防爆音。
 */
class PianoTone {

    private val sampleRate = 44100

    /** 单线程播放，保证前一个音被打断时不会两个线程同时写同一个 AudioTrack。 */
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "vmap-piano").apply { isDaemon = true }
    }

    private var track: AudioTrack? = null
    private val bufferCache = HashMap<Pair<Int, Int>, ShortArray>()

    /** 余音结束后把轨道停掉，避免缓冲区耗尽产生 underrun。 */
    private val scheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "vmap-piano-stop").apply { isDaemon = true }
    }

    /** 基准音：C4=60。传 midi 音高，[ms] 为余音长度。 */
    fun playMidi(midi: Int, ms: Int = 900, volume: Float = 0.55f) {
        val pcm = bufferCache.getOrPut(midi to ms) { synth(midi, ms) }
        executor.execute {
            runCatching {
                val t = ensureTrack(pcm.size * 2)
                t.pause()
                t.flush()
                t.setVolume(volume)
                t.write(pcm, 0, pcm.size)
                t.play()
                // 音尾淡出后主动停掉，否则 buffer 空了会有 underrun 告警
                scheduler.schedule({ runCatching { t.pause() } }, ms.toLong(), java.util.concurrent.TimeUnit.MILLISECONDS)
            }
        }
    }

    /** 逐城上行：第 [step] 座城弹 do/re/mi…，一路往上，走到高音区后维持最高音。 */
    fun playStep(step: Int, baseMidi: Int = 60) {
        playMidi(baseMidi + step.coerceIn(0, 24))
    }

    fun release() {
        executor.execute {
            runCatching {
                track?.pause()
                track?.flush()
                track?.release()
            }
            track = null
        }
        scheduler.shutdownNow()
    }

    private fun ensureTrack(bufferBytes: Int): AudioTrack {
        val existing = track
        if (existing != null && existing.state == AudioTrack.STATE_INITIALIZED) return existing
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val size = maxOf(minBuf, bufferBytes)
        val t = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
            size,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE,
        )
        track = t
        return t
    }

    private fun synth(midi: Int, ms: Int): ShortArray {
        val f0 = 440.0 * Math.pow(2.0, (midi - 69) / 12.0)
        val n = (sampleRate * ms / 1000.0).toInt()
        val out = ShortArray(n)
        // 泛音振幅：1、2、3、4、5、6 次
        val amps = doubleArrayOf(1.0, 0.46, 0.24, 0.13, 0.07, 0.04)
        val dur = ms / 1000.0
        for (i in 0 until n) {
            val t = i.toDouble() / sampleRate
            var v = 0.0
            for (h in amps.indices) {
                val f = f0 * (h + 1)
                if (f > sampleRate / 2.0) continue
                // 高次泛音衰减更快，音头亮、余音暖
                val decay = exp(-t * (2.1 + h * 1.6))
                v += amps[h] * decay * sin(2.0 * PI * f * t)
            }
            // 4ms 起音
            val attack = (t / 0.004).coerceAtMost(1.0)
            // 最后 90ms 淡出
            val tail = ((dur - t) / 0.09).coerceIn(0.0, 1.0)
            val s = v * attack * tail * 0.28
            out[i] = (s * Short.MAX_VALUE).toInt().coerceIn(-32768, 32767).toShort()
        }
        return out
    }
}
