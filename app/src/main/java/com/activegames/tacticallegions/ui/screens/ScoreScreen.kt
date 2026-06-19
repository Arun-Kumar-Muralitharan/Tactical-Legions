package com.activegames.tacticallegions.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.activegames.tacticallegions.network.PlayerScore
import com.activegames.tacticallegions.theme.*

@Composable
fun ScoreScreen(
    scores: List<PlayerScore>,
    title: String = "ROUND OVER",
    onReturnClicked: () -> Unit
) {
    val isTeamPlay = scores.any { it.team == "RED" || it.team == "BLUE" }
    val redScore = scores.filter { it.team == "RED" }.sumOf { it.score }
    val blueScore = scores.filter { it.team == "BLUE" }.sumOf { it.score }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepCharcoal)
    ) {
        // Cyan and Dark Red radial background glows
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(CyberBlue.copy(alpha = 0.12f), Color.Transparent),
                        radius = 1200f
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Title
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 24.dp)
            ) {
                Text(
                    text = "BATTLE DEBRIEFING",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberBlue,
                    letterSpacing = 3.sp
                )
                Text(
                    text = title,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    color = CyberRed,
                    letterSpacing = 2.sp
                )
            }

            if (isTeamPlay) {
                val (victoryText, victoryColor) = when {
                    redScore > blueScore -> "TEAM RED VICTORIOUS" to CyberRed
                    blueScore > redScore -> "TEAM BLUE VICTORIOUS" to CyberBlue
                    else -> "DRAW / TIED MATCH" to CyberGreen
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(BorderStroke(1.dp, victoryColor.copy(alpha = 0.5f)), RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = SurfaceGray.copy(alpha = 0.8f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = victoryText,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = victoryColor,
                            letterSpacing = 1.5.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("TEAM RED", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CyberRed)
                                Text("$redScore", fontSize = 28.sp, fontWeight = FontWeight.Black, color = CyberRed)
                            }
                            Text("VS", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = LightOnBackground.copy(alpha = 0.5f))
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("TEAM BLUE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CyberBlue)
                                Text("$blueScore", fontSize = 28.sp, fontWeight = FontWeight.Black, color = CyberBlue)
                            }
                        }
                    }
                }
            }

            // Standings list
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(scores) { index, item ->
                    val rank = index + 1
                    val isFirst = rank == 1

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isFirst) CyberBlue.copy(alpha = 0.15f) else SurfaceGray.copy(alpha = 0.6f))
                            .border(
                                BorderStroke(1.dp, if (isFirst) CyberBlue else GlassWhite),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Rank Number Badge
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isFirst) CyberBlue else SurfaceGray),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "#$rank",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 14.sp,
                                    color = if (isFirst) Color.Black else Color.White
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = item.name.uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = if (isFirst) CyberBlue else Color.White
                                )
                                if (isTeamPlay && item.team.isNotBlank()) {
                                    Text(
                                        text = if (item.team == "RED") "TEAM RED" else "TEAM BLUE",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        color = if (item.team == "RED") CyberRed else CyberBlue
                                    )
                                }
                            }
                        }

                        // Kills Score
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${item.score}",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                color = if (isFirst) CyberBlue else CyberGreen
                            )
                            Text(
                                text = "KILLS",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = LightOnBackground.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }

            // Return to Lobby Button
            Button(
                onClick = onReturnClicked,
                colors = ButtonDefaults.buttonColors(containerColor = CyberGreen, contentColor = Color.Black),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(
                    text = "RETURN TO REGISTRATION",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}
