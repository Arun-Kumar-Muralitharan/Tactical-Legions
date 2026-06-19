package com.activegames.tacticallegions.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.activegames.tacticallegions.camera.FaceAnalyzer
import com.activegames.tacticallegions.network.PlayerState
import com.activegames.tacticallegions.theme.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Composable
fun GameScreen(
    players: List<PlayerState>,
    localPlayerId: String,
    matchTimeSeconds: Int?,
    countdownTime: Int?,
    isTargetInCrosshair: Boolean,
    successfulHitCount: Int,
    onTargetStatusChanged: (Boolean) -> Unit,
    onShootTriggered: () -> Unit,
    onConfirmHit: (String) -> Unit,
    onExitClicked: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    val localPlayer = players.find { it.id == localPlayerId }
    val health = localPlayer?.health ?: 100
    val isAlive = localPlayer?.isAlive ?: true
    val score = localPlayer?.score ?: 0

    // Show selection dialog when user tapped trigger and target was locked
    var showExitConfirmation by remember { mutableStateOf(false) }

    // Intercept back button during game and show exit confirmation modal
    BackHandler(enabled = true) {
        if (!showExitConfirmation) {
            showExitConfirmation = true
        } else {
            showExitConfirmation = false
        }
    }

    // Lock-on targeting state
    val otherPlayers = remember(players) { players.filter { it.id != localPlayerId && it.isAlive } }
    var activeTargetId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(otherPlayers) {
        if (otherPlayers.isNotEmpty()) {
            if (activeTargetId == null || otherPlayers.none { it.id == activeTargetId }) {
                activeTargetId = otherPlayers.first().id
            }
        } else {
            activeTargetId = null
        }
    }

    // Hit flashing effect state
    var lastHealth by remember { mutableStateOf(health) }
    val hitFlashAlpha = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(health) {
        if (health < lastHealth && health > 0) {
            hitFlashAlpha.snapTo(0.6f)
            hitFlashAlpha.animateTo(
                targetValue = 0f,
                animationSpec = androidx.compose.animation.core.tween(durationMillis = 500)
            )
        }
        lastHealth = health
    }

    // Blood splash effect state
    var lastSuccessfulHitCount by remember { mutableStateOf(successfulHitCount) }
    var triggerBloodSplash by remember { mutableStateOf(0) }
    LaunchedEffect(successfulHitCount) {
        if (successfulHitCount > lastSuccessfulHitCount) {
            triggerBloodSplash += 1
        }
        lastSuccessfulHitCount = successfulHitCount
    }

    DisposableEffect(context) {
        val activity = context.findActivity()
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
            // Static red screen when dead with "Wait until Respawn" text
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF8B0000)), // Dark Red
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Wait until Respawn",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        letterSpacing = 2.sp,
                        textAlign = TextAlign.Center
                    )
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        }

        // Render blood splash effect (for hitting opponent)
        BloodSplashEffect(
            triggerCount = triggerBloodSplash,
            onAnimationFinished = {}
        )

        // Render hit flash (for taking damage)
        if (hitFlashAlpha.value > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Red.copy(alpha = hitFlashAlpha.value))
            )
        }

        // Center Crosshair
        if (isAlive && countdownTime == null) {
            CrosshairWidget(isTargetInCrosshair = isTargetInCrosshair)
        }

        // --- Countdown Timer Overlay ---
        AnimatedVisibility(
            visible = countdownTime != null && countdownTime >= 0,
            enter = fadeIn() + scaleIn(initialScale = 0.8f),
            exit = fadeOut() + scaleOut(targetScale = 1.5f),
            modifier = Modifier.align(Alignment.Center)
        ) {
            if (countdownTime != null) {
                val displayText = if (countdownTime == 0) "GO!" else "$countdownTime"
                val displayColor = if (countdownTime == 0) CyberGreen else CyberRed
                
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .background(SurfaceGray.copy(alpha = 0.8f), CircleShape)
                        .border(BorderStroke(3.dp, displayColor), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = displayText,
                        fontSize = 64.sp,
                        fontWeight = FontWeight.Black,
                        color = displayColor,
                        letterSpacing = 1.sp
                    )
                }
            }
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

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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

                    // Exit Match Button
                    IconButton(
                        onClick = { showExitConfirmation = true },
                        colors = IconButtonDefaults.iconButtonColors(containerColor = CyberRed.copy(alpha = 0.85f)),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Text(
                            text = "X",
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            if (showExitConfirmation) {
                AlertDialog(
                    onDismissRequest = { showExitConfirmation = false },
                    title = {
                        Text(
                            text = "ABORT MISSION?",
                            color = CyberRed,
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            letterSpacing = 2.sp
                        )
                    },
                    text = {
                        Text(
                            text = "Are you sure you want to disconnect and abandon the active match?",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showExitConfirmation = false
                                onExitClicked()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberRed)
                        ) {
                            Text("ABORT", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showExitConfirmation = false },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                        ) {
                            Text("CANCEL", fontWeight = FontWeight.Bold)
                        }
                    },
                    containerColor = SurfaceGray,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.border(BorderStroke(1.dp, CyberRed), RoundedCornerShape(16.dp))
                )
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
        // Action Trigger Button and Lock HUD at the Bottom
        if (isAlive && countdownTime == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(bottom = 32.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Pre-selection Lock-on Target HUD
                    if (otherPlayers.size > 1) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(SurfaceGray.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
                                .border(BorderStroke(1.dp, GlassWhite), RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "LOCK TARGET:",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = LightOnBackground.copy(alpha = 0.6f),
                                letterSpacing = 1.5.sp
                            )
                            otherPlayers.forEach { opponent ->
                                val isSelected = opponent.id == activeTargetId
                                val badgeColor = if (isSelected) CyberRed else Color.Transparent
                                val textColor = if (isSelected) Color.White else LightOnBackground.copy(alpha = 0.8f)
                                val borderStroke = if (isSelected) BorderStroke(1.dp, CyberRed) else BorderStroke(1.dp, GlassWhite)

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(badgeColor)
                                        .border(borderStroke, RoundedCornerShape(6.dp))
                                        .clickable { activeTargetId = opponent.id }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = opponent.name.uppercase(),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = textColor
                                    )
                                }
                            }
                        }
                    } else if (otherPlayers.size == 1) {
                        // Single opponent locked automatically
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(SurfaceGray.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
                                .border(BorderStroke(1.dp, GlassWhite), RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "LOCKED TARGET: ",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = LightOnBackground.copy(alpha = 0.6f),
                                letterSpacing = 1.5.sp
                            )
                            Text(
                                text = otherPlayers.first().name.uppercase(),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = CyberRed,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }

                    // FIRE trigger
                    Button(
                        onClick = {
                            onShootTriggered()
                            if (isTargetInCrosshair) {
                                activeTargetId?.let { targetId ->
                                    onConfirmHit(targetId)
                                }
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
        }

        // The old full screen overlay has been removed to display "Wait until Respawn" directly over the view bounds.
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

data class BloodSplatter(
    val id: Int,
    val xPercent: Float,
    val yPercent: Float,
    val maxRadius: Float,
    val rotation: Float
)

@Composable
fun BloodSplashEffect(
    triggerCount: Int,
    onAnimationFinished: () -> Unit
) {
    var splatters by remember { mutableStateOf<List<BloodSplatter>>(emptyList()) }
    val animProgress = remember { androidx.compose.animation.core.Animatable(0f) }

    LaunchedEffect(triggerCount) {
        if (triggerCount > 0) {
            val random = java.util.Random()
            splatters = List(6) { id ->
                BloodSplatter(
                    id = id,
                    xPercent = 0.2f + random.nextFloat() * 0.6f,
                    yPercent = 0.2f + random.nextFloat() * 0.6f,
                    maxRadius = 40f + random.nextFloat() * 80f,
                    rotation = random.nextFloat() * 360f
                )
            }
            animProgress.snapTo(0f)
            animProgress.animateTo(
                targetValue = 1f,
                animationSpec = androidx.compose.animation.core.tween(
                    durationMillis = 600,
                    easing = androidx.compose.animation.core.FastOutSlowInEasing
                )
            )
            onAnimationFinished()
        }
    }

    if (animProgress.value > 0f && animProgress.value < 1f) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val width = constraints.maxWidth.toFloat()
            val height = constraints.maxHeight.toFloat()

            Canvas(modifier = Modifier.fillMaxSize()) {
                splatters.forEach { splatter ->
                    val x = splatter.xPercent * width
                    val y = splatter.yPercent * height
                    
                    val scale = if (animProgress.value < 0.15f) {
                        animProgress.value / 0.15f
                    } else {
                        1f
                    }
                    val alpha = 1f - animProgress.value
                    val currentRadius = splatter.maxRadius * scale
                    val dripY = y + (animProgress.value * 25.dp.toPx())

                    // Draw main splatter center
                    drawCircle(
                        color = Color(0xFFB30000).copy(alpha = alpha),
                        radius = currentRadius,
                        center = Offset(x, dripY)
                    )

                    // Draw satellite droplets
                    val satelliteCount = 5
                    for (i in 0 until satelliteCount) {
                        val angle = (splatter.rotation + i * (360 / satelliteCount)) * Math.PI / 180
                        val distance = currentRadius * 1.4f * animProgress.value
                        val sx = x + (Math.cos(angle) * distance).toFloat()
                        val sy = dripY + (Math.sin(angle) * distance).toFloat()
                        drawCircle(
                            color = Color(0xFF8B0000).copy(alpha = alpha),
                            radius = currentRadius * 0.25f,
                            center = Offset(sx, sy)
                        )
                    }
                }
            }

            val textAlpha = 1f - animProgress.value
            val textScale = 0.8f + animProgress.value * 0.5f
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "HIT!",
                    color = CyberGreen,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 4.sp,
                    modifier = Modifier
                        .graphicsLayer(
                            scaleX = textScale,
                            scaleY = textScale,
                            alpha = textAlpha
                        )
                )
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
