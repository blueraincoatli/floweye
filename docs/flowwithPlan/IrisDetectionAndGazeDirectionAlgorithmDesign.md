# 虹膜检测与眼球方向判断算法设计文档

## 1. 引言

本文档详细规划了基于 MediaPipe Face Landmarker V5 输出数据，在安卓平台（特别是无 GMS 的华为中低端设备）上实现虹膜检测和眼球方向判断的算法。目标是设计一个能够判断用户是否看向特定设备区域（如屏幕中心）并提供相应置信度的算法，为后续的编码实现提供清晰的技术蓝图。

## 2. 算法输入数据定义

本算法依赖于 MediaPipe Face Landmarker V5 在 `RUNNING_MODE_LIVE_STREAM` 模式下的检测结果。核心输入数据来源于 `FaceLandmarkerResult` 对象中的以下部分：

*   **`faceLandmarks`**: `List<NormalizedLandmarkList>`，包含检测到的人脸的 478 个 3D 特征点。这些点提供人脸关键部位（包括眼睛、虹膜）在模型坐标系中的 3D 坐标 (x, y, z) 以及归一化 2D 坐标 (x, y)。
    *   **关键点提取**: 需要提取以下关键点：
        *   **左右眼轮廓点**: 用于估计眼球中心。MediaPipe Face Mesh 提供了详细的眼部轮廓索引。
        *   **左右眼虹膜点**: 用于估计虹膜中心或方向。MediaPipe 模型提供了虹膜边界点和可能的中心点（具体索引需查阅 MediaPipe Face Geometry 或 Facemesh 定义，通常在 468-487 范围内）。
*   **`facialTransformationMatrixes`**: `List<Matrix>`，一个 4x4 变换矩阵列表，描述了每张检测到人脸的姿态（旋转和平移），将点从规范人脸空间转换到相机空间。对于单人脸检测，列表通常只包含一个矩阵。
    *   **参数提取**: 需要从该矩阵中提取头部在相机坐标系中的旋转和翻译向量（通常是 Rodrigues 向量或欧拉角以及 XYZ 平移）。
*   **`faceBlendshapes`**: `List<Blendshapes>`，一个 Blendshape 列表，描述了人脸的表情和姿态。
    *   **关键参数提取**: 需要提取与眼球运动直接相关的 Blendshape 系数，例如：
        *   `eyeLookUpLeft`, `eyeLookDownLeft`, `eyeLookInLeft`, `eyeLookOutLeft`
        *   `eyeLookUpRight`, `eyeLookDownRight`, `eyeLookInRight`, `eyeLookOutRight`
        这些系数（通常在 0.0 到 1.0 之间）表示对应眼球运动的激活程度。

**数据结构概览**:

```svg
<svg width="600" height="250" xmlns="http://www.w3.org/2000/svg">
  <style>
    .node { fill: #e0e0e0; stroke: #333; stroke-width: 2px; }
    .edge { stroke: #666; stroke-width: 1.5px; marker-end: url(#arrow); }
    .text { font-family: sans-serif; font-size: 14px; fill: #333; text-anchor: middle; }
    .input { fill: #a8d8ff; }
    .output { fill: #a8ffb8; }
    .process { fill: #fff; }
    #arrow { markerUnits: strokeWidth; overflow: visible; }
    #arrow path { fill: #666; stroke: #666; }
  </style>
  <defs>
    <marker id="arrow" viewBox="0 -5 10 10" refX="10" refY="0" markerWidth="10" markerHeight="10" orient="auto">
      <path d="M0,-5L10,0L0,5Z" />
    </marker>
  </defs>

  <!-- Nodes -->
  <rect x="50" y="20" rx="10" ry="10" width="120" height="60" class="node input"/>
  <text x="110" y="50" class="text">faceLandmarks</text>

  <rect x="50" y="90" rx="10" ry="10" width="120" height="60" class="node input"/>
  <text x="110" y="120" class="text">facialTransformation<tspan x="110" dy="1.2em">Matrixes</tspan></text>

  <rect x="50" y="160" rx="10" ry="10" width="120" height="60" class="node input"/>
  <text x="110" y="190" class="text">faceBlendshapes</text>

  <rect x="250" y="90" rx="10" ry="10" width="120" height="60" class="node process"/>
  <text x="310" y="120" class="text">Gaze Algorithm</text>

  <rect x="450" y="90" rx="10" ry="10" width="120" height="60" class="node output"/>
  <text x="510" y="110" class="text">isLookingAtDevice</text>
   <text x="510" y="130" class="text">gazeConfidence</text>


  <!-- Edges -->
  <line x1="170" y1="50" x2="250" y2="120" class="edge"/>
  <line x1="170" y1="120" x2="250" y2="120" class="edge"/>
  <line x1="170" y1="190" x2="250" y2="120" class="edge"/>
  <line x1="370" y1="120" x2="450" y2="120" class="edge"/>

</svg>
```

