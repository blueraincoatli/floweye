# Floweye UI 重设计：光圈引导系统 实施计划

> **状态: COMPLETED (2026-05-06)** — 8 个任务全部完成，10 个提交已合入 main，真机联调通过。
>
> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 Android 端界面从粗糙的功能性 UI 升级为带有视线引导光圈、多主题切换、两段式反馈的精致交互系统。

**Architecture:** 新增 `ui/` 包下三个独立组件——`ThemeConfig` 管理三套主题颜色/参数，`GazeHaloView` 自定义 View 实现光圈收缩引导 + 圆弧进度绘制，`ArcProgressView` 提供可复用的圆弧进度组件。MainActivity 状态机保留现有 5 状态结构，将按钮驱动 UI 替换为光圈驱动 UI，新增两段式视线反馈和主题切换逻辑。

**Tech Stack:** Kotlin, Android View (Canvas/Paint/ValueAnimator), ConstraintLayout, SharedPreferences, JUnit 4

**文件结构:**

| 文件 | 职责 |
|------|------|
| `ui/ThemeConfig.kt` (新) | 三套主题的数据类 + SharedPreferences 存取 |
| `ui/GazeHaloView.kt` (新) | 光圈引导核心 View：RadialGradient 光晕 + 两段式动画 + 圆弧进度 |
| `ui/ArcProgressView.kt` (新) | 独立圆弧进度 View（可复用） |
| `res/values/colors.xml` (改) | 主题色彩系统，按三套主题定义 |
| `res/layout/activity_main.xml` (改) | 竖屏：全屏光圈 + 中央文字 + 边缘状态层 |
| `res/layout-land/activity_main.xml` (改) | 横屏：双光圈 + 双按钮 |
| `res/values/themes.xml` (改) | 更新样式引用 |
| `MainActivity.kt` (改) | 集成光圈、两段式反馈、主题切换、操作者/患者模式 |

---

### Task 1: ThemeConfig — 主题配置系统

**Files:**
- Create: `android_mvp/app/src/main/java/com/gazeinteraction/ui/ThemeConfig.kt`
- Create: `android_mvp/app/src/test/java/com/gazeinteraction/ui/ThemeConfigTest.kt`

- [ ] **Step 1: 创建测试目录并编写失败测试**

```bash
mkdir -p android_mvp/app/src/test/java/com/gazeinteraction/ui
```

```kotlin
// android_mvp/app/src/test/java/com/gazeinteraction/ui/ThemeConfigTest.kt
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
```

- [ ] **Step 2: 运行测试验证失败**

```bash
cd android_mvp && ./gradlew testDebugUnitTest --tests "com.gazeinteraction.ui.ThemeConfigTest" 2>&1 | tail -20
```

Expected: BUILD FAILED — `Unresolved reference: ThemeConfig`

- [ ] **Step 3: 实现 ThemeConfig**

```kotlin
// android_mvp/app/src/main/java/com/gazeinteraction/ui/ThemeConfig.kt
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
```

- [ ] **Step 4: 运行测试验证通过**

```bash
cd android_mvp && ./gradlew testDebugUnitTest --tests "com.gazeinteraction.ui.ThemeConfigTest" 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 提交**

```bash
git add android_mvp/app/src/main/java/com/gazeinteraction/ui/ThemeConfig.kt android_mvp/app/src/test/java/com/gazeinteraction/ui/ThemeConfigTest.kt
git commit -m "feat: add ThemeConfig with 3 themes (warm healing, high contrast, modern minimal)"
```

---

### Task 2: Color Resources — 更新 colors.xml

**Files:**
- Modify: `android_mvp/app/src/main/res/values/colors.xml`

- [ ] **Step 1: 重写 colors.xml 为完整三主题色彩系统**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- ========== 温暖疗愈主题 ========== -->
    <color name="warm_bg">#FF1C1917</color>
    <color name="warm_halo_yes">#FF6BA87A</color>
    <color name="warm_halo_no">#FFC47A6E</color>
    <color name="warm_text_primary">#FFF5F0E8</color>
    <color name="warm_bg_idle">#FF262220</color>

    <!-- ========== 高对比功能主题 ========== -->
    <color name="contrast_bg">#FF000000</color>
    <color name="contrast_halo_yes">#FF00FF66</color>
    <color name="contrast_halo_no">#FFFF3333</color>
    <color name="contrast_text_primary">#FFFFFFFF</color>
    <color name="contrast_bg_idle">#FF0A0A0A</color>

    <!-- ========== 现代简约主题 ========== -->
    <color name="minimal_bg">#FFF5F5F0</color>
    <color name="minimal_halo_yes">#FF4A7FD9</color>
    <color name="minimal_halo_no">#FF7A8B9E</color>
    <color name="minimal_text_primary">#FF1A1A1A</color>
    <color name="minimal_bg_idle">#FFE8E8E3</color>

    <!-- ========== 通用状态颜色 ========== -->
    <color name="status_connected">#FF6BA87A</color>
    <color name="status_connecting">#FFD4A373</color>
    <color name="status_disconnected">#FFA86B6B</color>
    <color name="white">#FFFFFFFF</color>
    <color name="black">#FF000000</color>
    <color name="ic_launcher_background">#FF1E1E2E</color>

    <!-- ========== 确认层颜色（通用） ========== -->
    <color name="confirm_bg">#FF2E1F2E</color>
    <color name="confirm_halo_yes">#FFB87AD9</color>
    <color name="confirm_halo_no">#FFD97A8A</color>
    <color name="confirm_text">#FFCE93D8</color>

    <!-- ========== 层级背景色（扫描态） ========== -->
    <color name="bg_level_0">#FF0D1825</color>
    <color name="bg_level_1">#FF0D2137</color>
    <color name="bg_level_2">#FF0D3320</color>

    <!-- ========== 旧颜色保留（向后兼容，逐步移除） ========== -->
    <color name="purple_200">#FFBB86FC</color>
    <color name="purple_500">#FF6200EE</color>
    <color name="purple_700">#FF3700B3</color>
    <color name="teal_200">#FF03DAC5</color>
    <color name="teal_700">#FF018786</color>
    <color name="primary_blue">#FF2196F3</color>
    <color name="primary_green">#FF4CAF50</color>
    <color name="primary_red">#FFF44336</color>
    <color name="yes_button_normal">#FF4A5C52</color>
    <color name="yes_button_selected">#FF3D6B4F</color>
    <color name="yes_button_gaze">#FF7DB88A</color>
    <color name="no_button_normal">#FF5C4A48</color>
    <color name="no_button_selected">#FF6B3D3D</color>
    <color name="no_button_gaze">#FFB88A8A</color>
    <color name="background_dark">#FF1E1E2E</color>
    <color name="background_light">#FFFFFFFF</color>
    <color name="text_primary">#FFF0F0F0</color>
    <color name="text_secondary">#FFB0B0B0</color>
    <color name="text_on_primary">#FFF0F0F0</color>
    <color name="background_warm">#FF3D3D2E</color>
    <color name="text_level_1">#FF90CAF9</color>
    <color name="text_level_2">#FFA5D6A7</color>
    <color name="text_confirm">#FFCE93D8</color>
    <color name="yes_normal_l0">#FF2E4057</color>
    <color name="yes_gaze_l0">#FF4A90D9</color>
    <color name="yes_normal_l1">#FF2E5740</color>
    <color name="yes_gaze_l1">#FF4AD97A</color>
    <color name="yes_normal_l2">#FF57402E</color>
    <color name="yes_gaze_l2">#FFD9A04A</color>
    <color name="yes_normal_cf">#FF402E57</color>
    <color name="yes_gaze_cf">#FF904AD9</color>
    <color name="no_normal_l0">#FF57312E</color>
    <color name="no_gaze_l0">#FFD9564A</color>
    <color name="no_normal_l1">#FF57312E</color>
    <color name="no_gaze_l1">#FFD9564A</color>
    <color name="no_normal_l2">#FF57312E</color>
    <color name="no_gaze_l2">#FFD9564A</color>
    <color name="no_normal_cf">#FF57312E</color>
    <color name="no_gaze_cf">#FFD9564A</color>
</resources>
```

- [ ] **Step 2: 提交**

```bash
git add android_mvp/app/src/main/res/values/colors.xml
git commit -m "feat: expand color resources with 3-theme system"
```

