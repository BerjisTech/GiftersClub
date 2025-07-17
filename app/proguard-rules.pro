
# ----------------------------------------------------------------
# R8 / ProGuard keep rules to preserve generic signatures and reflection targets
# ----------------------------------------------------------------
-keepattributes Signature
-keepattributes *Annotation*

# Keep your model and network classes (adjust package if needed)
-keep class club.gifters.giftersclub.models.** { *; }
-keep class club.gifters.giftersclub.network.** { *; }

# Gson: keep classes and generic signatures for JSON (if you use Gson)
-keep class com.google.gson.** { *; }
-keep class com.google.gson.stream.** { *; }
# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile