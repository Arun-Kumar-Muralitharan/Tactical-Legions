package com.activegames.tacticallegions

import com.activegames.tacticallegions.network.GameMessage
import com.activegames.tacticallegions.network.PlayerState
import com.activegames.tacticallegions.network.PowerUpType
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameUnitTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testGameMessageJoinSerialization() {
        val originalMessage = GameMessage.Join(nickname = "LaserSniper", playerId = "uuid-12345")
        
        // Serialize
        val jsonString = json.encodeToString(GameMessage.serializer(), originalMessage)
        
        // Deserialize
        val decodedMessage = json.decodeFromString<GameMessage>(jsonString)
        
        assertTrue(decodedMessage is GameMessage.Join)
        val joinMsg = decodedMessage as GameMessage.Join
        assertEquals("LaserSniper", joinMsg.nickname)
        assertEquals("uuid-12345", joinMsg.playerId)
    }

    @Test
    fun testGameMessagePlayerHitSerialization() {
        val originalMessage = GameMessage.PlayerHit(
            targetId = "target-555",
            shooterId = "shooter-888",
            damage = 34,
            currentHealth = 66
        )

        // Serialize
        val jsonString = json.encodeToString(GameMessage.serializer(), originalMessage)

        // Deserialize
        val decodedMessage = json.decodeFromString<GameMessage>(jsonString)

        assertTrue(decodedMessage is GameMessage.PlayerHit)
        val hitMsg = decodedMessage as GameMessage.PlayerHit
        assertEquals("target-555", hitMsg.targetId)
        assertEquals("shooter-888", hitMsg.shooterId)
        assertEquals(34, hitMsg.damage)
        assertEquals(66, hitMsg.currentHealth)
    }

    @Test
    fun testFaceTargetingMath() {
        // Mock dimensions of camera frame
        val imageWidth = 400
        val imageHeight = 300
        val centerX = imageWidth / 2 // 200
        val centerY = imageHeight / 2 // 150

        // Helper containing standard android.graphics.Rect.contains(x, y) logical check:
        // rect.left <= x && rect.right >= x && rect.top <= y && rect.bottom >= y
        fun rectContains(left: Int, top: Int, right: Int, bottom: Int, x: Int, y: Int): Boolean {
            return x in left..right && y in top..bottom
        }

        // Case 1: Face centered in crosshairs
        // Face occupies area (100, 80) to (300, 220). Center (200, 150) is inside.
        assertTrue(
            rectContains(
                left = 100,
                top = 80,
                right = 300,
                bottom = 220,
                x = centerX,
                y = centerY
            )
        )

        // Case 2: Face in the corner, not centered
        // Face occupies area (0, 0) to (100, 80). Center (200, 150) is outside.
        assertFalse(
            rectContains(
                left = 0,
                top = 0,
                right = 100,
                bottom = 80,
                x = centerX,
                y = centerY
            )
        )

        // Case 3: Face partially overlapping, but center crosshair is missed
        // Face occupies area (220, 160) to (320, 260). Center (200, 150) is outside.
        assertFalse(
            rectContains(
                left = 220,
                top = 160,
                right = 320,
                bottom = 260,
                x = centerX,
                y = centerY
            )
        )
    }

    @Test
    fun testFaceSignatureMatching() {
        val sig1 = listOf(0.8f, 0.8f, 1.2f, 1.2f, 0.5f, 0.5f, 0.6f, 0.8f, 0.8f, 1.3f)
        // Highly similar face (slightly different ratios due to movement/noise)
        val sigSimilar = listOf(0.81f, 0.79f, 1.22f, 1.18f, 0.51f, 0.49f, 0.61f, 0.81f, 0.79f, 1.31f)
        // Completely different face structure
        val sigDifferent = listOf(1.5f, 0.4f, 2.2f, 0.8f, 0.9f, 0.2f, 1.1f, 1.9f, 0.3f, 2.5f)

        val diffSimilar = com.activegames.tacticallegions.camera.FaceSignatureHelper.calculateDifference(sig1, sigSimilar)
        val diffDifferent = com.activegames.tacticallegions.camera.FaceSignatureHelper.calculateDifference(sig1, sigDifferent)

        // Similar faces must be well within our match threshold of 0.08
        assertTrue("Similar faces difference score ($diffSimilar) should be low", diffSimilar < 0.01f)
        assertTrue("Similar faces difference score should be below matching threshold", diffSimilar <= 0.08f)

        // Different faces must be far above the threshold
        assertTrue("Different faces difference score ($diffDifferent) should be high", diffDifferent > 0.5f)
        assertTrue("Different faces difference score should exceed matching threshold", diffDifferent > 0.08f)
    }

    @Test
    fun testFaceCoveringAndTooCloseLogic() {
        val frameWidth = 640
        val frameHeight = 480

        fun isTooClose(boxWidth: Int, boxHeight: Int): Boolean {
            val widthRatio = boxWidth.toFloat() / frameWidth.toFloat()
            val heightRatio = boxHeight.toFloat() / frameHeight.toFloat()
            val areaRatio = (boxWidth.toFloat() * boxHeight.toFloat()) / (frameWidth.toFloat() * frameHeight.toFloat())
            return widthRatio >= 0.8f || heightRatio >= 0.8f || areaRatio >= 0.8f
        }

        // Case 1: Normal face size (e.g., 200x200)
        assertFalse(isTooClose(200, 200))

        // Case 2: Face covering 80% or more of width (e.g., 520x200)
        assertTrue(isTooClose(520, 200))

        // Case 3: Face covering 80% or more of height (e.g., 200x390)
        assertTrue(isTooClose(200, 390))

        // Case 4: Face covering 80% or more of area
        assertTrue(isTooClose(520, 400))
    }

    @Test
    fun testPowerUpSerialization() {
        val originalMessage = GameMessage.ActivatePowerUp(playerId = "player-1", powerUp = PowerUpType.AUTO_GUN)
        val jsonString = json.encodeToString(GameMessage.serializer(), originalMessage)
        val decodedMessage = json.decodeFromString<GameMessage>(jsonString)
        
        assertTrue(decodedMessage is GameMessage.ActivatePowerUp)
        val activeMsg = decodedMessage as GameMessage.ActivatePowerUp
        assertEquals("player-1", activeMsg.playerId)
        assertEquals(PowerUpType.AUTO_GUN, activeMsg.powerUp)
    }

    @Test
    fun testPowerUpGameplayLogic() {
        var player = PlayerState(
            id = "player-1",
            name = "Test",
            isReady = true,
            isAlive = true,
            health = 100,
            score = 0
        )
        
        var newHealth = (player.health + 300).coerceAtMost(400)
        player = player.copy(activePowerUp = PowerUpType.HEALTH_BOOST, health = newHealth)
        assertEquals(400, player.health)
        assertEquals(PowerUpType.HEALTH_BOOST, player.activePowerUp)

        player = player.copy(activePowerUp = null, health = player.health.coerceAtMost(100))
        assertEquals(100, player.health)
        assertEquals(null, player.activePowerUp)

        val normalShooter = PlayerState(
            id = "shooter-1", name = "Shooter", isReady = true, isAlive = true, health = 100, score = 0
        )
        val oneShotShooter = normalShooter.copy(activePowerUp = PowerUpType.ONE_SHOT_KILL)

        val damageNormal = if (normalShooter.activePowerUp == PowerUpType.ONE_SHOT_KILL) 200 else 34
        val damageOneShot = if (oneShotShooter.activePowerUp == PowerUpType.ONE_SHOT_KILL) 200 else 34

        assertEquals(34, damageNormal)
        assertEquals(200, damageOneShot)
    }

    @Test
    fun testPowerUpCountLimit() {
        val playerPowerUpCounts = mutableMapOf<String, Int>()
        
        fun handleActivatePowerUp(playerId: String): Boolean {
            val count = playerPowerUpCounts[playerId] ?: 0
            if (count >= 2) return false
            playerPowerUpCounts[playerId] = count + 1
            return true
        }

        assertTrue(handleActivatePowerUp("player-1"))
        assertEquals(1, playerPowerUpCounts["player-1"])

        assertTrue(handleActivatePowerUp("player-1"))
        assertEquals(2, playerPowerUpCounts["player-1"])

        assertFalse(handleActivatePowerUp("player-1"))
        assertEquals(2, playerPowerUpCounts["player-1"])
    }

    @Test
    fun testOneShotCooldown() {
        val lastShotTimes = mutableMapOf<String, Long>()

        fun canShoot(playerId: String, now: Long): Boolean {
            val lastShot = lastShotTimes[playerId]
            if (lastShot != null && now - lastShot < 2000L) {
                return false
            }
            lastShotTimes[playerId] = now
            return true
        }

        assertTrue(canShoot("player-1", 1000L))
        assertFalse(canShoot("player-1", 2000L))
        assertTrue(canShoot("player-1", 3100L))
    }

    @Test
    fun testPowerUpMatchDurationRequirement() {
        fun isPowerUpAvailable(durationSeconds: Int): Boolean {
            return durationSeconds >= 180
        }

        assertFalse(isPowerUpAvailable(60))
        assertFalse(isPowerUpAvailable(120))
        assertTrue(isPowerUpAvailable(180))
        assertTrue(isPowerUpAvailable(600))
    }

    @Test
    fun testFirstPowerUpSpawningDelay() {
        val delays = List(100) {
            kotlin.random.Random.nextLong(30000, 40000)
        }
        delays.forEach { delay ->
            assertTrue(delay in 30000L..40000L)
        }
    }

    @Test
    fun testSpawningLimit() {
        var spawnedCount = 0
        fun spawnPowerUp() {
            if (spawnedCount < 2) {
                spawnedCount++
            }
        }

        spawnPowerUp()
        spawnPowerUp()
        spawnPowerUp()
        spawnPowerUp()

        assertEquals(2, spawnedCount)
    }
}