---

### Task 3: GazeHaloView — 光圈引导核心 View

**Files:**
- Create: `android_mvp/app/src/main/java/com/gazeinteraction/ui/GazeHaloView.kt`

- [ ] **Step 1: 创建 GazeHaloView 完整实现**

```kotlin
// android_mvp/app/src/main/java/com/gazeinteraction/ui/GazeHaloView.kt
package com.gazeinteraction.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator

class GazeHaloView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // ---------- 可外部设置的属性 ----------
    var haloColor: Int = 0xFF6BA87A.toInt()
        set(v) { field = v; updatePaints(); invalidate() }
    var textColor: Int = 0xFFF5F0E8.toInt()
        set(v) { field = v; invalidate() }
    var labelText: String = ""
        set(v) { field = v; invalidate() }
    var arcProgress: Float = 0f  // 0..1
        set(v) { field = v.coerceIn(0f, 1f); invalidate() }

    /** 注视阶段回调：0=无注视, 1=感知阶段(0-0.5s), 2=引导阶段(>0.5s) */
    var gazePhase: Int = 0
        private set

    var onPerceptionStart: (() -> Unit)? = null
    var onGuidanceStart: (() -> Unit)? = null

    // ---------- 内部状态 ----------
    private val density = context.resources.displayMetrics.density
    private var currentRadius = 0f
    private var currentAlpha = 0.3f
    private var initialRadius = 0f
    private var isGazing = false
    private var phase2Triggered = false

    // ---------- Paint 对象 ----------
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f * density
        strokeCap = Paint.Cap.ROUND
    }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f * density
        strokeCap = Paint.Cap.ROUND
    }
    private val bgPaint = Paint().apply { alpha = 0 }

    private fun updatePaints() {
        ringPaint.color = haloColor
        arcPaint.color = haloColor
    }

    // ---------- 动画控制 ----------
    private val handler = Handler(Looper.getMainLooper())
    private var perceptionAnim: ValueAnimator? = null
    private var guidanceAnim: ValueAnimator? = null
    private var reverseAnim: ValueAnimator? = null

    init {
        updatePaints()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        initialRadius = minOf(w, h) * 0.38f
        if (currentRadius == 0f) {
            currentRadius = initialRadius
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f

        // 1. 背景光晕 (RadialGradient)
        val gradientCenterRatio = 0.65f
        haloPaint.shader = RadialGradient(
            cx, cy, currentRadius * 1.5f,
            intArrayOf(
                adjustAlpha(haloColor, currentAlpha),
                adjustAlpha(haloColor, currentAlpha * 0.4f),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, gradientCenterRatio, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, currentRadius * 1.5f, haloPaint)

        // 2. 内圈细环
        val ringRadius = currentRadius * 0.82f
        ringPaint.alpha = (currentAlpha * 160).toInt().coerceIn(0, 255)
        canvas.drawCircle(cx, cy, ringRadius, ringPaint)

        // 3. 圆弧进度
        if (arcProgress > 0f && arcProgress < 1f) {
            arcPaint.alpha = (currentAlpha * 255).toInt().coerceIn(0, 255)
            val arcRect = RectF(
                cx - ringRadius, cy - ringRadius,
                cx + ringRadius, cy + ringRadius
            )
            canvas.drawArc(arcRect, -90f, arcProgress * 360f, false, arcPaint)
        }

        // 4. 完成脉冲效果
        if (arcProgress >= 1f && isGazing) {
            arcPaint.alpha = 255
            val pulseExtra = 12f * density
            val arcRect = RectF(
                cx - ringRadius - pulseExtra, cy - ringRadius - pulseExtra,
                cx + ringRadius + pulseExtra, cy + ringRadius + pulseExtra
            )
            canvas.drawArc(arcRect, -90f, 360f, false, arcPaint)
        }

        // 5. 中央文字
        if (labelText.isNotEmpty()) {
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textColor
                textAlign = Paint.Align.CENTER
                isFakeBoldText = true
                textSize = minOf(width, height) * 0.1f
            }
            val textY = cy - (textPaint.descent() + textPaint.ascent()) / 2
            canvas.drawText(labelText, cx, textY, textPaint)
        }
    }

    // ---------- 公开方法 ----------

    /** 注视检测到：启动两段式动画 */
    fun onGazeDetected() {
        if (isGazing) return
        isGazing = true
        phase2Triggered = false
        cancelAnimations()
        handler.removeCallbacksAndMessages(null)

        gazePhase = 1
        onPerceptionStart?.invoke()

        // 阶段1：感知 (0-500ms)，仅提亮，不收缩
        perceptionAnim = ValueAnimator.ofFloat(currentAlpha, 0.55f).apply {
            duration = 500
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                currentAlpha = it.animatedValue as Float
                invalidate()
            }
            start()
        }

        // 500ms 后进入阶段2
        handler.postDelayed({
            if (isGazing && !phase2Triggered) {
                phase2Triggered = true
                gazePhase = 2
                onGuidanceStart?.invoke()
                startGuidanceAnimation()
            }
        }, 500)
    }

    private fun startGuidanceAnimation() {
        // 阶段2：引导 (500-1500ms)，收缩 + 继续提亮
        val contractTarget = minOf(width, height) * 0.10f
        guidanceAnim = ValueAnimator.ofFloat(currentRadius, contractTarget).apply {
            duration = 1000
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val frac = it.animatedFraction
                currentRadius = it.animatedValue as Float
                currentAlpha = 0.55f + frac * 0.45f  // 0.55 -> 1.0
                invalidate()
            }
            start()
        }
    }

    /** 视线离开 */
    fun onGazeLost() {
        isGazing = false
        phase2Triggered = false
        gazePhase = 0
        cancelAnimations()
        handler.removeCallbacksAndMessages(null)

        // 平滑扩散回初始态 (0.8s)
        reverseAnim = ValueAnimator.ofFloat(currentRadius, initialRadius).apply {
            duration = 800
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                currentRadius = it.animatedValue as Float
                currentAlpha = 0.15f + (1f - it.animatedFraction) * 0.25f
                invalidate()
            }
            start()
        }
    }

    /** 重置为 IDLE 态 */
    fun resetToIdle() {
        cancelAnimations()
        handler.removeCallbacksAndMessages(null)
        isGazing = false
        phase2Triggered = false
        gazePhase = 0
        arcProgress = 0f
        currentRadius = initialRadius
        currentAlpha = 0.3f
        invalidate()
    }

    /** 进入扫描态，有背景呼吸 */
    fun enterScanMode() {
        arcProgress = 0f
        currentAlpha = 0.3f
        currentRadius = initialRadius
        isGazing = false
        phase2Triggered = false
        gazePhase = 0
        cancelAnimations()
        handler.removeCallbacksAndMessages(null)
        invalidate()
    }

    /** 进入确认态，颜色更饱和 */
    fun enterConfirmMode() {
        enterScanMode()
        currentAlpha = 0.4f
    }

    private fun cancelAnimations() {
        perceptionAnim?.cancel()
        guidanceAnim?.cancel()
        reverseAnim?.cancel()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cancelAnimations()
        handler.removeCallbacksAndMessages(null)
    }

    companion object {
        private fun adjustAlpha(color: Int, alpha: Float): Int {
            val a = (Color.alpha(color) * alpha.coerceIn(0f, 1f)).toInt()
            return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
        }
    }
}
```

- [ ] **Step 2: 验证编译**

```bash
cd android_mvp && ./gradlew compileDebugKotlin 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add android_mvp/app/src/main/java/com/gazeinteraction/ui/GazeHaloView.kt
git commit -m "feat: add GazeHaloView with two-phase gaze animation and radial gradient"
```

---

### Task 4: ArcProgressView — 独立圆弧进度 View

**Files:**
- Create: `android_mvp/app/src/main/java/com/gazeinteraction/ui/ArcProgressView.kt`

- [ ] **Step 1: 创建 ArcProgressView**

