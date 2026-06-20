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
}
