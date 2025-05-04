import 'package:flutter/material.dart';
import 'package:camera/camera.dart';

// Global variable to store the list of available cameras.
List<CameraDescription> cameras = [];

Future<void> main() async {
  // Ensure that plugin services are initialized so that `availableCameras()`
  // can be called before `runApp()`
  WidgetsFlutterBinding.ensureInitialized();

  // Obtain a list of the available cameras on the device.
  try {
    cameras = await availableCameras();
  } on CameraException catch (e) {
    // Log the error to the console.
    // Consider showing a user-friendly error message.
    print('Error initializing cameras: ${e.code} - ${e.description}');
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

class _CameraScreenState extends State<CameraScreen> {
  CameraController? _controller;
  bool _isCameraInitialized = false;
  CameraDescription? _selectedCamera; // Store the selected camera

  @override
  void initState() {
    super.initState();
    // Initialize the camera only if cameras were found
    if (cameras.isNotEmpty) {
      // Select the front camera if available, otherwise the first camera
      _selectedCamera = cameras.firstWhere(
        (camera) => camera.lensDirection == CameraLensDirection.front,
        orElse: () => cameras.first, // Fallback to the first camera
      );
      _initializeCameraController(_selectedCamera!);
    } else {
      // Handle the case where no cameras are available
      print('No cameras found on this device.');
      // You might want to show a message to the user here
    }
  }

  Future<void> _initializeCameraController(
    CameraDescription cameraDescription,
  ) async {
    // Dispose of the old controller if it exists
    await _controller?.dispose();

    // Create a new controller
    _controller = CameraController(
      // Get a specific camera from the list of available cameras.
      cameraDescription,
      // Define the resolution to use.
      ResolutionPreset.medium, // Start with medium, adjust later if needed
      enableAudio: false, // We don't need audio
      imageFormatGroup:
          ImageFormatGroup.yuv420, // Commonly used format for processing
    );

    // Next, initialize the controller. This returns a Future.
    try {
      await _controller!.initialize();
      // After initialization, check if the widget is still mounted before updating the state
      if (!mounted) {
        return;
      }
      // TODO: Lock exposure and focus here based on PoC results
      // Example (needs refinement and error handling based on PoC):
      // await _controller!.setExposureMode(ExposureMode.locked);
      // await _controller!.setFocusMode(FocusMode.locked); // Remember focus lock might not work reliably

      setState(() {
        _isCameraInitialized = true;
      });
      print('Camera initialized successfully.');

      // Start image stream
      _controller!.startImageStream((CameraImage image) {
        // TODO: Process image frame here for face detection
        // Be mindful of performance. Process frames selectively if needed.
        // Example: Only process every Nth frame or when not already processing.
        // print('Received frame: ${image.width}x${image.height}'); // Uncomment for debugging if needed
      });
    } on CameraException catch (e) {
      print(
        'Error initializing camera controller: ${e.code} - ${e.description}',
      );
      // Handle initialization errors, maybe show a message to the user
      // Consider retrying or allowing the user to select another camera
      if (mounted) {
        setState(() {
          _isCameraInitialized = false; // Mark as not initialized on error
        });
      }
    } catch (e) {
      // Catch any other potential errors
      print('An unexpected error occurred during camera initialization: $e');
      if (mounted) {
        setState(() {
          _isCameraInitialized = false;
        });
      }
    }
  }

  @override
  void dispose() {
    // Dispose of the controller when the widget is disposed.
    _controller?.dispose();
    print('Camera controller disposed.');
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    // Display camera preview if initialized, otherwise show loading or error message
    if (!_isCameraInitialized ||
        _controller == null ||
        !_controller!.value.isInitialized) {
      // Handle cases: no cameras, initialization error, or still initializing
      return Scaffold(
        appBar: AppBar(title: const Text('Initializing Camera...')),
        body: Center(
          child:
              cameras.isEmpty
                  ? const Text('No cameras found on this device.')
                  : const CircularProgressIndicator(), // Show loading indicator while initializing
        ),
      );
    }

    // Camera preview
    return Scaffold(
      appBar: AppBar(title: const Text('Eye Direction Client')),
      body: CameraPreview(_controller!),
      // TODO: Add controls or status display later
    );
  }
}
