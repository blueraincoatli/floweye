import 'dart:ui'; // MockFace 需要 Rect

import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
// 导入 FaceMesh 相关类
import 'package:google_mlkit_face_mesh_detection/google_mlkit_face_mesh_detection.dart';
// 导入 InputImage
// import 'package:google_mlkit_commons/google_mlkit_commons.dart'; // 移除未使用导入
import 'package:floweye/services/face_detection_service.dart'; // 修改包路径

// Mock FaceMeshDetector
class MockFaceMeshDetector extends Mock implements FaceMeshDetector {}

// InputImage is complex to mock fully, consider testing with real data if possible,
// or mock specific properties needed for the test.
class MockInputImage extends Mock implements InputImage {}

// Mock FaceMesh
class MockFaceMesh extends Mock implements FaceMesh {
  // Mock boundingBox if needed
  @override
  Rect get boundingBox => Rect.fromLTRB(10, 10, 110, 110);

  // Mock points list if needed (e.g., return an empty list or a few mock points)
  @override
  List<FaceMeshPoint> get points => [];
}

void main() {
  // 修改为 MockFaceMeshDetector
  late MockFaceMeshDetector mockFaceMeshDetector;
  late FaceDetectionService faceDetectionService;

  // Register fallback values for any() matcher if needed for complex types
  // setUpAll(() {
  //   registerFallbackValue(MockInputImage());
  // });

  setUp(() {
    // 修改为 MockFaceMeshDetector
    mockFaceMeshDetector = MockFaceMeshDetector();
    // Inject the mock detector into the service for testing
    faceDetectionService = FaceDetectionService(detector: mockFaceMeshDetector);

    // Ensure processImage is called with InputImage type
    registerFallbackValue(MockInputImage());
  });

  tearDown(() async {
    // 可以在这里验证 mockFaceMeshDetector.close() 是否被调用
    // when(() => mockFaceMeshDetector.close()).thenAnswer((_) async {});
    // await faceDetectionService.dispose();
    // verify(() => mockFaceMeshDetector.close()).called(1);
  });

  group('FaceDetectionService', () {
    test(
      'detectFaces should return list of face meshes when meshes are detected',
      () async {
        // Arrange
        final inputImage = MockInputImage();
        // 修改为 MockFaceMesh
        final expectedMeshes = [MockFaceMesh(), MockFaceMesh()];

        // Configure the mock detector to return expected meshes
        when(
          () => mockFaceMeshDetector.processImage(any()),
        ).thenAnswer((_) async => expectedMeshes);

        // Act
        final actualMeshes = await faceDetectionService.detectFaces(inputImage);

        // Assert
        expect(actualMeshes, equals(expectedMeshes));
        verify(() => mockFaceMeshDetector.processImage(inputImage)).called(1);
      },
    );

    test(
      'detectFaces should return empty list when no meshes are detected',
      () async {
        // Arrange
        final inputImage = MockInputImage();
        // 修改为空的 FaceMesh 列表
        final expectedMeshes = <FaceMesh>[]; // Empty list

        when(
          () => mockFaceMeshDetector.processImage(any()),
        ).thenAnswer((_) async => expectedMeshes);

        // Act
        final actualMeshes = await faceDetectionService.detectFaces(inputImage);

        // Assert
        expect(actualMeshes, isEmpty);
        verify(() => mockFaceMeshDetector.processImage(inputImage)).called(1);
      },
    );

    test(
      'detectFaces should re-throw exceptions from FaceMeshDetector',
      () async {
        // Arrange
        final inputImage = MockInputImage();
        final exception = Exception('ML Kit failed');

        when(
          () => mockFaceMeshDetector.processImage(any()),
        ).thenThrow(exception);

        // Act & Assert
        expectLater(
          () async => await faceDetectionService.detectFaces(inputImage),
          throwsA(isA<Exception>()),
        );
        verify(() => mockFaceMeshDetector.processImage(inputImage)).called(1);
      },
    );

    test('dispose should call close on the face mesh detector', () async {
      // Arrange
      when(() => mockFaceMeshDetector.close()).thenAnswer((_) async {});

      // Act
      await faceDetectionService.dispose();

      // Assert
      verify(() => mockFaceMeshDetector.close()).called(1);
    });

    // TODO: 添加 isLookingAtScreen 方法的测试 (基于 FaceMesh)
    // group('isLookingAtScreen', () {
    //   test('should return true when mesh indicates looking forward (placeholder)', () {
    //     final mockMesh = MockFaceMesh();
    //     // TODO: Configure mockMesh properties if needed for the algorithm
    //     expect(faceDetectionService.isLookingAtScreen(mockMesh), isTrue);
    //   });
    // });
  });
}
