package com.gazeinteraction.coordinator

class GazeInterpreter(
    private val selectSec: Float = 1.5f,
    private val skipSec: Float = 1.5f,
    private val wakeSec: Float = 2.0f,
    private val confidenceThreshold: Float = 0.45f
) {
    var isCalibrated: Boolean = true

    fun evaluate(
        role: String,
        looking: Boolean,
        confidence: Float,
        gazeStartTimeMs: Long
    ): String {
        if (!looking) return "none"
        if (confidence < confidenceThreshold && isCalibrated) return "none"
        val duration = System.currentTimeMillis() - gazeStartTimeMs

        return when (role) {
            "yes" -> when {
                duration >= selectSec * 1000 -> "select"
                duration >= 400 -> "hesitate"
                else -> "none"
            }
            "no" -> when {
                duration >= skipSec * 1000 -> "skip"
                else -> "none"
            }
            else -> "none"
        }
    }

    fun evaluateWake(
        looking: Boolean,
        confidence: Float,
        gazeStartTimeMs: Long
    ): Boolean {
        if (!looking) return false
        if (confidence < confidenceThreshold && isCalibrated) return false
        return (System.currentTimeMillis() - gazeStartTimeMs) >= wakeSec * 1000
    }
}
