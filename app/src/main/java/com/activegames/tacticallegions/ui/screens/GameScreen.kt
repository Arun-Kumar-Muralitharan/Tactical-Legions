package com.activegames.tacticallegions.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.activity.compose.BackHandler
import com.activegames.tacticallegions.network.GameMode
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
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.painter.ColorPainter
import android.graphics.BitmapFactory
import androidx.compose.animation.core.*
import com.activegames.tacticallegions.network.PowerUpType
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
import com.activegames.tacticallegions.camera.FrontFaceAnalyzer
import com.activegames.tacticallegions.camera.WarningFeedbackHelper
import androidx.camera.core.ConcurrentCamera
import androidx.camera.core.UseCaseGroup
import com.activegames.tacticallegions.network.PlayerState
import com.activegames.tacticallegions.theme.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Composable
fun rememberAssetPainter(assetName: String): Painter {
    val context = LocalContext.current
    return remember(assetName) {
        try {
            context.assets.open(assetName).use { stream ->
                val bitmap = BitmapFactory.decodeStream(stream)
                BitmapPainter(bitmap.asImageBitmap())
            }
        } catch (e: Exception) {
            ColorPainter(Color.Transparent)
        }
    }
}

@Composable
fun GameScreen(
    players: List<PlayerState>,
    localPlayerId: String,
    matchTimeSeconds: Int?,
    matchDurationSeconds: Int,
    countdownTime: Int?,
    isTargetInCrosshair: Boolean,
    successfulHitCount: Int,
    gameMode: GameMode,
    scoreLimit: Int,
    isFaceCoveredDev: Boolean,
    onTargetStatusChanged: (Boolean) -> Unit,
    onShootTriggered: () -> Unit,
    onConfirmHit: (String) -> Unit,
    onExitClicked: () -> Unit,
    onPowerUpActivated: (PowerUpType) -> Unit = {}
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    var isFaceCoveredFromCamera by remember { mutableStateOf(false) }
    val isFaceCovered = isFaceCoveredFromCamera || isFaceCoveredDev
    var isScreenBlackout by remember { mutableStateOf(false) }

    val warningFeedbackHelper = remember(context) { WarningFeedbackHelper(context) }
    DisposableEffect(warningFeedbackHelper) {
        onDispose {
            warningFeedbackHelper.release()
        }
    }

    var coveringStartTimestamp by remember { mutableStateOf<Long?>(null) }
    var lastWarningTimestamp by remember { mutableStateOf(0L) }

    LaunchedEffect(isFaceCovered, isScreenBlackout) {
        if (isFaceCovered && !isScreenBlackout) {
            coveringStartTimestamp = System.currentTimeMillis()
            warningFeedbackHelper.triggerWarning()
            lastWarningTimestamp = System.currentTimeMillis()
        } else {
            coveringStartTimestamp = null
        }
    }

    LaunchedEffect(isFaceCovered, isScreenBlackout) {
        if (isFaceCovered && !isScreenBlackout) {
            while (true) {
                val now = System.currentTimeMillis()
                val start = coveringStartTimestamp ?: now
                val durationCovered = now - start

                if (durationCovered >= 5000L) {
                    isScreenBlackout = true
                    coveringStartTimestamp = null
                    break
                }

                if (now - lastWarningTimestamp >= 1500L) {
                    warningFeedbackHelper.triggerWarning()
                    lastWarningTimestamp = now
                }

                kotlinx.coroutines.delay(100)
            }
        }
    }

    LaunchedEffect(isScreenBlackout) {
        if (isScreenBlackout) {
            warningFeedbackHelper.startBlackoutVibration()
            kotlinx.coroutines.delay(3000L)
            warningFeedbackHelper.stopVibration()
            isScreenBlackout = false
            if (isFaceCovered) {
                coveringStartTimestamp = System.currentTimeMillis()
                warningFeedbackHelper.triggerWarning()
                lastWarningTimestamp = System.currentTimeMillis()
            }
        }
    }

    val localPlayer = players.find { it.id == localPlayerId }
    val health = localPlayer?.health ?: 100
    val isAlive = localPlayer?.isAlive ?: true
    val score = localPlayer?.score ?: 0

    var floatingPowerUp by remember { mutableStateOf<PowerUpType?>(null) }
    var powerUpPosition by remember { mutableStateOf(Offset.Zero) }
    var powerUpSpawnTime by remember { mutableStateOf(0L) }

    var activePowerUpTimeLeft by remember { mutableStateOf(0) }
    var currentActivePowerUp by remember { mutableStateOf<PowerUpType?>(null) }

    var showFlashingHeart by remember { mutableStateOf(false) }
    var previousHealth by remember { mutableStateOf<Int?>(null) }

    val heartTransition = rememberInfiniteTransition(label = "heart_transition")
    val heartAlpha by heartTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(250, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heart_alpha"
    )

    LaunchedEffect(localPlayer?.health) {
        val currentHealth = localPlayer?.health
        if (currentHealth != null) {
            if (previousHealth != null && previousHealth != currentHealth) {
                showFlashingHeart = true
                kotlinx.coroutines.delay(1500)
                showFlashingHeart = false
            }
            previousHealth = currentHealth
        }
    }

    LaunchedEffect(localPlayer?.activePowerUp) {
        val newPowerUp = localPlayer?.activePowerUp
        if (newPowerUp != currentActivePowerUp) {
            currentActivePowerUp = newPowerUp
            if (newPowerUp != null) {
                activePowerUpTimeLeft = 30
            } else {
                activePowerUpTimeLeft = 0
            }
        }
    }

    LaunchedEffect(activePowerUpTimeLeft) {
        if (activePowerUpTimeLeft > 0) {
            kotlinx.coroutines.delay(1000)
            activePowerUpTimeLeft--
        }
    }

    var hasSpawnedOnce by remember { mutableStateOf(false) }
    var spawnedCount by remember { mutableStateOf(0) }
    var collectedCount by remember { mutableStateOf(0) }
    val isPowerUpFeatureEnabled = matchDurationSeconds >= 180
    val initialDelayMs = remember(matchDurationSeconds) {
        kotlin.random.Random.nextLong(30000, 40000)
    }

    LaunchedEffect(isAlive, spawnedCount) {
        if (isPowerUpFeatureEnabled && isAlive && spawnedCount < 2) {
            // 1. First guaranteed spawn (strictly between 30 and 40 seconds)
            kotlinx.coroutines.delay(initialDelayMs)
            if (floatingPowerUp == null && !hasSpawnedOnce && spawnedCount < 2) {
                floatingPowerUp = PowerUpType.values().random()
                val randomX = kotlin.random.Random.nextFloat() * 0.6f + 0.2f
                val randomY = kotlin.random.Random.nextFloat() * 0.5f + 0.25f
                powerUpPosition = Offset(randomX, randomY)
                powerUpSpawnTime = System.currentTimeMillis()
                hasSpawnedOnce = true
                spawnedCount++
            }

            // 2. Subsequent random spawns
            while (spawnedCount < 2) {
                val delayMs = kotlin.random.Random.nextLong(20000, 40000)
                kotlinx.coroutines.delay(delayMs)
                if (floatingPowerUp == null && spawnedCount < 2) {
                    floatingPowerUp = PowerUpType.values().random()
                    val randomX = kotlin.random.Random.nextFloat() * 0.6f + 0.2f
                    val randomY = kotlin.random.Random.nextFloat() * 0.5f + 0.25f
                    powerUpPosition = Offset(randomX, randomY)
                    powerUpSpawnTime = System.currentTimeMillis()
                    spawnedCount++
                }
            }
        }
    }

    LaunchedEffect(floatingPowerUp, powerUpSpawnTime) {
        if (floatingPowerUp != null) {
            kotlinx.coroutines.delay(3000L)
            floatingPowerUp = null
        }
    }

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

    val localTeam = localPlayer?.team ?: ""
    val isTeamPlayActive = players.size > 4 && localTeam.isNotBlank()

    // Lock-on targeting state
    val otherPlayers = remember(players, localTeam) {
        players.filter { opponent ->
            opponent.id != localPlayerId && (!isTeamPlayActive || opponent.team != localTeam)
        }
    }
    var activeTargetId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(otherPlayers) {
        if (activeTargetId != null && otherPlayers.none { it.id == activeTargetId }) {
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
                                it.setAnalyzer(cameraExecutor, FaceAnalyzer(
                                    getOtherPlayers = { otherPlayers },
                                    onTargetLocked = { inCrosshair, targetId ->
                                        activity?.runOnUiThread {
                                            onTargetStatusChanged(inCrosshair)
                                            activeTargetId = targetId
                                        }
                                    }
                                ))
                            }

                        val frontAnalyzer = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also {
                                it.setAnalyzer(cameraExecutor, FrontFaceAnalyzer { isCovering ->
                                    activity?.runOnUiThread {
                                        isFaceCoveredFromCamera = isCovering
                                    }
                                })
                            }

                        val concurrentCameraInfos = cameraProvider.availableConcurrentCameraInfos
                        android.util.Log.d("TacticalLegionsCamera", "availableConcurrentCameraInfos size: ${concurrentCameraInfos.size}")
                        
                        var backSelector: CameraSelector? = null
                        var frontSelector: CameraSelector? = null

                        for (cameraInfos in concurrentCameraInfos) {
                            val fSel = cameraInfos.firstOrNull { it.lensFacing == CameraSelector.LENS_FACING_FRONT }?.cameraSelector
                            val bSel = cameraInfos.firstOrNull { it.lensFacing == CameraSelector.LENS_FACING_BACK }?.cameraSelector
                            if (fSel != null && bSel != null) {
                                frontSelector = fSel
                                backSelector = bSel
                                android.util.Log.d("TacticalLegionsCamera", "Found concurrent cameras: front = $fSel, back = $bSel")
                                break
                            }
                        }

                        val isConcurrentSupported = frontSelector != null && backSelector != null
                        android.util.Log.d("TacticalLegionsCamera", "isConcurrentSupported: $isConcurrentSupported")

                        val finalBackSelector = backSelector ?: CameraSelector.DEFAULT_BACK_CAMERA
                        val finalFrontSelector = frontSelector ?: CameraSelector.DEFAULT_FRONT_CAMERA

                        try {
                            cameraProvider.unbindAll()
                            android.util.Log.d("TacticalLegionsCamera", "Attempting concurrent camera binding...")
                            val backUseCaseGroup = UseCaseGroup.Builder()
                                .addUseCase(preview)
                                .addUseCase(imageAnalyzer)
                                .build()

                            val frontUseCaseGroup = UseCaseGroup.Builder()
                                .addUseCase(frontAnalyzer)
                                .build()

                            val backConfig = ConcurrentCamera.SingleCameraConfig(
                                finalBackSelector,
                                backUseCaseGroup,
                                lifecycleOwner
                            )

                            val frontConfig = ConcurrentCamera.SingleCameraConfig(
                                finalFrontSelector,
                                frontUseCaseGroup,
                                lifecycleOwner
                            )

                            cameraProvider.bindToLifecycle(listOf(backConfig, frontConfig))
                            android.util.Log.d("TacticalLegionsCamera", "Successfully bound concurrent cameras (forced)")
                        } catch (e: Exception) {
                            android.util.Log.e("TacticalLegionsCamera", "Failed concurrent binding, falling back to back camera only", e)
                            try {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    imageAnalyzer
                                )
                                android.util.Log.d("TacticalLegionsCamera", "Successfully fell back to back camera only")
                            } catch (fallbackEx: Exception) {
                                android.util.Log.e("TacticalLegionsCamera", "Error during fallback camera binding", fallbackEx)
                            }
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
                    if (isTeamPlayActive) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SurfaceGray.copy(alpha = 0.7f)),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (localTeam == "RED") CyberRed else CyberBlue)
                        ) {
                            Text(
                                text = localTeam,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (localTeam == "RED") CyberRed else CyberBlue,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                letterSpacing = 1.sp
                            )
                        }
                    }

                    // Score HUD
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceGray.copy(alpha = 0.7f)),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, GlassWhite)
                    ) {
                        val scoreText = if (gameMode == GameMode.RACE) "SCORE: $score/$scoreLimit" else "SCORE: $score"
                        Text(
                            text = scoreText,
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

            // Health Status Bar and Active Power-up Badge
            val isHealthBoosted = localPlayer?.activePowerUp == PowerUpType.HEALTH_BOOST
            val maxHealth = if (isHealthBoosted) 400 else 100
            val healthText = if (isHealthBoosted) "$health/400 HP" else "$health%"

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(0.85f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = SurfaceGray.copy(alpha = 0.7f)),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, GlassWhite)
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (showFlashingHeart) {
                                Text(
                                    text = "❤️",
                                    fontSize = 14.sp,
                                    modifier = Modifier
                                        .graphicsLayer { alpha = heartAlpha }
                                        .padding(end = 4.dp)
                                )
                            }
                            Text(
                                text = "HP ",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (health > maxHealth * 0.34f) CyberGreen else CyberRed
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
                                        .background(if (health >= maxHealth * 0.34f) CyberGreen else Color.Gray.copy(alpha = 0.3f))
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(if (health >= maxHealth * 0.66f) CyberGreen else Color.Gray.copy(alpha = 0.3f))
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(if (health >= maxHealth) CyberGreen else Color.Gray.copy(alpha = 0.3f))
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = healthText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }
                    }

                    // Active Power-up Badge (if any)
                    localPlayer?.activePowerUp?.let { activePower ->
                        val (badgeName, badgeColor) = when (activePower) {
                            PowerUpType.HEALTH_BOOST -> Pair("+300 HP", Color(0xFF00FF66))
                            PowerUpType.AUTO_GUN -> Pair("AUTO FIRE", Color(0xFF00F0FF))
                            PowerUpType.ONE_SHOT_KILL -> Pair("1-SHOT", Color(0xFFFF0055))
                        }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SurfaceGray.copy(alpha = 0.85f)),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.5.dp, badgeColor)
                        ) {
                            Text(
                                text = "$badgeName (${activePowerUpTimeLeft}s)",
                                color = badgeColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }

                // Small Power-up countdown timer row underneath the health bar
                if (localPlayer?.activePowerUp != null && activePowerUpTimeLeft > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(0.85f),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "⏱️ POWER-UP ACTIVE: ${activePowerUpTimeLeft}s",
                            color = Color(0xFFFFEA00), // Cyber Yellow
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
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
                    // Face Lock-on Target HUD
                    val currentTarget = otherPlayers.find { it.id == activeTargetId }
                    val displayTargetName = currentTarget?.name?.uppercase() ?: "NO TARGET ACQUIRED"
                    val targetColor = if (currentTarget != null) CyberRed else LightOnBackground.copy(alpha = 0.6f)
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(SurfaceGray.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
                            .border(BorderStroke(1.dp, GlassWhite), RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "LOCK STATUS: ",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = LightOnBackground.copy(alpha = 0.6f),
                            letterSpacing = 1.5.sp
                        )
                        Text(
                            text = displayTargetName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = targetColor,
                            letterSpacing = 0.5.sp
                        )
                    }

                    val isAutoGunActive = localPlayer?.activePowerUp == PowerUpType.AUTO_GUN
                    val isOneShotKillActive = localPlayer?.activePowerUp == PowerUpType.ONE_SHOT_KILL
                    var isPressing by remember { mutableStateOf(false) }
                    var lastShootTime by remember { mutableStateOf(0L) }

                    if (isAutoGunActive) {
                        LaunchedEffect(isPressing, isTargetInCrosshair, activeTargetId) {
                            if (isPressing) {
                                while (true) {
                                    onShootTriggered()
                                    if (isTargetInCrosshair) {
                                        activeTargetId?.let { targetId ->
                                            onConfirmHit(targetId)
                                        }
                                    }
                                    kotlinx.coroutines.delay(200L)
                                }
                            }
                        }
                    }

                    // FIRE trigger
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f))
                            .border(BorderStroke(2.5.dp, Color.White), CircleShape)
                            .pointerInput(isAutoGunActive, isOneShotKillActive, isTargetInCrosshair, activeTargetId, lastShootTime) {
                                detectTapGestures(
                                    onPress = {
                                        if (isAutoGunActive) {
                                            isPressing = true
                                            try {
                                                awaitRelease()
                                            } finally {
                                                isPressing = false
                                            }
                                        } else {
                                            val now = System.currentTimeMillis()
                                            if (isOneShotKillActive) {
                                                if (now - lastShootTime >= 2000L) {
                                                    onShootTriggered()
                                                    lastShootTime = now
                                                    if (isTargetInCrosshair) {
                                                        activeTargetId?.let { targetId ->
                                                            onConfirmHit(targetId)
                                                        }
                                                    }
                                                }
                                            } else {
                                                onShootTriggered()
                                                if (isTargetInCrosshair) {
                                                    activeTargetId?.let { targetId ->
                                                        onConfirmHit(targetId)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                    ) {
                        val assetName = when {
                            isOneShotKillActive -> "sniper_bullet.png"
                            isAutoGunActive -> "assault_rifle.png"
                            else -> "pistol_bullet.png"
                        }
                        val painter = rememberAssetPainter(assetName)
                        Image(
                            painter = painter,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }
        }

        // The old full screen overlay has been removed to display "Wait until Respawn" directly over the view bounds.
        
        // Face Covered Warning Text overlay (displayed before blackout)
        if (isAlive && isFaceCovered && !isScreenBlackout) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 180.dp), // Position below top HUD
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CyberRed.copy(alpha = 0.85f)),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.5.dp, Color.White),
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    Text(
                        text = "WARNING: DO NOT COVER YOUR FACE",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        letterSpacing = 1.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Full screen blackout overlay for Face Covering
        if (isScreenBlackout) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable(enabled = false) {}, // Intercept/absorb click events to block screen interactions
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "UNCOVER YOUR FACE",
                        color = CyberRed,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Do not point your phone straight to your face or cover your face with it.",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }
        }

        floatingPowerUp?.let { pType ->
            val biasX = (powerUpPosition.x * 2f) - 1f
            val biasY = (powerUpPosition.y * 2f) - 1f
            
            val infiniteTransition = rememberInfiniteTransition(label = "powerup")
            val bobOffset by infiniteTransition.animateFloat(
                initialValue = -12f,
                targetValue = 12f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bob"
            )
            val glowScale by infiniteTransition.animateFloat(
                initialValue = 0.9f,
                targetValue = 1.1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, easing = LinearOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "glow"
            )

            val (name, color, icon) = when (pType) {
                PowerUpType.HEALTH_BOOST -> Triple("HEALTH BOOST", Color(0xFF00FF66), "➕")
                PowerUpType.AUTO_GUN -> Triple("AUTO FIRE", Color(0xFF00F0FF), "⚡")
                PowerUpType.ONE_SHOT_KILL -> Triple("1-SHOT KILL", Color(0xFFFF0055), "🎯")
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = BiasAlignment(biasX, biasY)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.75f)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(2.dp, color),
                    modifier = Modifier
                        .size(100.dp)
                        .graphicsLayer {
                            translationY = bobOffset
                            shadowElevation = 8.dp.toPx()
                        }
                        .clickable {
                            if (collectedCount < 2) {
                                onPowerUpActivated(pType)
                                activePowerUpTimeLeft = 30
                                collectedCount++
                            }
                            floatingPowerUp = null
                        }
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(48.dp)
                                .graphicsLayer {
                                    scaleX = glowScale
                                    scaleY = glowScale
                                }
                                .background(color.copy(alpha = 0.15f), CircleShape)
                        ) {
                            Text(text = icon, fontSize = 24.sp)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = name,
                            color = color,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            textAlign = TextAlign.Center
                        )
                    }
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
