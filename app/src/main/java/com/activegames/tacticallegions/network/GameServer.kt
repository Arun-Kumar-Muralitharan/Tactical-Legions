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
                                                val player = existing ?: PlayerState(
                                                    id = message.playerId,
                                                    name = message.nickname,
                                                    isReady = false,
                                                    isAlive = true,
                                                    health = 100,
                                                    score = 0
                                                )
                                                _playersState[message.playerId] = player
                                                
                                                broadcast(GameMessage.LobbyUpdate(_playersState.values.toList(), customMatchDurationSeconds))
                                            }
                                            is GameMessage.ToggleReady -> {
                                                val pId = message.playerId
                                                val existing = _playersState[pId]
                                                if (existing != null) {
                                                    _playersState[pId] = existing.copy(isReady = message.isReady)
                                                    broadcast(GameMessage.LobbyUpdate(_playersState.values.toList(), customMatchDurationSeconds))
                                                    checkLobbyReadyAndStart()
                                                }
                                            }
                                            is GameMessage.ConfigureMatch -> {
                                                customMatchDurationSeconds = message.durationSeconds
                                                broadcast(GameMessage.LobbyUpdate(_playersState.values.toList(), customMatchDurationSeconds))
                                            }
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
                                    broadcast(GameMessage.LobbyUpdate(_playersState.values.toList(), customMatchDurationSeconds))
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
            gameStarted = true
            gameLoopJob = serverScope.launch {
                // Phase 1: 5s Countdown
                for (i in 5 downTo 0) {
                    broadcast(GameMessage.StartGame(countdownSeconds = i, durationSeconds = customMatchDurationSeconds))
                    delay(1000)
                }
                
                // Reset player health/scores for match start
                _playersState.keys.forEach { pId ->
                    val p = _playersState[pId]!!
                    _playersState[pId] = p.copy(health = 100, isAlive = true, score = 0, isExited = false)
                }
                broadcast(GameMessage.LobbyUpdate(_playersState.values.toList(), customMatchDurationSeconds))

                // Phase 2: In-Game custom timer
                matchTimeRemaining = customMatchDurationSeconds
                while (matchTimeRemaining > 0) {
                    broadcast(GameMessage.MatchTimerTick(matchTimeRemaining))
                    delay(1000)
                    matchTimeRemaining--
                }

                // Phase 3: Game Over
                val scoreboard = _playersState.values
                    .map { PlayerScore(name = it.name, score = it.score) }
                    .sortedByDescending { it.score }
                broadcast(GameMessage.GameOver(scoreboard))
                gameStarted = false
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
            _playersState[shooterId] = shooter.copy(score = shooter.score + 1)
            
            // Broadcast elimination
            broadcast(GameMessage.PlayerEliminated(targetId = targetId, shooterId = shooterId, respawnSeconds = 5))
            broadcast(GameMessage.LobbyUpdate(_playersState.values.toList(), customMatchDurationSeconds))

            // Trigger respawn in 5 seconds
            serverScope.launch {
                delay(5000)
                val currentTarget = _playersState[targetId]
                if (currentTarget != null) {
                    _playersState[targetId] = currentTarget.copy(health = 100, isAlive = true)
                    broadcast(GameMessage.Respawned(targetId))
                    broadcast(GameMessage.LobbyUpdate(_playersState.values.toList(), customMatchDurationSeconds))
                }
            }
        }
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
