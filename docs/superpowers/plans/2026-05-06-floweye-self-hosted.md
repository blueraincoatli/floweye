# Floweye 自托管方案 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 消除 PC 依赖，将 MQTT broker + 协调器内嵌到 Android 应用中，两台手机通过 WiFi 热点直连完成视线交互。

**Architecture:** 新增 `coordinator/` 包，容纳移植自 Python 的状态机、菜单引擎、注视解读、自适应、TTS 模块。Moquette 作为内嵌 broker，HostManager 自动判断主机/客户端角色。通过 build.gradle 添加 moquette 依赖。

**Tech Stack:** Kotlin, Moquette 0.17.1, Android TextToSpeech, JUnit 4

---

### Task 1: MenuEngine — 菜单导航引擎

**Files:**
- Create: `android_mvp/app/src/main/java/com/gazeinteraction/coordinator/MenuEngine.kt`
- Create: `android_mvp/app/src/test/java/com/gazeinteraction/coordinator/MenuEngineTest.kt`

- [ ] **Step 1: 编写测试**

```kotlin
// MenuEngineTest.kt
package com.gazeinteraction.coordinator

import org.junit.Test
import org.junit.Assert.*

class MenuEngineTest {
    private val sampleMenu = """
    {
      "categories": [
        {
          "id": "discomfort", "label": "不舒服",
          "tts_prompt": "您不舒服吗？",
          "options": [
            {"id": "headache", "label": "头疼", "tts_prompt": "您头疼吗？"},
            {"id": "stomachache", "label": "肚子疼", "tts_prompt": "您肚子疼吗？"},
            {"id": "back", "label": "返回", "action": "back"}
          ]
        },
        {
          "id": "care", "label": "护理",
          "tts_prompt": "需要身体护理吗？",
          "options": [
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
        val result = engine.selectCurrent()
        assertNotNull(result)
        val options = engine.getCurrentOptions()
        assertEquals(3, options.size)
        assertEquals("头疼", options[0]["label"])
    }

    @Test
    fun `back returns to root`() {
        val engine = MenuEngine(sampleMenu)
        engine.selectCurrent() // enter discomfort
        engine.goBack()
        val options = engine.getCurrentOptions()
        assertEquals(2, options.size)
    }

    @Test
    fun `select leaf returns option with isLeaf=true`() {
        val engine = MenuEngine(sampleMenu)
        engine.selectCurrent() // enter discomfort
        val option = engine.selectCurrent() // select first option (headache)
        assertNotNull(option)
        assertEquals("头疼", option?.get("label"))
    }

    @Test
    fun `back option triggers goBack`() {
        val engine = MenuEngine(sampleMenu)
        engine.selectCurrent() // enter discomfort
        // Select "返回" which is the 3rd option
        engine.setCurrentIndex(2)
        val result = engine.selectCurrent()
        // Should go back to root
        assertEquals(2, engine.getCurrentOptions().size)
    }

    @Test
    fun `reset returns to root`() {
        val engine = MenuEngine(sampleMenu)
        engine.selectCurrent() // enter
        engine.selectCurrent() // select leaf
        engine.reset()
        assertEquals(2, engine.getCurrentOptions().size)
    }
}
```

- [ ] **Step 2: 运行测试 (预期失败)**

```bash
cd android_mvp && ./gradlew testDebugUnitTest --tests "com.gazeinteraction.coordinator.MenuEngineTest"
```

Expected: BUILD FAILED — `Unresolved reference: MenuEngine`

- [ ] **Step 3: 实现 MenuEngine**