```kotlin
// android_mvp/app/src/main/java/com/gazeinteraction/ui/ArcProgressView.kt
package com.gazeinteraction.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class ArcProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var arcColor: Int = 0xFF6BA87A.toInt()
        set(v) { field = v; arcPaint.color = v; invalidate() }
    var progress: Float = 0f
        set(v) { field = v.coerceIn(0f, 1f); invalidate() }
    var strokeWidthDp: Float = 5f
        set(v) {
            field = v
            arcPaint.strokeWidth = v * context.resources.displayMetrics.density
            invalidate()
        }
    var arcAlpha: Float = 1f
        set(v) { field = v.coerceIn(0f, 1f); invalidate() }

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f * context.resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (progress <= 0f) return

        val cx = width / 2f
        val cy = height / 2f
        val padding = arcPaint.strokeWidth / 2 + 2f
        val radius = minOf(width, height) / 2f - padding

        arcPaint.alpha = (arcAlpha * 255).toInt().coerceIn(0, 255)

        val oval = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        canvas.drawArc(oval, -90f, progress * 360f, false, arcPaint)

        // 满进度时画完整圆 + 外发光
        if (progress >= 1f) {
            val glowPaint = Paint(arcPaint).apply {
                alpha = (arcAlpha * 100).toInt().coerceIn(0, 255)
                strokeWidth = arcPaint.strokeWidth * 2.5f
            }
            canvas.drawArc(oval, -90f, 360f, false, glowPaint)
            canvas.drawArc(oval, -90f, 360f, false, arcPaint)
        }
    }
}
```

- [ ] **Step 2: 验证编译**

```bash
cd android_mvp && ./gradlew compileDebugKotlin 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add android_mvp/app/src/main/java/com/gazeinteraction/ui/ArcProgressView.kt
git commit -m "feat: add ArcProgressView for standalone arc progress indicator"
```

---

### Task 5: Layout Rewrite — 竖屏 + 横屏布局

**Files:**
- Modify: `android_mvp/app/src/main/res/layout/activity_main.xml`
- Modify: `android_mvp/app/src/main/res/layout-land/activity_main.xml`

- [ ] **Step 1: 重写竖屏布局 activity_main.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:id="@+id/rootLayout"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/warm_bg"
    tools:context=".MainActivity">

    <!-- 光圈引导层（全屏） -->
    <com.gazeinteraction.ui.GazeHaloView
        android:id="@+id/gazeHaloView"
        android:layout_width="0dp"
        android:layout_height="0dp"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent" />

    <!-- 顶部边缘状态条 -->
    <LinearLayout
        android:id="@+id/topStatusBar"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:paddingHorizontal="16dp"
        android:paddingTop="12dp"
        android:gravity="center_vertical"
        app:layout_constraintTop_toTopOf="parent">

        <!-- 连接状态圆点 -->
        <View
            android:id="@+id/connectionDot"
            android:layout_width="8dp"
            android:layout_height="8dp"
            android:background="@drawable/dot_status_idle" />

        <View
            android:layout_width="0dp"
            android:layout_height="0dp"
            android:layout_weight="1" />

        <!-- 设置按钮（操作者模式下可见） -->
        <com.google.android.material.floatingactionbutton.FloatingActionButton
            android:id="@+id/settingsButton"
            android:layout_width="36dp"
            android:layout_height="36dp"
            android:src="@android:drawable/ic_menu_preferences"
            android:scaleType="centerInside"
            app:fabSize="mini"
            app:backgroundTint="#33FFFFFF"
            app:tint="@color/white" />
    </LinearLayout>

    <!-- 选项名文字（扫描态，紧邻按钮上方） -->
    <TextView
        android:id="@+id/optionNameText"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text=""
        android:textSize="32sp"
        android:textColor="@color/warm_text_primary"
        android:gravity="center"
        android:layout_marginBottom="24dp"
        android:visibility="gone"
        app:layout_constraintBottom_toTopOf="@+id/centerTextContainer"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent" />

    <!-- 中央文字区域 -->
    <FrameLayout
        android:id="@+id/centerTextContainer"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent">

        <TextView
            android:id="@+id/mainButton"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_gravity="center"
            android:text="你好"
            android:textSize="96sp"
            android:autoSizeTextType="uniform"
            android:autoSizeMinTextSize="48sp"
            android:autoSizeMaxTextSize="160sp"
            android:textStyle="bold"
            android:textColor="@color/warm_text_primary"
            android:gravity="center"
            android:padding="4dp" />
    </FrameLayout>

    <!-- 圆弧进度指示器（独立 View，叠在文字区域上方） -->
    <com.gazeinteraction.ui.ArcProgressView
        android:id="@+id/arcProgressView"
        android:layout_width="0dp"
        android:layout_height="0dp"
        android:visibility="gone"
        app:layout_constraintTop_toTopOf="@id/centerTextContainer"
        app:layout_constraintBottom_toBottomOf="@id/centerTextContainer"
        app:layout_constraintStart_toStartOf="@id/centerTextContainer"
        app:layout_constraintEnd_toEndOf="@id/centerTextContainer" />

    <!-- 底部状态栏 -->
    <LinearLayout
        android:id="@+id/bottomInfo"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:paddingHorizontal="16dp"
        android:paddingBottom="16dp"
        android:gravity="center"
        app:layout_constraintBottom_toBottomOf="parent">

        <TextView
            android:id="@+id/gazeStatus"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/no_gaze_detected"
            android:textSize="14sp"
            android:textColor="@color/warm_text_primary"
            android:alpha="0.6"
            android:layout_marginBottom="2dp" />

        <TextView
            android:id="@+id/confidenceText"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text=""
            android:textSize="12sp"
            android:textColor="@color/warm_text_primary"
            android:alpha="0.4" />
    </LinearLayout>

    <!-- 校准按钮（始终可见，底部右侧） -->
    <com.google.android.material.floatingactionbutton.FloatingActionButton
        android:id="@+id/calibrateButton"
        android:layout_width="40dp"
        android:layout_height="40dp"
        android:layout_margin="8dp"
        android:src="@android:drawable/ic_menu_compass"
        android:scaleType="centerInside"
        app:fabSize="mini"
        app:backgroundTint="#33FFFFFF"
        app:tint="@color/warm_text_primary"
        app:layout_constraintBottom_toTopOf="@id/bottomInfo"
        app:layout_constraintEnd_toEndOf="parent" />

    <!-- 角色切换按钮（左上角，操作者模式可见） -->
    <com.google.android.material.floatingactionbutton.FloatingActionButton
        android:id="@+id/roleButton"
        android:layout_width="36dp"
        android:layout_height="36dp"
        android:layout_margin="8dp"
        android:src="@android:drawable/ic_menu_manage"
        android:scaleType="centerInside"
        app:fabSize="mini"
        app:backgroundTint="#33FFFFFF"
        app:tint="@color/white"
        app:layout_constraintTop_toBottomOf="@id/topStatusBar"
        app:layout_constraintStart_toStartOf="parent" />

    <!-- 调试叠加层 -->
    <include layout="@layout/debug_panel" />
</androidx.constraintlayout.widget.ConstraintLayout>
```

- [ ] **Step 2: 创建连接状态圆点 drawable**

```xml
<!-- android_mvp/app/src/main/res/drawable/dot_status_idle.xml -->
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="oval">
    <solid android:color="@color/status_connecting" />
    <size android:width="8dp" android:height="8dp" />
</shape>
```

```xml
<!-- android_mvp/app/src/main/res/drawable/dot_status_connected.xml -->
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="oval">
    <solid android:color="@color/status_connected" />
    <size android:width="8dp" android:height="8dp" />
</shape>
```

```xml
<!-- android_mvp/app/src/main/res/drawable/dot_status_disconnected.xml -->
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="oval">
    <solid android:color="@color/status_disconnected" />
    <size android:width="8dp" android:height="8dp" />
