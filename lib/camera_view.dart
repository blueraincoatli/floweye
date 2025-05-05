import 'package:flutter/material.dart';
import 'package:camera/camera.dart';
import 'package:flutter/services.dart'; // 导入服务包
import 'package:google_mlkit_face_detection/google_mlkit_face_detection.dart';
import '../widgets/face_overlay_painter.dart'; // 确保路径正确
import 'package:shared_preferences/shared_preferences.dart'; // 导入 SharedPreferences
// import '../main.dart'; // 移除未使用的导入

// 添加 InputImageConverter 需要的导入 - 移动到 utils 文件后不再需要
// import 'dart:io';
// import 'package:flutter/foundation.dart';
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
  final FaceDetector _faceDetector = FaceDetector(
    options: FaceDetectorOptions(
      enableContours: true,
      enableLandmarks: true,
      performanceMode: FaceDetectorMode.fast,
    ),
  );
  List<Face> _faces = [];
  Size? _imageSize; // Store image size
  double _minExposure = 0.0;
  double _maxExposure = 0.0;
  double _currentExposure = 0.0;
  bool _isLandscape = false; // 添加状态变量跟踪方向
  InputImageRotation _imageRotation =
      InputImageRotation.rotation270deg; // 默认前置摄像头竖屏

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
        // 横屏时，通常我们希望画面是正的
        // 如果传感器是 90 或 270，需要对应锁定 landscapeRight 或 landscapeLeft
        // 这里假设前置摄像头（通常 sensor=270）锁定为 landscapeLeft 可以得到正向预览
        // 后置摄像头 (通常 sensor=90) 锁定为 landscapeRight
        orientationToLock =
            camera.lensDirection == CameraLensDirection.front
                ? DeviceOrientation.landscapeLeft
                : DeviceOrientation.landscapeRight;
        // 更新 imageRotation 以匹配 ML Kit 的需要
        // 注意：这里的逻辑可能需要根据实际设备和传感器方向进行调整
        // InputImageRotation 需要的是图像相对于竖直向上的方向
        if (camera.lensDirection == CameraLensDirection.front) {
          // 前置横屏，sensor 270，锁定 landscapeLeft
          _imageRotation = InputImageRotation.rotation180deg;
        } else {
          // 后置横屏，sensor 90，锁定 landscapeRight
          _imageRotation = InputImageRotation.rotation0deg;
        }
      } else {
        // 竖屏时，锁定为 portraitUp
        orientationToLock = DeviceOrientation.portraitUp;
        // 更新 imageRotation
        // 前置竖屏，sensor 270 -> rotation270deg
        // 后置竖屏，sensor 90 -> rotation90deg
        _imageRotation =
            InputImageRotationValue.fromRawValue(sensorOrientation) ??
            InputImageRotation.rotation0deg;
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
        if (mounted) {
          setState(() {
            _imageSize = Size(image.width.toDouble(), image.height.toDouble());
            debugPrint(
              "Image size updated from stream: $_imageSize",
            ); // 使用 debugPrint
          });
        }
        // 如果 imageSize 首次获取或发生变化，可能需要重新评估 painter
        // 但由于 setState 已经调用，应该会自动重绘
      }

      // 确保 _imageSize 不为 null 才继续处理
      if (_imageSize == null) {
        debugPrint("Waiting for image size..."); // 使用 debugPrint
        _isDetecting = false;
        return;
      }

      final inputImage = InputImageConverter.fromCameraImage(
        image,
        _cameras![_cameraIndex],
        _imageRotation, // 使用我们计算好的 rotation
      );

      if (inputImage != null) {
        final faces = await _faceDetector.processImage(inputImage);
        if (mounted) {
          setState(() {
            _faces = faces;
          });
        }
      } else {
        debugPrint("InputImage conversion failed."); // 使用 debugPrint
      }
    } catch (e) {
      debugPrint("Error processing image: $e"); // 使用 debugPrint
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
    _faceDetector.close();
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

  @override
  Widget build(BuildContext context) {
    // 在控制器为空或未初始化时显示加载指示器
    if (_controller == null || !_controller!.value.isInitialized) {
      debugPrint(
        "Build: Controller not ready, showing loading indicator.",
      ); // 使用 debugPrint
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }

    debugPrint("Build: Controller ready, building UI."); // 使用 debugPrint
    final size = MediaQuery.of(context).size;
    var camera = _controller!.value;

    // 计算 scale 以适应屏幕
    // 需要根据当前屏幕方向和相机预览图像的固有方向（通常是横向）来计算
    // camera.aspectRatio 是预览图像的宽高比 (width/height)
    // size.aspectRatio 是屏幕的宽高比 (width/height)
    double scale = 1.0;
    double cameraAspectRatio = camera.aspectRatio;

    // MLKit 返回的 imageSize 通常是传感器原始方向的尺寸
    // 而 CameraPreview 渲染的是旋转后适应屏幕方向的图像
    // CameraController.value.aspectRatio 应该是预览画面的实际比例
    debugPrint(
      "Screen size: $size, Screen aspect ratio: ${size.aspectRatio}",
    ); // 使用 debugPrint
    debugPrint(
      "Camera preview aspect ratio: $cameraAspectRatio",
    ); // 使用 debugPrint

    if (cameraAspectRatio > 0) {
      // 防止除零
      // 如果屏幕是竖屏，相机预览是横屏 (常见情况)
      if (size.aspectRatio < 1 && cameraAspectRatio > 1) {
        // scale = screenWidth / previewHeight = screenHeight*screenAspect / previewWidth/previewAspect
        // scale = screenHeight / previewWidth  ( approximately)
        scale = size.aspectRatio * cameraAspectRatio;
      }
      // 如果屏幕是横屏，相机预览也是横屏
      else if (size.aspectRatio > 1 && cameraAspectRatio > 1) {
        scale = size.aspectRatio / cameraAspectRatio;
      }
      // 如果屏幕是竖屏，相机预览也是竖屏 (不常见，但处理一下)
      else if (size.aspectRatio < 1 && cameraAspectRatio < 1) {
        scale = size.aspectRatio / cameraAspectRatio;
      }
      // 如果屏幕是横屏，相机预览是竖屏 (不常见)
      else if (size.aspectRatio > 1 && cameraAspectRatio < 1) {
        scale = size.aspectRatio * cameraAspectRatio; // ? 这个可能需要调整
      }

      // 确保 scale 不会小于 1 (防止缩小)
    if (scale < 1) scale = 1 / scale;
      debugPrint("Calculated scale for CameraPreview: $scale"); // 使用 debugPrint
    } else {
      debugPrint(
        "Warning: Camera aspect ratio is zero or negative.",
      ); // 使用 debugPrint
    }

    return Scaffold(
      // 移除 AppBar，让相机预览全屏
      // appBar: AppBar(
      //   title: const Text('Camera View'),
      // ),
      body: Stack(
        fit: StackFit.expand,
        children: <Widget>[
          // 使用 Transform.scale 保证预览填满屏幕，同时保持比例
          Center(
            child: Transform.scale(
              scale: scale,
              child: CameraPreview(_controller!),
            ),
          ),
          // 绘制人脸框 - 确保所有依赖项都有效
          if (_faces.isNotEmpty &&
              _imageSize != null &&
              _controller != null &&
              _controller!.value.isInitialized &&
              mounted) // 添加 mounted 检查
            LayoutBuilder(
              builder: (context, constraints) {
                // 使用 LayoutBuilder 获取 CustomPaint 的实际尺寸
                final Size canvasSize = constraints.biggest;
                debugPrint(
                  // 使用 debugPrint
                  "CustomPaint canvas size from LayoutBuilder: $canvasSize",
                );
                return CustomPaint(
                  size: canvasSize, // 明确传递尺寸
              painter: FaceOverlayPainter(
                faces: _faces,
                    imageSize: _imageSize!, // 图像原始尺寸
                    imageRotation: _imageRotation, // 使用状态变量中的旋转信息
                cameraLensDirection: _cameras![_cameraIndex].lensDirection,
                    canvasSize: canvasSize, // 传递画布尺寸给 Painter
                    scale: scale, // 传递计算出的 scale 给 Painter
              ),
                );
              },
            ),
          // 添加曝光调节滑块
          _exposureSlider(),
          // 添加方向切换按钮
          _orientationButton(),
        ],
      ),
    );
  }
}

// 更新 InputImageConverter 以接受可选的 rotation - 此类已移至 utils 文件
// class InputImageConverter { ... } // 删除整个类定义
