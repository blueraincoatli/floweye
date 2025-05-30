# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# MediaPipe相关规则
-keep class com.google.mediapipe.** { *; }
-keep class org.tensorflow.** { *; }
-dontwarn com.google.mediapipe.**
-dontwarn org.tensorflow.**

# MQTT相关规则
-keep class org.eclipse.paho.** { *; }
-dontwarn org.eclipse.paho.**

# 如果你的应用使用了反射，请添加相应的规则
-keepattributes Signature
-keepattributes *Annotation*