</shape>
```

- [ ] **Step 3: 重写横屏布局 activity_main.xml (layout-land)**

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:id="@+id/rootLayout"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/warm_bg"
    tools:context=".MainActivity">

    <!-- 顶部边缘状态条 -->
    <LinearLayout
        android:id="@+id/topStatusBar"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:paddingHorizontal="16dp"
        android:paddingTop="8dp"
        android:gravity="center_vertical"
        app:layout_constraintTop_toTopOf="parent">

        <View
            android:id="@+id/connectionDot"
            android:layout_width="8dp"
            android:layout_height="8dp"
            android:background="@drawable/dot_status_idle" />

        <View
            android:layout_width="0dp"
            android:layout_height="0dp"
            android:layout_weight="1" />

        <com.google.android.material.floatingactionbutton.FloatingActionButton
            android:id="@+id/settingsButton"
            android:layout_width="32dp"
            android:layout_height="32dp"
            android:src="@android:drawable/ic_menu_preferences"
            android:scaleType="centerInside"
            app:fabSize="mini"
            app:backgroundTint="#33FFFFFF"
            app:tint="@color/white" />
    </LinearLayout>

    <!-- 双按钮区域 -->
    <LinearLayout
        android:id="@+id/buttonContainer"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:orientation="horizontal"
        android:padding="8dp"
        app:layout_constraintTop_toBottomOf="@id/topStatusBar"
        app:layout_constraintBottom_toTopOf="@+id/bottomInfo"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent">

        <!-- 是按钮 -->
        <FrameLayout
            android:layout_width="0dp"
            android:layout_height="match_parent"
            android:layout_weight="1"
            android:layout_marginEnd="4dp">

            <com.gazeinteraction.ui.GazeHaloView
                android:id="@+id/gazeHaloYes"
                android:layout_width="match_parent"
                android:layout_height="match_parent" />

            <TextView
                android:id="@+id/yesButton"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:text="@string/yes_button"
                android:textSize="72sp"
                android:autoSizeTextType="uniform"
                android:autoSizeMinTextSize="36sp"
                android:autoSizeMaxTextSize="120sp"
                android:textStyle="bold"
                android:textColor="@color/warm_text_primary"
                android:gravity="center" />
        </FrameLayout>

        <!-- 否按钮 -->
        <FrameLayout
            android:layout_width="0dp"
            android:layout_height="match_parent"
            android:layout_weight="1"
            android:layout_marginStart="4dp">

            <com.gazeinteraction.ui.GazeHaloView
                android:id="@+id/gazeHaloNo"
                android:layout_width="match_parent"
                android:layout_height="match_parent" />

            <TextView
                android:id="@+id/noButton"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:text="@string/no_button"
                android:textSize="72sp"
                android:autoSizeTextType="uniform"
                android:autoSizeMinTextSize="36sp"
                android:autoSizeMaxTextSize="120sp"
                android:textStyle="bold"
                android:textColor="@color/warm_text_primary"
                android:gravity="center" />
        </FrameLayout>
    </LinearLayout>

    <!-- 底部状态 -->
    <LinearLayout
        android:id="@+id/bottomInfo"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:paddingHorizontal="16dp"
        android:paddingBottom="8dp"
        android:gravity="center"
        app:layout_constraintBottom_toBottomOf="parent">

        <TextView
            android:id="@+id/gazeStatus"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/no_gaze_detected"
            android:textSize="12sp"
            android:textColor="@color/warm_text_primary"
            android:alpha="0.6" />

        <TextView
            android:id="@+id/confidenceText"
            android:layout_width="0dp"
            android:layout_weight="1"
            android:layout_height="wrap_content"
            android:text=""
            android:textSize="11sp"
            android:textColor="@color/warm_text_primary"
            android:alpha="0.4"
            android:gravity="end" />
    </LinearLayout>

    <!-- 校准按钮 -->
    <com.google.android.material.floatingactionbutton.FloatingActionButton
        android:id="@+id/calibrateButton"
        android:layout_width="36dp"
        android:layout_height="36dp"
        android:layout_margin="8dp"
        android:src="@android:drawable/ic_menu_compass"
        android:scaleType="centerInside"
        app:fabSize="mini"
        app:backgroundTint="#33FFFFFF"
        app:tint="@color/warm_text_primary"
        app:layout_constraintBottom_toTopOf="@id/bottomInfo"
        app:layout_constraintEnd_toEndOf="parent" />

    <!-- 调试叠加层 -->
    <include layout="@layout/debug_panel" />
</androidx.constraintlayout.widget.ConstraintLayout>
```

- [ ] **Step 4: 验证布局**

```bash
cd android_mvp && ./gradlew compileDebugKotlin 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 提交**

```bash
git add android_mvp/app/src/main/res/layout/activity_main.xml android_mvp/app/src/main/res/layout-land/activity_main.xml android_mvp/app/src/main/res/drawable/dot_status_*.xml
git commit -m "feat: rewrite layouts with GazeHaloView, edge status bar, calibration button"
```

---

### Task 6: Styles Update — 更新 themes.xml

**Files:**
- Modify: `android_mvp/app/src/main/res/values/themes.xml`

- [ ] **Step 1: 精简 themes.xml，移除旧按钮样式，新增操作者相关样式**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources xmlns:tools="http://schemas.android.com/tools">
    <style name="Theme.GazeInteractionApp" parent="Theme.Material3.DayNight">
        <item name="colorPrimary">@color/primary_blue</item>
        <item name="colorPrimaryVariant">@color/purple_700</item>
        <item name="colorOnPrimary">@color/white</item>
        <item name="colorSecondary">@color/teal_200</item>
        <item name="colorSecondaryVariant">@color/teal_700</item>
        <item name="colorOnSecondary">@color/black</item>
        <item name="android:statusBarColor" tools:targetApi="l">?attr/colorPrimaryVariant</item>
    </style>

    <style name="Theme.GazeInteractionApp.NoActionBar">
        <item name="windowActionBar">false</item>
        <item name="windowNoTitle">true</item>
        <item name="android:windowFullscreen">true</item>
    </style>

    <!-- 中央大字样式 -->
    <style name="CenterLabelStyle">
        <item name="android:layout_width">wrap_content</item>
        <item name="android:layout_height">wrap_content</item>
        <item name="android:textSize">96sp</item>
        <item name="android:autoSizeTextType">uniform</item>
        <item name="android:autoSizeMinTextSize">48sp</item>
        <item name="android:autoSizeMaxTextSize">160sp</item>
        <item name="android:autoSizeStepGranularity">2sp</item>
        <item name="android:textStyle">bold</item>
        <item name="android:gravity">center</item>
        <item name="android:padding">4dp</item>
    </style>

    <!-- 选项名样式 -->
    <style name="OptionNameStyle">
        <item name="android:layout_width">wrap_content</item>
        <item name="android:layout_height">wrap_content</item>
        <item name="android:textSize">32sp</item>
        <item name="android:gravity">center</item>
        <item name="android:padding">4dp</item>
    </style>

    <!-- 底部状态文字样式 -->
    <style name="BottomStatusStyle">
        <item name="android:layout_width">wrap_content</item>
        <item name="android:layout_height">wrap_content</item>
        <item name="android:textSize">14sp</item>
        <item name="android:gravity">center</item>
    </style>

    <!-- 操作者面板文字样式 -->
    <style name="OperatorPanelTextStyle">
        <item name="android:layout_width">wrap_content</item>
        <item name="android:layout_height">wrap_content</item>
        <item name="android:textSize">11sp</item>
        <item name="android:gravity">start</item>
        <item name="android:fontFamily">monospace</item>
    </style>
</resources>
```

- [ ] **Step 2: 提交**

```bash
git add android_mvp/app/src/main/res/values/themes.xml
git commit -m "feat: update styles for halo-guided UI (remove old button styles)"
```

---

### Task 7: MainActivity Rewrite — 集成所有组件

**Files:**
- Modify: `android_mvp/app/src/main/java/com/gazeinteraction/MainActivity.kt`

> 此任务对 MainActivity.kt 进行结构性重写。核心变化：
> 1. 用 GazeHaloView + TextView 替代旧的单按钮 TextView
> 2. 实现两段式视线反馈（感知 0.5s → 引导）
> 3. 替换纯文字状态栏为连接圆点
> 4. 新增主题选择和切换
> 5. 新增操作者/患者模式分离
> 6. 校准按钮独立，始终可见
> 7. 紧急状态去特殊化（患者端与普通选项相同）

- [ ] **Step 1: 重写 MainActivity.kt**

整个文件内容如下（815 行替换）：

