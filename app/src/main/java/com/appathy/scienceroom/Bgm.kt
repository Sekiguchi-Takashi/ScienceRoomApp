package com.appathy.scienceroom

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * 音源ファイルを持たずに BGM を鳴らす。
 * 純正律に近いペンタトニックをゆっくり分散させ、減衰の長い柔らかい音を並べる。
 * 音量は控えめにして、考えごとの邪魔にならない範囲に収める。
 */
object Bgm {

    private const val SAMPLE_RATE = 22050
    private const val NOTE_MS = 900
    private const val GAIN = 0.16f

    /** ヨナ抜き五音音階。どの順で鳴らしても濁りにくい */
    private val scale = floatArrayOf(
        261.63f, 293.66f, 329.63f, 392.00f, 440.00f,
        523.25f, 587.33f, 659.25f, 784.00f, 880.00f
    )

    private val pattern = intArrayOf(0, 2, 4, 3, 5, 7, 5, 3, 2, 0, 1, 3, 6, 4, 2, 1)

    @Volatile
    private var playing = false
    private var worker: Thread? = null
    private var track: AudioTrack? = null

    val isPlaying: Boolean get() = playing

    /** 1音分の波形。倍音を少し足し、指数関数で減衰させる */
    private fun renderNote(freq: Float, samples: Int): ShortArray {
        val buffer = ShortArray(samples)
        for (i in 0 until samples) {
            val t = i.toDouble() / SAMPLE_RATE
            val decay = exp(-2.2 * t)
            val attack = if (i < 400) i / 400.0 else 1.0
            val wave = sin(2 * PI * freq * t) +
                0.28 * sin(4 * PI * freq * t) +
                0.12 * sin(6 * PI * freq * t)
            val v = wave * decay * attack * GAIN * Short.MAX_VALUE
            buffer[i] = v.toInt().coerceIn(-32768, 32767).toShort()
        }
        return buffer
    }

    fun start() {
        if (playing) return
        playing = true

        worker = thread(start = true, isDaemon = true, name = "bgm") {
            val samplesPerNote = SAMPLE_RATE * NOTE_MS / 1000
            val minBuffer = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = maxOf(minBuffer, samplesPerNote * 2)

            val built = try {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            } catch (e: Exception) {
                playing = false
                return@thread
            }

            track = built
            try {
                built.play()
                var index = 0
                while (playing) {
                    val step = pattern[index % pattern.size]
                    val note = renderNote(scale[step % scale.size], samplesPerNote)
                    built.write(note, 0, note.size)
                    index++
                }
            } catch (e: Exception) {
                // 端末が音を出せない場合は静かに諦める
            } finally {
                try {
                    built.stop()
                    built.release()
                } catch (e: Exception) {
                    // すでに解放済みなら無視
                }
                track = null
            }
        }
    }

    fun stop() {
        playing = false
        try {
            track?.pause()
            track?.flush()
        } catch (e: Exception) {
            // 停止途中なら無視
        }
        worker = null
    }

    @Suppress("UNUSED_PARAMETER")
    fun streamType(): Int = AudioManager.STREAM_MUSIC
}
