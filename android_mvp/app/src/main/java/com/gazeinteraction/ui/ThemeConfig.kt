package com.gazeinteraction.ui

data class ThemeConfig(
    val name: String,
    val bgColor: Int,
    val haloYes: Int,
    val haloNo: Int,
    val textPrimary: Int,
    val haloBlurDp: Float,
    val pulsePeriodMs: Long
) {
    fun haloColorFor(role: String): Int = when (role) {
        "yes" -> haloYes
        "no" -> haloNo
        else -> haloYes
    }

    companion object {
        val WARM_HEALING = ThemeConfig(
            name = "温暖疗愈",
            bgColor = 0xFF1C1917.toInt(),
            haloYes = 0xFF6BA87A.toInt(),
            haloNo = 0xFFC47A6E.toInt(),
            textPrimary = 0xFFF5F0E8.toInt(),
            haloBlurDp = 16f,
            pulsePeriodMs = 2500L
        )

        val HIGH_CONTRAST = ThemeConfig(
            name = "高对比功能",
            bgColor = 0xFF000000.toInt(),
            haloYes = 0xFF00FF66.toInt(),
            haloNo = 0xFFFF3333.toInt(),
            textPrimary = 0xFFFFFFFF.toInt(),
            haloBlurDp = 4f,
            pulsePeriodMs = 1500L
        )

        val MODERN_MINIMAL = ThemeConfig(
            name = "现代简约",
            bgColor = 0xFFF5F5F0.toInt(),
            haloYes = 0xFF4A7FD9.toInt(),
            haloNo = 0xFF7A8B9E.toInt(),
            textPrimary = 0xFF1A1A1A.toInt(),
            haloBlurDp = 8f,
            pulsePeriodMs = 2000L
        )

        val ALL = listOf(WARM_HEALING, HIGH_CONTRAST, MODERN_MINIMAL)

        fun byName(name: String): ThemeConfig =
            ALL.find { it.name == name } ?: WARM_HEALING
    }
}
