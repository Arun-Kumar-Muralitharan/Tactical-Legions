package com.example.tacticallegions

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tacticallegions.network.ConnectionState
import com.example.tacticallegions.theme.*
import com.example.tacticallegions.ui.GameViewModel
import com.example.tacticallegions.ui.screens.*

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        // Handle result if necessary
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request camera permission on startup
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        enableEdgeToEdge()
        setContent {
            TacticalLegionsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TacticalAppContent()
                }
            }
        }
    }
}

@Composable
fun TacticalAppContent(
    viewModel: GameViewModel = viewModel()
) {
    val connectionState by viewModel.client.connectionState.collectAsState()
    val players by viewModel.client.players.collectAsState()
    val countdownTime by viewModel.client.countdownTime.collectAsState()
    val matchTimeSeconds by viewModel.client.matchTimeRemaining.collectAsState()
    val finalScores by viewModel.client.finalScores.collectAsState()

    val localIp by viewModel.localIp
    val isHost by viewModel.isHost
    val isTargetInCrosshair by viewModel.isTargetInCrosshair

    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    // Periodically re-check camera permissions
    LaunchedEffect(Unit) {
        while (true) {
            hasCameraPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            kotlinx.coroutines.delay(1000)
        }
    }

    if (!hasCameraPermission) {
        PermissionRequiredScreen()
        return
    }

    when {
        finalScores != null -> {
            ScoreScreen(
                scores = finalScores!!,
                onReturnClicked = {
                    viewModel.disconnect()
                }
            )
        }
        matchTimeSeconds != null || countdownTime != null -> {
            val successfulHitCount by viewModel.successfulHitCount
            GameScreen(
                players = players,
                localPlayerId = viewModel.client.playerId,
                matchTimeSeconds = matchTimeSeconds,
                countdownTime = countdownTime,
                isTargetInCrosshair = isTargetInCrosshair,
                successfulHitCount = successfulHitCount,
                onTargetStatusChanged = { inCrosshair ->
                    viewModel.setTargetStatus(inCrosshair)
                },
                onShootTriggered = {
                    viewModel.triggerShoot()
                },
                onConfirmHit = { targetId ->
                    viewModel.confirmHit(targetId)
                },
                onExitClicked = {
                    viewModel.disconnect()
                }
            )
        }
        connectionState is ConnectionState.Connected -> {
            val displayIp = if (isHost) localIp else "Connected Client"
            LobbyScreen(
                players = players,
                localPlayerId = viewModel.client.playerId,
                isHost = isHost,
                hostIp = displayIp,
                countdownTime = countdownTime,
                onReadyToggled = { ready ->
                    viewModel.toggleReady(ready)
                },
                onDisconnectClicked = {
                    viewModel.disconnect()
                }
            )
        }
        connectionState is ConnectionState.Connecting -> {
            ConnectingScreen()
        }
        else -> {
            val errorMsg = (connectionState as? ConnectionState.Failed)?.reason
            SetupScreen(
                onHostClicked = { nickname ->
                    viewModel.startHost(nickname)
                },
                onJoinClicked = { ip, nickname ->
                    viewModel.joinGame(ip, nickname)
                },
                errorMessage = errorMsg
            )
        }
    }
}

@Composable
fun ConnectingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepCharcoal),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(color = CyberBlue)
            Text(
                text = "ESTABLISHING LINK...",
                color = CyberBlue,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
        }
    }
}

@Composable
fun PermissionRequiredScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepCharcoal),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "CAMERA PERMISSION REQUIRED",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = CyberRed,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Tactical Legions uses the device camera as the weapon viewfinder. Please grant camera permission in Android settings to start.",
                textAlign = TextAlign.Center,
                fontSize = 14.sp,
                color = LightOnBackground.copy(alpha = 0.8f)
            )
        }
    }
}