## 3. 核心算法逻辑详述

本算法旨在结合头部姿态和眼球自身旋转来估计最终的视线方向。考虑到 Blendshape 在描述眼球相对旋转方面的直接性，我们将以 Blendshape 数据为主，结合头部姿态矩阵，计算相机空间中的视线向量。

假设伪代码包含以下核心步骤：

1.  提取并校验输入数据。
2.  从 `facialTransformationMatrix` 计算头部姿态（旋转和翻译）。
3.  从 `faceBlendshapes` 计算眼球相对于头部的旋转角度。
4.  将眼球相对旋转与头部姿态融合，得到眼球在相机坐标系中的绝对方向向量。
5.  定义设备位置参数（屏幕平面在相机坐标系中的位置和方向）。
6.  计算视线向量与设备屏幕平面的交点。
7.  根据交点位置判断是否看向目标区域（如屏幕中心），并计算相对角度。
8.  评估检测过程的质量，生成置信度。

以下详细解释每个步骤：

### 3.1 数据输入与关键点提取

从 MediaPipe 返回的 `FaceLandmarkerResult` 中获取 `faceLandmarks` (取第一个 `NormalizedLandmarkList`，对应第一张脸), `facialTransformationMatrixes` (取第一个 `Matrix`), 和 `faceBlendshapes` (取第一个 `Blendshapes` 列表)。

提取用于后续计算的关键点 3D 坐标和 Blendshape 系数值。例如，通过索引获取左右眼轮廓点和虹膜点的 `x, y, z` 坐标（这些坐标通常是在一个以人脸为中心的局部 3D 空间中，需要通过 `facialTransformationMatrix` 转换到相机空间），以及 `eyeLook` 相关的 Blendshape 值。

### 3.2 头部姿态解析 (From Transformation Matrix)

`facialTransformationMatrixes` 中的 4x4 矩阵 $M_{cam \leftarrow face}$ 描述了从规范人脸坐标系到相机坐标系的变换。这个矩阵的形式通常是：

```latex
$$
M = \begin{pmatrix}
R_{11} & R_{12} & R_{13} & T_x \\
R_{21} & R_{22} & R_{23} & T_y \\
R_{31} & R_{32} & R_{33} & T_z \\
0 & 0 & 0 & 1
\end{pmatrix}
$$
```

其中，左上角的 3x3 子矩阵 $R$ 是旋转矩阵，$(T_x, T_y, T_z)^T$ 是平移向量。

*   **平移向量**: 直接提取矩阵的第四列的前三个元素 $(T_x, T_y, T_z)$。这表示规范人脸原点在相机坐标系中的位置。我们可以将其视为头部的近似中心位置。
*   **旋转矩阵**: 提取左上角的 3x3 矩阵 $R$. 这个矩阵描述了规范人脸坐标系相对于相机坐标系的旋转。
*   **姿态角 (可选)**: 可以将旋转矩阵 $R$ 转换为欧拉角 (Roll, Pitch, Yaw) 或 Rodrigues 向量，以便更直观地理解头部朝向。但对于后续的向量变换，直接使用旋转矩阵 $R$ 更方便。

### 3.3 眼球自身旋转计算 (Blendshapes为主)

