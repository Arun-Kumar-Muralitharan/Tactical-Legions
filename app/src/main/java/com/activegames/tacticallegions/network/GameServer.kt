package com.activegames.tacticallegions.network

import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.concurrent.ConcurrentHashMap

class GameServer {
    private var serverJob: Job? = null
    private val serverScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var gameLoopJob: Job? = null

    private val json = Json { ignoreUnknownKeys = true }

    // Thread-safe map of active player IDs to their connection sessions
    private val sessions = ConcurrentHashMap<String, io.ktor.websocket.DefaultWebSocketSession>()

    // Local mutable copy of player states
    private val _playersState = ConcurrentHashMap<String, PlayerState>()
    
    // Exposed server states for Host UI
    private val _serverIp = MutableStateFlow<String>("Unknown")
    val serverIp = _serverIp.asStateFlow()

    private val _serverPort = MutableStateFlow<Int>(8080)
    val serverPort = _serverPort.asStateFlow()

    private var gameStarted = false
    private var matchTimeRemaining = 600
    private var customMatchDurationSeconds = 600
    private var customGameMode = GameMode.CLASSIC
    private var customScoreLimit = 10

    fun start(port: Int = 8080) {
        if (serverJob != null) return // Already running
        _serverPort.value = port
        _serverIp.value = getLocalIpAddress()

        serverJob = serverScope.launch {
            try {
                embeddedServer(CIO, port = port) {
                    install(WebSockets)
                    routing {
                        webSocket("/game") {
                            var connectedPlayerId: String? = null
                            try {
                                for (frame in incoming) {
                                    if (frame is Frame.Text) {
                                        val text = frame.readText()
                                        val message = json.decodeFromString<GameMessage>(text)
                                        
                                        when (message) {
                                            is GameMessage.Join -> {
                                                connectedPlayerId = message.playerId
                                                sessions[message.playerId] = this
                                                
                                                // Create or update player state
                                                val existing = _playersState[message.playerId]
                                                val player = existing?.copy(faceSignature = message.faceSignature) ?: PlayerState(
                                                    id = message.playerId,
                                                    name = message.nickname,
                                                    isReady = false,
                                                    isAlive = true,
                                                    health = 100,
                                                    score = 0,
                                                    faceSignature = message.faceSignature
                                                )
                                                _playersState[message.playerId] = player
                                                
                                                broadcast(GameMessage.LobbyUpdate(_playersState.values.toList(), customMatchDurationSeconds, customGameMode, customScoreLimit))
                                            }
                                            is GameMessage.ToggleReady -> {
                                                val pId = message.playerId
                                                val existing = _playersState[pId]
                                                if (existing != null) {
                                                    _playersState[pId] = existing.copy(isReady = message.isReady)
                                                    broadcast(GameMessage.LobbyUpdate(_playersState.values.toList(), customMatchDurationSeconds, customGameMode, customScoreLimit))
                                                    checkLobbyReadyAndStart()
                                                }
                                            }
                                            is GameMessage.ConfigureMatch -> {
                                                customMatchDurationSeconds = message.durationSeconds
                                                customGameMode = message.gameMode
                                                customScoreLimit = message.scoreLimit
                                                broadcast(GameMessage.LobbyUpdate(_playersState.values.toList(), customMatchDurationSeconds, customGameMode, customScoreLimit))
                                            }
                                            is GameMessage.ChooseTeam -> {
                                                val pId = message.playerId
                                                val existing = _playersState[pId]
                                                if (existing != null && !gameStarted) {
                                                    _playersState[pId] = existing.copy(team = message.team)
                                                    broadcast(GameMessage.LobbyUpdate(_playersState.values.toList(), customMatchDurationSeconds, customGameMode, customScoreLimit))
                                                }
                                            }
                                            is GameMessage.RandomizeTeams -> {
                                                if (!gameStarted) {
                                                    randomizeTeamsEvenly()
                                                }
                                            }
                                            // is GameMessage.AddMockPlayers -> {
                                            //     if (!gameStarted) {
                                            //         val mockNames = listOf("Alpha", "Bravo", "Charlie", "Delta", "Echo")
                                            //         mockNames.forEach { name ->
                                            //             val pId = java.util.UUID.randomUUID().toString()
                                            //             _playersState[pId] = PlayerState(
                                            //                 id = pId,
                                            //                 name = name,
                                            //                 isReady = true,
                                            //                 isAlive = true,
                                            //                 health = 100,
                                            //                 score = 0,
                                            //                 team = ""
                                            //             )
                                            //         }
                                            //         broadcast(GameMessage.LobbyUpdate(_playersState.values.toList(), customMatchDurationSeconds, customGameMode, customScoreLimit))
                                            //     }
                                            // }
                                            is GameMessage.ActionShoot -> {
                                                handleShootAction(message.shooterId, message.targetId)
                                            }
                                            else -> { /* Other messages ignored on server */ }
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            } finally {
                                // Clean up on disconnect
                                connectedPlayerId?.let { pId ->
                                    sessions.remove(pId)
                                    if (gameStarted) {
                                        val existing = _playersState[pId]
                                        if (existing != null) {
                                            _playersState[pId] = existing.copy(isExited = true, isAlive = false, health = 0)
                                        }
                                    } else {
                                        // Lobby stage: completely remove them so they don't linger if they disconnect before starting
                                        _playersState.remove(pId)
                                    }
                                    broadcast(GameMessage.LobbyUpdate(_playersState.values.toList(), customMatchDurationSeconds, customGameMode, customScoreLimit))
                                }
                            }
                        }
                    }
                }.start(wait = true)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun broadcast(message: GameMessage) {
        val jsonStr = json.encodeToString(GameMessage.serializer(), message)
        sessions.values.forEach { session ->
            try {
                if (session.isActive) {
                    session.send(Frame.Text(jsonStr))
                }
            } catch (e: Exception) {
                // Ignore disconnect issues during broadcast
            }
        }
    }

    private fun checkLobbyReadyAndStart() {
        if (gameStarted) return
        val currentPlayers = _playersState.values.toList()
        
        // Start the game if all players are ready, with at least 1 player (for solo testing/debug)
        if (currentPlayers.isNotEmpty() && currentPlayers.all { it.isReady } && currentPlayers.size <= 8) {
            // Apply team balancing / assignment before game loop starts
            if (currentPlayers.size > 4) {
                val redChose = currentPlayers.filter { it.team == "RED" }.toMutableList()
                val blueChose = currentPlayers.filter { it.team == "BLUE" }.toMutableList()
                val unassigned = currentPlayers.filter { it.team != "RED" && it.team != "BLUE" }.shuffled().toMutableList()

                val redFinal = redChose
                val blueFinal = blueChose

                // 1. Distribute unassigned players to the smaller team first
                for (player in unassigned) {
                    if (redFinal.size <= blueFinal.size) {
                        redFinal.add(player)
                    } else {
                        blueFinal.add(player)
                    }
                }

                // 2. If teams are still uneven (difference > 1), balance them
                val total = currentPlayers.size
                val maxAllowedDiff = if (total % 2 == 0) 0 else 1
                
                while (Math.abs(redFinal.size - blueFinal.size) > maxAllowedDiff) {
                    if (redFinal.size > blueFinal.size) {
                        val p = redFinal.removeAt(redFinal.size - 1)
                        blueFinal.add(p)
                    } else {
                        val p = blueFinal.removeAt(blueFinal.size - 1)
                        redFinal.add(p)
                    }
                }

                // Save back to players state
                redFinal.forEach { _playersState[it.id] = it.copy(team = "RED") }
                blueFinal.forEach { _playersState[it.id] = it.copy(team = "BLUE") }
            } else {
                currentPlayers.forEach { player ->
                    _playersState[player.id] = player.copy(team = "")
                }
            }

            gameStarted = true
            gameLoopJob = serverScope.launch {
                // Phase 1: 5s Countdown
                for (i in 5 downTo 0) {
                    broadcast(GameMessage.StartGame(countdownSeconds = i, durationSeconds = customMatchDurationSeconds, gameMode = customGameMode, scoreLimit = customScoreLimit))
                    delay(1000)
                }
                
                // Reset player health/scores for match start
                _playersState.keys.forEach { pId ->
                    val p = _playersState[pId]!!
                    _playersState[pId] = p.copy(health = 100, isAlive = true, score = 0, isExited = false)
                }
                broadcast(GameMessage.LobbyUpdate(_playersState.values.toList(), customMatchDurationSeconds, customGameMode, customScoreLimit))

                // Phase 2: In-Game custom timer
                matchTimeRemaining = customMatchDurationSeconds
                while (matchTimeRemaining > 0 && gameStarted) {
                    broadcast(GameMessage.MatchTimerTick(matchTimeRemaining))
                    delay(1000)
                    matchTimeRemaining--
                }

                if (gameStarted) {
                    endGame()
                }
            }
        }
    }

    private suspend fun handleShootAction(shooterId: String, targetId: String) {
        if (!gameStarted || matchTimeRemaining <= 0) return

        val target = _playersState[targetId] ?: return
        val shooter = _playersState[shooterId] ?: return

        // Can only shoot alive players
        if (!target.isAlive || target.health <= 0) return

        val damage = 34
        val newHealth = (target.health - damage).coerceAtLeast(0)
        
        if (newHealth > 0) {
            // Player hit, but remains alive
            _playersState[targetId] = target.copy(health = newHealth)
            broadcast(GameMessage.PlayerHit(targetId = targetId, shooterId = shooterId, damage = damage, currentHealth = newHealth))
        } else {
            // Player eliminated
            _playersState[targetId] = target.copy(health = 0, isAlive = false)
            val newScore = shooter.score + 1
            _playersState[shooterId] = shooter.copy(score = newScore)
            
            // Broadcast elimination
            broadcast(GameMessage.PlayerEliminated(targetId = targetId, shooterId = shooterId, respawnSeconds = 5))
            broadcast(GameMessage.LobbyUpdate(_playersState.values.toList(), customMatchDurationSeconds, customGameMode, customScoreLimit))

            if (customGameMode == GameMode.RACE && newScore >= customScoreLimit) {
                endGame()
            } else {
                // Trigger respawn in 5 seconds
                serverScope.launch {
                    delay(5000)
                    val currentTarget = _playersState[targetId]
                    if (currentTarget != null && gameStarted) {
                        _playersState[targetId] = currentTarget.copy(health = 100, isAlive = true)
                        broadcast(GameMessage.Respawned(targetId))
                        broadcast(GameMessage.LobbyUpdate(_playersState.values.toList(), customMatchDurationSeconds, customGameMode, customScoreLimit))
                    }
                }
            }
        }
    }

    private fun endGame() {
        if (!gameStarted) return
        gameStarted = false
        gameLoopJob?.cancel()
        gameLoopJob = null
        
        serverScope.launch {
            val scoreboard = _playersState.values
                .map { PlayerScore(name = it.name, score = it.score, team = it.team) }
                .sortedByDescending { it.score }
            broadcast(GameMessage.GameOver(scoreboard))
        }
    }

    private suspend fun randomizeTeamsEvenly() {
        val players = _playersState.values.toList()
        if (players.size > 4) {
            val shuffled = players.shuffled()
            val half = shuffled.size / 2
            shuffled.forEachIndexed { index, player ->
                val assignedTeam = if (index < half) "RED" else "BLUE"
                _playersState[player.id] = player.copy(team = assignedTeam)
            }
        } else {
            players.forEach { player ->
                _playersState[player.id] = player.copy(team = "")
            }
        }
        broadcast(GameMessage.LobbyUpdate(_playersState.values.toList(), customMatchDurationSeconds, customGameMode, customScoreLimit))
    }

    fun stop() {
        gameLoopJob?.cancel()
        gameLoopJob = null
        serverJob?.cancel()
        serverJob = null
        serverScope.cancel()
        sessions.clear()
        _playersState.clear()
        gameStarted = false
    }

    // Helper to fetch the local IPv4 address
    private fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    val hostAddress = addr.hostAddress
                    if (addr is InetAddress && !addr.isLoopbackAddress && hostAddress != null && !hostAddress.contains(":")) {
                        return hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "127.0.0.1" // Fallback
    }
}
