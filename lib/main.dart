import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:camera/camera.dart';
import 'package:flutter/services.dart'; // For SystemChrome
// import 'package:google_mlkit_face_detection/google_mlkit_face_detection.dart'; // 移除未使用导入
import 'package:floweye/services/face_detection_service.dart'; // 更新导入路径
// ignore: unused_import
import 'package:floweye/utils/input_image_converter.dart'; // 更新导入路径
import 'package:floweye/widgets/face_overlay_painter.dart'; // 更新导入路径
import 'package:shared_preferences/shared_preferences.dart'; // Import SharedPreferences
import 'package:google_mlkit_face_mesh_detection/google_mlkit_face_mesh_detection.dart'; // 添加 FaceMesh 导入
// For debugPrint
// Import math for max function

// Global variable to store the list of available cameras.
List<CameraDescription> cameras = [];

// Define a global variable or use a state management approach for selected orientation
// For simplicity, here we'll use a simple variable, but in a real project, it's recommended to use state management
// Note: This global variable approach is not recommended for large projects
AppOrientation currentOrientation = AppOrientation.portrait;

enum AppOrientation { portrait, landscape }

Future<void> main() async {
  // Ensure that plugin services are initialized so that `availableCameras()`
  // can be called before `runApp()`
  WidgetsFlutterBinding.ensureInitialized();

  // Read saved orientation settings
  final prefs = await SharedPreferences.getInstance();
  // Default to portrait if 'isLandscape' doesn't exist or is false
  final isLandscape = prefs.getBool('isLandscape') ?? false;
  currentOrientation =
      isLandscape ? AppOrientation.landscape : AppOrientation.portrait;

  // Lock screen orientation based on settings
  if (isLandscape) {
    await SystemChrome.setPreferredOrientations([
      DeviceOrientation.landscapeLeft,
      DeviceOrientation.landscapeRight,
    ]);
  } else {
    await SystemChrome.setPreferredOrientations([
      DeviceOrientation.portraitUp,
      DeviceOrientation.portraitDown,
    ]);
  }

  // Obtain a list of the available cameras on the device.
  try {
    cameras = await availableCameras();
  } on CameraException catch (e) {
    // Log the error to the console.
    // Consider showing a user-friendly error message.
    debugPrint(
      'Error initializing cameras: ${e.code} - ${e.description}',
    ); // Use debugPrint
    // Depending on the error, you might want to exit the app
    // or proceed without camera functionality.
  }

  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  // This widget is the root of your application.
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Eye Direction Array Client',
      theme: ThemeData(
        // This is the theme of your application.
        //
        // TRY THIS: Try running your application with "flutter run". You'll see
        // the application has a purple toolbar. Then, without quitting the app,
        // try changing the seedColor in the colorScheme below to Colors.green
        // and then invoke "hot reload" (save your changes or press the "hot
        // reload" button in a Flutter-supported IDE, or press "r" if you used
        // the command line to start the app).
        //
        // Notice that the counter didn't reset back to zero; the application
        // state is not lost during the reload. To reset the state, use hot
        // restart instead.
        //
        // This works for code too, not just values: Most code changes can be
        // tested with just a hot reload.
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.deepPurple),
        useMaterial3: true,
      ),
      home: const CameraScreen(), // Start with the camera screen
    );
  }
}

class CameraScreen extends StatefulWidget {
  const CameraScreen({super.key});

  @override
  State<CameraScreen> createState() => _CameraScreenState();
}