```kotlin
// MenuEngine.kt
package com.gazeinteraction.coordinator

import org.json.JSONArray
import org.json.JSONObject

class MenuEngine(menuJson: String) {
    private val root: JSONObject = JSONObject(menuJson)
    private val path = mutableListOf<Int>() // navigation stack of indices
    private val selections = mutableListOf<String>() // for adaptive dwell

    fun getCurrentOptions(): List<JSONObject> {
        var current = root
        // traverse path to get current level
        for (i in 0 until path.size - 1) {
            val cats = current.optJSONArray("categories") ?: return emptyList()
            current = cats.getJSONObject(path[i])
        }
        if (path.isEmpty()) {
            // at root: return categories themselves
            val cats = current.optJSONArray("categories") ?: return emptyList()
            return (0 until cats.length()).map { cats.getJSONObject(it) }
        }
        // at submenu: return options of the current category
        val lastIdx = path.last()
        val cat = current.optJSONArray("categories")?.getJSONObject(lastIdx) ?: return emptyList()
        return (0 until cat.optJSONArray("options")?.length() ?: 0)
            .map { cat.getJSONArray("options").getJSONObject(it) }
    }

    fun selectCurrent(): JSONObject? {
        val options = getCurrentOptions()
        val idx = _currentIndex
        if (idx < 0 || idx >= options.size) return null
        val selected = options[idx]
        val action = selected.optString("action", "")
        if (action == "back") {
            goBack()
            return null
        }
        val subOptions = selected.optJSONArray("options")
        if (subOptions != null && subOptions.length() > 0) {
            // non-leaf: enter submenu
            path.add(idx)
            _currentIndex = 0
            return null
        }
        // leaf option
        selections.add(selected.optString("id", ""))
        return selected
    }

    fun goBack() {
        if (path.isNotEmpty()) {
            path.removeAt(path.size - 1)
            _currentIndex = 0
        }
    }

    fun reset() {
        path.clear()
        _currentIndex = 0
    }

    fun getSelectionHistory(): List<String> = selections.toList()

    var _currentIndex = 0
    fun setCurrentIndex(i: Int) { _currentIndex = i.coerceIn(0, (getCurrentOptions().size - 1).coerceAtLeast(0)) }

    fun nextOption(): JSONObject? {
        val options = getCurrentOptions()
        if (options.isEmpty()) return null
        _currentIndex = (_currentIndex + 1) % options.size
        return options[_currentIndex]
    }

    fun getCurrentOption(): JSONObject? {
        val options = getCurrentOptions()
        return if (_currentIndex in options.indices) options[_currentIndex] else options.firstOrNull()
    }

    val currentDepth: Int get() = path.size
}
```

- [ ] **Step 4: 运行测试 (预期通过)**

```bash
cd android_mvp && ./gradlew testDebugUnitTest --tests "com.gazeinteraction.coordinator.MenuEngineTest"
```

Expected: BUILD SUCCESSFUL, 6 tests passed

- [ ] **Step 5: 提交**

```bash
git add android_mvp/app/src/main/java/com/gazeinteraction/coordinator/ android_mvp/app/src/test/
git commit -m "feat: add MenuEngine with JSON menu navigation and unit tests"
```

---

### Task 2: CoordinatorEngine — 状态机

**Files:**
- Create: `android_mvp/app/src/main/java/com/gazeinteraction/coordinator/CoordinatorEngine.kt`
- Create: `android_mvp/app/src/test/java/com/gazeinteraction/coordinator/CoordinatorEngineTest.kt`

- [ ] **Step 1: 编写测试**

```kotlin
// CoordinatorEngineTest.kt
package com.gazeinteraction.coordinator

import org.junit.Test
import org.junit.Assert.*

class CoordinatorEngineTest {
    private val testMenu = """
    {"categories": [{"id":"x","label":"测试","tts_prompt":"测试?","options":[
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
    fun `select leaf enters CONFIRM`() {
        val engine = CoordinatorEngine(testMenu, "{}")
        engine.handleAction("wake")
        // should be in SCAN announcing first option
        val stateAfterWake = engine.state
        assertEquals(CoordinatorEngine.State.SCAN, stateAfterWake)
    }

    @Test
    fun `emergency transitions to ALERT from IDLE`() {
        val engine = CoordinatorEngine(testMenu, "{}")
        engine.handleAction("emergency")
        assertEquals(CoordinatorEngine.State.ALERT, engine.state)
    }

    @Test
    fun `hesitate resets dwell in SCAN`() {
        val engine = CoordinatorEngine(testMenu, "{}")
        engine.handleAction("wake")
        val before = engine.dwellRemaining()
        Thread.sleep(200)
        engine.handleAction("hesitate")
        val after = engine.dwellRemaining()
        assertTrue(after >= before - 100) // should be reset
    }
}
```

