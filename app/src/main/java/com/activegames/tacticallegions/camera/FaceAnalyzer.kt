package com.activegames.tacticallegions.camera

import android.graphics.PointF
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.activegames.tacticallegions.network.PlayerState
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import kotlin.math.hypot
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Vibrator
import android.os.VibrationEffect
import android.os.Build

object FaceSignatureHelper {
    
    // Calculates Euclidean distance between two 2D points
    private fun dist(p1: PointF, p2: PointF): Float {
        return hypot(p1.x - p2.x, p1.y - p2.y)
    }

    // Generates a 10-dimensional relative-distance signature
    fun calculateSignature(face: Face): List<Float>? {
        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position ?: return null
        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position ?: return null
        val noseBase = face.getLandmark(FaceLandmark.NOSE_BASE)?.position ?: return null
        val mouthLeft = face.getLandmark(FaceLandmark.MOUTH_LEFT)?.position ?: return null
        val mouthRight = face.getLandmark(FaceLandmark.MOUTH_RIGHT)?.position ?: return null
        val mouthBottom = face.getLandmark(FaceLandmark.MOUTH_BOTTOM)?.position ?: return null

        val eyeDist = dist(leftEye, rightEye)
        if (eyeDist < 1f) return null // Avoid division by zero or extremely tiny face bounds

        return listOf(
            dist(leftEye, noseBase) / eyeDist,
            dist(rightEye, noseBase) / eyeDist,
            dist(leftEye, mouthLeft) / eyeDist,
            dist(rightEye, mouthRight) / eyeDist,
            dist(noseBase, mouthLeft) / eyeDist,
            dist(noseBase, mouthRight) / eyeDist,
            dist(mouthLeft, mouthRight) / eyeDist,
            dist(leftEye, mouthRight) / eyeDist,
            dist(rightEye, mouthLeft) / eyeDist,
            dist(noseBase, mouthBottom) / eyeDist
        )
    }

    // Calculates difference score (squared Euclidean distance) between two signatures
    fun calculateDifference(sig1: List<Float>, sig2: List<Float>): Float {
        if (sig1.size != sig2.size) return Float.MAX_VALUE
        var sum = 0f
        for (i in sig1.indices) {
            val diff = sig1[i] - sig2[i]
            sum += diff * diff
        }
        return sum
    }
}

class FaceAnalyzer(
    private val getOtherPlayers: () -> List<PlayerState>,
    private val onTargetLocked: (isTargetInCrosshair: Boolean, targetId: String?) -> Unit
) : ImageAnalysis.Analyzer {

    private val options = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
        .build()

    private val detector = FaceDetection.getClient(options)

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        detector.process(image)
            .addOnSuccessListener { faces ->
                val rotation = imageProxy.imageInfo.rotationDegrees
                val rotatedWidth = if (rotation == 90 || rotation == 270) imageProxy.height else imageProxy.width
                val rotatedHeight = if (rotation == 90 || rotation == 270) imageProxy.width else imageProxy.height
                
                val centerX = rotatedWidth / 2
                val centerY = rotatedHeight / 2

                var targetInCrosshair = false
                var matchedTargetId: String? = null

                // Filter opponents to match against
                val opponents = getOtherPlayers().filter { it.isAlive }

                for (face in faces) {
                    val box = face.boundingBox
                    // Expand hitbox by 20% to make target locking more forgiving and smooth
                    val extraW = (box.width() * 0.2f).toInt()
                    val extraH = (box.height() * 0.2f).toInt()
                    
                    val expandedBox = android.graphics.Rect(
                        box.left - extraW,
                        box.top - extraH,
                        box.right + extraW,
                        box.bottom + extraH
                    )

                    // Check if the center of the camera frame lies inside the expanded face bounding box
                    if (expandedBox.contains(centerX, centerY)) {
                        targetInCrosshair = true
                        
                        // Extract detected signature
                        val detectedSig = FaceSignatureHelper.calculateSignature(face)
                        if (detectedSig != null && opponents.isNotEmpty()) {
                            // Find the best matching opponent signature
                            var bestMatchId: String? = null
                            var minDiff = Float.MAX_VALUE
                            
                            for (opponent in opponents) {
                                val oppSig = opponent.faceSignature ?: continue
                                val diff = FaceSignatureHelper.calculateDifference(detectedSig, oppSig)
                                if (diff < minDiff) {
                                    minDiff = diff
                                    bestMatchId = opponent.id
                                }
                            }
                            
                            // Check if the best match is within threshold
                            if (minDiff <= 0.08f) { // Threshold for matching
                                matchedTargetId = bestMatchId
                            }
                        }
                        break
                    }
                }
                
                onTargetLocked(targetInCrosshair, matchedTargetId)
                imageProxy.close()
            }
            .addOnFailureListener { e ->
                e.printStackTrace()
                imageProxy.close()
            }
    }
}

class FrontFaceAnalyzer(
    private val onFaceDetected: (Boolean) -> Unit
) : ImageAnalysis.Analyzer {

    private val options = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
        .build()

    private val detector = FaceDetection.getClient(options)

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        detector.process(image)
            .addOnSuccessListener { faces ->
                val face = faces.firstOrNull()
                if (face == null) {
                    // No face detected -> face is covered
                    onFaceDetected(true)
                } else {
                    val box = face.boundingBox
                    val frameWidth = imageProxy.width
                    val frameHeight = imageProxy.height

                    val widthRatio = box.width().toFloat() / frameWidth.toFloat()
                    val heightRatio = box.height().toFloat() / frameHeight.toFloat()
                    val areaRatio = (box.width().toFloat() * box.height().toFloat()) / (frameWidth.toFloat() * frameHeight.toFloat())

                    // If face covers 80% or more of the width, height, or total area,
                    // we treat it as too close / covered.
                    val isTooClose = widthRatio >= 0.8f || heightRatio >= 0.8f || areaRatio >= 0.8f
                    onFaceDetected(isTooClose)
                }
                imageProxy.close()
            }
            .addOnFailureListener { e ->
                e.printStackTrace()
                imageProxy.close()
            }
    }
}

class WarningFeedbackHelper(context: Context) {
    private var toneGenerator: ToneGenerator? = null
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun triggerWarning() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_CDMA_PIP, 200)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            vibrator?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    it.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    it.vibrate(300)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun startBlackoutVibration() {
        try {
            vibrator?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    it.vibrate(VibrationEffect.createOneShot(3000, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    it.vibrate(3000)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopVibration() {
        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun release() {
        try {
            stopVibration()
            toneGenerator?.release()
            toneGenerator = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
