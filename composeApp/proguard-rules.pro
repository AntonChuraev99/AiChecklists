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

# General
-keepattributes Signature
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