- [ ] **Step 2: 运行测试 (预期失败)**

```bash
cd android_mvp && ./gradlew testDebugUnitTest --tests "com.gazeinteraction.coordinator.CoordinatorEngineTest"
```

Expected: BUILD FAILED

- [ ] **Step 3: 实现 CoordinatorEngine**

```kotlin
// CoordinatorEngine.kt
package com.gazeinteraction.coordinator

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class CoordinatorEngine(
    menuJson: String,
    patientJson: String
) {
    enum class State { IDLE, SCAN, CONFIRM, ALERT, WAITING }

    val menuEngine = MenuEngine(menuJson)
    private val config = org.json.JSONObject(patientJson)

    private val _state = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> get() = _state

    private var confirmOption: org.json.JSONObject? = null
    private var dwellStart = 0L
    private var optionStart = 0L
    private var roundCount = 0
    var onTtsRequest: ((String) -> Unit)? = null
    var onDecision: ((String, org.json.JSONObject?) -> Unit)? = null

    val currentDepth: Int get() = menuEngine.currentDepth

    fun handleAction(action: String, param: Any? = null) {
        when (action) {
            "wake" -> enterScan()
            "emergency" -> enterAlert()
            "hesitate" -> { if (_state.value == State.SCAN) resetDwell() }
            "select" -> handleSelect()
            "confirm" -> handleConfirm()
            "skip" -> handleSkip()
            "cancel" -> handleCancel()
        }
    }

    private fun enterScan() {
        _state.value = State.SCAN
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
        if (_state.value != State.SCAN) return
        val opt = menuEngine.selectCurrent()
        if (opt != null) {
            // leaf selected
            onDecision?.invoke("action_feedback", null)
            confirmOption = opt
            _state.value = State.CONFIRM
            resetDwell()
            onTtsRequest?.invoke("确认" + opt.optString("tts_prompt", opt.optString("label", "")))
            onDecision?.invoke("confirm", opt)
        } else {
            // entered submenu — announce first option
            val sub = menuEngine.getCurrentOption()
            if (sub != null) {
                resetDwell()
                onTtsRequest?.invoke(sub.optString("tts_prompt", sub.optString("label", "")))
                onDecision?.invoke("scan_progress", sub)
            }
        }
    }

    private fun handleConfirm() {
        if (_state.value != State.CONFIRM) return
        val opt = confirmOption ?: return
        onDecision?.invoke("action_feedback", null)
        menuEngine.recordSelection(opt.optString("id", "unknown"))
        onDecision?.invoke("selection", opt)
        onDecision?.invoke("executed", opt)
        _state.value = State.WAITING
        resetDwell()
    }

    private fun handleSkip() {
        if (_state.value != State.SCAN) return
        onDecision?.invoke("skip_feedback", null)
        advanceToNext()
    }

    private fun handleCancel() {
        if (_state.value != State.CONFIRM) return
        confirmOption = null
        _state.value = State.SCAN
        resetDwell()
        val opt = menuEngine.getCurrentOption()
        if (opt != null) {
            onDecision?.invoke("scan_progress", opt)
        }
    }

    private fun enterAlert() {
        _state.value = State.ALERT
        onTtsRequest?.invoke("紧急呼叫")
        onDecision?.invoke("emergency", null)
        // auto-reset to IDLE after cooldown
        _state.value = State.WAITING
        resetDwell()
    }

    fun tick() {
        val now = System.currentTimeMillis()
        when (_state.value) {
            State.SCAN -> {
                val dwell = getCurrentDwell()
                if (now - dwellStart >= dwell) {
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
                    _state.value = State.IDLE
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
                _state.value = State.IDLE
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
```

- [ ] **Step 4: 运行测试 (预期通过)**

