package com.activegames.tacticallegions.ui.screens

import android.content.Context
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.activegames.tacticallegions.camera.FaceSignatureHelper
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import java.util.concurrent.Executors
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.geometry.Offset
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.activegames.tacticallegions.theme.*

@Composable
fun SetupScreen(
    onHostClicked: (nickname: String, faceSignature: List<Float>) -> Unit,
    onJoinClicked: (ip: String, nickname: String, faceSignature: List<Float>) -> Unit,
    errorMessage: String? = null
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val sharedPreferences = remember { context.getSharedPreferences("tactical_legions_prefs", Context.MODE_PRIVATE) }
    var nickname by remember { mutableStateOf(sharedPreferences.getString("nickname", "") ?: "") }
    var hostIp by remember { mutableStateOf(sharedPreferences.getString("host_ip", "") ?: "") }
    var isJoiningMode by remember { mutableStateOf(false) }
    
    // Face ID Registration state
    val savedFaceSignatureStr = remember { sharedPreferences.getString("face_signature", null) }
    val initialFaceSignature = remember(savedFaceSignatureStr) {
        savedFaceSignatureStr?.split(",")?.mapNotNull { it.toFloatOrNull() }?.takeIf { it.size == 10 }
    }
    var faceSignature by remember { mutableStateOf<List<Float>?>(initialFaceSignature) }
    var showCameraRegistration by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepCharcoal),
        contentAlignment = Alignment.Center
    ) {
        // Decorative glowing background gradients
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(CyberBlue.copy(alpha = 0.15f), Color.Transparent),
                        radius = 1200f
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Futuristic Game Title
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "TACTICAL",
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 6.sp,
                    color = CyberGreen,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "L E G I O N S",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 12.sp,
                    color = CyberBlue,
                    textAlign = TextAlign.Center
                )
            }

            // Glassmorphism login card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(1.dp, GlassWhite), RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = SurfaceGray.copy(alpha = 0.85f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "PILOT REGISTRATION",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyberBlue,
                        letterSpacing = 2.sp
                    )

                    OutlinedTextField(
                        value = nickname,
                        onValueChange = { nickname = it.take(12) },
                        label = { Text("Nickname", color = LightOnBackground.copy(alpha = 0.6f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberGreen,
                            unfocusedBorderColor = GlassWhite,
                            focusedLabelColor = CyberGreen,
                            unfocusedLabelColor = LightOnBackground.copy(alpha = 0.6f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Face ID Enrollment Status & Action
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                BorderStroke(1.dp, if (faceSignature != null) CyberGreen.copy(alpha = 0.4f) else CyberRed.copy(alpha = 0.4f)),
                                RoundedCornerShape(8.dp)
                            ),
                        colors = CardDefaults.cardColors(
                            containerColor = if (faceSignature != null) CyberGreen.copy(alpha = 0.05f) else CyberRed.copy(alpha = 0.05f)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(12.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (faceSignature != null) "FACE ID: ACTIVE" else "FACE ID: REQUIRED",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (faceSignature != null) CyberGreen else CyberRed,
                                    letterSpacing = 1.5.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (faceSignature != null) "Geometry signature enrolled." else "Register face to deploy to lobby.",
                                    fontSize = 9.sp,
                                    color = LightOnBackground.copy(alpha = 0.6f)
                                )
                            }
                            
                            Button(
                                onClick = {
                                     focusManager.clearFocus()
                                     showCameraRegistration = true
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (faceSignature != null) SurfaceGray else CyberRed,
                                    contentColor = if (faceSignature != null) CyberGreen else Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                border = if (faceSignature != null) BorderStroke(1.dp, CyberGreen) else null,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text(
                                    text = if (faceSignature != null) "RE-SCAN" else "SCAN",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    errorMessage?.let {
                        Text(
                            text = it,
                            color = CyberRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Host Button
                        Button(
                            onClick = {
                                if (nickname.isNotBlank() && faceSignature != null) {
                                    val trimmed = nickname.trim()
                                    sharedPreferences.edit().putString("nickname", trimmed).apply()
                                    onHostClicked(trimmed, faceSignature!!)
                                }
                            },
                            enabled = nickname.isNotBlank() && faceSignature != null && !isJoiningMode,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CyberGreen,
                                contentColor = Color.Black,
                                disabledContainerColor = CyberGreen.copy(alpha = 0.3f),
                                disabledContentColor = Color.Black.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("HOST", fontWeight = FontWeight.Bold)
                        }

                        // Trigger Join UI Button
                        Button(
                            onClick = { isJoiningMode = true },
                            enabled = nickname.isNotBlank() && faceSignature != null && !isJoiningMode,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SurfaceGray,
                                contentColor = CyberBlue,
                                disabledContainerColor = SurfaceGray.copy(alpha = 0.3f),
                                disabledContentColor = CyberBlue.copy(alpha = 0.3f)
                            ),
                            border = BorderStroke(1.dp, CyberBlue),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("JOIN", fontWeight = FontWeight.Bold)
                        }
                    }

                    // Hidden Join Input fields
                    AnimatedVisibility(
                        visible = isJoiningMode,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            OutlinedTextField(
                                value = hostIp,
                                onValueChange = { hostIp = it },
                                label = { Text("Host IP Address", color = LightOnBackground.copy(alpha = 0.6f)) },
                                placeholder = { Text("e.g. 192.168.1.100") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CyberBlue,
                                    unfocusedBorderColor = GlassWhite,
                                    focusedLabelColor = CyberBlue,
                                    unfocusedLabelColor = LightOnBackground.copy(alpha = 0.6f),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Connect Button
                                Button(
                                    onClick = {
                                        if (nickname.isNotBlank() && hostIp.isNotBlank() && faceSignature != null) {
                                            val trimmedNick = nickname.trim()
                                            val trimmedIp = hostIp.trim()
                                            sharedPreferences.edit()
                                                .putString("nickname", trimmedNick)
                                                .putString("host_ip", trimmedIp)
                                                .apply()
                                            onJoinClicked(trimmedIp, trimmedNick, faceSignature!!)
                                        }
                                    },
                                    enabled = nickname.isNotBlank() && hostIp.isNotBlank() && faceSignature != null,
                                    colors = ButtonDefaults.buttonColors(containerColor = CyberBlue, contentColor = Color.Black),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("CONNECT", fontWeight = FontWeight.Bold)
                                }

                                // Cancel button
                                TextButton(
                                    onClick = { isJoiningMode = false },
                                    colors = ButtonDefaults.textButtonColors(contentColor = CyberRed),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("CANCEL", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Full Screen Face Scanner Overlay
        AnimatedVisibility(
            visible = showCameraRegistration,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            FaceScannerOverlay(
                onCaptureCompleted = { sig ->
                    faceSignature = sig
                    showCameraRegistration = false
                    sharedPreferences.edit()
                        .putString("face_signature", sig.joinToString(","))
                        .apply()
                },
                onCancelClicked = {
                    showCameraRegistration = false
                }
            )
        }
    }
}

@Composable
fun FaceScannerOverlay(
    onCaptureCompleted: (List<Float>) -> Unit,
    onCancelClicked: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    
    var detectedSignature by remember { mutableStateOf<List<Float>?>(null) }
    var scanMessage by remember { mutableStateOf("POSITION FACE IN PREVIEW") }

    DisposableEffect(context) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepCharcoal)
    ) {
        // Camera Viewfinder
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = androidx.camera.core.Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val imageAnalyzer = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also {
                            it.setAnalyzer(cameraExecutor, RegistrationFaceAnalyzer { sig ->
                                detectedSignature = sig
                                scanMessage = if (sig != null) {
                                    "LINK READY - FACE GEOMETRY SECURED"
                                } else {
                                    "ALIGNING LANDMARKS..."
                                }
                            })
                        }

                    val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageAnalyzer
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Futuristic Sci-Fi overlay Hud
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val ringRadius = size.width * 0.32f
            
            // Outer Scanning Ring
            drawCircle(
                color = if (detectedSignature != null) CyberGreen else CyberBlue,
                radius = ringRadius,
                style = Stroke(width = 3.dp.toPx())
            )

            // Inner Bracket Ticks
            val bracketLength = 20.dp.toPx()
            val bracketDist = ringRadius + 12.dp.toPx()
            
            // Draw four brackets
            // Top-Left
            drawLine(
                color = if (detectedSignature != null) CyberGreen else CyberBlue,
                start = Offset(center.x - bracketDist, center.y - bracketDist),
                end = Offset(center.x - bracketDist + bracketLength, center.y - bracketDist),
                strokeWidth = 2.dp.toPx()
            )
            drawLine(
                color = if (detectedSignature != null) CyberGreen else CyberBlue,
                start = Offset(center.x - bracketDist, center.y - bracketDist),
                end = Offset(center.x - bracketDist, center.y - bracketDist + bracketLength),
                strokeWidth = 2.dp.toPx()
            )
            // Top-Right
            drawLine(
                color = if (detectedSignature != null) CyberGreen else CyberBlue,
                start = Offset(center.x + bracketDist, center.y - bracketDist),
                end = Offset(center.x + bracketDist - bracketLength, center.y - bracketDist),
                strokeWidth = 2.dp.toPx()
            )
            drawLine(
                color = if (detectedSignature != null) CyberGreen else CyberBlue,
                start = Offset(center.x + bracketDist, center.y - bracketDist),
                end = Offset(center.x + bracketDist, center.y - bracketDist + bracketLength),
                strokeWidth = 2.dp.toPx()
            )
            // Bottom-Left
            drawLine(
                color = if (detectedSignature != null) CyberGreen else CyberBlue,
                start = Offset(center.x - bracketDist, center.y + bracketDist),
                end = Offset(center.x - bracketDist + bracketLength, center.y + bracketDist),
                strokeWidth = 2.dp.toPx()
            )
            drawLine(
                color = if (detectedSignature != null) CyberGreen else CyberBlue,
                start = Offset(center.x - bracketDist, center.y + bracketDist),
                end = Offset(center.x - bracketDist, center.y + bracketDist - bracketLength),
                strokeWidth = 2.dp.toPx()
            )
            // Bottom-Right
            drawLine(
                color = if (detectedSignature != null) CyberGreen else CyberBlue,
                start = Offset(center.x + bracketDist, center.y + bracketDist),
                end = Offset(center.x + bracketDist - bracketLength, center.y + bracketDist),
                strokeWidth = 2.dp.toPx()
            )
            drawLine(
                color = if (detectedSignature != null) CyberGreen else CyberBlue,
                start = Offset(center.x + bracketDist, center.y + bracketDist),
                end = Offset(center.x + bracketDist, center.y + bracketDist - bracketLength),
                strokeWidth = 2.dp.toPx()
            )
        }

        // Top Status HUD
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(24.dp)
                .background(SurfaceGray.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                .border(BorderStroke(1.dp, GlassWhite), RoundedCornerShape(12.dp))
                .padding(16.dp)
                .align(Alignment.TopCenter),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "PILOT BIOMETRIC SIGNATURE SCAN",
                color = CyberBlue,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = scanMessage.uppercase(),
                color = if (detectedSignature != null) CyberGreen else CyberRed,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )
        }

        // Bottom Controls
        Row(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(bottom = 40.dp)
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Cancel button
            Button(
                onClick = onCancelClicked,
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceGray),
                border = BorderStroke(1.dp, CyberRed.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
            ) {
                Text("CANCEL", color = CyberRed, fontWeight = FontWeight.Bold)
            }

            // Capture button
            Button(
                onClick = {
                    detectedSignature?.let { sig ->
                        onCaptureCompleted(sig)
                    }
                },
                enabled = detectedSignature != null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyberGreen,
                    contentColor = Color.Black,
                    disabledContainerColor = CyberGreen.copy(alpha = 0.3f),
                    disabledContentColor = Color.Black.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
            ) {
                Text("REGISTER", fontWeight = FontWeight.Black)
            }
        }
    }
}

class RegistrationFaceAnalyzer(
    private val onFaceSignatureDetected: (List<Float>?) -> Unit
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
                val face = faces.firstOrNull()
                if (face != null) {
                    val signature = FaceSignatureHelper.calculateSignature(face)
                    onFaceSignatureDetected(signature)
                } else {
                    onFaceSignatureDetected(null)
                }
                imageProxy.close()
            }
            .addOnFailureListener {
                onFaceSignatureDetected(null)
                imageProxy.close()
            }
    }
}
