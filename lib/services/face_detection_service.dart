import 'package:google_mlkit_face_mesh_detection/google_mlkit_face_mesh_detection.dart';
import 'package:flutter/foundation.dart'; // For Rect if needed later, and debugPrint
import 'dart:ui' show Size; // 导入Size类
// import 'dart:math'; // 移除未使用的导入

class FaceDetectionService {
  final FaceMeshDetector _faceMeshDetector;

  // 添加缺少的属性
  List<FaceMesh>? _faceMeshes;
  Size? _imageSize;

  // Getters
  List<FaceMesh>? get faceMeshes => _faceMeshes;
  Size? get imageSize => _imageSize;

  FaceDetectionService({FaceMeshDetector? detector})
    : _faceMeshDetector =
          detector ??
          FaceMeshDetector(
            // 使用正确的枚举值初始化
            option: FaceMeshDetectorOptions.faceMesh,
          );

  /// Processes the given image to detect face meshes.
  ///
  /// Returns a list of [FaceMesh] objects found in the image.
  /// Returns an empty list if no faces are detected.
  /// Throws an exception if the detection process fails.
  Future<List<FaceMesh>> detectFaces(InputImage inputImage) async {
    try {
      // 保存输入图像的尺寸
      _imageSize = Size(
        inputImage.metadata?.size.width ?? 0,
        inputImage.metadata?.size.height ?? 0,
      );

      final List<FaceMesh> faceMeshes = await _faceMeshDetector.processImage(
        inputImage,
      );

      // 保存检测到的面部网格
      _faceMeshes = faceMeshes;

      if (faceMeshes.isNotEmpty) {
        final faceMesh = faceMeshes.first;
        debugPrint(
          'Detected Face Mesh with ${faceMesh.points.length} points. BoundingBox: ${faceMesh.boundingBox}',
        );
      }
      return faceMeshes;
    } catch (e) {
      debugPrint('Error detecting face meshes: $e');
      rethrow;
    }
  }

  bool isLookingAtScreen(FaceMesh faceMesh) {
    debugPrint("isLookingAtScreen based on FaceMesh is not implemented yet.");
    return true;
  }

  /// Closes the face mesh detector and releases resources.
  /// Should be called when the service is no longer needed.
  Future<void> dispose() async {
    await _faceMeshDetector.close();
  }
}
