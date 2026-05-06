package com.gazeinteraction.coordinator

import org.junit.Test
import org.junit.Assert.*

class MenuEngineTest {
    private val sampleMenu = """
    {
      "options": [
        {
          "id": "discomfort", "label": "不舒服",
          "tts_prompt": "您不舒服吗？",
          "submenu": [
            {"id": "headache", "label": "头疼", "tts_prompt": "您头疼吗？"},
            {"id": "stomachache", "label": "肚子疼", "tts_prompt": "您肚子疼吗？"},
            {"id": "back", "label": "返回", "action": "back"}
          ]
        },
        {
          "id": "care", "label": "护理",
          "tts_prompt": "需要身体护理吗？",
          "submenu": [
            {"id": "turn_over", "label": "想翻身", "tts_prompt": "您想翻身吗？"},
            {"id": "back", "label": "返回", "action": "back"}
          ]
        }
      ]
    }
    """

    @Test
    fun `initial state returns root categories`() {
        val engine = MenuEngine(sampleMenu)
        val options = engine.getCurrentOptions()
        assertEquals(2, options.size)
        assertEquals("不舒服", options[0]["label"])
    }

    @Test
    fun `select category enters submenu`() {
        val engine = MenuEngine(sampleMenu)
        engine.selectCurrent()
        val options = engine.getCurrentOptions()
        assertEquals(3, options.size)
        assertEquals("头疼", options[0]["label"])
    }

    @Test
    fun `back returns to root`() {
        val engine = MenuEngine(sampleMenu)
        engine.selectCurrent()
        engine.goBack()
        assertEquals(2, engine.getCurrentOptions().size)
    }

    @Test
    fun `select leaf returns option`() {
        val engine = MenuEngine(sampleMenu)
        engine.selectCurrent()
        val option = engine.selectCurrent()
        assertNotNull(option)
        assertEquals("头疼", option?.get("label"))
    }

    @Test
    fun `back action option triggers goBack`() {
        val engine = MenuEngine(sampleMenu)
        engine.selectCurrent()
        engine.setCurrentIndex(2)
        engine.selectCurrent()
        assertEquals(2, engine.getCurrentOptions().size)
    }

    @Test
    fun `reset returns to root`() {
        val engine = MenuEngine(sampleMenu)
        engine.selectCurrent()
        engine.selectCurrent()
        engine.reset()
        assertEquals(2, engine.getCurrentOptions().size)
    }
}
