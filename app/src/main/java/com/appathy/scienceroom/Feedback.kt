package com.appathy.scienceroom

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * 効果音は音声ファイルを持たず、端末の ToneGenerator で鳴らす。
 * APK を太らせず、著作権の心配もない。
 */
object Feedback {

    enum class Kind { TAP, CORRECT, WRONG, SUCCESS, FAIL, DISCOVER, UNLOCK }

    private var generator: ToneGenerator? = null

    private fun tone(): ToneGenerator? {
        if (generator == null) {
            generator = try {
                ToneGenerator(AudioManager.STREAM_MUSIC, 70)
            } catch (e: RuntimeException) {
                null
            }
        }
        return generator
    }

    private fun vibrator(context: Context): Vibrator? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(VibratorManager::class.java)
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    } catch (e: Exception) {
        null
    }

    private fun beep(type: Int, ms: Int) {
        try {
            tone()?.startTone(type, ms)
        } catch (e: Exception) {
            // 鳴らせなくても進行に影響はない
        }
    }

    private fun buzz(context: Context, ms: Long) {
        val v = vibrator(context) ?: return
        if (!v.hasVibrator()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(ms)
            }
        } catch (e: Exception) {
            // 権限がなければ何もしない
        }
    }

    /** soundOn / hapticOn は呼び出し側の設定を渡す */
    fun play(context: Context, kind: Kind, soundOn: Boolean, hapticOn: Boolean) {
        if (soundOn) {
            when (kind) {
                Kind.TAP -> beep(ToneGenerator.TONE_PROP_BEEP, 40)
                Kind.CORRECT -> beep(ToneGenerator.TONE_PROP_ACK, 120)
                Kind.WRONG -> beep(ToneGenerator.TONE_PROP_NACK, 200)
                Kind.SUCCESS -> beep(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 300)
                Kind.FAIL -> beep(ToneGenerator.TONE_SUP_ERROR, 250)
                Kind.DISCOVER -> beep(ToneGenerator.TONE_PROP_BEEP2, 200)
                Kind.UNLOCK -> beep(ToneGenerator.TONE_CDMA_CONFIRM, 300)
            }
        }
        if (hapticOn) {
            when (kind) {
                Kind.TAP -> buzz(context, 15)
                Kind.CORRECT, Kind.DISCOVER -> buzz(context, 30)
                Kind.WRONG -> buzz(context, 60)
                Kind.SUCCESS, Kind.UNLOCK -> buzz(context, 90)
                Kind.FAIL -> buzz(context, 120)
            }
        }
    }

    fun release() {
        try {
            generator?.release()
        } catch (e: Exception) {
            // すでに解放済みなら無視
        }
        generator = null
    }
}
