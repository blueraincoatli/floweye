import 'dart:ui' as ui;
import 'dart:math' as math;

import 'package:camera/camera.dart';
import 'package:flutter/material.dart';
import 'package:google_mlkit_face_detection/google_mlkit_face_detection.dart';

// Helper function to convert InputImageRotation to degrees
double _rotationDegrees(InputImageRotation rotation) {
  switch (rotation) {
    case InputImageRotation.rotation0deg:
      return 0;
    case InputImageRotation.rotation90deg:
      return 90;
    case InputImageRotation.rotation180deg:
      return 180;
    case InputImageRotation.rotation270deg:
      return 270;
  }
}

class FaceOverlayPainter extends CustomPainter {
  final List<Face> faces;
  final Size imageSize; // Absolute size of the image analysed (oriented sensor)
  final InputImageRotation imageRotation; // Rotation of the image from sensor
  final CameraLensDirection cameraLensDirection; // Front or back camera
  final Size? canvasSize;
  final double? scale;

  FaceOverlayPainter({
    required this.faces,
    required this.imageSize,
    required this.imageRotation,
    required this.cameraLensDirection,
    this.canvasSize,
    this.scale,
  });

  @override
  void paint(ui.Canvas canvas, Size size) {
    final Size canvasSize = size;
    debugPrint("--- FaceOverlayPainter.paint START ---");
    debugPrint('Painter: Canvas Size: $canvasSize');
    debugPrint('Painter: Image Size: $imageSize, Rotation: $imageRotation');
    debugPrint('Painter: Faces: ${faces.length}');

    // --- NEW: Calculate scale inside painter ---
    final bool isRotated =
        imageRotation == InputImageRotation.rotation90deg ||
        imageRotation == InputImageRotation.rotation270deg;
    final double imgWidth = isRotated ? imageSize.height : imageSize.width;
    final double imgHeight = isRotated ? imageSize.width : imageSize.height;
    final double scale = math.max(
      canvasSize.width / imgWidth,
      canvasSize.height / imgHeight,
    );
    debugPrint('Painter: Calculated scale for cover: $scale');
    // --- End NEW ---

    // 绘制蓝框和红线 (保留作为参考)
    final Paint testPaint =
        Paint()
          ..style = PaintingStyle.stroke
          ..strokeWidth = 10.0
          ..color = Colors.blue;

    final Rect testRect = Rect.fromLTWH(
      size.width * 0.25,
      size.height * 0.25,
      size.width * 0.5,
      size.height * 0.5,
    );

    canvas.drawRect(testRect, testPaint);
    canvas.drawLine(
      Offset(0, 0),
      Offset(size.width, size.height),
      testPaint..color = Colors.red,
    );

    // 如果检测到人脸，尝试使用最简单的方式绘制红框
    if (faces.isNotEmpty) {
      debugPrint('Drawing ${faces.length} face(s)');

      // 每个人脸创建一个缩放后的矩形
      for (final Face face in faces) {
        final Rect faceRect = face.boundingBox;
        debugPrint('Original face rect: $faceRect');

        // --- 重写坐标转换逻辑 ---

        final bool isFrontCamera =
            cameraLensDirection == CameraLensDirection.front;

        // 使用传入的 canvasSize 和 scale (如果有)
        final Map<String, double> transform = _prepareTransform(
          canvasSize,
          imageSize,
          imageRotation,
          isFrontCamera,
          scale,
        );
        final double scaleX = transform['scaleX']!;
        final double scaleY = transform['scaleY']!;
        final double offsetX = transform['offsetX']!;
        final double offsetY = transform['offsetY']!;

        // 获取面部特征点以创建更精确的边界框
        Rect adjustedRect = faceRect;

        // 如果有面部轮廓点，使用它们来创建更精确的边界框
        if (face.contours.isNotEmpty) {
          // 尝试使用面部轮廓点来调整边界框
          try {
            // 获取面部轮廓点
            final faceOval = face.contours[FaceContourType.face];
            if (faceOval != null && faceOval.points.isNotEmpty) {
              // 找出轮廓点的最小和最大坐标
              double minX = double.infinity;
              double minY = double.infinity;
              double maxX = 0;
              double maxY = 0;

              for (final point in faceOval.points) {
                minX = math.min(minX, point.x.toDouble());
                minY = math.min(minY, point.y.toDouble());
                maxX = math.max(maxX, point.x.toDouble());
                maxY = math.max(maxY, point.y.toDouble());
              }

              // 创建基于轮廓点的矩形
              adjustedRect = Rect.fromLTRB(minX, minY, maxX, maxY);
              debugPrint('Adjusted face rect from contours: $adjustedRect');
            }
          } catch (e) {
            debugPrint('Error using face contours: $e');
            // 如果出错，回退到原始边界框
            adjustedRect = faceRect;
          }
        } else if (face.landmarks.isNotEmpty) {
          // 如果没有轮廓点但有特征点，尝试使用特征点
          try {
            // 获取关键特征点
            final leftEye = face.landmarks[FaceLandmarkType.leftEye];
            final rightEye = face.landmarks[FaceLandmarkType.rightEye];
            final nose = face.landmarks[FaceLandmarkType.noseBase];
            final leftMouth = face.landmarks[FaceLandmarkType.leftMouth];
            final rightMouth = face.landmarks[FaceLandmarkType.rightMouth];

            // 如果有足够的特征点，创建更精确的边界框
            if (leftEye != null &&
                rightEye != null &&
                (nose != null || (leftMouth != null && rightMouth != null))) {
              // 计算眼睛之间的距离作为比例参考
              final eyeDistance =
                  (rightEye.position.x - leftEye.position.x).abs();

              // 估计面部宽度（通常是眼睛距离的2.5-3倍）
              final faceWidth = eyeDistance * 2.7;

              // 估计面部高度（通常是宽度的1.3-1.5倍）
              final faceHeight = faceWidth * 1.4;

              // 计算面部中心点
              double centerX = (leftEye.position.x + rightEye.position.x) / 2;
              double centerY;

              if (nose != null) {
                // 如果有鼻子特征点，使用眼睛和鼻子的中点作为面部中心
                centerY =
                    (leftEye.position.y +
                        rightEye.position.y +
                        nose.position.y) /
                    3;
              } else if (leftMouth != null && rightMouth != null) {
                // 否则使用眼睛和嘴巴的中点
                centerY =
                    (leftEye.position.y +
                        rightEye.position.y +
                        leftMouth.position.y +
                        rightMouth.position.y) /
                    4;
              } else {
                // 如果只有眼睛，使用眼睛的Y坐标加上一个估计值
                centerY =
                    (leftEye.position.y + rightEye.position.y) / 2 +
                    eyeDistance * 0.5;
              }

              // 创建基于特征点的矩形
              adjustedRect = Rect.fromCenter(
                center: Offset(centerX, centerY),
                width: faceWidth,
                height: faceHeight,
              );
              debugPrint('Adjusted face rect from landmarks: $adjustedRect');
            }
          } catch (e) {
            debugPrint('Error using face landmarks: $e');
            // 如果出错，回退到原始边界框
            adjustedRect = faceRect;
          }
        }

        // 应用转换到调整后的矩形
        final double finalLeft = offsetX + adjustedRect.left * scaleX;
        final double finalTop = offsetY + adjustedRect.top * scaleY;
        final double finalRight = offsetX + adjustedRect.right * scaleX;
        final double finalBottom = offsetY + adjustedRect.bottom * scaleY;

        // 确保坐标顺序正确 (left < right, top < bottom)
        final Rect scaledRect = Rect.fromLTRB(
          math.min(finalLeft, finalRight),
          math.min(finalTop, finalBottom),
          math.max(finalLeft, finalRight),
          math.max(finalTop, finalBottom),
        );

        // --- 坐标转换结束 ---

        debugPrint('Painter: Scaled Face Rect: $scaledRect'); // 使用 debugPrint

        // 使用红色画笔绘制人脸矩形
        final Paint facePaint =
            Paint()
              ..style = PaintingStyle.stroke
              ..strokeWidth =
                  2.0 // 调细一点
              ..color = Colors.red;

        canvas.drawRect(scaledRect, facePaint);

        // 绘制面部特征点以便更好地可视化检测精度
        if (face.landmarks.isNotEmpty) {
          final Paint landmarkPaint =
              Paint()
                ..style = PaintingStyle.fill
                ..color = Colors.green
                ..strokeWidth = 4.0;

          // 绘制关键特征点
          void drawLandmark(FaceLandmarkType type) {
            final landmark = face.landmarks[type];
            if (landmark != null) {
              final double x =
                  offsetX + landmark.position.x.toDouble() * scaleX;
              final double y =
                  offsetY + landmark.position.y.toDouble() * scaleY;
              canvas.drawCircle(Offset(x, y), 2.0, landmarkPaint);
            }
          }

          // 绘制眼睛、鼻子和嘴巴的特征点
          drawLandmark(FaceLandmarkType.leftEye);
          drawLandmark(FaceLandmarkType.rightEye);
          drawLandmark(FaceLandmarkType.noseBase);
          drawLandmark(FaceLandmarkType.leftMouth);
          drawLandmark(FaceLandmarkType.rightMouth);
        }

        // 绘制面部轮廓线以更好地可视化面部边界
        if (face.contours.isNotEmpty) {
          final Paint contourPaint =
              Paint()
                ..style = PaintingStyle.stroke
                ..color = Colors.yellow
                ..strokeWidth = 1.5;

          // 绘制面部轮廓
          void drawContour(FaceContourType type) {
            final contour = face.contours[type];
            if (contour != null && contour.points.isNotEmpty) {
              final Path path = Path();
              bool isFirst = true;

              for (final point in contour.points) {
                final double x = offsetX + point.x.toDouble() * scaleX;
                final double y = offsetY + point.y.toDouble() * scaleY;

                if (isFirst) {
                  path.moveTo(x, y);
                  isFirst = false;
                } else {
                  path.lineTo(x, y);
                }
              }

              // 如果是闭合轮廓，则闭合路径
              if (type == FaceContourType.face) {
                path.close();
              }

              canvas.drawPath(path, contourPaint);
            }
          }

          // 绘制主要轮廓
          drawContour(FaceContourType.face);

          // 可选：绘制其他轮廓（眼睛、嘴巴等）
          // drawContour(FaceContourType.leftEye);
          // drawContour(FaceContourType.rightEye);
          // drawContour(FaceContourType.leftEyebrowTop);
          // drawContour(FaceContourType.rightEyebrowTop);
          // drawContour(FaceContourType.upperLipTop);
          // drawContour(FaceContourType.lowerLipBottom);
        }
      }
    } else {
      debugPrint('No faces detected to draw');
    }
    debugPrint("--- FaceOverlayPainter.paint END ---"); // 调试标记
  }

