package com.gazeinteraction.ui

import org.junit.Test
import org.junit.Assert.*

class ThemeConfigTest {

    @Test
    fun `warm healing theme has correct values`() {
        val t = ThemeConfig.WARM_HEALING
        assertEquals("温暖疗愈", t.name)
        assertEquals(0xFF1C1917.toInt(), t.bgColor)
        assertEquals(0xFF6BA87A.toInt(), t.haloYes)
        assertEquals(0xFFC47A6E.toInt(), t.haloNo)
        assertEquals(0xFFF5F0E8.toInt(), t.textPrimary)
        assertEquals(16f, t.haloBlurDp)
        assertEquals(2500L, t.pulsePeriodMs)
    }

    @Test
    fun `high contrast theme has correct values`() {
        val t = ThemeConfig.HIGH_CONTRAST
        assertEquals("高对比功能", t.name)
        assertEquals(0xFF000000.toInt(), t.bgColor)
        assertEquals(0xFF00FF66.toInt(), t.haloYes)
        assertEquals(0xFFFF3333.toInt(), t.haloNo)
        assertEquals(0xFFFFFFFF.toInt(), t.textPrimary)
        assertEquals(4f, t.haloBlurDp)
        assertEquals(1500L, t.pulsePeriodMs)
    }

    @Test
    fun `modern minimal theme has correct values`() {
        val t = ThemeConfig.MODERN_MINIMAL
        assertEquals("现代简约", t.name)
        assertEquals(0xFFF5F5F0.toInt(), t.bgColor)
        assertEquals(0xFF4A7FD9.toInt(), t.haloYes)
        assertEquals(0xFF7A8B9E.toInt(), t.haloNo)
        assertEquals(0xFF1A1A1A.toInt(), t.textPrimary)
        assertEquals(8f, t.haloBlurDp)
        assertEquals(2000L, t.pulsePeriodMs)
    }

    @Test
    fun `ALL contains exactly three themes`() {
        assertEquals(3, ThemeConfig.ALL.size)
        assertTrue(ThemeConfig.ALL.contains(ThemeConfig.WARM_HEALING))
        assertTrue(ThemeConfig.ALL.contains(ThemeConfig.HIGH_CONTRAST))
        assertTrue(ThemeConfig.ALL.contains(ThemeConfig.MODERN_MINIMAL))
    }

    @Test
    fun `haloColorFor returns correct color based on role`() {
        val t = ThemeConfig.WARM_HEALING
        assertEquals(0xFF6BA87A.toInt(), t.haloColorFor("yes"))
        assertEquals(0xFFC47A6E.toInt(), t.haloColorFor("no"))
    }

    @Test
    fun `byName returns correct theme`() {
        assertEquals(ThemeConfig.WARM_HEALING, ThemeConfig.byName("温暖疗愈"))
        assertEquals(ThemeConfig.HIGH_CONTRAST, ThemeConfig.byName("高对比功能"))
        assertEquals(ThemeConfig.MODERN_MINIMAL, ThemeConfig.byName("现代简约"))
        assertEquals(ThemeConfig.WARM_HEALING, ThemeConfig.byName("nonexistent"))
    }
}
