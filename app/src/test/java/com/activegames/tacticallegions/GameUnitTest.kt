package com.activegames.tacticallegions

import com.activegames.tacticallegions.network.GameMessage
import com.activegames.tacticallegions.network.PlayerState
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
}