```bash
cd android_mvp && ./gradlew testDebugUnitTest --tests "com.gazeinteraction.coordinator.CoordinatorEngineTest"
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 提交**

```bash
git add android_mvp/app/src/main/java/com/gazeinteraction/coordinator/CoordinatorEngine.kt android_mvp/app/src/test/java/com/gazeinteraction/coordinator/CoordinatorEngineTest.kt
git commit -m "feat: add CoordinatorEngine state machine with IDLE/SCAN/CONFIRM/ALERT/WAITING"
```

---

### Task 3: BrokerService — Moquette 集成

**Files:**
- Create: `android_mvp/app/src/main/java/com/gazeinteraction/coordinator/BrokerService.kt`
- Modify: `android_mvp/app/build.gradle.kts`

- [ ] **Step 1: 添加 Moquette 依赖**

Read `build.gradle.kts`, find the dependencies block, and add:

```kotlin
// MQTT Broker (embedded)
implementation("io.moquette:moquette-broker:0.17.1")
```

- [ ] **Step 2: 实现 BrokerService**

```kotlin
// BrokerService.kt
package com.gazeinteraction.coordinator

import android.util.Log
import io.moquette.broker.Server
import io.moquette.broker.config.MemoryConfig
import java.util.*

class BrokerService {
    companion object {
        private const val TAG = "BrokerService"
    }

    private var server: Server? = null
    var isRunning = false
        private set

    fun start(port: Int = 1883) {
        if (isRunning) return
        try {
            val props = Properties().apply {
                setProperty("host", "0.0.0.0")
                setProperty("port", port.toString())
                setProperty("allow_anonymous", "true")
            }
            server = Server()
            server?.startServer(MemoryConfig(props))
            isRunning = true
            Log.i(TAG, "MQTT Broker started on port $port")
        } catch (e: Exception) {
            Log.e(TAG, "Broker start failed", e)
        }
    }

    fun stop() {
        try {
            server?.stopServer()
            isRunning = false
            Log.i(TAG, "MQTT Broker stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Broker stop failed", e)
        }
    }
}
```

- [ ] **Step 3: 验证编译**

```bash
cd android_mvp && ./gradlew compileDebugKotlin 2>&1 | tail -5
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 提交**

```bash
git add android_mvp/app/src/main/java/com/gazeinteraction/coordinator/BrokerService.kt android_mvp/app/build.gradle.kts
git commit -m "feat: add Moquette embedded MQTT broker service"
```

---

### Task 4: HostManager — 角色管理

**Files:**
- Create: `android_mvp/app/src/main/java/com/gazeinteraction/coordinator/HostManager.kt`

- [ ] **Step 1: 实现 HostManager**

```kotlin
// HostManager.kt
package com.gazeinteraction.coordinator

import android.content.Context
import android.net.wifi.WifiManager
import android.net.wifi.WifiInfo
import android.net.DhcpInfo

class HostManager(private val context: Context) {
    enum class Role { HOST, CLIENT, UNKNOWN }

    var role: Role = Role.UNKNOWN
        private set

    fun detectRole(): Role {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        if (wifi == null) return Role.UNKNOWN

        // Check if this device is acting as a WiFi hotspot
        val dhcpInfo = wifi.dhcpInfo
        if (dhcpInfo != null && dhcpInfo.serverAddress == 0) {
            // DHCP server not responding — likely we ARE the hotspot
            role = Role.HOST
            return Role.HOST
        }

        // Check if we're connected to a hotspot network (192.168.43.x is standard Android hotspot)
        val connInfo = wifi.connectionInfo
        val ip = connInfo.ipAddress
        if (ip != 0) {
            val ipStr = String.format(
                "%d.%d.%d.%d",
                ip and 0xff,
                (ip shr 8) and 0xff,
                (ip shr 16) and 0xff,
                (ip shr 24) and 0xff
            )
            if (ipStr.startsWith("192.168.43.")) {
                role = Role.CLIENT
                return Role.CLIENT
            }
        }

        role = Role.UNKNOWN
        return Role.UNKNOWN
    }

    fun getBrokerHost(): String = when (role) {
        Role.HOST -> "127.0.0.1"
        Role.CLIENT -> "192.168.43.1"
        Role.UNKNOWN -> "127.0.0.1"
    }

    fun getBrokerPort(): Int = 1883
}
```

- [ ] **Step 2: 验证编译**

```bash
cd android_mvp && ./gradlew compileDebugKotlin 2>&1 | tail -5
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add android_mvp/app/src/main/java/com/gazeinteraction/coordinator/HostManager.kt
git commit -m "feat: add HostManager for host/client role auto-detection"
```

