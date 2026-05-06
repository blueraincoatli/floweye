package com.gazeinteraction.coordinator

import org.json.JSONObject

class CoordinatorEngine(
    menuJson: String,
    patientJson: String
) {
    enum class State { IDLE, SCAN, CONFIRM, ALERT, WAITING }

    val menuEngine = MenuEngine(menuJson)
    private val config = JSONObject(patientJson)

    @Volatile var state: State = State.IDLE
        private set

    private var confirmOption: JSONObject? = null
    private var dwellStart = System.currentTimeMillis()
    var roundCount = 0
        private set
    var onTtsRequest: ((String) -> Unit)? = null
    var onDecision: ((String, JSONObject?) -> Unit)? = null
    val currentDepth: Int get() = menuEngine.currentDepth

    fun handleAction(action: String) {
        when (action) {
            "wake" -> enterScan()
            "emergency" -> enterAlert()
            "hesitate" -> { if (state == State.SCAN) resetDwell() }
            "select" -> handleSelect()
            "confirm" -> handleConfirm()
            "skip" -> handleSkip()
            "cancel" -> handleCancel()
        }
    }

    private fun enterScan() {
        state = State.SCAN
        menuEngine.reset()
        roundCount = 0
        resetDwell()
        val opt = menuEngine.getCurrentOption()
        if (opt != null) {
            onTtsRequest?.invoke(opt.optString("tts_prompt", opt.optString("label", "")))
            onDecision?.invoke("scan_progress", opt)
        }
    }

    private fun handleSelect() {
        if (state != State.SCAN) return
        val opt = menuEngine.selectCurrent()
        if (opt != null) {
            onDecision?.invoke("action_feedback", null)
            confirmOption = opt
            state = State.CONFIRM
            resetDwell()
            onTtsRequest?.invoke("确认" + opt.optString("tts_prompt", opt.optString("label", "")))
            onDecision?.invoke("confirm", opt)
        } else {
            val sub = menuEngine.getCurrentOption()
            if (sub != null) {
                resetDwell()
                onTtsRequest?.invoke(sub.optString("tts_prompt", sub.optString("label", "")))
                onDecision?.invoke("scan_progress", sub)
            }
        }
    }

    private fun handleConfirm() {
        if (state != State.CONFIRM) return
        val opt = confirmOption ?: return
        onDecision?.invoke("action_feedback", null)
        onDecision?.invoke("selection", opt)
        onDecision?.invoke("executed", opt)
        state = State.WAITING
        resetDwell()
    }

    private fun handleSkip() {
        if (state != State.SCAN) return
        onDecision?.invoke("skip_feedback", null)
        advanceToNext()
    }

    private fun handleCancel() {
        if (state != State.CONFIRM) return
        confirmOption = null
        state = State.SCAN
        resetDwell()
        val opt = menuEngine.getCurrentOption()
        if (opt != null) {
            onDecision?.invoke("scan_progress", opt)
        }
    }

    private fun enterAlert() {
        state = State.ALERT
        onTtsRequest?.invoke("紧急呼叫")
        onDecision?.invoke("emergency", null)
        resetDwell()
    }

    fun tick() {
        val now = System.currentTimeMillis()
        when (state) {
            State.ALERT -> {
                state = State.WAITING
                resetDwell()
            }
            State.SCAN -> {
                if (now - dwellStart >= getCurrentDwell()) {
                    advanceToNext()
                }
            }
            State.CONFIRM -> {
                if (now - dwellStart >= getConfirmTimeout()) {
                    handleCancel()
                }
            }
            State.WAITING -> {
                if (now - dwellStart >= 3000) {
                    state = State.IDLE
                    onDecision?.invoke("idle", null)
                }
            }
            else -> {}
        }
    }

    private fun advanceToNext() {
        val prevIdx = menuEngine._currentIndex
        val opt = menuEngine.nextOption()
        resetDwell()
        if (opt == null) return
        val wrapped = prevIdx != 0 && menuEngine._currentIndex == 0
        if (wrapped) {
            roundCount++
            if (roundCount >= 1) {
                state = State.IDLE
                onDecision?.invoke("idle", null)
                return
            }
        }
        onTtsRequest?.invoke(opt.optString("tts_prompt", opt.optString("label", "")))
        onDecision?.invoke("scan_progress", opt)
    }

    fun dwellRemaining(): Long = getCurrentDwell() - (System.currentTimeMillis() - dwellStart)

    private fun getCurrentDwell(): Long {
        val base = config.optJSONObject("single_device")?.optLong("dwell_seconds", 10) ?: 10
        return base * 1000
    }

    private fun getConfirmTimeout(): Long = 5000L
    private fun resetDwell() { dwellStart = System.currentTimeMillis() }
}
