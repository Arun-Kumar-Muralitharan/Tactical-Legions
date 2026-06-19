package com.activegames.tacticallegions.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.activegames.tacticallegions.network.GameMode
import com.activegames.tacticallegions.network.PlayerState
import com.activegames.tacticallegions.theme.*

@Composable
fun LobbyScreen(
    players: List<PlayerState>,
    localPlayerId: String,
    isHost: Boolean,
    hostIp: String,
    countdownTime: Int?,
    matchDurationSeconds: Int,
    gameMode: GameMode,
    scoreLimit: Int,
    onConfigChanged: (Int, GameMode, Int) -> Unit,
    onTeamSelected: (String) -> Unit,
    onRandomizeTeamsClicked: () -> Unit,
    onAddMockPlayersClicked: () -> Unit = {},
    onReadyToggled: (Boolean) -> Unit,
    onDisconnectClicked: () -> Unit
) {
    val localPlayer = players.find { it.id == localPlayerId }
    val isReady = localPlayer?.isReady ?: false
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepCharcoal)
    ) {
        // Neon background glow
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(CyberGreen.copy(alpha = 0.05f), Color.Transparent)
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (isHost) "HOSTING BATTLE" else "CONNECTED TO SERVER",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isHost) CyberGreen else CyberBlue,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = "TACTICAL LOBBY",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                }

                TextButton(
                    onClick = onDisconnectClicked,
                    colors = ButtonDefaults.textButtonColors(contentColor = CyberRed)
                ) {
                    Text("LEAVE", fontWeight = FontWeight.Bold)
                }
            }

            // Host Connection Box
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(1.dp, GlassWhite), RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = SurfaceGray.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "SHARE IP WITH FRIENDS",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = LightOnBackground.copy(alpha = 0.5f),
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = hostIp,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyberBlue,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Max players: 8  |  Connected: ${players.size}/8",
                        fontSize = 12.sp,
                        color = LightOnBackground
                    )
                    // if (isHost) {
                    //     Spacer(modifier = Modifier.height(10.dp))
                    //     Button(
                    //         onClick = onAddMockPlayersClicked,
                    //         colors = ButtonDefaults.buttonColors(
                    //             containerColor = CyberBlue.copy(alpha = 0.15f),
                    //             contentColor = CyberBlue
                    //         ),
                    //         border = BorderStroke(1.dp, CyberBlue),
                    //         shape = RoundedCornerShape(8.dp),
                    //         modifier = Modifier.fillMaxWidth()
                    //     ) {
                    //         Text("ADD TEST PLAYERS (DEBUG)", fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp)
                    //     }
                    // }
                }
            }

            // Game Mode Selection Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(1.dp, GlassWhite), RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = SurfaceGray.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "GAME MODE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = LightOnBackground.copy(alpha = 0.5f),
                        letterSpacing = 1.5.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Classic Mode Select Button
                        val isClassicSelected = gameMode == GameMode.CLASSIC
                        val classicBgColor = if (isClassicSelected) CyberGreen.copy(alpha = 0.15f) else SurfaceGray
                        val classicTitleColor = if (isClassicSelected) CyberGreen else Color.White

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(classicBgColor)
                                .border(BorderStroke(1.dp, if (isClassicSelected) CyberGreen else GlassWhite), RoundedCornerShape(8.dp))
                                .clickable(enabled = isHost) {
                                    onConfigChanged(matchDurationSeconds, GameMode.CLASSIC, scoreLimit)
                                }
                                .padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "CLASSIC",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = classicTitleColor
                            )
                            Text(
                                text = "Time limit match. Highest score wins.",
                                fontSize = 9.sp,
                                color = LightOnBackground.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center,
                                lineHeight = 12.sp
                            )
                        }

                        // Race Mode Select Button
                        val isRaceSelected = gameMode == GameMode.RACE
                        val raceBgColor = if (isRaceSelected) CyberBlue.copy(alpha = 0.15f) else SurfaceGray
                        val raceTitleColor = if (isRaceSelected) CyberBlue else Color.White

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(raceBgColor)
                                .border(BorderStroke(1.dp, if (isRaceSelected) CyberBlue else GlassWhite), RoundedCornerShape(8.dp))
                                .clickable(enabled = isHost) {
                                    onConfigChanged(matchDurationSeconds, GameMode.RACE, scoreLimit)
                                }
                                .padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "RACE",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = raceTitleColor
                            )
                            Text(
                                text = "Score limit match. First to limit wins.",
                                fontSize = 9.sp,
                                color = LightOnBackground.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center,
                                lineHeight = 12.sp
                            )
                        }
                    }
                }
            }

            // Dynamic Configuration Stepper Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(1.dp, GlassWhite), RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = SurfaceGray.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (gameMode == GameMode.CLASSIC) {
                        Text(
                            text = "MATCH DURATION LIMIT",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = LightOnBackground.copy(alpha = 0.5f),
                            letterSpacing = 1.5.sp
                        )

                        val durationMinutes = matchDurationSeconds / 60

                        if (isHost) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        if (durationMinutes > 1) {
                                            onConfigChanged((durationMinutes - 1) * 60, GameMode.CLASSIC, scoreLimit)
                                        }
                                    },
                                    colors = IconButtonDefaults.iconButtonColors(containerColor = GlassWhite),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Text("-", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 18.sp)
                                }

                                Text(
                                    text = "$durationMinutes MINS",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black,
                                    color = CyberGreen,
                                    letterSpacing = 0.5.sp
                                )

                                IconButton(
                                    onClick = {
                                        if (durationMinutes < 20) {
                                            onConfigChanged((durationMinutes + 1) * 60, GameMode.CLASSIC, scoreLimit)
                                        }
                                    },
                                    colors = IconButtonDefaults.iconButtonColors(containerColor = GlassWhite),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Text("+", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 18.sp)
                                }
                            }
                            Text(
                                text = "Max limit: 20 minutes",
                                fontSize = 10.sp,
                                color = LightOnBackground.copy(alpha = 0.4f)
                            )
                        } else {
                            Text(
                                text = "$durationMinutes MINS (SET BY HOST)",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyberBlue,
                                letterSpacing = 0.5.sp,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    } else {
                        Text(
                            text = "SCORE LIMIT GOAL",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = LightOnBackground.copy(alpha = 0.5f),
                            letterSpacing = 1.5.sp
                        )

                        if (isHost) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        if (scoreLimit > 2) {
                                            onConfigChanged(matchDurationSeconds, GameMode.RACE, scoreLimit - 1)
                                        }
                                    },
                                    colors = IconButtonDefaults.iconButtonColors(containerColor = GlassWhite),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Text("-", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 18.sp)
                                }

                                Text(
                                    text = "$scoreLimit KILLS",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black,
                                    color = CyberBlue,
                                    letterSpacing = 0.5.sp
                                )

                                IconButton(
                                    onClick = {
                                        if (scoreLimit < 50) {
                                            onConfigChanged(matchDurationSeconds, GameMode.RACE, scoreLimit + 1)
                                        }
                                    },
                                    colors = IconButtonDefaults.iconButtonColors(containerColor = GlassWhite),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Text("+", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 18.sp)
                                }
                            }
                            Text(
                                text = "Range: 2 - 50 kills to win",
                                fontSize = 10.sp,
                                color = LightOnBackground.copy(alpha = 0.4f)
                            )
                        } else {
                            Text(
                                text = "$scoreLimit KILLS (SET BY HOST)",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyberBlue,
                                letterSpacing = 0.5.sp,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            if (players.size > 4) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(BorderStroke(1.dp, GlassWhite), RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = SurfaceGray.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "TEAM ASSIGNMENT",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = LightOnBackground.copy(alpha = 0.5f),
                            letterSpacing = 1.5.sp
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val currentTeam = localPlayer?.team ?: ""
                            
                            val isRed = currentTeam == "RED"
                            val redBgColor = if (isRed) CyberRed.copy(alpha = 0.2f) else SurfaceGray
                            val redBorderColor = if (isRed) CyberRed else GlassWhite
                            
                            Button(
                                onClick = { onTeamSelected("RED") },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = redBgColor,
                                    contentColor = if (isRed) CyberRed else Color.White
                                ),
                                border = BorderStroke(1.dp, redBorderColor),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("TEAM RED", fontWeight = FontWeight.Bold)
                            }
                            
                            val isBlue = currentTeam == "BLUE"
                            val blueBgColor = if (isBlue) CyberBlue.copy(alpha = 0.2f) else SurfaceGray
                            val blueBorderColor = if (isBlue) CyberBlue else GlassWhite
                            
                            Button(
                                onClick = { onTeamSelected("BLUE") },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = blueBgColor,
                                    contentColor = if (isBlue) CyberBlue else Color.White
                                ),
                                border = BorderStroke(1.dp, blueBorderColor),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("TEAM BLUE", fontWeight = FontWeight.Bold)
                            }
                        }
                        
                        if (isHost) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Button(
                                onClick = onRandomizeTeamsClicked,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CyberGreen.copy(alpha = 0.15f),
                                    contentColor = CyberGreen
                                ),
                                border = BorderStroke(1.dp, CyberGreen),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("RANDOMIZE TEAMS", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            }
                        }
                    }
                }
            }

            // Players list title
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "ACTIVE SQUAD",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberBlue,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "READY STATUS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberBlue,
                    letterSpacing = 1.sp
                )
            }

            // Player grid/list
            // Player grid/list
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                players.forEach { player ->
                    val isSelf = player.id == localPlayerId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelf) GlassWhite else SurfaceGray.copy(alpha = 0.5f))
                            .border(
                                BorderStroke(1.dp, if (isSelf) CyberGreen.copy(alpha = 0.4f) else Color.Transparent),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (player.isReady) CyberGreen else CyberRed)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = player.name + if (isSelf) " (YOU)" else "",
                                fontWeight = if (isSelf) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 16.sp,
                                color = if (isSelf) CyberGreen else Color.White
                            )
                            if (players.size > 4 && player.team.isNotBlank()) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (player.team == "RED") CyberRed.copy(alpha = 0.15f) else CyberBlue.copy(alpha = 0.15f))
                                        .border(BorderStroke(1.dp, if (player.team == "RED") CyberRed else CyberBlue), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = player.team,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (player.team == "RED") CyberRed else CyberBlue
                                    )
                                }
                            }
                        }

                        // Ready Badge
                        Text(
                            text = if (player.isReady) "READY" else "WAITING",
                            color = if (player.isReady) CyberGreen else CyberRed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }

            // Big Ready Button
            Button(
                onClick = { onReadyToggled(!isReady) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isReady) CyberRed else CyberGreen,
                    contentColor = if (isReady) Color.White else Color.Black
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(
                    text = if (isReady) "CANCEL READY" else "LOCK IN READY",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }

        // Full Screen Animated Countdown Overlay
        AnimatedVisibility(
            visible = countdownTime != null,
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
                        text = "COMMENCING BATTLE IN",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyberBlue,
                        letterSpacing = 4.sp
                    )
                    
                    Text(
                        text = "${countdownTime ?: 10}",
                        fontSize = 120.sp,
                        fontWeight = FontWeight.Black,
                        color = CyberGreen
                    )

                    Text(
                        text = "ALL PILOTS LOCKED IN",
                        fontSize = 12.sp,
                        color = LightOnBackground.copy(alpha = 0.5f),
                        letterSpacing = 2.sp
                    )
                }
            }
        }
    }
}
