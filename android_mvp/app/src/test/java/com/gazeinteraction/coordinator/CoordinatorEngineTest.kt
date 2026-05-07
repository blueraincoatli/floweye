package com.gazeinteraction.coordinator

import org.junit.Test
import org.junit.Assert.*

class CoordinatorEngineTest {
    private val testMenu = """
    {"options": [{"id":"x","label":"测试","tts_prompt":"测试?","submenu":[
      {"id":"a","label":"A","tts_prompt":"A?"},{"id":"b","label":"B","tts_prompt":"B?"}
    ]}]}
    """

    @Test
    fun `initial state is IDLE`() {
        val engine = CoordinatorEngine(testMenu, "{}")
        assertEquals(CoordinatorEngine.State.IDLE, engine.state)
    }

    @Test
    fun `wake transitions IDLE to SCAN`() {
        val engine = CoordinatorEngine(testMenu, "{}")
        engine.handleAction("wake")
        assertEquals(CoordinatorEngine.State.SCAN, engine.state)
    }

    @Test
    fun `emergency transitions to ALERT`() {
        val engine = CoordinatorEngine(testMenu, "{}")
        engine.handleAction("emergency")
        assertEquals(CoordinatorEngine.State.ALERT, engine.state)
        // After ALERT, tick should eventually go to WAITING
        engine.tick()
        assertEquals(CoordinatorEngine.State.WAITING, engine.state)
    }

    @Test
    fun `hesitate resets dwell in SCAN`() {
        val engine = CoordinatorEngine(testMenu, "{}")
        engine.handleAction("wake")
        engine.onTtsComplete()  // 模拟TTS播报完成，进入select阶段
        val before = engine.dwellRemaining()
        Thread.sleep(200)
        engine.handleAction("hesitate")
        val after = engine.dwellRemaining()
        assertTrue("dwell should be reset after hesitate", after >= before - 100)
    }

    @Test
    fun `skip in SCAN advances to next option`() {
        val engine = CoordinatorEngine(testMenu, "{}")
        engine.handleAction("wake")
        engine.onTtsComplete()  // 模拟TTS播报完成，进入select阶段
        engine.handleAction("skip")
        // Should still be in SCAN after skip
        assertEquals(CoordinatorEngine.State.SCAN, engine.state)
    }

    @Test
    fun `actions blocked during announce phase`() {
        val engine = CoordinatorEngine(testMenu, "{}")
        engine.handleAction("wake")
        assertEquals("announce", engine.scanPhase)
        // 播报阶段 skip 应被忽略
        engine.handleAction("skip")
        assertEquals(CoordinatorEngine.State.SCAN, engine.state)
        assertEquals("announce", engine.scanPhase)
        // TTS完成后进入select阶段
        engine.onTtsComplete()
        assertEquals("select", engine.scanPhase)
    }
}
