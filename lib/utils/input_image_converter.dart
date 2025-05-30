import 'dart:io';
import 'dart:ui';

import 'package:camera/camera.dart';
import 'package:flutter/foundation.dart';
import 'package:google_mlkit_face_detection/google_mlkit_face_detection.dart';
// ML Kit Commons might be needed for InputImagePlaneMetadata if not exported by face_detection
import 'package:google_mlkit_commons/google_mlkit_commons.dart' as ml_commons;

class InputImageConverter {
  /// Converts a [CameraImage] from the camera plugin to an [InputImage]
  /// suitable for ML Kit Vision APIs.
  ///
  /// Requires the [cameraImage] and the [cameraDescription] of the camera
  /// that produced the image, to determine the correct image rotation.
  static ml_commons.InputImage? fromCameraImage(
    CameraImage cameraImage,
    CameraDescription cameraDescription,
    InputImageRotation imageRotation, // Pass rotation explicitly
  ) {
    debugPrint("InputImageConverter using rotation: $imageRotation");

    final ml_commons.InputImageFormat inputImageFormat;
    if (Platform.isIOS) {
      inputImageFormat = ml_commons.InputImageFormat.bgra8888;
    } else if (Platform.isAndroid) {
      // Check the format provided by the camera image
      if (cameraImage.format.group == ImageFormatGroup.nv21) {
        inputImageFormat = ml_commons.InputImageFormat.nv21;
      } else if (cameraImage.format.group == ImageFormatGroup.yuv420) {
        // YUV_420_888 can be represented as NV21 for ML Kit
        inputImageFormat = ml_commons.InputImageFormat.nv21;
      } else if (cameraImage.format.group == ImageFormatGroup.bgra8888) {
        inputImageFormat = ml_commons.InputImageFormat.bgra8888;
      } else {
        debugPrint(
          'Unsupported Android image format: ${cameraImage.format.group}. Defaulting to NV21.',
        );
        // Defaulting to NV21 might work for some YUV formats but could fail for others.
        inputImageFormat = ml_commons.InputImageFormat.nv21;
      }
    } else {
      debugPrint('Unsupported platform for image format determination.');
      return null;
    }

    // Get image size
    final Size imageSize = Size(
      cameraImage.width.toDouble(),
      cameraImage.height.toDouble(),
    );

    // Get bytesPerRow from the first plane, if available.
    final int bytesPerRow =
        cameraImage.planes.isNotEmpty ? cameraImage.planes[0].bytesPerRow : 0;

    if (bytesPerRow == 0 &&
        Platform.isAndroid &&
        inputImageFormat != ml_commons.InputImageFormat.nv21) {
      debugPrint(
        'Warning: bytesPerRow is 0 on Android for non-NV21 format. This might be an issue.',
      );
    } else if (bytesPerRow == 0 && !Platform.isAndroid) {
      debugPrint('Warning: Could not determine bytesPerRow from image planes.');
    }

    // Combine all plane bytes into a single Uint8List if needed (e.g., for NV21)
    // For BGRA8888, only the first plane's bytes are needed.
    final Uint8List bytes;
    if (inputImageFormat == ml_commons.InputImageFormat.nv21) {
      // For NV21 (and YUV420), concatenate all plane bytes.
      bytes = Uint8List.fromList(
        cameraImage.planes.fold<List<int>>(
          [],
          (previousValue, element) => previousValue..addAll(element.bytes),
        ),
      );
    } else if (inputImageFormat == ml_commons.InputImageFormat.bgra8888 &&
        cameraImage.planes.isNotEmpty) {
      // For BGRA, use only the first plane.
      bytes = cameraImage.planes[0].bytes;
    } else {
      debugPrint('Cannot extract bytes for format $inputImageFormat');
      return null;
    }

    // Create InputImageMetadata
    final ml_commons.InputImageMetadata inputImageMetadata =
        ml_commons.InputImageMetadata(
          size: imageSize,
          rotation: imageRotation, // Use the explicitly passed rotation
          format: inputImageFormat,
          bytesPerRow:
              bytesPerRow, // Pass bytesPerRow (might be 0 for NV21 on Android)
        );

    // Create InputImage
    return ml_commons.InputImage.fromBytes(
      bytes: bytes,
      metadata: inputImageMetadata,
    );
  }
}
