package com.activegames.tacticallegions.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
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

    private var gunshotTrack: AudioTrack? = null

    init {
        try {
            val sampleRate = 44100
            val duration = 0.4f // 0.4 seconds
            val numSamples = (sampleRate * duration).toInt()
            val pcmData = ShortArray(numSamples)
            val random = java.util.Random()

            for (i in 0 until numSamples) {
                val t = i.toFloat() / sampleRate
                
                // 1. Initial Transient (Sharp crack)
                val initialCrackNoise = (random.nextFloat() * 2f - 1f) * Math.exp(-200.0 * t).toFloat()
                
                // High frequency metallic/barrel chirp (from 4000Hz down to 1200Hz in ~10ms)
                val chirpPhase = 2f * Math.PI.toFloat() * (4000f * t - 140000f * t * t).coerceAtLeast(0f)
                val chirp = Math.sin(chirpPhase.toDouble()).toFloat() * Math.exp(-150.0 * t).toFloat()
                
                // 2. Muzzle Blast Thump (Explosive bass)
                val thumpPhase = 2f * Math.PI * 130f * (1.0 - Math.exp(-35.0 * t)) / 35.0
                val thump = Math.sin(thumpPhase).toFloat() * Math.exp(-20.0 * t).toFloat()
                
                // 3. Barrel Resonance & Combustion Noise (Mid-range texture)
                val midNoise = (random.nextFloat() * 2f - 1f) * Math.exp(-35.0 * t).toFloat()
                
                // Low-frequency reverb tail (echo)
                val tailNoise = (random.nextFloat() * 2f - 1f) * Math.exp(-8.0 * t).toFloat()
                
                // Mix the elements
                val rawSample = (
                    initialCrackNoise * 0.8f + 
                    chirp * 0.4f + 
                    thump * 0.5f + 
                    midNoise * 0.3f + 
                    tailNoise * 0.15f
                )
                
                // Soft clipping / saturation for raw power
                val drive = 1.6f
                var drivenSample = rawSample * drive
                if (drivenSample > 1.0f) {
                    drivenSample = 2.0f / 3.0f
                } else if (drivenSample < -1.0f) {
                    drivenSample = -2.0f / 3.0f
                } else {
                    drivenSample = drivenSample - (drivenSample * drivenSample * drivenSample) / 3.0f
                }
                val finalSample = drivenSample * 1.2f
                
                // Scale to 16-bit signed PCM range
                pcmData[i] = (finalSample * 32767).toInt().coerceIn(-32768, 32767).toShort()
            }

            val bufferSize = numSamples * 2

            val audioTrack = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize,
                    AudioTrack.MODE_STATIC
                )
            }

            audioTrack.write(pcmData, 0, pcmData.size)
            gunshotTrack = audioTrack
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun playShootSound() {
        val track = gunshotTrack
        if (track != null) {
            try {
                track.stop()
                track.reloadStaticData()
                track.play()
            } catch (e: Exception) {
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_PROMPT, 100)
            }
        } else {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_PROMPT, 100)
        }
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
        val pattern = longArrayOf(0, 300, 300) // 300ms vibrate, 300ms pause
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }
    }

    fun stopVibration() {
        vibrator?.cancel()
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
        try {
            gunshotTrack?.stop()
            gunshotTrack?.release()
        } catch (e: Exception) {}
    }
}
