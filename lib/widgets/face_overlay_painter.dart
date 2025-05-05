// import 'dart:ui' as ui;
// import 'dart:math' as math;

import 'package:camera/camera.dart';
import 'package:flutter/material.dart';
// import 'package:google_mlkit_commons/google_mlkit_commons.dart';
import 'package:google_mlkit_face_mesh_detection/google_mlkit_face_mesh_detection.dart';
import 'dart:math' as math; // Import math for min

class FaceOverlayPainter extends CustomPainter {
  final List<FaceMesh> faceMeshes;
  final Size imageSize; // Absolute size of the image analysed (oriented sensor)
  final InputImageRotation imageRotation; // Rotation of the image from sensor
  final CameraLensDirection cameraLensDirection; // Front or back camera
  // Remove external scale and canvasSize
  // final double scale;
  // final Size canvasSize;

  FaceOverlayPainter({
    required this.faceMeshes,
    required this.imageSize,
    required this.imageRotation,
    required this.cameraLensDirection,
    // required this.scale, // Removed
    // required this.canvasSize, // Removed
  });

  @override
  void paint(Canvas canvas, Size size) {
    if (faceMeshes.isEmpty) return;

    // 重新实现，不使用画布旋转，而是直接变换坐标
    final Paint boxPaint =
        Paint()
          ..style = PaintingStyle.stroke
          ..strokeWidth = 2.0
          ..color = Colors.green;

    // 定义不同区域的颜色
    final Paint pointPaint =
        Paint()
          ..style = PaintingStyle.fill
          ..color = Colors.red;
    final Paint eyePaint =
        Paint()
          ..style = PaintingStyle.fill
          ..color = Colors.yellow;
    final Paint nosePaint =
        Paint()
          ..style = PaintingStyle.fill
          ..color = Colors.blue;
    final Paint lipPaint =
        Paint()
          ..style = PaintingStyle.fill
          ..color = Colors.pink;
    final Paint eyebrowPaint =
        Paint()
          ..style = PaintingStyle.fill
          ..color = Colors.orange;

    // 输出调试信息
    debugPrint("Canvas size: $size, Image size: $imageSize");
    debugPrint(
      "Camera direction: $cameraLensDirection, Image rotation: $imageRotation",
    );

    for (final FaceMesh mesh in faceMeshes) {
      // 计算并绘制边界框
      Rect boundingBox = mesh.boundingBox;
      Rect adjustedBox = _adjustRectForPortrait(boundingBox, imageSize, size);
      canvas.drawRect(adjustedBox, boxPaint);

      // 绘制对角线
      canvas.drawLine(
        adjustedBox.topLeft,
        adjustedBox.bottomRight,
        Paint()
          ..color = Colors.green
          ..strokeWidth = 1.0,
      );

      // 为所有点创建映射
      final Map<int, Offset> mappedPoints = {};

      for (final point3D in mesh.points) {
        // 原始坐标（0-1范围内的比例）
        double nx = point3D.x / imageSize.width;
        double ny = point3D.y / imageSize.height;

        // 前置摄像头需要水平翻转
        if (cameraLensDirection == CameraLensDirection.front) {
          nx = 1.0 - nx;
        }

        // 不使用旋转变换，而是直接计算正确的位置
        // 在竖屏模式下，需要将宽高对调，保持正确的比例
        double screenX = nx * size.width;
        double screenY = ny * size.height;

        mappedPoints[point3D.index] = Offset(screenX, screenY);
      }

      // 根据不同区域绘制点
      for (final entry in mappedPoints.entries) {
        final index = entry.key;
        final position = entry.value;
        double pointSize = 1.5;
        Paint currentPaint = pointPaint;

        // 右眼区域
        if (index >= 33 && index <= 42 ||
            index >= 159 && index <= 168 ||
            index >= 133 && index <= 154) {
          pointSize = 2.0;
          currentPaint = eyePaint;
        }
        // 左眼区域
        else if (index >= 263 && index <= 272 ||
            index >= 385 && index <= 394 ||
            index >= 362 && index <= 381) {
          pointSize = 2.0;
          currentPaint = eyePaint;
        }
        // 鼻子区域
        else if (index >= 1 && index <= 32 || index >= 97 && index <= 132) {
          pointSize = 2.0;
          currentPaint = nosePaint;
        }
        // 嘴唇区域
        else if (index >= 61 && index <= 96) {
          pointSize = 2.0;
          currentPaint = lipPaint;
        }
        // 眉毛区域
        else if (index >= 336 && index <= 346 || index >= 296 && index <= 334) {
          pointSize = 2.0;
          currentPaint = eyebrowPaint;
        }

        canvas.drawCircle(position, pointSize, currentPaint);
      }

      // 可选：绘制面部特征线
      if (mappedPoints.length > 400) {
        final eyeOutlineIndices = [33, 133, 157, 158, 159, 160, 161, 246, 33];
        _drawFacialFeatureLine(
          canvas,
          mappedPoints,
          eyeOutlineIndices,
          Colors.cyan,
        );
      }
    }
  }

  // 为竖屏模式调整矩形
  Rect _adjustRectForPortrait(
    Rect originalRect,
    Size imageSize,
    Size screenSize,
  ) {
    // 计算比例
    double scaleX = screenSize.width / imageSize.width;
    double scaleY = screenSize.height / imageSize.height;

    // 如果是前置摄像头，水平翻转
    double left = originalRect.left;
    double right = originalRect.right;

    if (cameraLensDirection == CameraLensDirection.front) {
      left = imageSize.width - originalRect.right;
      right = imageSize.width - originalRect.left;
    }

    // 创建调整后的矩形
    return Rect.fromLTRB(
      left * scaleX,
      originalRect.top * scaleY,
      right * scaleX,
      originalRect.bottom * scaleY,
    );
  }

  // 绘制面部特征线条
  void _drawFacialFeatureLine(
    Canvas canvas,
    Map<int, Offset> points,
    List<int> indices,
    Color color, {
    double strokeWidth = 1.0,
  }) {
    if (indices.length < 2) return;

    final paint =
        Paint()
          ..color = color
          ..strokeWidth = strokeWidth
          ..style = PaintingStyle.stroke;

    final path = Path();
    path.moveTo(points[indices.first]!.dx, points[indices.first]!.dy);

    for (int i = 1; i < indices.length; i++) {
      if (points.containsKey(indices[i])) {
        path.lineTo(points[indices[i]]!.dx, points[indices[i]]!.dy);
      }
    }

    canvas.drawPath(path, paint);
  }

  @override
  bool shouldRepaint(covariant FaceOverlayPainter oldDelegate) {
    return oldDelegate.faceMeshes != faceMeshes ||
        oldDelegate.imageSize != imageSize ||
        oldDelegate.imageRotation != imageRotation ||
        oldDelegate.cameraLensDirection != cameraLensDirection;
    // Removed scale and canvasSize comparison
    // oldDelegate.scale != scale ||
    // oldDelegate.canvasSize != canvasSize;
  }
}
