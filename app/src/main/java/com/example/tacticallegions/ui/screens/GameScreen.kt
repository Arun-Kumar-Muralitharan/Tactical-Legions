package com.example.tacticallegions.ui.screens

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.tacticallegions.camera.FaceAnalyzer
import com.example.tacticallegions.network.PlayerState
import com.example.tacticallegions.theme.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Composable
fun GameScreen(
    players: List<PlayerState>,
    localPlayerId: String,
    matchTimeSeconds: Int?,
    countdownTime: Int?,
    isTargetInCrosshair: Boolean,
    onTargetStatusChanged: (Boolean) -> Unit,
    onShootTriggered: () -> Unit,
    onConfirmHit: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    val localPlayer = players.find { it.id == localPlayerId }
    val health = localPlayer?.health ?: 100
    val isAlive = localPlayer?.isAlive ?: true
    val score = localPlayer?.score ?: 0

    // Show selection dialog when user tapped trigger and target was locked
    var showTargetSelector by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(DeepCharcoal)) {
        if (isAlive) {
            // Live Camera Viewfinder
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
                                it.setAnalyzer(cameraExecutor, FaceAnalyzer { inCrosshair ->
                                    onTargetStatusChanged(inCrosshair)
                                })
                            }

                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
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
        } else {
            // Static dark red screen when dead
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(CyberRed.copy(alpha = 0.2f))
            )
        }

        // Center Crosshair
        if (isAlive) {
            CrosshairWidget(isTargetInCrosshair = isTargetInCrosshair)
        }

        // Match HUD (Time, Score, Health)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Time HUD
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceGray.copy(alpha = 0.7f)),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, GlassWhite)
                ) {
                    Text(
                        text = formatTime(matchTimeSeconds ?: 0),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyberBlue,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        letterSpacing = 1.sp
                    )
                }

                // Score HUD
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceGray.copy(alpha = 0.7f)),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, GlassWhite)
                ) {
                    Text(
                        text = "SCORE: $score",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyberGreen,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        letterSpacing = 1.sp
                    )
                }
            }

            // Health Status Bar
            Card(
                modifier = Modifier.fillMaxWidth(0.6f),
                colors = CardDefaults.cardColors(containerColor = SurfaceGray.copy(alpha = 0.7f)),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, GlassWhite)
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "HP ",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (health > 34) CyberGreen else CyberRed
                    )
                    
                    // Simple Segmented Health Bar
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(8.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (health >= 34) CyberGreen else Color.Gray.copy(alpha = 0.3f))
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(8.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (health >= 66) CyberGreen else Color.Gray.copy(alpha = 0.3f))
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(8.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (health >= 100) CyberGreen else Color.Gray.copy(alpha = 0.3f))
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "$health%",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        // Action Trigger Button at the Bottom
        if (isAlive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(bottom = 32.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Button(
                    onClick = {
                        onShootTriggered()
                        if (isTargetInCrosshair) {
                            showTargetSelector = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberRed),
                    shape = CircleShape,
                    modifier = Modifier
                        .size(80.dp)
                        .border(BorderStroke(2.dp, Color.White), CircleShape),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = "FIRE",
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        color = Color.White,
                        letterSpacing = 1.sp
                    )
                }
            }
        }

        // Custom Quick Target Confirmation Sheet (when hitting a target)
        AnimatedVisibility(
            visible = showTargetSelector && isTargetInCrosshair && isAlive,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .border(BorderStroke(2.dp, CyberRed), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
                colors = CardDefaults.cardColors(containerColor = SurfaceGray),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "CONFIRM TARGET ELIMINATED",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyberRed,
                        letterSpacing = 2.sp
                    )

                    val otherPlayers = players.filter { it.id != localPlayerId && it.isAlive }

                    if (otherPlayers.isEmpty()) {
                        Text(
                            text = "No other active targets alive.",
                            color = LightOnBackground.copy(alpha = 0.6f),
                            fontSize = 14.sp
                        )
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            otherPlayers.forEach { target ->
                                Button(
                                    onClick = {
                                        onConfirmHit(target.id)
                                        showTargetSelector = false
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = GlassWhite),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, CyberBlue),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = target.name.uppercase(),
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }

                    TextButton(
                        onClick = { showTargetSelector = false },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                    ) {
                        Text("CANCEL", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Full Screen Respawn / Elimination Overlay
        AnimatedVisibility(
            visible = !isAlive,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DarkOverlay),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "YOU WERE ELIMINATED",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = CyberRed,
                        letterSpacing = 4.sp
                    )

                    Text(
                        text = "RESPAWNING IN COOLDOWN",
                        fontSize = 12.sp,
                        color = LightOnBackground.copy(alpha = 0.5f),
                        letterSpacing = 2.sp
                    )

                    // Displays simple progress circle for wait
                    CircularProgressIndicator(
                        color = CyberRed,
                        modifier = Modifier.size(64.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CrosshairWidget(isTargetInCrosshair: Boolean) {
    val crosshairColor = if (isTargetInCrosshair) CyberRed else CyberGreen

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(100.dp)) {
            val center = Offset(size.width / 2, size.height / 2)
            
            // Center Reticle Circle
            drawCircle(
                color = crosshairColor,
                radius = 18.dp.toPx(),
                style = Stroke(width = 2.dp.toPx())
            )

            // Center Pinpoint Dot
            drawCircle(
                color = crosshairColor,
                radius = 2.5.dp.toPx()
            )

            // Outer Reticle Ticks (North, South, East, West)
            val tickLength = 12.dp.toPx()
            val offsetStart = 26.dp.toPx()
            val offsetEnd = offsetStart + tickLength

            // North
            drawLine(
                color = crosshairColor,
                start = Offset(center.x, center.y - offsetStart),
                end = Offset(center.x, center.y - offsetEnd),
                strokeWidth = 2.5.dp.toPx()
            )
            // South
            drawLine(
                color = crosshairColor,
                start = Offset(center.x, center.y + offsetStart),
                end = Offset(center.x, center.y + offsetEnd),
                strokeWidth = 2.5.dp.toPx()
            )
            // West
            drawLine(
                color = crosshairColor,
                start = Offset(center.x - offsetStart, center.y),
                end = Offset(center.x - offsetEnd, center.y),
                strokeWidth = 2.5.dp.toPx()
            )
            // East
            drawLine(
                color = crosshairColor,
                start = Offset(center.x + offsetStart, center.y),
                end = Offset(center.x + offsetEnd, center.y),
                strokeWidth = 2.5.dp.toPx()
            )
        }
    }
}

private fun formatTime(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format("%02d:%02d", mins, secs)
}