MediaPipe 的 `eyeLook` Blendshape 值提供了眼球相对于其在头部坐标系中中立位置的旋转信息。这些值是归一化的，需要映射到实际的角度。

*   **映射 Blendshape 值到角度**:
    *   定义每个 `eyeLook` Blendshape 的最大旋转角度（例如，水平和垂直方向各约 15-20 度）。这些最大角度可能需要通过实验或标定来确定。
    *   将 Blendshape 值线性映射到角度：
        *   水平旋转角度 $\theta_{horz}$: `angle = (eyeLookOut - eyeLookIn) * max_horizontal_angle`
        *   垂直旋转角度 $\theta_{vert}$: `angle = (eyeLookDown - eyeLookUp) * max_vertical_angle`
        注意：左右眼的 `In` 和 `Out` 方向是相反的。例如，左眼的 `eyeLookInLeft` 表示眼球向右旋转（朝向鼻子），而右眼的 `eyeLookInRight` 表示眼球向左旋转（朝向鼻子）。计算时需要区分左右眼，并确保角度方向一致。
*   **构建眼球相对旋转矩阵**:
    *   根据计算出的水平 $\theta_{horz}$ 和垂直 $\theta_{vert}$ 角度，构建相对于眼睛自身坐标系（或头部坐标系中眼球位置的局部坐标系）的 3D 旋转矩阵 $R_{eye\_rel}$. 通常假设眼球旋转是绕其中心进行的，且水平旋转主要绕垂直轴，垂直旋转主要绕水平轴。可以分别构建绕 Y 轴和 X 轴的旋转矩阵，然后相乘得到 $R_{eye\_rel}$.

虽然伪代码可能提到了从关键点计算虹膜偏移向量，但这通常用于基于 2D 图像或简单 3D 模型的几何方法。对于 MediaPipe 提供的 3D 关键点和 Blendshapes，Blendshape 提供的是更直接的旋转信息，且相对更稳定。因此，**建议主要依赖 Blendshapes 来计算眼球的相对旋转**。几何方法可以作为辅助校验或在 Blendshape 不足时的补充。

### 3.4 融合头部姿态与眼球旋转得到相机空间视线向量

*   **定义中立眼球方向**: 在头部坐标系中，假设眼球在中立状态下是向前看的。这个“向前”向量可能是 $(0, 0, 1)$ 或 $(0, 0, -1)$，取决于 MediaPipe 使用的具体头部坐标系定义（通常 Z+ 或 Z- 表示向前）。需要查阅 MediaPipe 的坐标系文档确定。假设中立方向向量为 $V_{eye\_neutral\_face}$.
*   **应用眼球相对旋转**: 将中立眼球方向向量 $V_{eye\_neutral\_face}$ 乘以眼球相对旋转矩阵 $R_{eye\_rel}$ (从 Blendshape 计算得到)，得到眼球在头部坐标系中的实际方向向量 $V_{eye\_actual\_face}$:
    $V_{eye\_actual\_face} = R_{eye\_rel} \times V_{eye\_neutral\_face}$
*   **应用头部姿态旋转**: 将眼球在头部坐标系中的方向向量 $V_{eye\_actual\_face}$ 乘以从 `facialTransformationMatrix` 提取的头部旋转矩阵 $R_{head}$ (即 3.2 节的 3x3 矩阵)，得到眼球在相机坐标系中的方向向量 $V_{gaze\_camera}$:
    $V_{gaze\_camera} = R_{head} \times V_{eye\_actual\_face}$
    $V_{gaze\_camera}$ 是从眼球位置出发指向用户视线目标的单位向量。

*   **眼球在相机空间的位置**: 使用 `facialTransformationMatrix` 的平移向量 $(T_x, T_y, T_z)$ 作为眼球在相机空间中的大致位置 $P_{eye\_camera}$. （更精确的做法是取左右眼轮廓点在头部坐标系中的均值，然后用完整 4x4 矩阵转换到相机空间）。

