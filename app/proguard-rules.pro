# Blockchain — Web3j
-keep class org.web3j.** { *; }
-keep class org.bouncycastle.** { *; }

# QR — ZXing
-keep class com.google.zxing.** { *; }

# Kotlin serialization
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}

# Keep Compose
-keep class androidx.compose.** { *; }

# Play Integrity API
-keep class com.google.android.play.core.integrity.** { *; }