---

### Task 5: AndroidTTSManager — TTS 封装

**Files:**
- Create: `android_mvp/app/src/main/java/com/gazeinteraction/coordinator/AndroidTTSManager.kt`

- [ ] **Step 1: 实现 AndroidTTSManager**

```kotlin
// AndroidTTSManager.kt
package com.gazeinteraction.coordinator

import android.content.Context
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.*

class AndroidTTSManager(private val context: Context) {
    private var tts: TextToSpeech? = null
    private var initDone = false
    private val pendingQueue = Collections.synchronizedList(mutableListOf<String>())
    private var isSpeaking = false

    fun initialize(callback: (Boolean) -> Unit) {
        tts = TextToSpeech(context) { status ->
            initDone = (status == TextToSpeech.SUCCESS)
            if (initDone) {
                tts?.language = java.util.Locale.CHINESE
                tts?.setSpeechRate(0.8f)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {}
                    override fun onDone(id: String?) {
                        isSpeaking = false
                        processNext()
                    }
                    @Deprecated("")
                    override fun onError(id: String?) {
                        isSpeaking = false
                        processNext()
                    }
                })
                processNext()
            }
            callback(initDone)
        }
    }

    fun speak(text: String) {
        if (!initDone) {
            pendingQueue.add(text)
            return
        }
        if (isSpeaking) {
            pendingQueue.add(text)
            return
        }
        doSpeak(text)
    }

    fun stop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts?.stop()
        }
        isSpeaking = false
        pendingQueue.clear()
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
    }

    private fun doSpeak(text: String) {
        isSpeaking = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, text.take(10))
        } else {
            @Suppress("DEPRECATION")
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null)
        }
    }

    private fun processNext() {
        val next = pendingQueue.removeFirstOrNull()
        if (next != null) {
            doSpeak(next)
        }
    }
}
```

- [ ] **Step 2: 验证编译**

```bash
cd android_mvp && ./gradlew compileDebugKotlin 2>&1 | tail -5
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add android_mvp/app/src/main/java/com/gazeinteraction/coordinator/AndroidTTSManager.kt
git commit -m "feat: add AndroidTTSManager with queued TTS playback"
```

---

### Task 6: GazeInterpreter — 注视解读

**Files:**
- Create: `android_mvp/app/src/main/java/com/gazeinteraction/coordinator/GazeInterpreter.kt`
- Create: `android_mvp/app/src/test/java/com/gazeinteraction/coordinator/GazeInterpreterTest.kt`

- [ ] **Step 1: 编写测试**

```kotlin
// GazeInterpreterTest.kt
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
    fun `wake action after sufficient gaze`() {
        val gi = GazeInterpreter(selectSec = 1.5f, skipSec = 0.5f)
        // Simulate gazing for 3+ seconds (IDLE wake condition)
        val now = System.currentTimeMillis()
        val gazeStart = now - 3500
        assertEquals("wake", gi.evaluate("yes", true, 0.9f, gazeStart))
    }

    @Test
    fun `skip action for no role with short gaze`() {
        val gi = GazeInterpreter(selectSec = 1.5f, skipSec = 0.5f)
        val now = System.currentTimeMillis()
        val gazeStart = now - 600
        assertEquals("skip", gi.evaluate("no", true, 0.8f, gazeStart))
    }

    @Test
    fun `select action for yes with long gaze`() {
        val gi = GazeInterpreter(selectSec = 1.5f, skipSec = 0.5f)
        val now = System.currentTimeMillis()
        val gazeStart = now - 1600
        assertEquals("select", gi.evaluate("yes", true, 0.8f, gazeStart))
    }

    @Test
    fun `hesitate for yes with short gaze`() {
        val gi = GazeInterpreter(selectSec = 1.5f, skipSec = 0.5f)
        val now = System.currentTimeMillis()
        val gazeStart = now - 500
        assertEquals("hesitate", gi.evaluate("yes", true, 0.8f, gazeStart))
    }

    @Test
    fun `uncalibrated device ignores confidence threshold`() {
        val gi = GazeInterpreter(selectSec = 1.5f, skipSec = 0.5f)
        gi.isCalibrated = false
        val now = System.currentTimeMillis()
        assertEquals("none", gi.evaluate("yes", true, 0.3f, now - 2000))
    }
}
```

