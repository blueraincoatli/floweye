import 'package:google_mlkit_face_detection/google_mlkit_face_detection.dart';
import 'package:flutter/foundation.dart'; // For Rect if needed later, and debugPrint

class FaceDetectionService {
  final FaceDetector _faceDetector;

  // Constructor allowing injection of a FaceDetector instance.
  // This is useful for testing with a mock detector.
  FaceDetectionService({FaceDetector? detector})
    : _faceDetector =
          detector ??
          FaceDetector(
            options: FaceDetectorOptions(
              performanceMode:
                  FaceDetectorMode
                      .accurate, // Use accurate mode for better precision
              enableContours:
                  true, // Enable facial contours for more precise detection
              enableLandmarks:
                  true, // Enable facial landmarks for better face feature detection
              enableClassification:
                  true, // Enable classification for detecting smiling, etc.
              minFaceSize: 0.15, // Set minimum face size to 15% of the image
            ),
          );

  /// Processes the given image to detect faces.
  ///
  /// Returns a list of [Face] objects found in the image.
  /// Returns an empty list if no faces are detected.
  /// Throws an exception if the detection process fails.
  Future<List<Face>> detectFaces(InputImage inputImage) async {
    try {
      final List<Face> faces = await _faceDetector.processImage(inputImage);
      return faces;
    } catch (e) {
      // Log the error or handle it as needed
      debugPrint('Error detecting faces: $e');
      // Re-throw the exception to allow calling code to handle it
      rethrow;
    }
  }

  /// Closes the face detector and releases resources.
  /// Should be called when the service is no longer needed.
  Future<void> dispose() async {
    await _faceDetector.close();
  }

  // TODO: Implement estimateEyeRegions method
  /*
  EyeRegions estimateEyeRegions(Face face) {
    final Rect boundingBox = face.boundingBox;
    // Add logic here to estimate left and right eye regions
    // based on the bounding box dimensions and typical face proportions.
    // This will likely involve some assumptions or heuristics.
    // Example placeholder:
    final double eyeRegionWidth = boundingBox.width * 0.2;
    final double eyeRegionHeight = boundingBox.height * 0.15;
    final double eyeOffsetY = boundingBox.height * 0.3;
    final double leftEyeOffsetX = boundingBox.width * 0.2;
    final double rightEyeOffsetX = boundingBox.width * 0.6;

    final Rect leftEyeRect = Rect.fromLTWH(
      boundingBox.left + leftEyeOffsetX,
      boundingBox.top + eyeOffsetY,
      eyeRegionWidth,
      eyeRegionHeight,
    );
    final Rect rightEyeRect = Rect.fromLTWH(
      boundingBox.left + rightEyeOffsetX,
      boundingBox.top + eyeOffsetY,
      eyeRegionWidth,
      eyeRegionHeight,
    );

    return EyeRegions(leftEye: leftEyeRect, rightEye: rightEyeRect);
  }
  */
}

/*
// Helper class to hold estimated eye regions
class EyeRegions {
  final Rect leftEye;
  final Rect rightEye;

  EyeRegions({required this.leftEye, required this.rightEye});
}
*/
