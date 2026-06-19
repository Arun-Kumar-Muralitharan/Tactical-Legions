package com.activegames.tacticallegions.camera

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions

class FaceAnalyzer(
    private val onTargetStatusChanged: (isTargetInCrosshair: Boolean) -> Unit
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

        // Create InputImage from mediaImage and rotation
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        detector.process(image)
            .addOnSuccessListener { faces ->
                val rotation = imageProxy.imageInfo.rotationDegrees
                val rotatedWidth = if (rotation == 90 || rotation == 270) imageProxy.height else imageProxy.width
                val rotatedHeight = if (rotation == 90 || rotation == 270) imageProxy.width else imageProxy.height
                
                val centerX = rotatedWidth / 2
                val centerY = rotatedHeight / 2

                var targetInCrosshair = false
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
                        break
                    }
                }
                onTargetStatusChanged(targetInCrosshair)
                imageProxy.close()
            }
            .addOnFailureListener { e ->
                e.printStackTrace()
                imageProxy.close()
            }
    }
}
