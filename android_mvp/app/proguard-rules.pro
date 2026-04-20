# MediaPipe
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**

# MQTT (Eclipse Paho)
-keep class org.eclipse.paho.** { *; }
-dontwarn org.eclipse.paho.**

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }

# App data classes used in MQTT messages
-keep class com.gazeinteraction.** { *; }