```kotlin
package com.gazeinteraction

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Bundle
import android.util.Log
import android.view.SurfaceView
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.gazeinteraction.camera.CameraManager
import com.gazeinteraction.debug.FaceMeshOverlayView
import com.gazeinteraction.gaze.GazeDetectionAlgorithm
import com.gazeinteraction.mediapipe.FaceLandmarkerHelper
import com.gazeinteraction.mqtt.MqttClient
import com.gazeinteraction.ui.ArcProgressView
import com.gazeinteraction.ui.GazeHaloView
import com.gazeinteraction.ui.ThemeConfig
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class MainActivity : AppCompatActivity(),
    FaceLandmarkerHelper.LandmarkerListener,
    GazeDetectionAlgorithm.GazeListener {

    companion object {
        private const val TAG = "MainActivity"
        private const val PREFS_NAME = "gaze_prefs"
        private const val KEY_DEVICE_ROLE = "device_role"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_THEME = "theme_name"
        private const val KEY_OPERATOR_MODE = "operator_mode"
        private const val MIN_CALIBRATION_SAMPLES = 10
        private const val PERCEPTION_PHASE_MS = 500L
        private const val GAZE_SELECT_THRESHOLD_MS = 1500L
    }

    private enum class ScreenState { IDLE, TRANSITION, SCAN, CONFIRM, FEEDBACK }

    // ---------- UI 组件 ----------
    private lateinit var mainButton: TextView
    private lateinit var gazeHaloView: GazeHaloView
    private lateinit var arcProgressView: ArcProgressView
    private lateinit var gazeStatus: TextView
    private lateinit var confidenceText: TextView
    private lateinit var optionNameText: TextView
    private lateinit var calibrateButton: FloatingActionButton
    private lateinit var settingsButton: FloatingActionButton
    private lateinit var roleButton: FloatingActionButton
    private lateinit var connectionDot: View
    private lateinit var topStatusBar: View
    private lateinit var bottomInfo: View
    private lateinit var centerTextContainer: View

    // ---------- 横屏双按钮 ----------
    private var yesButton: TextView? = null
    private var noButton: TextView? = null
    private var gazeHaloYes: GazeHaloView? = null
    private var gazeHaloNo: GazeHaloView? = null

    // ---------- 音效 ----------
    private var soundPool: SoundPool? = null
    private var soundDingDong = 0
    private var soundWhoosh = 0

    // ---------- 主题 ----------
    private var currentTheme: ThemeConfig = ThemeConfig.WARM_HEALING
    private var isOperatorMode = false

    // ---------- 调试叠加层 ----------
    private var debugPanel: FrameLayout? = null
    private var debugSurfaceView: SurfaceView? = null
    private var faceMeshOverlay: FaceMeshOverlayView? = null
    private var isDebugMode = false

    private val debugSurfaceCallback = object : android.view.SurfaceHolder.Callback {
        override fun surfaceCreated(holder: android.view.SurfaceHolder) {
            if (::cameraManager.isInitialized) cameraManager.setPreviewSurface(holder.surface)
        }
        override fun surfaceChanged(holder: android.view.SurfaceHolder, fmt: Int, w: Int, h: Int) {}
        override fun surfaceDestroyed(holder: android.view.SurfaceHolder) {
            if (::cameraManager.isInitialized) cameraManager.setPreviewSurface(null)
        }
    }

    // ---------- 核心组件 ----------
    private lateinit var cameraManager: CameraManager
    private lateinit var faceLandmarkerHelper: FaceLandmarkerHelper
    private lateinit var gazeDetectionAlgorithm: GazeDetectionAlgorithm
    private lateinit var mqttClient: MqttClient

    // ---------- 状态 ----------
    private var deviceRole: String = "yes"
    private var deviceId: String = ""
    private var isLookingAtScreen = false
    private var currentConfidence: Float = 0.0f
    private var screenState = ScreenState.IDLE
    private var isAnnouncing = false
    private var currentMenuDepth = 0

    // 两段式反馈
    private var gazeStartTimeMs = 0L
    private var perceptionPhaseActive = false
    private var guidancePhaseActive = false

    // ---------- MQTT 节流 ----------
    private var lastPublishTimeMs: Long = 0
    private val PUBLISH_MIN_INTERVAL_MS = 500L

    // ---------- 校准 ----------
    private var isCalibrating = false
    private val calibrationSamples = mutableListOf<Double>()

    private val requestCameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) initializeComponents()
            else showPermissionDeniedMessage()
        }

    // ==================== 生命周期 ====================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        loadPreferences()
        initializeViews()
        checkAndRequestCameraPermission()
    }

    private fun loadPreferences() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        deviceRole = prefs.getString(KEY_DEVICE_ROLE, "yes") ?: "yes"
        currentTheme = ThemeConfig.byName(prefs.getString(KEY_THEME, "温暖疗愈") ?: "温暖疗愈")
        isOperatorMode = prefs.getBoolean(KEY_OPERATOR_MODE, false)
    }

    private fun initializeViews() {
        // 主光圈
        gazeHaloView = findViewById(R.id.gazeHaloView)
        mainButton = findViewById(R.id.mainButton)
        arcProgressView = findViewById(R.id.arcProgressView)
        optionNameText = findViewById(R.id.optionNameText)
        gazeStatus = findViewById(R.id.gazeStatus)
        confidenceText = findViewById(R.id.confidenceText)
        calibrateButton = findViewById(R.id.calibrateButton)
        settingsButton = findViewById(R.id.settingsButton)
        roleButton = findViewById(R.id.roleButton)
        connectionDot = findViewById(R.id.connectionDot)
        topStatusBar = findViewById(R.id.topStatusBar)
        bottomInfo = findViewById(R.id.bottomInfo)
        centerTextContainer = findViewById(R.id.centerTextContainer)

        // 横屏双按钮（可能为 null）
        yesButton = findViewById<TextView?>(R.id.yesButton)
        noButton = findViewById<TextView?>(R.id.noButton)
        gazeHaloYes = findViewById<GazeHaloView?>(R.id.gazeHaloYes)
        gazeHaloNo = findViewById<GazeHaloView?>(R.id.gazeHaloNo)

        applyTheme(currentTheme)
        updateUIForState()
        updateOperatorUI()

        // 校准按钮
        calibrateButton.setOnClickListener { startCalibration() }
        calibrateButton.setOnLongClickListener {
            if (::gazeDetectionAlgorithm.isInitialized) {
                gazeDetectionAlgorithm.resetCalibration()
                Toast.makeText(this, "校准数据已重置", Toast.LENGTH_SHORT).show()
            }
            true
        }

        // 设置按钮（操作者模式）
        settingsButton.setOnClickListener {
            showSettingsDialog()
        }

        // 角色切换
        roleButton.setOnClickListener { showRoleSwitchDialog() }

        // 音效
        val audioAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder().setMaxStreams(2).setAudioAttributes(audioAttrs).build()
        soundDingDong = soundPool?.load(this, R.raw.ding_dong, 1) ?: 0
        soundWhoosh = soundPool?.load(this, R.raw.whoosh, 1) ?: 0

        // 调试
        debugPanel = findViewById(R.id.debugPanel)
        debugSurfaceView = findViewById(R.id.debugSurfaceView)
        faceMeshOverlay = findViewById(R.id.faceMeshOverlay)

        // 双击连接圆点切换调试模式
        connectionDot.setOnClickListener(object : View.OnClickListener {
            private var lastClick = 0L
            override fun onClick(v: View) {
                val now = System.currentTimeMillis()
                if (now - lastClick < 500) toggleDebugMode()
                lastClick = now
            }
        })

        // 长按连接圆点切换操作者模式
        connectionDot.setOnLongClickListener {
            isOperatorMode = !isOperatorMode
            updateOperatorUI()
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_OPERATOR_MODE, isOperatorMode).apply()
            Toast.makeText(this, if (isOperatorMode) "操作者模式" else "患者模式", Toast.LENGTH_SHORT).show()
            true
        }

        // 光圈回调
        gazeHaloView.onPerceptionStart = {
            perceptionPhaseActive = true
        }
        gazeHaloView.onGuidanceStart = {
            guidancePhaseActive = true
        }
    }

    // ==================== 主题管理 ====================

    private fun applyTheme(theme: ThemeConfig) {
        currentTheme = theme
        val rootLayout = findViewById<androidx.constraintlayout.widget.ConstraintLayout>(R.id.rootLayout)
        rootLayout.setBackgroundColor(theme.bgColor)
        mainButton.setTextColor(theme.textPrimary)
        optionNameText.setTextColor(theme.textPrimary)
        gazeStatus.setTextColor(theme.textPrimary)
        confidenceText.setTextColor(theme.textPrimary)
        gazeHaloView.textColor = theme.textPrimary
        gazeHaloView.haloColor = theme.haloColorFor(deviceRole)
        gazeHaloYes?.haloColor = theme.haloYes
        gazeHaloNo?.haloColor = theme.haloNo
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_THEME, theme.name).apply()
    }

    private fun showSettingsDialog() {
        val themeNames = ThemeConfig.ALL.map { it.name }.toTypedArray()
        val currentIdx = ThemeConfig.ALL.indexOf(currentTheme).coerceAtLeast(0)
        val options = arrayOf("切换主题", "切换角色 (当前: ${if (deviceRole == "yes") "是" else "否"})", "操作者模式: ${if (isOperatorMode) "开" else "关"}")
        AlertDialog.Builder(this)
            .setTitle("设置")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showThemePickerDialog(themeNames, currentIdx)
                    1 -> showRoleSwitchDialog()
                    2 -> {
                        isOperatorMode = !isOperatorMode
                        updateOperatorUI()
                        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                            .edit().putBoolean(KEY_OPERATOR_MODE, isOperatorMode).apply()
                    }
                }
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun showThemePickerDialog(themeNames: Array<String>, currentIdx: Int) {
        AlertDialog.Builder(this)
            .setTitle("选择主题")
            .setSingleChoiceItems(themeNames, currentIdx) { dialog, which ->
                applyTheme(ThemeConfig.ALL[which])
                updateUIForState()
                dialog.dismiss()
                Toast.makeText(this, "主题: ${ThemeConfig.ALL[which].name}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun updateOperatorUI() {
        val visibility = if (isOperatorMode) View.VISIBLE else View.GONE
        settingsButton.visibility = visibility
        roleButton.visibility = visibility
        // 患者模式下隐藏置信度文字
        confidenceText.visibility = if (isOperatorMode) View.VISIBLE else View.GONE
    }

    // ==================== UI 状态引擎 ====================

    private fun updateUIForState() {
        val rootLayout = findViewById<androidx.constraintlayout.widget.ConstraintLayout>(R.id.rootLayout)
        when (screenState) {
            ScreenState.IDLE -> {
                isAnnouncing = false
                gazeHaloView.resetToIdle()
                mainButton.visibility = View.VISIBLE
                mainButton.text = "你好"
                mainButton.alpha = 1f
                arcProgressView.visibility = View.GONE
                optionNameText.visibility = View.GONE
                calibrateButton.alpha = 1f
                roleButton.alpha = 1f
                rootLayout.setBackgroundColor(currentTheme.bgColor)
                startBreathingAnimation()
                gazeStatus.text = "注视屏幕开始"
            }
            ScreenState.TRANSITION -> {
                isAnnouncing = false
                mainButton.visibility = View.VISIBLE
                mainButton.text = ""
                optionNameText.visibility = View.GONE
                arcProgressView.visibility = View.GONE
                calibrateButton.alpha = 0.3f
                roleButton.alpha = 0.3f
                stopBreathingAnimation()
            }
            ScreenState.SCAN -> {
                if (isAnnouncing) {
                    mainButton.visibility = View.GONE
                    arcProgressView.visibility = View.GONE
                    stopBreathingAnimation()
                    gazeHaloView.visibility = View.GONE
                } else {
                    val label = if (deviceRole == "yes") "是" else "否"
                    mainButton.visibility = View.VISIBLE
                    mainButton.text = label
                    gazeHaloView.visibility = View.VISIBLE
                    gazeHaloView.labelText = label
                    gazeHaloView.haloColor = currentTheme.haloColorFor(deviceRole)
                    gazeHaloView.enterScanMode()
                    arcProgressView.visibility = View.VISIBLE
                    arcProgressView.arcColor = currentTheme.haloColorFor(deviceRole)
                    applyDepthColor(currentMenuDepth)
                }
                optionNameText.visibility = View.VISIBLE
                calibrateButton.alpha = 0.3f
                roleButton.alpha = 0.3f
                stopBreathingAnimation()
            }
            ScreenState.CONFIRM -> {
                isAnnouncing = false
                val label = if (deviceRole == "yes") "是" else "否"
                mainButton.visibility = View.VISIBLE
                mainButton.text = label
                gazeHaloView.visibility = View.VISIBLE
                gazeHaloView.labelText = label
                gazeHaloView.haloColor = currentTheme.haloColorFor(deviceRole)
                gazeHaloView.enterConfirmMode()
                arcProgressView.visibility = View.VISIBLE
                arcProgressView.arcColor = currentTheme.haloColorFor(deviceRole)
                optionNameText.visibility = View.VISIBLE
                applyDepthColor(99)
                calibrateButton.alpha = 0.3f
                roleButton.alpha = 0.3f
                stopBreathingAnimation()
            }
            ScreenState.FEEDBACK -> {
                isAnnouncing = false
                mainButton.visibility = View.VISIBLE
                mainButton.text = ""
                mainButton.alpha = 1f
                gazeHaloView.resetToIdle()
                arcProgressView.visibility = View.GONE
                optionNameText.visibility = View.VISIBLE
                calibrateButton.alpha = 1f
                roleButton.alpha = 1f
                rootLayout.setBackgroundColor(currentTheme.bgColor)
                stopBreathingAnimation()
            }
        }
    }

    private fun applyDepthColor(depth: Int) {
        val rootLayout = findViewById<android.view.View>(R.id.rootLayout)
        when {
            depth == 0 -> rootLayout.setBackgroundColor(ContextCompat.getColor(this, R.color.bg_level_0))
            depth == 1 -> rootLayout.setBackgroundColor(ContextCompat.getColor(this, R.color.bg_level_1))
            depth >= 2 -> rootLayout.setBackgroundColor(ContextCompat.getColor(this, R.color.bg_level_2))
            depth == 99 -> rootLayout.setBackgroundColor(ContextCompat.getColor(this, R.color.confirm_bg))
        }
    }

    // ==================== 两段式视线反馈 ====================

    private var gazeProgressJob: Job? = null

    private fun onGazeDetectedStart() {
        gazeStartTimeMs = System.currentTimeMillis()
        perceptionPhaseActive = true
        guidancePhaseActive = false
        gazeHaloView.onGazeDetected()
        gazeHaloYes?.onGazeDetected()
        gazeHaloNo?.onGazeDetected()
        startGazeProgress()
    }

    private fun onGazeDetectedEnd() {
        perceptionPhaseActive = false
        guidancePhaseActive = false
        gazeHaloView.onGazeLost()
        gazeHaloYes?.onGazeLost()
        gazeHaloNo?.onGazeLost()
        gazeProgressJob?.cancel()
    }

    private fun startGazeProgress() {
        gazeProgressJob?.cancel()
        gazeStartTimeMs = System.currentTimeMillis()
        gazeProgressJob = lifecycleScope.launch {
            val interval = 50L
            while (isActive && isLookingAtScreen) {
                val elapsed = System.currentTimeMillis() - gazeStartTimeMs
                val progress = (elapsed.toFloat() / GAZE_SELECT_THRESHOLD_MS).coerceAtMost(1f)
                gazeHaloView.arcProgress = progress
                gazeHaloYes?.arcProgress = progress
                gazeHaloNo?.arcProgress = progress
                arcProgressView.progress = progress
                if (perceptionPhaseActive && elapsed >= PERCEPTION_PHASE_MS) {
                    perceptionPhaseActive = false
                    guidancePhaseActive = true
                }
                delay(interval)
            }
        }
    }

    // ==================== 呼吸动画 ====================

    private var breathingJob: Job? = null

    private fun startBreathingAnimation() {
        breathingJob?.cancel()
        breathingJob = lifecycleScope.launch {
            val period = currentTheme.pulsePeriodMs
            val halfPeriod = period / 2
            while (isActive) {
                mainButton.animate().alpha(0.6f).setDuration(halfPeriod).start()
                delay(halfPeriod)
                mainButton.animate().alpha(1.0f).setDuration(halfPeriod).start()
                delay(halfPeriod)
            }
        }
    }

    private fun stopBreathingAnimation() {
        breathingJob?.cancel()
        mainButton.alpha = 1f
    }

    // ==================== 角色切换 ====================

    private fun showRoleSwitchDialog() {
        val options = arrayOf("是 (YES)", "否 (NO)")
        val currentIdx = if (deviceRole == "yes") 0 else 1
        AlertDialog.Builder(this)
            .setTitle("选择本设备角色")
            .setSingleChoiceItems(options, currentIdx) { dialog, which ->
                val newRole = if (which == 0) "yes" else "no"
                deviceRole = newRole
                getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putString(KEY_DEVICE_ROLE, newRole).apply()
                gazeHaloView.haloColor = currentTheme.haloColorFor(newRole)
                updateUIForState()
                dialog.dismiss()
                Toast.makeText(this, "角色: ${if (newRole == "yes") "是" else "否"}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ==================== 调试 ====================

    private fun toggleDebugMode() {
        isDebugMode = !isDebugMode
        debugPanel?.visibility = if (isDebugMode) View.VISIBLE else View.GONE
        if (isDebugMode) {
            debugSurfaceView?.holder?.removeCallback(debugSurfaceCallback)
            debugSurfaceView?.holder?.addCallback(debugSurfaceCallback)
        } else {
            debugSurfaceView?.holder?.removeCallback(debugSurfaceCallback)
            if (::cameraManager.isInitialized) cameraManager.setPreviewSurface(null)
            faceMeshOverlay?.clear()
        }
        Toast.makeText(this, if (isDebugMode) "调试开" else "调试关", Toast.LENGTH_SHORT).show()
    }

    private fun generateDeviceId() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedId = prefs.getString(KEY_DEVICE_ID, null)
        if (!savedId.isNullOrBlank()) {
            deviceId = savedId
            return
        }
        deviceId = "GAZE_${UUID.randomUUID().toString().substring(0, 8).uppercase()}"
        prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
    }

    private fun checkAndRequestCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED -> initializeComponents()
            else -> requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun showPermissionDeniedMessage() {
        Toast.makeText(this, "需要摄像头权限", Toast.LENGTH_LONG).show()
    }

    // ==================== 组件初始化 ====================

    private fun initializeComponents() {
        lifecycleScope.launch {
            runOnUiThread { updateUIForState() }

            try {
                faceLandmarkerHelper = FaceLandmarkerHelper(this@MainActivity, this@MainActivity)
                withContext(Dispatchers.IO) { faceLandmarkerHelper.initialize() }
            } catch (e: Exception) {
                Log.e(TAG, "MediaPipe初始化失败", e)
            }

            try {
                gazeDetectionAlgorithm = GazeDetectionAlgorithm(this@MainActivity)
                gazeDetectionAlgorithm.setGazeListener(this@MainActivity)
            } catch (e: Exception) {
                Log.e(TAG, "视线算法初始化失败", e)
            }

            try {
                cameraManager = CameraManager(this@MainActivity)
                cameraManager.initialize { bitmap ->
                    faceLandmarkerHelper.detectLiveStream(bitmap, System.currentTimeMillis())
                }
                cameraManager.startCamera()
            } catch (e: Exception) {
                Log.e(TAG, "摄像头初始化失败", e)
            }

            try {
                mqttClient = MqttClient(this@MainActivity, deviceId)
                mqttClient.connectionListener = object : MqttClient.ConnectionListener {
                    override fun onConnected() {
                        setConnectionDotStatus("connected")
                        startPeriodicPublish()
                    }
                    override fun onDisconnected() {
                        setConnectionDotStatus("disconnected")
                    }
                    override fun onConnectionFailed(error: String) {
                        setConnectionDotStatus("disconnected")
                    }
                    override fun onMessageReceived(topic: String, message: String) {
                        handleCoordinationMessage(topic, message)
                    }
                }
                setConnectionDotStatus("connecting")
                mqttClient.connect()
            } catch (e: Exception) {
                Log.e(TAG, "MQTT初始化失败", e)
            }
        }
    }

    private fun setConnectionDotStatus(status: String) {
        runOnUiThread {
            val drawable = when (status) {
                "connected" -> R.drawable.dot_status_connected
                "disconnected" -> R.drawable.dot_status_disconnected
                else -> R.drawable.dot_status_idle
            }
            connectionDot.setBackgroundResource(drawable)
        }
    }

    // ==================== MediaPipe 回调 ====================

    override fun onError(error: String, errorCode: Int) {
        Log.e(TAG, "MediaPipe错误: $error")
    }

    override fun onResults(resultBundle: FaceLandmarkerHelper.ResultBundle) {
        if (isDebugMode && faceMeshOverlay != null) {
            try {
                if (resultBundle.results.faceLandmarks().isNotEmpty()) {
                    val landmarks = resultBundle.results.faceLandmarks()[0]
                    runOnUiThread { faceMeshOverlay?.updateLandmarks(landmarks) }
                } else {
                    runOnUiThread { faceMeshOverlay?.clear() }
                }
            } catch (_: Exception) {}
        }

        if (isCalibrating) {
            collectCalibrationSample(resultBundle)
            return
        }

        gazeDetectionAlgorithm.processMediaPipeResults(resultBundle)
    }

    // ==================== 视线回调 ====================

    override fun onGazeAtScreen(confidence: Float) {
        runOnUiThread {
            isLookingAtScreen = true
            currentConfidence = confidence
            onGazeDetectedStart()
            updateScanUI()
            publishState()
            startPeriodicPublish()
        }
    }

    override fun onGazeAway() {
        runOnUiThread {
            isLookingAtScreen = false
            currentConfidence = 0.0f
            onGazeDetectedEnd()
            updateScanUI()
            publishState()
            stopPeriodicPublish()
        }
    }

    private fun updateScanUI() {
        if (screenState != ScreenState.SCAN && screenState != ScreenState.CONFIRM) {
            if (isLookingAtScreen && screenState == ScreenState.IDLE) {
                gazeStatus.text = "注视以唤醒"
            } else if (screenState == ScreenState.IDLE) {
                gazeStatus.text = "注视屏幕开始"
            }
        }
        if (isOperatorMode) {
            confidenceText.text = "置信度: ${(currentConfidence * 100).toInt()}%"
        }
    }

    // ==================== 持续发布 ====================

    private var periodicPublishJob: Job? = null

    private fun startPeriodicPublish() {
        if (periodicPublishJob?.isActive == true) return
        periodicPublishJob = lifecycleScope.launch {
            while (isActive) {
                delay(PUBLISH_MIN_INTERVAL_MS)
                publishState()
            }
        }
    }

    private fun stopPeriodicPublish() {
        periodicPublishJob?.cancel()
    }

    // ==================== 校准 ====================

    private fun startCalibration() {
        if (!::gazeDetectionAlgorithm.isInitialized) {
            Toast.makeText(this, "系统尚未就绪", Toast.LENGTH_SHORT).show()
            return
        }
        if (isCalibrating) {
            Toast.makeText(this, "校准进行中", Toast.LENGTH_SHORT).show()
            return
        }

        calibrationSamples.clear()
        isCalibrating = true

        lifecycleScope.launch {
            for (i in 3 downTo 1) {
                runOnUiThread { gazeStatus.text = "请正视屏幕... $i" }
                delay(1000)
            }

            isCalibrating = false

            if (calibrationSamples.size >= MIN_CALIBRATION_SAMPLES) {
                val avg = calibrationSamples.average()
                gazeDetectionAlgorithm.setCenterBaseline(avg)
                runOnUiThread {
                    gazeStatus.text = "校准完成"
                    Toast.makeText(this@MainActivity, "校准成功", Toast.LENGTH_SHORT).show()
                }
                delay(1500)
                runOnUiThread { updateUIForState() }
            } else {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "样本不足，请重试", Toast.LENGTH_SHORT).show()
                    updateUIForState()
                }
            }
        }
    }

    private fun collectCalibrationSample(resultBundle: FaceLandmarkerHelper.ResultBundle) {
        try {
            if (resultBundle.results.faceLandmarks().isEmpty()) return
            val landmarks = resultBundle.results.faceLandmarks()[0]
            val metrics = gazeDetectionAlgorithm.extractCalibrationMetrics(landmarks)
            if (metrics.eyeOpenness > 0.15) {
                calibrationSamples.add(metrics.pupilRatio)
            }
        } catch (e: Exception) {
            Log.e(TAG, "收集校准样本失败", e)
        }
    }

    // ==================== 协调器消息处理 ====================

    private var delayedUiJob: Job? = null

    private fun handleCoordinationMessage(topic: String, message: String) {
        try {
            val json = org.json.JSONObject(message)
            val type = json.optString("type", "")
            val depth = json.optInt("menuDepth", 0)
            runOnUiThread {
                currentMenuDepth = depth
                when (type) {
                    "idle" -> {
                        screenState = ScreenState.IDLE
                        currentMenuDepth = 0
                        delayedUiJob?.cancel()
                        updateUIForState()
                    }
                    "transition" -> {
                        screenState = ScreenState.TRANSITION
                        delayedUiJob?.cancel()
                        updateUIForState()
                        gazeStatus.text = "即将播放选项..."
                    }
                    "announce" -> {
                        screenState = ScreenState.SCAN
                        isAnnouncing = true
                        delayedUiJob?.cancel()
                        val label = json.optString("optionLabel", "")
                        optionNameText.text = label
                        applyDepthColor(depth)
                        gazeStatus.text = ""
                        updateUIForState()
                    }
                    "scan_progress" -> {
                        screenState = ScreenState.SCAN
                        isAnnouncing = false
                        delayedUiJob?.cancel()
                        val label = json.optString("optionLabel", "")
                        optionNameText.text = label
                        applyDepthColor(depth)
                        gazeStatus.text = "注视[${if (deviceRole == "yes") "是" else "否"}]${if (deviceRole == "yes") "选择" else "跳过"}此项"
                        updateUIForState()
                        arcProgressView.visibility = View.VISIBLE
                    }
                    "confirm" -> {
                        screenState = ScreenState.CONFIRM
                        isAnnouncing = false
                        delayedUiJob?.cancel()
                        val label = json.optString("optionLabel", "")
                        optionNameText.text = "确认: $label"
                        applyDepthColor(99)
                        gazeStatus.text = "注视[是]确认选择"
                        updateUIForState()
                    }
                    "action_feedback" -> {
                        val action = json.optString("action", "")
                        when (action) {
                            "select", "confirm" -> soundPool?.play(soundDingDong, 1f, 1f, 1, 0, 1f)
                            "skip", "cancel" -> soundPool?.play(soundWhoosh, 1f, 1f, 1, 0, 1f)
                        }
                    }
                    "skip_feedback" -> {
                        if (deviceRole == "no") {
                            gazeStatus.text = "已跳过"
                            delayedUiJob?.cancel()
                            delayedUiJob = lifecycleScope.launch {
                                delay(800)
                                gazeStatus.text = ""
                                updateUIForState()
                            }
                        }
                    }
                    "selection" -> {
                        val label = json.optString("optionLabel", "")
                        gazeStatus.text = "已选择: $label"
                    }
                    "executed" -> {
                        screenState = ScreenState.FEEDBACK
                        delayedUiJob?.cancel()
                        updateUIForState()
                        val label = json.optString("optionLabel", "")
                        optionNameText.text = "已通知\n$label"
                        gazeStatus.text = ""
                        lifecycleScope.launch {
                            delay(2000)
                            screenState = ScreenState.IDLE
                            currentMenuDepth = 0
                            updateUIForState()
                        }
                    }
                    "emergency" -> {
                        // 患者端不特殊处理，与普通扫描选项一致
                        screenState = ScreenState.SCAN
                        isAnnouncing = false
                        delayedUiJob?.cancel()
                        val label = json.optString("optionLabel", "紧急")
                        optionNameText.text = label
                        applyDepthColor(depth)
                        gazeStatus.text = "注视[${if (deviceRole == "yes") "是" else "否"}]${if (deviceRole == "yes") "选择" else "跳过"}此项"
                        updateUIForState()
                        arcProgressView.visibility = View.VISIBLE
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "parse coordination msg failed", e)
        }
    }

    // ==================== MQTT ====================

    private fun publishState() {
        val now = System.currentTimeMillis()
        if (now - lastPublishTimeMs < PUBLISH_MIN_INTERVAL_MS) return
        lastPublishTimeMs = now

        lifecycleScope.launch {
            try {
                val data = mapOf(
                    "deviceId" to deviceId,
                    "timestamp" to System.currentTimeMillis(),
                    "role" to deviceRole,
                    "lookingAtScreen" to isLookingAtScreen,
                    "confidence" to currentConfidence,
                    "calibrated" to (::gazeDetectionAlgorithm.isInitialized && gazeDetectionAlgorithm.isCalibrated())
                )
                mqttClient.publishGazeState(data)
            } catch (e: Exception) {
                Log.e(TAG, "MQTT发布失败", e)
            }
        }
    }

    // ==================== 生命周期 ====================

    override fun onResume() {
        super.onResume()
        try { if (::cameraManager.isInitialized) cameraManager.startCamera() } catch (_: Exception) {}
    }

    override fun onPause() {
        super.onPause()
        try { if (::cameraManager.isInitialized) cameraManager.stopCamera() } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            periodicPublishJob?.cancel()
            breathingJob?.cancel()
            gazeProgressJob?.cancel()
            if (::cameraManager.isInitialized) { cameraManager.setPreviewSurface(null); cameraManager.release() }
            if (::faceLandmarkerHelper.isInitialized) faceLandmarkerHelper.clearFaceLandmarker()
            if (::mqttClient.isInitialized) mqttClient.disconnect()
            faceMeshOverlay?.clear()
        } catch (_: Exception) {}
    }
}
```