```svg
<svg width="600" height="450" xmlns="http://www.w3.org/2000/svg">
  <style>
    .node { fill: #e0e0e0; stroke: #333; stroke-width: 2px; }
    .edge { stroke: #666; stroke-width: 1.5px; marker-end: url(#arrow); }
    .text { font-family: sans-serif; font-size: 14px; fill: #333; text-anchor: middle; }
    .input { fill: #a8d8ff; }
    .output { fill: #a8ffb8; }
    .process { fill: #fff; }
    #arrow { markerUnits: strokeWidth; overflow: visible; }
    #arrow path { fill: #666; stroke: #666; }
    .label { font-family: sans-serif; font-size: 12px; fill: #555; }
  </style>
  <defs>
    <marker id="arrow" viewBox="0 -5 10 10" refX="10" refY="0" markerWidth="10" markerHeight="10" orient="auto">
      <path d="M0,-5L10,0L0,5Z" />
    </marker>
  </defs>

  <!-- Nodes -->
  <rect x="50" y="20" rx="10" ry="10" width="120" height="40" class="node input"/>
  <text x="110" y="45" class="text">Blendshapes</text>

  <rect x="50" y="80" rx="10" ry="10" width="120" height="40" class="node input"/>
  <text x="110" y="105" class="text">Transf. Matrix</text>

  <rect x="250" y="20" rx="10" ry="10" width="140" height="40" class="node process"/>
  <text x="320" y="45" class="text">Map to Eye Rotation (Rel)</text>

   <rect x="250" y="80" rx="10" ry="10" width="140" height="40" class="node process"/>
  <text x="320" y="105" class="text">Parse Head Pose (Abs)</text>

  <rect x="450" y="50" rx="10" ry="10" width="140" height="40" class="node process"/>
  <text x="520" y="75" class="text">Combine Eye & Head Pose</text>

  <rect x="450" y="180" rx="10" ry="10" width="140" height="40" class="node output"/>
  <text x="520" y="205" class="text">Gaze Vector (Camera Space)</text>

  <rect x="50" y="280" rx="10" ry="10" width="120" height="60" class="node input"/>
  <text x="110" y="310" class="text">Device Position<tspan x="110" dy="1.2em">Parameters</tspan></text>

   <rect x="250" y="280" rx="10" ry="10" width="140" height="60" class="node process"/>
  <text x="320" y="310" class="text">Define Screen Plane<tspan x="320" dy="1.2em">in Camera Space</tspan></text>


   <rect x="450" y="280" rx="10" ry="10" width="140" height="60" class="node process"/>
  <text x="520" y="310" class="text">Calculate Ray-Plane<tspan x="520" dy="1.2em">Intersection</tspan></text>

   <rect x="250" y="380" rx="10" ry="10" width="140" height="60" class="node process"/>
  <text x="320" y="410" class="text">Calculate Relative<tspan x="320" dy="1.2em">Angle to Target</tspan></text>

   <rect x="450" y="380" rx="10" ry="10" width="140" height="40" class="node output"/>
  <text x="520" y="405" class="text">isLookingAtDevice</text>


  <!-- Edges -->
  <line x1="170" y1="40" x2="250" y2="40" class="edge"/>
   <line x1="170" y1="100" x2="250" y2="100" class="edge"/>

    <line x1="390" y1="40" x2="450" y2="70" class="edge"/>
    <line x1="390" y1="100" x2="450" y2="70" class="edge"/>

     <line x1="590" y1="70" x2="590" y2="180" class="edge"/>
     <line x1="590" y1="180" x2="590" y2="280" class="edge"/>
      <line x1="520" y1="220" x2="520" y2="280" class="edge"/> <!-- Gaze Vector to Intersection -->

    <line x1="170" y1="310" x2="250" y2="310" class="edge"/>
     <line x1="390" y1="310" x2="450" y2="310" class="edge"/>

     <line x1="520" y1="320" x2="520" y2="380" class="edge"/> <!-- Intersection to Angle -->
      <line x1="590" y1="310" x2="590" y2="380" class="edge"/> <!-- Screen Plane to Angle -->

    <line x1="390" y1="410" x2="450" y2="400" class="edge"/>


  <!-- Labels -->
  <text x="420" y="150" class="label" transform="rotate(90 420,150)">Combined Gaze</text>
  <text x="420" y="250" class="label" transform="rotate(90 420,250)">Screen Target</text>


</svg>
```

