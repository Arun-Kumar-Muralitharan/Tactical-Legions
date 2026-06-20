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
