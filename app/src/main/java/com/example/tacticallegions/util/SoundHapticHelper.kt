package com.example.tacticallegions.util

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator

class SoundHapticHelper(context: Context) {
    private val toneGenerator = try {
        ToneGenerator(AudioManager.STREAM_MUSIC, 100)
    } catch (e: Exception) {
        null
    }

    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    fun playShootSound() {
        toneGenerator?.startTone(ToneGenerator.TONE_PROP_PROMPT, 100)
    }

    fun playMissSound() {
        toneGenerator?.startTone(ToneGenerator.TONE_PROP_NACK, 200)
    }

    fun playHitConfirmSound() {
        toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 150)
    }

    fun playTargetLockSound() {
        toneGenerator?.startTone(ToneGenerator.TONE_CDMA_PIP, 50)
    }

    fun playEliminationSound() {
        toneGenerator?.startTone(ToneGenerator.TONE_SUP_ERROR, 500)
    }

    fun playCountdownSound() {
        toneGenerator?.startTone(ToneGenerator.TONE_CDMA_PIP, 100)
    }

    fun playRoundStartSound() {
        toneGenerator?.startTone(ToneGenerator.TONE_SUP_CONFIRM, 500)
    }

    fun playRoundEndSound() {
        toneGenerator?.startTone(ToneGenerator.TONE_SUP_ERROR, 800)
    }

    fun vibrateShoot() {
        vibrate(80)
    }

    fun vibrateLock() {
        vibrate(30)
    }

    fun vibrateHit() {
        vibrate(250)
    }

    fun vibrateEliminated() {
        val pattern = longArrayOf(0, 150, 100, 150, 100, 400)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, -1)
        }
    }

    private fun vibrate(duration: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(duration)
        }
    }

    fun release() {
        try {
            toneGenerator?.release()
        } catch (e: Exception) {}
    }
}