  // 修改辅助方法以接受可选的外部 scale 值
  Map<String, double> _prepareTransform(
    Size canvasSize,
    Size imageSize,
    InputImageRotation rotation,
    bool isFrontCamera,
    double scale,
  ) {
    double scaleX = 1.0, scaleY = 1.0, offsetX = 0.0, offsetY = 0.0;

    // 确定旋转后的图像尺寸
    final bool isRotated =
        rotation == InputImageRotation.rotation90deg ||
        rotation == InputImageRotation.rotation270deg;
    final double imgWidth = isRotated ? imageSize.height : imageSize.width;
    final double imgHeight = isRotated ? imageSize.width : imageSize.height;

    // --- 使用传入的 scale 作为基础缩放因子 ---
    final double baseScale = scale; // 直接使用传入的 cover scale
    debugPrint("PrepareTransform using baseScale: $baseScale");

    // 计算应用 baseScale 后，图像在画布中占据的尺寸
    final double scaledImageWidth = imgWidth * baseScale;
    final double scaledImageHeight = imgHeight * baseScale;

    // 计算居中偏移量 (基于应用了 baseScale 之后的尺寸)
    offsetX = (canvasSize.width - scaledImageWidth) / 2.0;
    offsetY = (canvasSize.height - scaledImageHeight) / 2.0;

    // --- 调整 scaleX/scaleY 和 offsetX/offsetY 以适应旋转和镜像 ---
    // 这里的 scaleX/Y 最终会乘以原始图像坐标
    switch (rotation) {
      case InputImageRotation.rotation0deg:
        scaleX = baseScale;
        scaleY = baseScale;
        if (isFrontCamera) {
          scaleX = -baseScale; // 水平镜像
          offsetX += scaledImageWidth; // 调整偏移以匹配镜像后的原点
        }
        break;
      case InputImageRotation.rotation90deg:
        // 尝试映射: 原始(x,y) -> 画布(offsetX + y*baseScale, offsetY + (imgWidth-x)*baseScale)
        // 这对直接画 Rect 仍然复杂, 但我们基于 baseScale 调整
        scaleX = baseScale; // 应用于原始 y
        scaleY = baseScale; // 应用于原始 x
        // offsetX, offsetY 已经是基于 baseScale 计算的居中值
        debugPrint(
          "WARN: 90deg rotation drawing with baseScale=$baseScale. Offsets/scales might need further adjustment for mirroring.",
        );
        // TODO: 需要更精确处理旋转和镜像组合
        // if (isFrontCamera) { ... } // 镜像逻辑待定
        break;
      case InputImageRotation.rotation180deg:
        scaleX = -baseScale; // 水平翻转
        scaleY = -baseScale; // 垂直翻转
        offsetX += scaledImageWidth; // 调整 X 偏移
        offsetY += scaledImageHeight; // 调整 Y 偏移
        if (isFrontCamera) {
          // 镜像抵消水平翻转，但垂直翻转仍在
          scaleX = baseScale;
          offsetX = (canvasSize.width - scaledImageWidth) / 2.0; // X 偏移回到居中
          // Y 轴的翻转和偏移保持
        }
        break;
      case InputImageRotation.rotation270deg:
        // 尝试映射: 原始(x,y) -> 画布(offsetX + (imgHeight-y)*baseScale, offsetY + x*baseScale)
        scaleX = baseScale; // 应用于原始 y
        scaleY = baseScale; // 应用于原始 x
        // offsetX, offsetY 已经是基于 baseScale 计算的居中值
        debugPrint(
          "WARN: 270deg rotation drawing with baseScale=$baseScale. Offsets/scales might need further adjustment for mirroring.",
        );
        // TODO: 需要更精确处理旋转和镜像组合
        // if (isFrontCamera) { ... } // 镜像逻辑待定
        break;
    }

    return {
      'scaleX': scaleX,
      'scaleY': scaleY,
      'offsetX': offsetX,
      'offsetY': offsetY,
    };
  }

  @override
  bool shouldRepaint(covariant FaceOverlayPainter oldDelegate) {
    // Repaint if the faces list, image size, rotation, or lens direction changes.
    return oldDelegate.faces != faces ||
        oldDelegate.imageSize != imageSize ||
        oldDelegate.imageRotation != imageRotation ||
        oldDelegate.cameraLensDirection != cameraLensDirection ||
        oldDelegate.canvasSize != canvasSize ||
        oldDelegate.scale != scale;
  }
}
