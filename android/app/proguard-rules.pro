# WebRTC keep rules
-keep class org.webrtc.** { *; }

# Socket.IO
-keep class io.socket.** { *; }
-dontwarn okio.**
-dontwarn okhttp3.**

# General
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
