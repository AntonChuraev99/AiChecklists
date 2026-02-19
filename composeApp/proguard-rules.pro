# ProGuard rules for AI Checklists

# Keep application classes
-keep class com.antonchuraev.aichecklists.** { *; }
-keep class com.antonchuraev.homesearchchecklist.** { *; }

# Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.antonchuraev.**$$serializer { *; }
-keepclassmembers class com.antonchuraev.** {
    *** Companion;
}
-keepclasseswithmembers class com.antonchuraev.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Firebase
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# Google Play Core (In-App Review)
-dontwarn com.google.android.gms.common.annotation.NoNullnessRewrite

# RevenueCat
-keep class com.revenuecat.** { *; }

# Koin
-keep class org.koin.** { *; }
-keep class * extends org.koin.core.module.Module { *; }

# Compose
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }

# SQLite
-keep class androidx.sqlite.** { *; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Ktor
-keep class io.ktor.** { *; }
-keep class kotlinx.coroutines.** { *; }
-dontwarn io.ktor.**
-dontwarn kotlinx.atomicfu.**

# OkHttp (used by Ktor)
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# Generative AI (Gemini)
-keep class dev.shreyaspatil.** { *; }
-keep class com.google.ai.** { *; }
-dontwarn dev.shreyaspatil.**

# General
-keepattributes Signature
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
