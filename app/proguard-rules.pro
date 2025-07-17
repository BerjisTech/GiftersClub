
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

-dontwarn org.bouncycastle.jsse.BCSSLParameters
-dontwarn org.bouncycastle.jsse.BCSSLSocket
-dontwarn org.bouncycastle.jsse.provider.BouncyCastleJsseProvider
-dontwarn org.conscrypt.Conscrypt$Version
-dontwarn org.conscrypt.Conscrypt
-dontwarn org.conscrypt.ConscryptHostnameVerifier
-dontwarn org.openjsse.javax.net.ssl.SSLParameters
-dontwarn org.openjsse.javax.net.ssl.SSLSocket
-dontwarn org.openjsse.net.ssl.OpenJSSE

# Keep main entry points
-keep class club.gifters.giftersclub.**Activity { *; }

# Prevent initializer strip issues
-keepclassmembers class * {
    static <clinit>();
}

# TLS providers (prevent irrelevant warnings)
-dontwarn org.bouncycastle.jsse.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

# Retrofit & Gson
-keep class com.google.gson.** { *; }
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn retrofit2.**

# Retrofit: keep method annotations and generic signatures for HTTP interface methods
-keepattributes Signature,InnerClasses,EnclosingMethod
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# CameraX, uCrop, GPUImage
-keep class androidx.camera.** { *; }
-keep class com.yalantis.ucrop.** { *; }
-keep class jp.co.cyberagent.android.gpuimage.** { *; }
