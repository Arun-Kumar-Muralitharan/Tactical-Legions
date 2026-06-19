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
                            Text(
                                text = item.name.uppercase(),
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = if (isFirst) CyberBlue else Color.White
                            )
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