### 3.5 设备参数定义与屏幕目标区域

为了判断用户是否看向设备屏幕，需要定义屏幕在相机坐标系中的位置和朝向。这部分信息通常是固定的（对于前置摄像头），需要在应用中配置。

*   **设备位置参数**: 定义屏幕平面在相机坐标系中的参数。最简化的模型可以假定屏幕中心位于相机前方某个固定距离上，屏幕法线方向与相机光轴方向平行。更精确的模型需要考虑屏幕的物理尺寸、分辨率、以及相机坐标系原点到屏幕四个角的 3D 坐标。这些参数可以**预先测量或通过简单的用户校准过程来确定**。
    *   例如：定义屏幕中心在相机坐标系中的 3D 位置 $P_{screen\_center\_camera}$ 和屏幕平面的法向量 $N_{screen\_camera}$。
*   **屏幕目标区域**: 定义屏幕上被视为“看向”目标的区域。最简单是整个屏幕，或者屏幕中心的一个矩形区域。这个区域可以定义为屏幕坐标系（通常左上角为 (0,0)，右下角为 (width, height)）中的一个矩形。

### 3.6 视线与屏幕目标的相对位置及角度计算

*   **视线射线**: 视线可以表示为一条射线：起点是眼球在相机空间的位置 $P_{eye\_camera}$，方向是视线向量 $V_{gaze\_camera}$。
*   **计算射线与屏幕平面的交点**: 使用射线与平面的交点公式，计算视线射线与屏幕平面（由 $P_{screen\_center\_camera}$ 和 $N_{screen\_camera}$ 定义）的交点 $P_{intersection\_camera}$。
    *   如果射线与平面平行或不相交（例如视线方向远离屏幕），则无交点。
*   **判断是否看向目标区域**:
    *   将相机空间中的交点 $P_{intersection\_camera}$ 转换到屏幕坐标系。这需要知道相机内参（焦距、主点等）以及屏幕的物理尺寸和分辨率。一个简化的方法是利用透视投影原理，将 $P_{intersection\_camera}$ 投影到屏幕平面上，然后根据屏幕的物理尺寸和分辨率进行缩放和平移，得到屏幕像素坐标 $(u, v)$。
    *   检查计算出的屏幕坐标 $(u, v)$ 是否落在预定义的屏幕目标区域范围内。
    *   另一种方法是计算眼球位置 $P_{eye\_camera}$ 到屏幕目标区域中心（或代表点）的向量 $V_{eye\_to\_target}$. 计算 $V_{gaze\_camera}$ 和 $V_{eye\_to\_target}$ 之间的夹角。如果夹角小于某个阈值，则认为用户正在看向该目标区域。这种方法不依赖于屏幕尺寸和分辨率，更侧重于视线方向的相对性。**推荐采用计算角度的方法，因为它更符合“看向”的定义，且对屏幕参数依赖较少。**
*   **计算相对角度**: 计算向量 $V_{gaze\_camera}$ 与向量 $V_{eye\_to\_target}$ 之间的夹角 $\alpha$。
    $\alpha = \operatorname{arccos}\left( \frac{V_{gaze\_camera} \cdot V_{eye\_to\_target}}{|V_{gaze\_camera}| |V_{eye\_to\_target}|} \right)$
    其中 $V_{eye\_to\_target}$ 是从 $P_{eye\_camera}$ 到屏幕目标区域代表点 $P_{target\_camera}$ 的向量，$V_{eye\_to\_target} = P_{target\_camera} - P_{eye\_camera}$。选择屏幕中心作为 $P_{target\_camera}$ 是一个合理的起点。
*   **判断逻辑**: 如果计算出的夹角 $\alpha$ 小于某个预设阈值 $\theta_{gaze\_threshold}$，则判断 `isLookingAtDevice = true`。否则 `isLookingAtDevice = false`。

### 3.7 检测质量评估与置信度生成