- [ ] **Step 2: 验证编译**

```bash
cd android_mvp && ./gradlew compileDebugKotlin 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add android_mvp/app/src/main/java/com/gazeinteraction/MainActivity.kt
git commit -m "feat: integrate GazeHaloView with two-phase gaze feedback and theme system"
```

---

### Task 8: Build Verification — 完整构建验证

- [ ] **Step 1: 全量编译**

```bash
cd android_mvp && ./gradlew assembleDebug 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL，APK 生成在 `app/build/outputs/apk/debug/app-debug.apk`

- [ ] **Step 2: 运行单元测试**

```bash
cd android_mvp && ./gradlew testDebugUnitTest 2>&1 | tail -15
```

Expected: BUILD SUCCESSFUL，ThemeConfigTest 通过

- [ ] **Step 3: 提交（如有未提交变更）**

```bash
git status
# 如果无变更则跳过；如有遗漏文件则 add + commit
```

---

## Self-Review

### Spec Coverage Check

| Spec 需求 | 对应 Task |
|-----------|----------|
| 2. 核心视觉概念（屏幕是灯） | Task 3 GazeHaloView RadialGradient |
| 3. 两段式视觉反馈 | Task 3 onGazeDetected() + Task 7 两段式逻辑 |
| 4. 三套主题系统 | Task 1 ThemeConfig + Task 2 colors.xml |
| 5.1 IDLE 状态 | Task 7 updateUIForState IDLE |
| 5.2 TRANSITION | Task 7 updateUIForState TRANSITION |
| 5.3 SCAN 扫描选择 | Task 7 updateUIForState SCAN + 光圈收缩 |
| 5.4 CONFIRM 确认 | Task 7 updateUIForState CONFIRM |
| 5.5 FEEDBACK 反馈 | Task 7 updateUIForState FEEDBACK |
| 5.6 EMERGENCY 去特殊化 | Task 7 handleCoordinationMessage "emergency" |
| 6. 边缘状态层 | Task 5 topStatusBar + connectionDot + Task 7 |
| 7. 视线引导光圈 | Task 3 GazeHaloView |
| 8. 字体排版 | Task 5 layout + Task 6 styles |
| 9. 音效配合 | Task 7 action_feedback + soundPool（保持不变） |

### Placeholder Scan
- 无 TBD/TODO/implement later
- 无 "add appropriate error handling" 空泛表述
- 所有代码步骤均含完整代码

### Type Consistency
- `ThemeConfig.haloColorFor(role: String)` → Task 1 定义，Task 7 `currentTheme.haloColorFor(deviceRole)` 使用 ✓
- `GazeHaloView.onGazeDetected()` → Task 3 定义，Task 7 `gazeHaloView.onGazeDetected()` 使用 ✓
- `GazeHaloView.onGazeLost()` → Task 3 定义，Task 7 `gazeHaloView.onGazeLost()` 使用 ✓
- `GazeHaloView.arcProgress` → Task 3 定义，Task 7 `gazeHaloView.arcProgress = progress` 使用 ✓
- `GazeHaloView.gazePhase` → Task 3 定义 Int，Task 7 通过 `onPerceptionStart`/`onGuidanceStart` 回调使用 ✓
