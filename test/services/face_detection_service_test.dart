import 'dart:ui'; // MockFace 需要 Rect

import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:google_mlkit_face_detection/google_mlkit_face_detection.dart';
import 'package:camera_poc/services/face_detection_service.dart'; // Import the service

// Mock classes for ML Kit components
class MockFaceDetector extends Mock implements FaceDetector {}

// InputImage is complex to mock fully, consider testing with real data if possible,
// or mock specific properties needed for the test.
class MockInputImage extends Mock implements InputImage {}

class MockFace extends Mock implements Face {
  // Example of mocking a property if needed by the service logic
  @override
  Rect get boundingBox => Rect.fromLTRB(10, 10, 110, 110);

  // Need to mock other properties if accessed by the service (not needed for basic detectFaces)
}

void main() {
  late MockFaceDetector mockFaceDetector;
  late FaceDetectionService faceDetectionService;

  // Register fallback values for any() matcher if needed for complex types
  // setUpAll(() {
  //   registerFallbackValue(MockInputImage());
  // });

  setUp(() {
    mockFaceDetector = MockFaceDetector();
    // Inject the mock detector into the service for testing
    faceDetectionService = FaceDetectionService(detector: mockFaceDetector);

    // Ensure processImage is called with InputImage type
    registerFallbackValue(MockInputImage());
  });

  tearDown(() async {
    // Verify that dispose closes the detector
    // We might test this separately or ensure it's called in a real scenario test
  });

  group('FaceDetectionService', () {
    test(
      'detectFaces should return list of faces when faces are detected',
      () async {
        // Arrange
        final inputImage = MockInputImage();
        final expectedFaces = [MockFace(), MockFace()];

        // Configure the mock detector to return expected faces when processImage is called
        when(
          () => mockFaceDetector.processImage(any()),
        ) // Use any() matcher from mocktail
        .thenAnswer((_) async => expectedFaces);

        // Act
        final actualFaces = await faceDetectionService.detectFaces(inputImage);

        // Assert
        expect(actualFaces, equals(expectedFaces));
        // Verify that processImage was called exactly once with the inputImage
        verify(() => mockFaceDetector.processImage(inputImage)).called(1);
      },
    );

    test(
      'detectFaces should return empty list when no faces are detected',
      () async {
        // Arrange
        final inputImage = MockInputImage();
        final expectedFaces = <Face>[]; // Empty list

        when(
          () => mockFaceDetector.processImage(any()),
        ).thenAnswer((_) async => expectedFaces);

        // Act
        final actualFaces = await faceDetectionService.detectFaces(inputImage);

        // Assert
        expect(actualFaces, isEmpty);
        verify(() => mockFaceDetector.processImage(inputImage)).called(1);
      },
    );

    test('detectFaces should re-throw exceptions from FaceDetector', () async {
      // Arrange
      final inputImage = MockInputImage();
      final exception = Exception('ML Kit failed');

      when(() => mockFaceDetector.processImage(any())).thenThrow(exception);

      // Act & Assert
      // Use expectLater for async functions that throw
      expectLater(
        () async => await faceDetectionService.detectFaces(inputImage),
        throwsA(isA<Exception>()), // Check if it throws any Exception
      );
      // Verify that processImage was called
      verify(() => mockFaceDetector.processImage(inputImage)).called(1);
    });

    test('dispose should call close on the face detector', () async {
      // Arrange
      // Ensure the close method returns a Future<void>
      when(() => mockFaceDetector.close()).thenAnswer((_) async {});

      // Act
      await faceDetectionService.dispose();

      // Assert
      verify(() => mockFaceDetector.close()).called(1);
    });

    // TODO: Implement and test estimateEyeRegions
  });
}