为了提供判断的可信度，需要评估输入数据的质量和算法计算的稳定性。

*   **输入数据质量**:
    *   **MediaPipe 置信度**: 考虑 MediaPipe 内部可能提供的单帧检测置信度（如果可用）。
    *   **关键点数量和分布**: 检查眼睛和虹膜区域的关键点是否被充分检测到，是否存在明显的离群点。如果关键点稀疏或不稳定，降低置信度。
    *   **Blendshape 有效性**: 检查 `eyeLook` Blendshape 值是否在合理范围内（例如，不应该持续为 0 或 1，除非眼睛极端转动）。
*   **算法稳定性**:
    *   **帧间一致性**: 比较当前帧的头部姿态和眼球方向与前几帧的结果。如果变化过大（超出正常运动范围），可能表示检测不稳定，降低置信度。可以采用简单的移动平均或更复杂的卡尔曼滤波来平滑结果并评估稳定性。
*   **融合策略**: 如果融合了几何方法和 Blendshape 方法，评估两者结果的一致性。不一致时降低置信度。
*   **置信度计算**:
    *   将上述因素结合起来，计算一个综合的置信度分数 `gazeConfidence`，范围通常为 0.0 到 1.0。例如，可以对各项质量指标进行加权平均，或者使用基于规则的系统（如“如果关键点少于 X 个，则置信度为 0.5”）。
    *   最终的 `isLookingAtDevice` 判断可以不仅仅依赖角度阈值，还可以结合置信度：例如，只有当 `gazeConfidence` 高于某个阈值 AND 角度小于 $\theta_{gaze\_threshold}$ 时，才判断为 `true`。

```svg
<svg width="600" height="200" xmlns="http://www.w3.org/2000/svg">
  <style>
    .node { fill: #e0e0e0; stroke: #333; stroke-width: 2px; }
    .edge { stroke: #666; stroke-width: 1.5px; marker-end: url(#arrow); }
    .text { font-family: sans-serif; font-size: 14px; fill: #333; text-anchor: middle; }
    .input { fill: #a8d8ff; }
    .output { fill: #a8ffb8; }
    .process { fill: #fff; }
    #arrow { markerUnits: strokeWidth; overflow: visible; }
    #arrow path { fill: #666; stroke: #666; }
  </style>
  <defs>
    <marker id="arrow" viewBox="0 -5 10 10" refX="10" refY="0" markerWidth="10" markerHeight="10" orient="auto">
      <path d="M0,-5L10,0L0,5Z" />
    </marker>
  </defs>

  <!-- Nodes -->
  <rect x="50" y="20" rx="10" ry="10" width="140" height="40" class="node input"/>
  <text x="120" y="45" class="text">Raw MP Results</text>

  <rect x="50" y="70" rx="10" ry="10" width="140" height="40" class="node input"/>
  <text x="120" y="95" class="text">Previous Frame Results</text>

  <rect x="250" y="45" rx="10" ry="10" width="140" height="60" class="node process"/>
  <text x="320" y="75" class="text">Evaluate Input Quality<tspan x="320" dy="1.2em">and Stability</tspan></text>


   <rect x="450" y="45" rx="10" ry="10" width="140" height="40" class="node output"/>
  <text x="520" y="70" class="text">gazeConfidence (0-1)</text>


  <!-- Edges -->
  <line x1="190" y1="40" x2="250" y2="65" class="edge"/>
  <line x1="190" y1="90" x2="250" y2="65" class="edge"/>
  <line x1="390" y1="75" x2="450" y2="65" class="edge"/>

</svg>
```

## 4. 算法输出结果定义

算法的最终输出应清晰且易于使用。核心输出包括：

*   **`isLookingAtDevice`**: Boolean 值，表示算法判断用户是否正在看向设备屏幕上的目标区域。
*   **`gazeConfidence`**: Float 值，范围 [0.0, 1.0]，表示本次判断的可信度。1.0 表示高度可信，0.0 表示完全不可信。
*   **`gazeRelativeAngle` (可选)**: Float 值，例如以度为单位，表示视线向量与指向屏幕目标区域向量之间的夹角。这可以提供更精细的视线方向信息。
*   **`gazeIntersectionPointOnScreen` (可选)**: PointF 或类似的结构，表示视线与屏幕平面交点在屏幕坐标系（如像素坐标）中的位置。

