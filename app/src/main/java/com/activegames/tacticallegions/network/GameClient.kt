package com.activegames.tacticallegions.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.url
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readText
import io.ktor.websocket.send
import io.ktor.websocket.close
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.util.UUID

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    object Connected : ConnectionState()
    data class Failed(val reason: String) : ConnectionState()
}

class GameClient {
    val playerId: String = UUID.randomUUID().toString()
    var playerNickname: String = ""
        private set

    private val json = Json { ignoreUnknownKeys = true }
    private var client: HttpClient? = null
    private var session: WebSocketSession? = null
    private var clientScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Game states observed by UI
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState = _connectionState.asStateFlow()

    private val _players = MutableStateFlow<List<PlayerState>>(emptyList())
    val players = _players.asStateFlow()

    private val _countdownTime = MutableStateFlow<Int?>(null)
    val countdownTime = _countdownTime.asStateFlow()

    private val _matchTimeRemaining = MutableStateFlow<Int?>(null)
    val matchTimeRemaining = _matchTimeRemaining.asStateFlow()

    private val _finalScores = MutableStateFlow<List<PlayerScore>?>(null)
    val finalScores = _finalScores.asStateFlow()

    private val _isGameActive = MutableStateFlow(false)
    val isGameActive = _isGameActive.asStateFlow()

    private val _matchDurationSeconds = MutableStateFlow(600)
    val matchDurationSeconds = _matchDurationSeconds.asStateFlow()

    // Real-time events for triggers (sounds/vibrations)
    private val _hitEvent = MutableSharedFlow<Triple<String, String, Int>>() // targetId, shooterId, currentHealth
    val hitEvent = _hitEvent.asSharedFlow()

    private val _eliminatedEvent = MutableSharedFlow<Pair<String, String>>() // targetId, shooterId
    val eliminatedEvent = _eliminatedEvent.asSharedFlow()

    private val _respawnedEvent = MutableSharedFlow<String>() // playerId
    val respawnedEvent = _respawnedEvent.asSharedFlow()

    fun connect(host: String, port: Int = 8080, nickname: String) {
        playerNickname = nickname
        _connectionState.value = ConnectionState.Connecting
        
        clientScope.launch {
            try {
                client = HttpClient(CIO) {
                    install(WebSockets)
                }

                val wsSession = client!!.webSocketSession {
                    url("ws://$host:$port/game")
                }
                session = wsSession
                _connectionState.value = ConnectionState.Connected

                // Join the game immediately upon connection
                sendMessage(GameMessage.Join(nickname, playerId))

                // Start listening for messages
                for (frame in wsSession.incoming) {
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        handleIncomingMessage(text)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _connectionState.value = ConnectionState.Failed(e.localizedMessage ?: "Unknown Error")
            } finally {
                disconnect()
            }
        }
    }

    private suspend fun handleIncomingMessage(text: String) {
        try {
            val message = json.decodeFromString<GameMessage>(text)
            when (message) {
                is GameMessage.LobbyUpdate -> {
                    _players.value = message.players
                    _matchDurationSeconds.value = message.matchDurationSeconds
                }
                is GameMessage.StartGame -> {
                    _isGameActive.value = true
                    _countdownTime.value = message.countdownSeconds
                    if (message.countdownSeconds == 0) {
                        _countdownTime.value = null // clear countdown overlay
                    }
                }
                is GameMessage.MatchTimerTick -> {
                    _matchTimeRemaining.value = message.secondsRemaining
                }
                is GameMessage.PlayerHit -> {
                    // Update target player health locally (pre-empt client sync)
                    _players.value = _players.value.map {
                        if (it.id == message.targetId) it.copy(health = message.currentHealth) else it
                    }
                    _hitEvent.emit(Triple(message.targetId, message.shooterId, message.currentHealth))
                }
                is GameMessage.PlayerEliminated -> {
                    _players.value = _players.value.map {
                        if (it.id == message.targetId) it.copy(isAlive = false, health = 0) else it
                    }
                    _eliminatedEvent.emit(Pair(message.targetId, message.shooterId))
                }
                is GameMessage.Respawned -> {
                    _players.value = _players.value.map {
                        if (it.id == message.playerId) it.copy(isAlive = true, health = 100) else it
                    }
                    _respawnedEvent.emit(message.playerId)
                }
                is GameMessage.GameOver -> {
                    _finalScores.value = message.finalScores
                    _matchTimeRemaining.value = null
                    _isGameActive.value = false
                }
                else -> { /* Heartbeats, etc */ }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun toggleReady(ready: Boolean) {
        clientScope.launch {
            sendMessage(GameMessage.ToggleReady(playerId, ready))
        }
    }

    fun configureMatch(durationSeconds: Int) {
        clientScope.launch {
            sendMessage(GameMessage.ConfigureMatch(durationSeconds))
        }
    }

    fun shoot(targetId: String) {
        clientScope.launch {
            sendMessage(GameMessage.ActionShoot(playerId, targetId))
        }
    }

    private suspend fun sendMessage(message: GameMessage) {
        val sessionRef = session
        if (sessionRef != null && _connectionState.value == ConnectionState.Connected) {
            try {
                val jsonStr = json.encodeToString(GameMessage.serializer(), message)
                sessionRef.send(Frame.Text(jsonStr))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun disconnect() {
        val wasGameActive = _isGameActive.value
        val currentPlayers = _players.value

        _isGameActive.value = false
        _matchDurationSeconds.value = 600
        _connectionState.value = ConnectionState.Disconnected
        _players.value = emptyList()
        _countdownTime.value = null
        _matchTimeRemaining.value = null
        
        if (wasGameActive && currentPlayers.isNotEmpty()) {
            _finalScores.value = currentPlayers.map { PlayerScore(name = it.name, score = it.score) }
                .sortedByDescending { it.score }
        } else {
            _finalScores.value = null
        }
        
        clientScope.launch {
            try {
                session?.close()
            } catch (e: Exception) {}
            session = null
            
            try {
                client?.close()
            } catch (e: Exception) {}
            client = null
        }
    }
}
