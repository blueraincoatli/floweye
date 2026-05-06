package com.gazeinteraction.coordinator

import org.junit.Test
import org.junit.Assert.*

class GazeInterpreterTest {
    @Test
    fun `no action when not gazing`() {
        val gi = GazeInterpreter(selectSec = 1.5f, skipSec = 0.5f)
        assertEquals("none", gi.evaluate("yes", false, 0f, 0L))
    }

    @Test
    fun `select action for yes with long gaze`() {
        val gi = GazeInterpreter(selectSec = 1.5f, skipSec = 0.5f)
        val now = System.currentTimeMillis()
        assertEquals("select", gi.evaluate("yes", true, 0.8f, now - 1600))
    }

    @Test
    fun `skip action for no with short gaze`() {
        val gi = GazeInterpreter(selectSec = 1.5f, skipSec = 0.5f)
        val now = System.currentTimeMillis()
        assertEquals("skip", gi.evaluate("no", true, 0.8f, now - 600))
    }

    @Test
    fun `hesitate for yes with very short gaze`() {
        val gi = GazeInterpreter(selectSec = 1.5f, skipSec = 0.5f)
        val now = System.currentTimeMillis()
        assertEquals("hesitate", gi.evaluate("yes", true, 0.8f, now - 500))
    }

    @Test
    fun `low confidence uncalibrated device still responds`() {
        val gi = GazeInterpreter(selectSec = 1.5f, skipSec = 0.5f)
        gi.isCalibrated = false
        val now = System.currentTimeMillis()
        assertEquals("select", gi.evaluate("yes", true, 0.3f, now - 2000))
    }

    @Test
    fun `evaluateWake returns true after sufficient gaze`() {
        val gi = GazeInterpreter(wakeSec = 3.0f)
        val now = System.currentTimeMillis()
        assertTrue(gi.evaluateWake(true, 0.9f, now - 3500))
    }

    @Test
    fun `evaluateWake returns false when not looking`() {
        val gi = GazeInterpreter(wakeSec = 3.0f)
        assertFalse(gi.evaluateWake(false, 0.9f, System.currentTimeMillis() - 3500))
    }
}