这些输出可以通过回调函数或事件总线传递给应用的 UI 或其他模块。

## 5. 移动设备实现考量与优化

在安卓移动设备（特别是中低端华为无 GMS 设备）上实现此算法，需要充分考虑性能和精度。

### 5.1 性能瓶颈与优化方向

*   **MediaPipe 推理速度**: 这是最主要的性能瓶颈。Face Landmarker V5 模型本身较大，推理计算量高。
    *   **优化**:
        *   **Delegate 选择**: 如参考文档所述，强制使用 `Delegate.CPU` 并确保 TFLite 后端优化（如 XNNPack）被启用通常是最稳健的方案。在特定设备上，可以测试 `Delegate.NNAPI` 或 `Delegate.GPU` 的性能，但需警惕其兼容性和稳定性问题。
        *   **输入分辨率/帧率**: 降低摄像头输入 MediaPipe 的分辨率或帧率。在保证能识别到关键特征的前提下，选择最低可接受的分辨率（如 480p 或 720p）。摄像头帧率可以设置为 20-30 fps。
        *   **`numFaces`**: 设置为 1，只检测单人脸。
        *   **`minFace...Confidence` 阈值**: 适当提高这些阈值可以减少处理低质量或部分遮挡帧的开销，但可能降低检测率。
*   **数据预处理与转换**: 将 Android `Image` 或 `ImageProxy` 转换为 MediaPipe `MPImage` 需要高效的图像处理（颜色空间转换、裁剪、缩放等）。
    *   **优化**: 使用 MediaPipe 提供的工具类（如 `ImageProxyUtil`，如果可用且兼容）或优化过的图像处理库。尽量避免不必要的内存拷贝。在处理回调中，尽快处理图像并释放资源 (`image.close()`, `imageProxy.close()`).
*   **算法计算**: 算法核心逻辑（向量/矩阵运算、几何计算）计算量相对较小，通常不是性能瓶颈。
*   **UI 绘制与同步**: 在 UI 线程绘制结果需要与 MediaPipe 的异步处理结果同步，避免卡顿。
    *   **优化**: 绘制逻辑应尽量轻量化。在后台线程进行所有计算，只在 UI 线程更新需要绘制的数据。

### 5.2 精度挑战与提升策略

*   **MediaPipe 准确性**: 虹膜和眼部关键点的检测精度受光照、面部角度、遮挡、用户个体差异等因素影响。Blendshape 的准确性也类似。
    *   **策略**:
        *   **优化摄像头配置**: 锁定曝光和对焦（如参考文档第 2 节所述），确保输入图像质量稳定。
        *   **鲁棒的关键点处理**: 对输入关键点进行有效性检查（如根据前后帧位置判断是否跳变过大），剔除异常点。
        *   **Blendshape 映射标定**: Blendshape 值到角度的映射系数 ($\max\_angle$) 对精度影响很大，可能需要针对目标设备或通用模型进行微调和标定。
*   **头部姿态准确性**: `facialTransformationMatrix` 的准确性影响视线向量在相机空间中的方向。
    *   **策略**: MediaPipe 提供的矩阵通常基于关键点拟合，其精度受关键点质量影响。确保关键点检测的稳定性。
*   **设备参数准确性**: 屏幕平面在相机空间中的位置和朝向参数是计算视线交点的关键。
    *   **策略**: **精确测量或标定这些参数**。一个简单的校准流程可以让用户在应用启动时看向屏幕中心，然后利用此时的头部姿态和眼球方向（Blendshape 趋近中立）来推算屏幕相对于相机的位置。
*   **融合策略**: 如果尝试融合 Blendshape 和几何方法，需要精心设计融合权重或逻辑，以利用各自的优势并规避缺点。基于 Blendshape 的方法对头部姿态变化不敏感，但可能受 Blendshape 训练数据限制；几何方法直接利用点位置，但对点抖动敏感。
*   ** temporal 稳定性**: 单帧结果可能不稳定，容易抖动。
    *   **策略**: 对最终的视线方向、相对角度或置信度进行时间上的平滑处理（如一阶滞后滤波或移动平均），提高结果的稳定性。

### 5.3 适配无 GMS 华为设备的额外考量

除了性能和通用精度考量，无 GMS 环境下的特定问题：

*   **TFLite Delegate**: 再次强调，强制或优先使用 CPU delegate，并进行充分测试。
*   **Camera API**: 如参考文档所述，优先考虑使用 Camera2 API 进行摄像头采集和控制，其原生性使其在无 GMS 环境下兼容性风险较低。CameraX 可能依赖 GMS 或存在其他兼容问题。
*   **第三方库依赖**: 仔细检查项目中引入的所有第三方库（包括 MediaPipe 的依赖库）是否存在对 GMS 的硬依赖。使用 `tasks-vision-android` 版本而非 `tasks-vision-play-services`。
*   **性能分析工具**: 在目标设备上进行充分的性能测试和剖析（使用 Android Studio Profiler 或其他工具），定位实际瓶颈。

## 6. 结论与实现蓝图

本文档详细阐述了基于 MediaPipe Face Landmarker V5 输出数据实现虹膜检测和眼球方向判断的核心算法设计。算法主要依赖于 MediaPipe 提供的头部姿态矩阵和眼球相关的 Blendshape 系数来计算相机空间中的视线向量，并通过定义设备参数来判断视线是否指向屏幕目标区域并计算置信度。

为了在无 GMS 的华为中低端设备上实现稳定、准确的检测，关键在于：

*   **优化 MediaPipe 配置**: 选择合适的 Delegate (优先 CPU)，调整输入分辨率和帧率。
*   **使用 Camera2 API**: 增强摄像头控制和数据采集的兼容性。
*   **精确的设备参数**: 通过测量或简单校准获取屏幕相对于相机的准确位置信息。
*   **鲁棒的后处理**: 对关键点、Blendshape 进行校验，对结果进行时间平滑。
*   **全面的设备兼容性与性能测试**: 在实际目标设备上进行多场景、长时间的测试，不断优化参数和实现细节。

**实现蓝图**:

1.  **数据获取模块**: 集成 MediaPipe Tasks Vision 库，实现摄像头帧捕获（使用 Camera2 API），将帧传递给 Face Landmarker 进行异步检测，并在回调中获取 `FaceLandmarkerResult`。
2.  **数据解析与预处理模块**: 从 `FaceLandmarkerResult` 中提取头部姿态矩阵、眼部关键点和 `eyeLook` Blendshape 系数。对关键点进行初步校验。
3.  **姿态与眼球方向计算模块**:
    *   实现从 4x4 矩阵解析头部旋转和平移。
    *   实现将 `eyeLook` Blendshape 系数映射到眼球相对旋转角度的逻辑。
    *   实现将眼球相对旋转与头部姿态旋转融合，计算相机空间视线向量和眼球位置。
4.  **设备参数与目标判断模块**:
    *   硬编码或提供界面配置设备参数（屏幕相对于相机的位置/朝向）。
    *   实现计算视线向量与屏幕平面交点或视线与屏幕中心连线夹角的逻辑。
    *   实现判断是否看向目标区域的逻辑（基于角度阈值）。
5.  **质量评估与置信度模块**:
    *   实现评估输入数据质量、帧间一致性的逻辑。
    *   实现计算综合置信度的逻辑。
6.  **输出模块**: 将 `isLookingAtDevice`, `gazeConfidence` 等结果通过接口或回调暴露出去。
7.  **性能监控与日志**: 集成性能监控工具，记录关键环节的耗时和潜在错误日志，便于调试和优化。
8.  **校准流程 (可选)**: 开发一个简易的用户校准界面，用于优化 Blendshape 映射参数或设备位置参数。

编码实现应严格遵循此设计文档，优先实现核心功能，然后进行迭代优化和在目标设备上的全面测试。