package com.activegames.tacticallegions.network

import kotlinx.serialization.Serializable

@Serializable
data class PlayerState(
    val id: String,
    val name: String,
    val isReady: Boolean,
    val isAlive: Boolean,
    val health: Int,
    val score: Int,
    val isExited: Boolean = false,
    val team: String = ""
)

@Serializable
data class PlayerScore(
    val name: String,
    val score: Int,
    val team: String = ""
)

@Serializable
enum class GameMode {
    CLASSIC,
    RACE
}

@Serializable
sealed class GameMessage {
    @Serializable
    data class ChooseTeam(val playerId: String, val team: String) : GameMessage()

    @Serializable
    object RandomizeTeams : GameMessage()

    // @Serializable
    // object AddMockPlayers : GameMessage()

    @Serializable
    data class Join(val nickname: String, val playerId: String) : GameMessage()

    @Serializable
    data class LobbyUpdate(
        val players: List<PlayerState>,
        val matchDurationSeconds: Int = 600,
        val gameMode: GameMode = GameMode.CLASSIC,
        val scoreLimit: Int = 10
    ) : GameMessage()

    @Serializable
    data class ConfigureMatch(
        val durationSeconds: Int,
        val gameMode: GameMode = GameMode.CLASSIC,
        val scoreLimit: Int = 10
    ) : GameMessage()

    @Serializable
    data class ToggleReady(val playerId: String, val isReady: Boolean) : GameMessage()

    @Serializable
    data class StartGame(
        val countdownSeconds: Int,
        val durationSeconds: Int,
        val gameMode: GameMode = GameMode.CLASSIC,
        val scoreLimit: Int = 10
    ) : GameMessage()

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
