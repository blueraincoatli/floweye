import 'package:flutter/material.dart';
import 'package:camera/camera.dart';
import 'package:google_mlkit_face_mesh_detection/google_mlkit_face_mesh_detection.dart';
import '../services/face_detection_service.dart';
import '../widgets/face_overlay_painter.dart';

class CameraView extends StatefulWidget {
  const CameraView({super.key});

  @override
  // ignore: library_private_types_in_public_api
  _CameraViewState createState() => _CameraViewState();
}

class _CameraViewState extends State<CameraView> {
  late CameraController _controller;
  late FaceDetectionService _detectionService;

  @override
  void initState() {
    super.initState();
    _initCamera();
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  Future<void> _initCamera() async {
    try {
      final cameras = await availableCameras();
      _controller = CameraController(cameras[0], ResolutionPreset.high);
      await _controller.initialize();
      _detectionService = FaceDetectionService();
      setState(() {});
    } catch (e) {
      debugPrint("Error initializing camera: $e");
    }
  }

  @override
  Widget build(BuildContext context) {
    if (!_controller.value.isInitialized) {
      return const Center(child: CircularProgressIndicator());
    }

    // Calculate the aspect ratio and provide constraints
    final mediaSize = MediaQuery.of(context).size;
    // Lock orientation to portrait mode for simplicity initially
    // final screenAspectRatio = mediaSize.width / mediaSize.height;
    var cameraAspectRatio = _controller.value.aspectRatio;
    // If controller is not ready, use a default aspect ratio
    if (cameraAspectRatio == 0) {
      cameraAspectRatio = 9.0 / 16.0; // Default to 9:16 if not available yet
    }

    final previewWidth =
        _controller.value.previewSize?.height ?? mediaSize.width;
    final previewHeight =
        _controller.value.previewSize?.width ?? mediaSize.height;

    // Ensure the camera preview is scaled correctly using FittedBox
    return Center(
      // Center the FittedBox
      child: FittedBox(
        fit: BoxFit.contain, // Scales down to fit within the parent
        child: SizedBox(
          width:
              previewWidth, // Use potential rotated dimensions from previewSize
          height: previewHeight,
          child: Stack(
            fit: StackFit.expand,
            children: <Widget>[
              CameraPreview(_controller),
              if (_detectionService.faceMeshes != null &&
                  _detectionService.imageSize != null)
                LayoutBuilder(
                  builder: (context, constraints) {
                    final Size canvasSize = constraints.biggest;
                    final InputImageRotation imageRotation = _getRotation(
                      _controller.description.sensorOrientation,
                    );
                    final CameraLensDirection lensDirection =
                        _controller.description.lensDirection;

                    return CustomPaint(
                      size: canvasSize,
                      painter: FaceOverlayPainter(
                        faceMeshes: _detectionService.faceMeshes!,
                        imageSize: _detectionService.imageSize!,
                        imageRotation: imageRotation,
                        cameraLensDirection: lensDirection,
                      ),
                    );
                  },
                ),
            ],
          ),
        ),
      ),
    );
  }

  // Helper function to convert sensor orientation degrees to InputImageRotation
  InputImageRotation _getRotation(int sensorOrientation) {
    switch (sensorOrientation) {
      case 90:
        return InputImageRotation.rotation90deg;
      case 180:
        return InputImageRotation.rotation180deg;
      case 270:
        return InputImageRotation.rotation270deg;
      case 0:
      default:
        return InputImageRotation.rotation0deg;
    }
  }
}
