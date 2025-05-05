import 'package:flutter/material.dart';
import 'package:camera/camera.dart';
import 'package:flutter/services.dart'; // 导入服务包
import 'package:google_mlkit_face_mesh_detection/google_mlkit_face_mesh_detection.dart';
import '../widgets/face_overlay_painter.dart'; // 确保路径正确
import 'package:shared_preferences/shared_preferences.dart'; // 导入 SharedPreferences
import 'dart:math' as math; // 引入math库
// import '../main.dart'; // 移除未使用的导入

// 添加 InputImageConverter 需要的导入 - 移动到 utils 文件后不再需要
// import 'dart:io';
// import 'package:google_mlkit_commons/google_mlkit_commons.dart' as ml_commons;

// 导入新的工具类
import '../utils/input_image_converter.dart';

class CameraView extends StatefulWidget {
  const CameraView({super.key});

  @override
  // ignore: library_private_types_in_public_api
  _CameraViewState createState() => _CameraViewState();
}

// ignore: library_private_types_in_public_api
class _CameraViewState extends State<CameraView> with WidgetsBindingObserver {
  CameraController? _controller;
  List<CameraDescription>? _cameras;
  int _cameraIndex = 0;
  bool _isDetecting = false;
  final FaceMeshDetector _faceMeshDetector = FaceMeshDetector(
    option: FaceMeshDetectorOptions.faceMesh,
  );
  List<FaceMesh> _faces = [];
  Size? _imageSize; // Store image size
  double _minExposure = 0.0;
  double _maxExposure = 0.0;
  double _currentExposure = 0.0;
  bool _isLandscape = false; // 添加状态变量跟踪方向
  InputImageRotation _imageRotation =
      InputImageRotation.rotation270deg; // 默认前置摄像头竖屏
  // 添加调试控制变量
  bool _showDebugInfo = true; // 是否显示调试信息
  bool _showMeshPoints = true; // 是否显示面部网格点

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _loadOrientationPreference(); // 加载保存的方向设置
    // _initializeCamera 移动到 _loadOrientationPreference 之后调用
  }

  // 加载保存的方向设置
  Future<void> _loadOrientationPreference() async {
    final prefs = await SharedPreferences.getInstance();
    setState(() {
      _isLandscape = prefs.getBool('isLandscape') ?? false;
    });
    // 加载设置后初始化相机
    await _setOrientationAndInitializeCamera();
  }

  // 设置屏幕方向并初始化相机
  Future<void> _setOrientationAndInitializeCamera() async {
    List<DeviceOrientation> orientations;
    if (_isLandscape) {
      orientations = [
        DeviceOrientation.landscapeLeft,
        DeviceOrientation.landscapeRight,
      ];
    } else {
      orientations = [
        DeviceOrientation.portraitUp,
        DeviceOrientation.portraitDown,
      ];
    }
    await SystemChrome.setPreferredOrientations(orientations);
    debugPrint(
      "SystemChrome preferred orientations set to: $orientations",
    ); // 使用 debugPrint

    // 确保在设置方向后初始化相机
    await _initializeCamera();
  }

  Future<void> _initializeCamera() async {
    // 如果控制器已存在，先释放
    if (_controller != null) {
      await _controller!.dispose();
      _controller = null;
      debugPrint("Previous camera controller disposed."); // 使用 debugPrint
    }

    _cameras = await availableCameras();
    if (_cameras != null && _cameras!.isNotEmpty) {
      // 优先选择前置摄像头
      _cameraIndex = _cameras!.indexWhere(
        (camera) => camera.lensDirection == CameraLensDirection.front,
      );
      if (_cameraIndex == -1) {
        _cameraIndex = 0; // 如果没有前置摄像头，使用第一个可用的
      }
      await _setupCameraController();
    } else {
      debugPrint("Error: No cameras available"); // 使用 debugPrint
      if (mounted) setState(() {}); // 更新UI显示错误状态
    }
  }

  Future<void> _setupCameraController() async {
    if (_cameras == null || _cameras!.isEmpty || !mounted) return;

    final camera = _cameras![_cameraIndex];
    debugPrint(
      "Setting up camera: ${camera.name} (${camera.lensDirection})",
    ); // 使用 debugPrint

    _controller = CameraController(
      camera,
      ResolutionPreset.high,
      enableAudio: false,
      imageFormatGroup: ImageFormatGroup.yuv420,
    );

    try {
      await _controller!.initialize();
      debugPrint("Camera controller initialized."); // 使用 debugPrint

      // 获取曝光范围
      try {
        _minExposure = await _controller!.getMinExposureOffset();
        _maxExposure = await _controller!.getMaxExposureOffset();
        // 可以设置一个初始曝光值，或者读取保存的值
        _currentExposure = 0.0; // 默认中间值
        await _controller!.setExposureOffset(_currentExposure);
        debugPrint(
          // 使用 debugPrint
          "Exposure range: $_minExposure to $_maxExposure. Current set to $_currentExposure",
        );
      } catch (e) {
        debugPrint("Error getting/setting exposure: $e"); // 使用 debugPrint
        // 如果出错，设置默认范围以防 Slider 报错
        _minExposure = -1.0;
        _maxExposure = 1.0;
        _currentExposure = 0.0;
      }

      // --- 锁定相机捕获方向 ---
      DeviceOrientation orientationToLock;
      int sensorOrientation = camera.sensorOrientation;
      debugPrint(
        "Sensor orientation: $sensorOrientation degrees",
      ); // 使用 debugPrint

      if (_isLandscape) {
        // 横屏模式
        if (camera.lensDirection == CameraLensDirection.front) {
          // 前置摄像头横屏
          orientationToLock = DeviceOrientation.landscapeLeft;
          // 前置摄像头的传感器方向通常为270度
          // 但在横屏模式中，我们需要调整为不同的旋转值
          _imageRotation = InputImageRotation.rotation270deg; // 修改为270度旋转
        } else {
          // 后置摄像头横屏
          orientationToLock = DeviceOrientation.landscapeRight;
          // 后置摄像头传感器方向通常为90度
          _imageRotation = InputImageRotation.rotation90deg; // 修改为90度旋转
        }
        debugPrint("横屏模式: 锁定方向=$orientationToLock, 图像旋转=$_imageRotation");
      } else {
        // 竖屏模式
        orientationToLock = DeviceOrientation.portraitUp;

        // 根据摄像头类型设置不同的旋转
        if (camera.lensDirection == CameraLensDirection.front) {
          // 前置摄像头竖屏的旋转值调整
          _imageRotation = InputImageRotation.rotation90deg; // 修改为90度旋转
        } else {
          // 后置摄像头竖屏通常需要90度旋转
          _imageRotation = InputImageRotation.rotation90deg;
        }
        debugPrint("竖屏模式: 锁定方向=$orientationToLock, 图像旋转=$_imageRotation");
      }

      await _controller!.lockCaptureOrientation(orientationToLock);
      debugPrint(
        "Camera capture orientation locked to: $orientationToLock",
      ); // 使用 debugPrint
      debugPrint("InputImageRotation set to: $_imageRotation"); // 使用 debugPrint
      // --- 锁定结束 ---

      // 开始图像流处理
      await _controller!.startImageStream(_processCameraImage);
      debugPrint("Camera image stream started."); // 使用 debugPrint

      // 获取图像尺寸用于坐标转换 - 这里改为在图像流中获取，避免阻塞初始化
      // final image = await _controller!.takePicture(); // 移除 takePicture
      // final decodedImage = await decodeImageFromList(await image.readAsBytes());
      // _imageSize = Size(
      //   decodedImage.width.toDouble(),
      //   decodedImage.height.toDouble(),
      // );
      // print("Initialized with Image Size: $_imageSize");
    } on CameraException catch (e) {
      debugPrint(
        'Error setting up camera controller: ${e.code}\n${e.description}',
      ); // 使用 debugPrint
      // 如果初始化失败，尝试清理控制器
      _controller?.dispose();
      _controller = null;
    } finally {
      // 确保在状态更新前检查 widget 是否仍然挂载
      if (mounted) {
        setState(() {}); // 更新 UI
        debugPrint("Camera setup finished, UI updated."); // 使用 debugPrint
      }
    }
  }

  Future<void> _processCameraImage(CameraImage image) async {
    if (_isDetecting || !mounted) return;
    _isDetecting = true;

    try {
      // 在图像流中更新图像尺寸，更可靠
      if (_imageSize == null ||
          _imageSize!.width != image.width.toDouble() ||
          _imageSize!.height != image.height.toDouble()) {
        // 对于旋转90/270度的情况，我们需要将宽高交换以匹配ML Kit的处理方式
        bool isRotated =
            _imageRotation == InputImageRotation.rotation90deg ||
            _imageRotation == InputImageRotation.rotation270deg;

        if (mounted) {
          setState(() {
            // 使用原始图像尺寸，确保和ML Kit使用相同的坐标系统
            _imageSize = Size(image.width.toDouble(), image.height.toDouble());
            debugPrint(
              "Image size updated from stream: $_imageSize, isRotated=$isRotated",
            );
          });
        }
      }

      // 确保 _imageSize 不为 null 才继续处理
      if (_imageSize == null) {
        debugPrint("Waiting for image size...");
        _isDetecting = false;
        return;
      }

      final inputImage = InputImageConverter.fromCameraImage(
        image,
        _cameras![_cameraIndex],
        _imageRotation, // 使用我们计算好的 rotation
      );

      if (inputImage != null) {
        final faces = await _faceMeshDetector.processImage(inputImage);
        if (mounted) {
          setState(() {
            _faces = faces;
            if (faces.isNotEmpty) {
              // 输出第一个人脸的边界框信息，帮助调试
              final box = faces.first.boundingBox;
              debugPrint(
                "Face detected: ${box.left},${box.top} - ${box.right},${box.bottom}",
              );
            }
          });
        }
      } else {
        debugPrint("InputImage conversion failed.");
      }
    } catch (e) {
      debugPrint("Error processing image: $e");
    } finally {
      // 加一个小的延迟确保状态更新有机会完成，防止检测过于频繁
      await Future.delayed(const Duration(milliseconds: 50));
      _isDetecting = false;
    }
  }

  // 切换方向的方法
  Future<void> _toggleOrientation() async {
    final prefs = await SharedPreferences.getInstance();
    setState(() {
      _isLandscape = !_isLandscape;
    });
    await prefs.setBool('isLandscape', _isLandscape);
    debugPrint(
      "Orientation toggled. isLandscape: $_isLandscape. Saved to prefs.",
    ); // 使用 debugPrint

    // 停止图像流并重新设置方向和初始化相机
    await _controller?.stopImageStream();
    debugPrint("Image stream stopped for orientation change."); // 使用 debugPrint
    await _setOrientationAndInitializeCamera();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _controller?.stopImageStream(); // 确保流已停止
    _controller?.dispose();
    _faceMeshDetector.close();
    // 退出页面时恢复自动旋转
    SystemChrome.setPreferredOrientations([
      DeviceOrientation.portraitUp,
      DeviceOrientation.portraitDown,
      DeviceOrientation.landscapeLeft,
      DeviceOrientation.landscapeRight,
    ]);
    debugPrint(
      "CameraView disposed, preferred orientations reset.",
    ); // 使用 debugPrint
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    final CameraController? cameraController = _controller;

    // 当 controller 不可用或未初始化时，不执行任何操作
    if (cameraController == null || !cameraController.value.isInitialized) {
      debugPrint(
        // 使用 debugPrint
        "App lifecycle changed to $state, but camera controller is not ready.",
      );
      return;
    }

    if (state == AppLifecycleState.inactive) {
      debugPrint(
        "App became inactive. Disposing camera controller.",
      ); // 使用 debugPrint
      // 异步释放，不需要等待完成
      cameraController
          .dispose()
          .then((_) {
            if (mounted) {
              setState(() {
                _controller = null; // 将控制器置空，以便 UI 显示加载状态
              });
            }
            debugPrint(
              "Camera controller disposed due to inactive state.",
            ); // 使用 debugPrint
          })
          .catchError((e) {
            debugPrint(
              "Error disposing camera controller on inactive state: $e",
            ); // 使用 debugPrint
          });
    } else if (state == AppLifecycleState.resumed) {
      debugPrint("App resumed. Re-initializing camera."); // 使用 debugPrint
      // 重新初始化相机，这会处理方向锁定等
      // _initializeCamera 会检查并释放旧控制器（如果存在）
      if (_controller == null) {
        // 仅当控制器确实被置空时才重新初始化
        _setOrientationAndInitializeCamera(); // 使用包含方向设置的方法
      } else {
        debugPrint(
          // 使用 debugPrint
          "Controller exists, likely didn't finish disposing. Will not re-initialize yet.",
        );
      }
    } else {
      debugPrint("App lifecycle changed to $state"); // 使用 debugPrint
    }
  }

  // 曝光调整 Slider
  Widget _exposureSlider() {
    // 检查曝光范围是否有效
    bool isExposureValid =
        _controller != null &&
        _controller!.value.isInitialized &&
        _minExposure < _maxExposure; // 确保范围有效

    if (!isExposureValid) {
      return const SizedBox.shrink(); // 如果无效，不显示滑块
    }

    return Positioned(
      bottom: 80, // 向上移动一点，避免与方向按钮重叠
      left: 20,
      right: 20,
      child: Container(
        padding: EdgeInsets.symmetric(horizontal: 10, vertical: 5),
        decoration: BoxDecoration(
          color: Colors.black54,
          borderRadius: BorderRadius.circular(10),
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(
              'Exposure: ${_currentExposure.toStringAsFixed(2)}',
              style: TextStyle(color: Colors.white),
            ),
            Slider(
              value: _currentExposure,
              min: _minExposure,
              max: _maxExposure,
              // divisions: (_maxExposure - _minExposure).abs() < 0.1 ? null : 20,
              // label: _currentExposure.toStringAsFixed(2), // Label 现在显示在 Text 里
              onChanged: (value) async {
                // 限制更新频率，避免过于频繁调用 setExposureOffset
                if ((value - _currentExposure).abs() >
                    (_maxExposure - _minExposure) / 100) {
                  if (mounted) {
                    setState(() {
                      _currentExposure = value;
                    });
                  }
                  try {
                    // 使用 then 处理异步操作，不阻塞 UI
                    _controller
                        ?.setExposureOffset(value)
                        .then((_) {
                          // print("Exposure set to $value");
                        })
                        .catchError((e) {
                          debugPrint(
                            "Error setting exposure async: $e",
                          ); // 使用 debugPrint
                        });
                  } catch (e) {
                    // 同步错误（例如控制器无效）
                    debugPrint(
                      "Error setting exposure sync: $e",
                    ); // 使用 debugPrint
                  }
                }
              },
              activeColor: Colors.white,
              inactiveColor: Colors.white30,
            ),
          ],
        ),
      ),
    );
  }

  // 方向切换按钮
  Widget _orientationButton() {
    return Positioned(
      bottom: 20,
      right: 20,
      child: FloatingActionButton(
        onPressed: _toggleOrientation,
        tooltip: 'Toggle Orientation',
        child: Icon(
          _isLandscape
              ? Icons.screen_lock_portrait
              : Icons.screen_lock_landscape,
        ),
      ),
    );
  }

  // 添加一个调试工具栏，可以切换不同的显示模式
  Widget _debugControls() {
    return Positioned(
      top: 80,
      right: 20,
      child: Container(
        padding: const EdgeInsets.all(8),
        decoration: BoxDecoration(
          color: Colors.black54,
          borderRadius: BorderRadius.circular(8),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.end,
          mainAxisSize: MainAxisSize.min,
          children: [
            Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  '调试信息',
                  style: const TextStyle(color: Colors.white, fontSize: 12),
                ),
                Switch(
                  value: _showDebugInfo,
                  onChanged: (value) {
                    setState(() {
                      _showDebugInfo = value;
                    });
                  },
                  activeColor: Colors.green,
                ),
              ],
            ),
            Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  '网格点',
                  style: const TextStyle(color: Colors.white, fontSize: 12),
                ),
                Switch(
                  value: _showMeshPoints,
                  onChanged: (value) {
                    setState(() {
                      _showMeshPoints = value;
                    });
                  },
                  activeColor: Colors.green,
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    // 在控制器为空或未初始化时显示加载指示器
    if (_controller == null || !_controller!.value.isInitialized) {
      debugPrint("Build: Controller not ready, showing loading indicator.");
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }

    debugPrint("Build: Controller ready, building UI.");
    final screenSize = MediaQuery.of(context).size;
    final camera = _controller!.value;

    // 获取相机预览的原始宽高比
    final double cameraAspectRatio = camera.aspectRatio;

    debugPrint(
      "Screen size: $screenSize, Screen aspect ratio: ${screenSize.aspectRatio}",
    );
    debugPrint("Camera preview aspect ratio: $cameraAspectRatio");

    return Scaffold(
      body: Stack(
        fit: StackFit.expand,
        children: <Widget>[
          // 使用简化的布局方式，尝试解决对齐问题
          Center(
            child: AspectRatio(
              aspectRatio: cameraAspectRatio,
              child: ClipRect(
                child: Stack(
                  fit: StackFit.expand,
                  children: [
                    // 相机预览
                    CameraPreview(_controller!),

                    // 人脸覆盖层
                    if (_faces.isNotEmpty &&
                        _imageSize != null &&
                        _controller != null &&
                        _controller!.value.isInitialized &&
                        mounted &&
                        _showMeshPoints)
                      CustomPaint(
                        size: Size.infinite,
                        painter: FaceOverlayPainter(
                          faceMeshes: _faces,
                          imageSize: _imageSize!,
                          imageRotation: _imageRotation,
                          cameraLensDirection:
                              _cameras![_cameraIndex].lensDirection,
                        ),
                      ),
                  ],
                ),
              ),
            ),
          ),

          // 曝光滑块
          _exposureSlider(),

          // 方向切换按钮
          _orientationButton(),

          // 添加调试信息标签
          if (_showDebugInfo)
            Positioned(
              top: 20,
              left: 20,
              child: Container(
                padding: const EdgeInsets.all(8),
                decoration: BoxDecoration(
                  color: Colors.black54,
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text(
                      '方向: ${_isLandscape ? "横屏" : "竖屏"}',
                      style: const TextStyle(color: Colors.white, fontSize: 12),
                    ),
                    Text(
                      '相机: ${_cameras![_cameraIndex].lensDirection.toString()}',
                      style: const TextStyle(color: Colors.white, fontSize: 12),
                    ),
                    Text(
                      '旋转: $_imageRotation',
                      style: const TextStyle(color: Colors.white, fontSize: 12),
                    ),
                    if (_imageSize != null)
                      Text(
                        '图像: ${_imageSize!.width.toInt()}x${_imageSize!.height.toInt()}',
                        style: const TextStyle(
                          color: Colors.white,
                          fontSize: 12,
                        ),
                      ),
                    Text(
                      '屏幕: ${MediaQuery.of(context).size.width.toInt()}x${MediaQuery.of(context).size.height.toInt()}',
                      style: const TextStyle(color: Colors.white, fontSize: 12),
                    ),
                    Text(
                      '人脸: ${_faces.length}',
                      style: const TextStyle(color: Colors.white, fontSize: 12),
                    ),
                    if (_faces.isNotEmpty)
                      Text(
                        '人脸点数: ${_faces.first.points.length}',
                        style: const TextStyle(
                          color: Colors.white,
                          fontSize: 12,
                        ),
                      ),
                    Text(
                      '坐标系: 直接映射(无旋转)',
                      style: const TextStyle(
                        color: Colors.green,
                        fontSize: 12,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ],
                ),
              ),
            ),

          // 添加维度参考线 (如果开启调试)
          if (_showDebugInfo)
            CustomPaint(size: Size.infinite, painter: DimensionGuidesPainter()),

          // 添加调试控制工具栏
          _debugControls(),
        ],
      ),
    );
  }
}

// 更新 InputImageConverter 以接受可选的 rotation - 此类已移至 utils 文件
// class InputImageConverter { ... } // 删除整个类定义

// 添加维度参考线绘制器
class DimensionGuidesPainter extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    final Paint linePaint =
        Paint()
          ..color = Colors.white.withOpacity(0.5)
          ..strokeWidth = 1.0
          ..style = PaintingStyle.stroke;

    // 绘制中心十字线
    canvas.drawLine(
      Offset(size.width / 2, 0),
      Offset(size.width / 2, size.height),
      linePaint,
    );

    canvas.drawLine(
      Offset(0, size.height / 2),
      Offset(size.width, size.height / 2),
      linePaint,
    );

    // 绘制中心圆形
    canvas.drawCircle(
      Offset(size.width / 2, size.height / 2),
      math.min(size.width, size.height) / 6,
      linePaint,
    );
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}
