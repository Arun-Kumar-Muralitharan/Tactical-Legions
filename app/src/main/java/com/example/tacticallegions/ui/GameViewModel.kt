package com.example.tacticallegions.ui

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.tacticallegions.network.ConnectionState
import com.example.tacticallegions.network.GameClient
import com.example.tacticallegions.network.GameServer
import com.example.tacticallegions.util.SoundHapticHelper
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class GameViewModel(application: Application) : AndroidViewModel(application) {
    val client = GameClient()
    private var server: GameServer? = null
    val soundHaptic = SoundHapticHelper(application)

    var isHost = mutableStateOf(false)
        private set

    var localIp = mutableStateOf("127.0.0.1")
        private set

    var isTargetInCrosshair = mutableStateOf(false)
        private set

    var successfulHitCount = mutableStateOf(0)
        private set

    private var lastLockStatus = false

    init {
        // Collect real-time events to play feedback sounds/vibrations
        viewModelScope.launch {
            client.hitEvent.collect { (targetId, shooterId, health) ->
                if (targetId == client.playerId) {
                    // Local player took damage
                    soundHaptic.playMissSound() // Warning tone
                    soundHaptic.vibrateHit()
                } else if (shooterId == client.playerId) {
                    // Local player hit someone
                    soundHaptic.playHitConfirmSound()
                    successfulHitCount.value += 1
                }
            }
        }

        viewModelScope.launch {
            client.eliminatedEvent.collect { (targetId, shooterId) ->
                if (targetId == client.playerId) {
                    // Local player eliminated
                    soundHaptic.playEliminationSound()
                    soundHaptic.vibrateEliminated()
                }
            }
        }

        viewModelScope.launch {
            client.respawnedEvent.collect { pId ->
                if (pId == client.playerId) {
                    soundHaptic.stopVibration()
                }
            }
        }

        viewModelScope.launch {
            client.countdownTime.collectLatest { countdown ->
                if (countdown != null && countdown > 0) {
                    soundHaptic.playCountdownSound()
                } else if (countdown == 0) {
                    soundHaptic.playRoundStartSound()
                }
            }
        }
    }

    fun startHost(nickname: String, port: Int = 8080) {
        viewModelScope.launch {
            isHost.value = true
            val activeServer = GameServer()
            server = activeServer
            activeServer.start(port)
            
            // Wait brief moment for server bind
            kotlinx.coroutines.delay(500)
            localIp.value = activeServer.serverIp.value

            client.connect(host = "127.0.0.1", port = port, nickname = nickname)
        }
    }

    fun joinGame(hostIp: String, nickname: String, port: Int = 8080) {
        isHost.value = false
        client.connect(host = hostIp, port = port, nickname = nickname)
    }

    fun toggleReady(ready: Boolean) {
        client.toggleReady(ready)
    }

    fun setTargetStatus(inCrosshair: Boolean) {
        if (inCrosshair != lastLockStatus) {
            lastLockStatus = inCrosshair
            isTargetInCrosshair.value = inCrosshair
            if (inCrosshair) {
                // Short lock-on haptic pulse and sound
                soundHaptic.vibrateLock()
                soundHaptic.playTargetLockSound()
            }
        }
    }

    fun triggerShoot() {
        soundHaptic.playShootSound()
        soundHaptic.vibrateShoot()
    }

    fun confirmHit(targetId: String) {
        client.shoot(targetId)
    }

    fun disconnect() {
        soundHaptic.stopVibration()
        client.disconnect()
        server?.stop()
        server = null
        isHost.value = false
        localIp.value = "127.0.0.1"
    }

    override fun onCleared() {
        super.onCleared()
        disconnect()
        soundHaptic.release()
    }
}
