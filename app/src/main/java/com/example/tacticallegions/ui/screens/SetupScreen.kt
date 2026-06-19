package com.example.tacticallegions.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tacticallegions.theme.*

@Composable
fun SetupScreen(
    onHostClicked: (nickname: String) -> Unit,
    onJoinClicked: (ip: String, nickname: String) -> Unit,
    errorMessage: String? = null
) {
    var nickname by remember { mutableStateOf("") }
    var hostIp by remember { mutableStateOf("") }
    var isJoiningMode by remember { mutableStateOf(false) }

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
                                if (nickname.isNotBlank()) {
                                    onHostClicked(nickname.trim())
                                }
                            },
                            enabled = nickname.isNotBlank() && !isJoiningMode,
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
                            enabled = nickname.isNotBlank() && !isJoiningMode,
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
                                        if (nickname.isNotBlank() && hostIp.isNotBlank()) {
                                            onJoinClicked(hostIp.trim(), nickname.trim())
                                        }
                                    },
                                    enabled = nickname.isNotBlank() && hostIp.isNotBlank(),
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
    }
}
