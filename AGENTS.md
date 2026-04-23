# Repository Guidelines

## Project Structure & Module Organization

Floweye is a proof-of-concept multi-device gaze interaction system. The Android client lives in `android_mvp/`, with Kotlin sources under `app/src/main/java/com/gazeinteraction/`, resources under `app/src/main/res/`, and the MediaPipe model under `app/src/main/assets/`. The Python coordinator lives in `coordinator_app/`; `simple_coordinator.py` is the main MQTT decision service, `scanning_coordinator.py` is the scanning variant, and JSON files hold menu and patient configuration. Design notes are in `docs/`; older research is in `docs/archive/`.

## Build, Test, and Development Commands

- `cd android_mvp; ./gradlew assembleDebug` builds the Android debug APK at `app/build/outputs/apk/debug/app-debug.apk`.
- `cd android_mvp; ./gradlew test` runs JVM unit tests when present.
- `cd android_mvp; ./gradlew connectedAndroidTest` runs instrumentation tests on a device or emulator.
- `cd coordinator_app; pip install -r requirements.txt` installs coordinator dependencies.
- `cd coordinator_app; python simple_coordinator.py <broker_ip> 1883` starts the MQTT coordinator.
- `python coordinator_app/test_coordinator.py` runs the simple coordinator test harness against the default local broker.
- `quick_build.bat` is a Windows helper for model setup and Android Studio workflow.

## Coding Style & Naming Conventions

Use Kotlin idioms in the Android app: 4-space indentation, `PascalCase` classes, `camelCase` functions and properties, and constants in `UPPER_SNAKE_CASE` or existing local style. Keep responsibilities separated by package: camera, gaze, mediapipe, mqtt, and UI/activity coordination. Python code should use 4-space indentation, `snake_case`, small classes, and explicit MQTT topic names. Avoid committing generated build output or local environment files.

## Testing Guidelines

Add Android unit tests under `android_mvp/app/src/test/` and instrumentation tests under `android_mvp/app/src/androidTest/`. Name tests after behavior, for example `GazeDetectionAlgorithmTest`. For Python coordinator changes, add focused tests or executable harnesses in `coordinator_app/` using `test_*.py`. Changes to gaze thresholds, MQTT payloads, or coordination logic should include automated coverage or documented manual verification.

## Commit & Pull Request Guidelines

Recent commits use short prefixes such as `feat:` and `fix:` plus a concise description. Continue that pattern, for example `feat: add scanning coordinator timeout` or `fix: handle mqtt reconnect errors`. Pull requests should describe behavior, list Android and coordinator commands run, mention MQTT/model setup, and include screenshots or logs for UI, camera, or device-interaction changes.

## Security & Configuration Tips

Do not commit private broker addresses, patient-identifying data, or local credentials. Keep the MediaPipe `.task` file in `android_mvp/app/src/main/assets/` only when needed for builds, and verify licensing before redistribution. Devices must share the MQTT broker LAN, default port `1883`.