// ignore: library_private_types_in_public_api
class _CameraScreenState extends State<CameraScreen>
    with WidgetsBindingObserver {
  CameraController? _controller;
  bool _isCameraInitialized = false;
  CameraDescription? _selectedCamera;
  final ResolutionPreset _resolutionPreset = ResolutionPreset.high;
  bool _isProcessingImage = false; // Flag for image stream processing
  bool _isInitializing = false; // Flag for camera initialization process

  // Face Detection Service
  late final FaceDetectionService _faceDetectionService;
  List<FaceMesh> _detectedFaces = [];
  Size? _imageSize;
  InputImageRotation? _imageRotation;
  double _minExposure = 0.0;
  double _maxExposure = 0.0;
  double _currentExposure = 0.0;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    SystemChrome.setEnabledSystemUIMode(
      SystemUiMode.immersiveSticky,
    ); // Fullscreen

    _faceDetectionService = FaceDetectionService();

    CameraDescription? frontCamera;
    for (var cam in cameras) {
      if (cam.lensDirection == CameraLensDirection.front) {
        frontCamera = cam;
        break;
      }
    }
    _selectedCamera = frontCamera;

    if (_selectedCamera != null) {
      debugPrint("Front camera found. Initializing..."); // Use debugPrint
      _initializeCamera();
    } else {
      debugPrint("Error: Front camera not found."); // Use debugPrint
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text("错误：未找到前置摄像头！"),
              duration: Duration(seconds: 5),
            ),
          );
        }
      });
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    // Ensure controller is disposed properly
    _controller
        ?.dispose()
        .then((_) {
          debugPrint(
            "CameraController disposed in Widget dispose.",
          ); // Use debugPrint
        })
        .catchError((e) {
          debugPrint(
            // Use debugPrint
            "Error disposing CameraController in Widget dispose: $e",
          );
        });
    _faceDetectionService.dispose();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    debugPrint("App lifecycle state changed: $state"); // Use debugPrint

    // If camera was never initialized (e.g., no front camera found), do nothing.
    if (_selectedCamera == null) return;

    switch (state) {
      case AppLifecycleState.inactive:
        debugPrint(
          "App inactive. Disposing camera controller...",
        ); // Use debugPrint
        // Set flag to prevent further processing while inactive
        _isCameraInitialized = false;
        // Dispose the controller. Dispose handles stopping the stream.
        _controller
            ?.dispose()
            .then((_) {
              debugPrint(
                // Use debugPrint
                "CameraController disposed due to inactive state.",
              );
              _controller =
                  null; // Set controller to null after successful dispose
            })
            .catchError((e) {
              debugPrint(
                // Use debugPrint
                "Error disposing CameraController in inactive state: $e",
              );
              // Even if dispose fails, try setting to null
              _controller = null;
            });
        // No need to call setState here, build method will handle _isCameraInitialized being false.
        break;
      case AppLifecycleState.resumed:
        debugPrint("App resumed."); // Use debugPrint
        // If the controller is null or wasn't initialized, try initializing.
        if (_controller == null || !_isCameraInitialized) {
          debugPrint("Camera needs re-initialization."); // Use debugPrint
          _initializeCamera();
        } else {
          debugPrint(
            // Use debugPrint
            "Camera controller exists. State: initialized=$_isCameraInitialized",
          );
          // Optional: Check if stream needs restarting (though init should handle it)
        }
        break;
      case AppLifecycleState.paused:
        debugPrint("App paused."); // Use debugPrint
        // Often similar to inactive, ensure controller might be disposed if needed
        // Depending on OS behavior, inactive might be followed by paused.
        // If camera is still active here, consider disposing like in inactive.
        if (_controller != null && _isCameraInitialized) {
          debugPrint(
            "Controller still active in paused state. Disposing.",
          ); // Use debugPrint
          _isCameraInitialized = false;
          _controller
              ?.dispose()
              .then((_) {
                debugPrint(
                  "Controller disposed due to paused state.",
                ); // Use debugPrint
                _controller = null;
              })
              .catchError((e) {
                debugPrint("Error disposing in paused: $e"); // Use debugPrint
                _controller = null;
              });
        }
        break;
      case AppLifecycleState.detached:
        debugPrint("App detached."); // Use debugPrint
        // Similar to inactive/paused, ensure resources are released.
        if (_controller != null) {
          _isCameraInitialized = false;
          _controller
              ?.dispose()
              .then((_) {
                debugPrint(
                  "Controller disposed due to detached state.",
                ); // Use debugPrint
                _controller = null;
              })
              .catchError((e) {
                debugPrint("Error disposing in detached: $e"); // Use debugPrint
                _controller = null;
              });
        }
        break;
      // Handle hidden state if necessary for Flutter 3.13+
      case AppLifecycleState.hidden:
        debugPrint("App hidden."); // Use debugPrint
        // Similar logic to inactive/paused might be needed
        if (_controller != null) {
          _isCameraInitialized = false;
          _controller
              ?.dispose()
              .then((_) {
                debugPrint(
                  "Controller disposed due to hidden state.",
                ); // Use debugPrint
                _controller = null;
              })
              .catchError((e) {
                debugPrint("Error disposing in hidden: $e"); // Use debugPrint
                _controller = null;
              });
        }
        break;
    }
  }

  Future<void> _initializeCamera() async {
    if (_selectedCamera == null || _isInitializing) return;

    // Prevent multiple initialization attempts concurrently
    _isInitializing = true;
    if (mounted) {
      setState(() {
        _isCameraInitialized = false; // Show loading indicator during init
      });
    }

    // Dispose previous controller if it exists
    if (_controller != null) {
      debugPrint(
        "Disposing previous controller before re-initialization...",
      ); // Use debugPrint
      await _controller!.dispose();
      _controller = null;
      debugPrint("Previous controller disposed."); // Use debugPrint
    }

    // Create and initialize the new controller
    _controller = CameraController(
      _selectedCamera!,
      _resolutionPreset,
      enableAudio: false,
      imageFormatGroup:
          Platform.isIOS
              ? ImageFormatGroup
                  .bgra8888 // iOS typically uses BGRA
              : ImageFormatGroup.yuv420, // Android often uses YUV
    );

    try {
      await _controller!.initialize();
      debugPrint("New CameraController initialized."); // Use debugPrint

      // Get exposure settings
      try {
        _minExposure = await _controller!.getMinExposureOffset();
        _maxExposure = await _controller!.getMaxExposureOffset();
        _currentExposure = 0.0; // Reset exposure
        await _controller!.setExposureOffset(_currentExposure);
        debugPrint(
          // Use debugPrint
          "Exposure re-initialized: $_minExposure to $_maxExposure, set to $_currentExposure",
        );
      } catch (e) {
        debugPrint("Error re-initializing exposure: $e"); // Use debugPrint
        _minExposure = -1.0;
        _maxExposure = 1.0;
        _currentExposure = 0.0;
      }

      // Lock orientation based on global setting
      if (currentOrientation == AppOrientation.landscape) {
        await _controller!.lockCaptureOrientation(
          // Use debugPrint
          _selectedCamera!.lensDirection == CameraLensDirection.front
              ? DeviceOrientation.landscapeLeft
              : DeviceOrientation.landscapeRight,
        );
        debugPrint(
          "Capture orientation locked to Landscape.",
        ); // Use debugPrint
      } else {
        await _controller!.lockCaptureOrientation(DeviceOrientation.portraitUp);
        debugPrint("Capture orientation locked to Portrait."); // Use debugPrint
      }

      // Determine image rotation for ML Kit
      int sensorOrientation = _selectedCamera!.sensorOrientation;
      debugPrint("Sensor orientation: $sensorOrientation degrees");

      // --- CORRECTED: Determine InputImageRotation based *only* on sensor orientation ---
      _imageRotation = InputImageRotationValue.fromRawValue(sensorOrientation);
      if (_imageRotation == null) {
        debugPrint(
          "Warning: Could not determine InputImageRotation from sensor orientation $sensorOrientation. Defaulting to 0deg.",
        );
        _imageRotation = InputImageRotation.rotation0deg;
      }
      debugPrint(
        "InputImageRotation determined: $_imageRotation (used for ML Kit)",
      );
      // --- End CORRECTED ---

      // Get image size from the first frame (safer than takePicture)
      // This might take a moment, so start stream first

      // Start image stream
      await _controller!.startImageStream(_processCameraImage);
      debugPrint("Image stream started."); // Use debugPrint

      // Set flag after successful initialization and stream start
      _isCameraInitialized = true;
    } on CameraException catch (e) {
      debugPrint(
        "Error initializing camera: ${e.code} - ${e.description}",
      ); // Use debugPrint
      _isCameraInitialized = false;
      // Show error to user if needed
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text("相机初始化失败: ${e.description}")));
      }
    } finally {
      _isInitializing = false;
      // Update UI after initialization attempt
      if (mounted) {
        setState(() {});
        debugPrint(
          "Initialization attempt finished. UI updated.",
        ); // Use debugPrint
      }
    }
  }

  Future<void> _processCameraImage(CameraImage image) async {
    if (_isProcessingImage || !mounted || !_isCameraInitialized) {
      // debugPrint("Skipping frame processing: isProcessing=$_isProcessingImage, mounted=$mounted, initialized=$_isCameraInitialized");
      return;
    }

    _isProcessingImage = true;

    try {
      // Update image size if it changes
      final newSize = Size(image.width.toDouble(), image.height.toDouble());
      if (_imageSize != newSize) {
        if (mounted) {
          setState(() {
            _imageSize = newSize;
            debugPrint(
              "Image size updated from stream: $_imageSize",
            ); // Use debugPrint
          });
        }
      }

      // Ensure rotation is set before creating InputImage
      if (_imageRotation == null) {
        debugPrint(
          "Skipping frame: Image rotation not yet determined.",
        ); // Use debugPrint
        _isProcessingImage = false;
        return;
      }

      // Create InputImage
      final inputImage = InputImageConverter.fromCameraImage(
        image,
        _selectedCamera!,
        _imageRotation!,
      );

      if (inputImage != null) {
        final faceMeshes = await _faceDetectionService.detectFaces(inputImage);
        if (mounted) {
          setState(() {
            _detectedFaces = faceMeshes;
            // 可以根据需要调整调试打印
            // debugPrint("Face meshes detected: ${_detectedFaces.length}");
          });

          // 如果检测到面部网格，调用 isLookingAtScreen (现在使用 FaceMesh)
          if (_detectedFaces.isNotEmpty) {
            final bool looking = _faceDetectionService.isLookingAtScreen(
              _detectedFaces.first,
            );
            // TODO: 根据 isLookingAtScreen 的结果执行相应操作
            debugPrint("Is looking at screen (FaceMesh): $looking");
          }
        }
      } else {
        debugPrint("InputImage conversion failed."); // Use debugPrint
      }
    } catch (e) {
      debugPrint("Error processing image stream: $e"); // Use debugPrint
    } finally {
      // Add a small delay to prevent overwhelming the CPU
      await Future.delayed(const Duration(milliseconds: 60)); // ~16 FPS target
      _isProcessingImage = false;
    }
  }

  // --- UI Building --- //

  @override
  Widget build(BuildContext context) {
    return Scaffold(backgroundColor: Colors.black, body: _buildCameraPreview());
  }

  Widget _buildCameraPreview() {
    if (!_isCameraInitialized || _controller == null) {
      // Show loading indicator or error message
      return Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const CircularProgressIndicator(),
            const SizedBox(height: 16),
            Text(
              _selectedCamera == null ? "未找到前置摄像头" : "正在初始化相机...",
              style: const TextStyle(color: Colors.white),
            ),
          ],
        ),
      );
    }

    // 计算 scale
    final Size canvasSize = MediaQuery.of(context).size;
    final scale = _calculatePreviewScale(canvasSize, _controller!.value);

    return Stack(
      fit: StackFit.expand,
      children: [
        // 相机预览
        Transform.scale(
          scale: scale, // 使用计算出的 scale
          alignment: Alignment.center,
          child: CameraPreview(_controller!),
        ),
        // 绘制层
        if (_detectedFaces.isNotEmpty &&
            _imageSize != null &&
            _imageRotation != null) // 确保所有参数有效
          CustomPaint(
            painter: FaceOverlayPainter(
              faceMeshes: _detectedFaces,
              imageSize: _imageSize!,
              imageRotation: _imageRotation!,
              cameraLensDirection: _selectedCamera!.lensDirection,
            ),
          ),

        // Exposure Slider
        _buildExposureSlider(),

        // Orientation Toggle Button
        _buildOrientationButton(),
      ],
    );
  }

  double _calculatePreviewScale(Size screenSize, CameraValue cameraValue) {
    // Implementation of _calculatePreviewScale method
    // This method should return a scale factor based on the screen size and camera value
    // For example, you can use the aspect ratio of the screen and camera to calculate the scale
    // This is a placeholder and should be implemented based on your specific requirements
    return 1.0; // Placeholder return, actual implementation needed
  }

  Widget _buildExposureSlider() {
    if (!_isCameraInitialized || _controller == null) {
      return const SizedBox.shrink();
    }

    bool canSetExposure = _minExposure < _maxExposure;

    return Positioned(
      bottom: 80, // Adjusted position
      left: 20,
      right: 20,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
        decoration: BoxDecoration(
          color: Colors.black54,
          borderRadius: BorderRadius.circular(10),
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(
              canSetExposure
                  ? 'Exposure: ${_currentExposure.toStringAsFixed(2)}'
                  : 'Exposure Not Available',
              style: const TextStyle(color: Colors.white),
            ),
            if (canSetExposure)
              Slider(
                value: _currentExposure,
                min: _minExposure,
                max: _maxExposure,
                onChanged: (value) async {
                  if ((value - _currentExposure).abs() >
                      (_maxExposure - _minExposure) / 100) {
                    if (mounted) {
                      setState(() {
                        _currentExposure = value;
                      });
                    }
                    try {
                      await _controller?.setExposureOffset(value);
                    } catch (e) {
                      debugPrint(
                        "Error setting exposure async: $e",
                      ); // Use debugPrint
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

  Widget _buildOrientationButton() {
    return Positioned(
      bottom: 20,
      right: 20,
      child: FloatingActionButton(
        onPressed: _toggleOrientation,
        tooltip: 'Toggle Orientation',
        child: Icon(
          currentOrientation == AppOrientation.landscape
              ? Icons.screen_lock_portrait
              : Icons.screen_lock_landscape,
        ),
      ),
    );
  }

  Future<void> _toggleOrientation() async {
    // Update global orientation state
    currentOrientation =
        currentOrientation == AppOrientation.portrait
            ? AppOrientation.landscape
            : AppOrientation.portrait;

    // Save preference
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(
      'isLandscape',
      currentOrientation == AppOrientation.landscape,
    );
    debugPrint(
      "Orientation toggled and saved: $currentOrientation",
    ); // Use debugPrint

    // Set system preference
    if (currentOrientation == AppOrientation.landscape) {
      await SystemChrome.setPreferredOrientations([
        DeviceOrientation.landscapeLeft,
        DeviceOrientation.landscapeRight,
      ]);
    } else {
      await SystemChrome.setPreferredOrientations([
        DeviceOrientation.portraitUp,
        DeviceOrientation.portraitDown,
      ]);
    }

    // Re-initialize the camera to apply orientation lock and recalculate rotation
    await _initializeCamera();

    // Trigger a rebuild to update the button icon
    if (mounted) {
      setState(() {});
    }
  }
}

// --- Potentially moved InputImageConverter --- (Keep separate if preferred)
// (Converter code would go here if moved from camera_view.dart)
