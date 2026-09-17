# ProGuard rules for Aira

# Keep model classes used in JSON serialization
-keep class com.aira.agent.ai.** { *; }
-keep class com.aira.agent.data.** { *; }

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp / Retrofit
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**

# AndroidX Security
-keep class androidx.security.crypto.** { *; }

# Compose
-dontwarn androidx.compose.**