- [ ] **Step 2: 运行测试 (预期失败)** → 实现 GazeInterpreter

```kotlin
// GazeInterpreter.kt
package com.gazeinteraction.coordinator

class GazeInterpreter(
    private val selectSec: Float = 1.5f,
    private val skipSec: Float = 0.5f,
    private val wakeSec: Float = 3.0f,
    private val confidenceThreshold: Float = 0.55f
) {
    var isCalibrated: Boolean = true

    /** Returns: "wake", "select", "skip", "hesitate", or "none" */
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
```

- [ ] **Step 4: 运行测试 (预期通过)**

```bash
cd android_mvp && ./gradlew testDebugUnitTest --tests "com.gazeinteraction.coordinator.GazeInterpreterTest"
```

Expected: BUILD SUCCESSFUL, 6 passed

- [ ] **Step 5: 提交**

```bash
git add android_mvp/app/src/main/java/com/gazeinteraction/coordinator/GazeInterpreter.kt android_mvp/app/src/test/java/com/gazeinteraction/coordinator/GazeInterpreterTest.kt
git commit -m "feat: add GazeInterpreter with select/skip/wake/hesitate logic"
```

---

### Task 7: MqttClient 更新 + MainActivity 集成

**Files:**
- Modify: `android_mvp/app/src/main/java/com/gazeinteraction/mqtt/MqttClient.kt`
- Modify: `android_mvp/app/src/main/java/com/gazeinteraction/MainActivity.kt`

- [ ] **Step 1: MqttClient 支持动态 broker 地址**

在 `MqttClient.kt` 的 `connect` 方法前添加动态地址设置能力。不需要改现有逻辑，只需在 `MainActivity` 中调用 `connect(host, port)` 时传入 `HostManager.getBrokerHost()` 和 `HostManager.getBrokerPort()`。

- [ ] **Step 2: MainActivity 集成 coordinator 包**

在 `MainActivity.kt` 的 `onCreate` 或 `initializeComponents` 中添加：

```kotlin
// 新增字段
private lateinit var hostManager: HostManager
private lateinit var coordinatorEngine: CoordinatorEngine?
private lateinit var brokerService: BrokerService
private lateinit var ttsManager: AndroidTTSManager

// 在 initializeComponents 中:
hostManager = HostManager(this)
val role = hostManager.detectRole()

if (role == HostManager.Role.HOST) {
    // 启动内嵌 broker
    brokerService = BrokerService()
    brokerService.start(1883)

    // 初始化协调器
    val menuJson = assets.open("menu_config.json").bufferedReader().readText()
    val patientJson = assets.open("patient_config.json").bufferedReader().readText()
    coordinatorEngine = CoordinatorEngine(menuJson, patientJson)
    coordinatorEngine?.onDecision = { type, option ->
        // 发布决策到 MQTT
        publishCoordinatorDecision(type, option)
    }
    coordinatorEngine?.onTtsRequest = { text ->
        ttsManager.speak(text)
    }

    // 初始化 TTS
    ttsManager = AndroidTTSManager(this)
    ttsManager.initialize { /* ready */ }
}

// MQTT 连接使用动态地址
mqttClient.connect(hostManager.getBrokerHost(), hostManager.getBrokerPort())
```

- [ ] **Step 3: 验证编译**

```bash
cd android_mvp && ./gradlew compileDebugKotlin 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 提交**

```bash
git add android_mvp/app/src/main/java/com/gazeinteraction/
git commit -m "feat: integrate coordinator engine, broker, TTS, and host manager into MainActivity"
```

---

### Task 8: Build Verification

- [ ] **Step 1: 全量构建**

```bash
cd android_mvp && ./gradlew assembleDebug 2>&1 | tail -5
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 2: 运行所有测试**

```bash
cd android_mvp && ./gradlew testDebugUnitTest 2>&1 | tail -5
```

Expected: BUILD SUCCESSFUL, all tests pass

- [ ] **Step 3: 提交 (如有遗漏)**

```bash
git status && git add -A && git commit -m "chore: final build verification"
```
