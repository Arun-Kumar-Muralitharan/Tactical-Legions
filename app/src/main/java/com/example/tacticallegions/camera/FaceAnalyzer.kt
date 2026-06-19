package com.example.tacticallegions.camera

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
                val imgWidth = imageProxy.width
                val imgHeight = imageProxy.height
                val centerX = imgWidth / 2
                val centerY = imgHeight / 2

                var targetInCrosshair = false
                for (face in faces) {
                    // Check if the center of the camera frame lies inside the detected face bounding box
                    if (face.boundingBox.contains(centerX, centerY)) {
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
