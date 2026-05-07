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
    private var idleSince = 0L  // 初始为0让首次唤醒不经过冷却期
    var roundCount = 0
        private set
    var onTtsRequest: ((String) -> Unit)? = null
    var onDecision: ((String, JSONObject?) -> Unit)? = null
    val currentDepth: Int get() = menuEngine.currentDepth

    // Two-phase scan: "announce" = TTS播报中(只显示选项文字), "select" = 等待用户选择(显示是/否)
    @Volatile var scanPhase: String = "select"
        private set
    private var scanGeneration: Int = 0

    fun handleAction(action: String) {
        // 播报阶段忽略所有注视动作，避免误触发
        // 仅在 SCAN 状态下生效：CONFIRM/IDLE 等状态不应被播报阶段阻挡
        if (state == State.SCAN && scanPhase == "announce") return
        when (action) {
            "wake" -> {
                // IDLE冷却期：执行完或超时回IDLE后至少等5秒才接受新唤醒
                if (state == State.IDLE &&
                    System.currentTimeMillis() - idleSince < 5000) return
                enterScan()
            }
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
        scanGeneration++
        onDecision?.invoke("transition", null)
        val opt = menuEngine.getCurrentOption()
        if (opt != null) {
            startAnnounce(opt)
        }
    }

    private fun startAnnounce(opt: JSONObject) {
        scanPhase = "announce"
        onDecision?.invoke("announce", opt)
        onTtsRequest?.invoke(opt.optString("tts_prompt", opt.optString("label", "")))
    }

    fun onTtsComplete() {
        if (state != State.SCAN || scanPhase != "announce") return
        scanPhase = "select"
        resetDwell()
        val opt = menuEngine.getCurrentOption()
        if (opt != null) {
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
            // 进入子菜单，播报第一个选项
            val sub = menuEngine.getCurrentOption()
            if (sub != null) {
                resetDwell()
                scanGeneration++
                startAnnounce(sub)
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
        scanGeneration++
        val opt = menuEngine.getCurrentOption()
        if (opt != null) {
            startAnnounce(opt)
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
                if (scanPhase != "announce" && now - dwellStart >= getCurrentDwell()) {
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
                    idleSince = now
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
                idleSince = System.currentTimeMillis()
                onDecision?.invoke("idle", null)
                return
            }
        }
        scanGeneration++
        startAnnounce(opt)
    }

    fun dwellRemaining(): Long = getCurrentDwell() - (System.currentTimeMillis() - dwellStart)

    private fun getCurrentDwell(): Long {
        val base = config.optJSONObject("single_device")?.optLong("dwell_seconds", 10) ?: 10
        return base * 1000
    }

    private fun getConfirmTimeout(): Long = 5000L
    private fun resetDwell() { dwellStart = System.currentTimeMillis() }
}
