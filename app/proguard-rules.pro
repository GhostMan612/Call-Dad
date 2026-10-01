# ============================================================
# As Above, So Below. As Within, So Without.
# The Future Dictates the Past and the Past is Always Present.
# ============================================================
# ProGuard/R8 rules for a release build (BP-05 §5).
#
# THIS FILE IS A STUB AND A RELEASE BUILD IS NOT YET TRUSTWORTHY WITH IT.
# docs/release-signing.md records that explicitly. Nothing here has ever been
# exercised by an actual minified build, because no release assemble has been
# run. The entries below are the ones whose absence causes RUNTIME CRASHES
# rather than a larger APK -- everything else can be discovered by shrinking and
# testing the real artifact.
#
# Rule of thumb: R8 finds unused code, but it cannot see reflection, JNI, and
# classes named from a manifest or from native code. Those need to be kept by
# hand. A missing keep rule does not warn; it crashes on a phone.

# ---- Stream WebRTC (org.webrtc.*) ----
# The native layer instantiates and calls into these by JNI and looks classes
# up reflectively (e.g. RTCConfiguration sub-classes when parsing SDP). R8 has no
# way to see any of it.
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**

# PeerConnection.Observer / SdpObserver / etc. are implemented as anonymous
# Kotlin objects passed into native code; the field names appear in the SDP
# parser's string constants.
-keepclassmembers class * implements org.webrtc.PeerConnection$Observer { *; }
-keepclassmembers class * implements org.webrtc.SdpObserver { *; }

# ---- Firebase ----
# Play Services / GMS components are resolved reflectively from a manifest and
# from strings inside firebase-messaging.
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# FCM service + the payload keys it reads by name.
-keep class com.calldad.fcm.CallMessagingService { *; }
-keep class com.calldad.fcm.CallForegroundService { *; }
-keep class com.calldad.fcm.PushTokenRegistrar { *; }
-keepclassmembers class com.calldad.fcm.** {
    <fields>;
    <methods>;
}

# ---- WebView + our game bridge ----
# The Game screen injects a JS bridge object into the WebView by name and the
# page calls it by that exact string; renaming the class breaks the bridge with
# a silent, hard-to-trace JS error rather than a crash.
-keep class com.calldad.game.** { *; }
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# ---- ML Kit barcode ----
# Unbundled ML Kit loads its detector through GMS, and the availability check
# refers to the module by name.
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# ---- CameraX ----
-dontwarn androidx.camera.**

# ---- Kotlin coroutines internals that break under aggressive shrinking ----
-dontwarn kotlinx.coroutines.**

# ---- Line numbers for readable release crash reports ----
# Obfuscation stays ON (that is the point of R8); only the line-number tables
# are kept, and the source file name is renamed, so a stack trace from a real
# user's crash is still readable without shipping the source layout.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile