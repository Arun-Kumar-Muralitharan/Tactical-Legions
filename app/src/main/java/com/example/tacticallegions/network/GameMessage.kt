package com.example.tacticallegions.network

import kotlinx.serialization.Serializable

@Serializable
data class PlayerState(
    val id: String,
    val name: String,
    val isReady: Boolean,
    val isAlive: Boolean,
    val health: Int,
    val score: Int
)

@Serializable
data class PlayerScore(
    val name: String,
    val score: Int
)

@Serializable
sealed class GameMessage {
    @Serializable
    data class Join(val nickname: String, val playerId: String) : GameMessage()

    @Serializable
    data class LobbyUpdate(val players: List<PlayerState>) : GameMessage()

    @Serializable
    data class ToggleReady(val playerId: String, val isReady: Boolean) : GameMessage()

    @Serializable
    data class StartGame(val countdownSeconds: Int, val durationSeconds: Int) : GameMessage()

    @Serializable
    data class ActionShoot(val shooterId: String, val targetId: String) : GameMessage()

    @Serializable
    data class PlayerHit(
        val targetId: String,
        val shooterId: String,
        val damage: Int,
        val currentHealth: Int
    ) : GameMessage()

    @Serializable
    data class PlayerEliminated(
        val targetId: String,
        val shooterId: String,
        val respawnSeconds: Int
    ) : GameMessage()

    @Serializable
    data class Respawned(val playerId: String) : GameMessage()

    @Serializable
    data class MatchTimerTick(val secondsRemaining: Int) : GameMessage()

    @Serializable
    data class GameOver(val finalScores: List<PlayerScore>) : GameMessage()

    @Serializable
    object Heartbeat : GameMessage()
}
