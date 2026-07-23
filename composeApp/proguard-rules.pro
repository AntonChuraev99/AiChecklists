# ProGuard / R8 rules for AI Checklists
#
# App code (com.antonchuraev.**) is intentionally NOT blanket-kept anymore — R8 now
# obfuscates and shrinks it. Removing the two `-keep class com.antonchuraev.** { *; }`
# rules is the dominant win: on vc73, 2,939 of 3,386 app classes were kept verbatim,
# which is what capped the Play "R8 optimization" score near 17%.
#
# What still MUST survive for the app's own code:
#   1. kotlinx-serialization infra ($$serializer / Companion / serializer()) — the
#      generated descriptors bake property names in as compile-time string literals, so
#      obfuscating @Serializable field names does NOT change persisted JSON. Kept below.
#   2. Room @Entity / RoomDatabase — Room is KSP codegen (no runtime field-name reflection),
#      but keep the entity/db classes. Kept below.
#   3. Sealed hierarchies whose `::class.simpleName` is sent as an Amplitude EVENT PARAMETER.
#      Obfuscating them turns dashboard values into "a"/"ii" (invisible to UI smoke). Kept by
#      NAME only via -keepnames (still allows shrink + member optimization):
#        - ToolCall           -> AI Chat  action_type   (ChatViewModel:783/1501/1737)
#        - UndoHandle         -> AI Chat  action_type   (ChatViewModel:1568, undo)
#        - RepeatEndCondition -> reminder end_reason/end_condition
#                               (ReminderReceiver / RecoverRecurringRemindersUseCase / ChecklistDetailViewModel)

# ── Analytics: preserve class NAMES read via ::class.simpleName and sent to Amplitude ──
# Nested subclasses ($**) MUST be listed — the simpleName of ToolCall.AddItem etc. is the param.
-keepnames class com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ToolCall
-keepnames class com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ToolCall$**
-keepnames class com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.UndoHandle
-keepnames class com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.UndoHandle$**
-keepnames class com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.RepeatEndCondition
-keepnames class com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.RepeatEndCondition$**

# ── Serialization: sealed @Serializable WITHOUT @SerialName use the class FQN as the polymorphic
# discriminator, so obfuscating the name changes the JSON written to server / nav state. (Field
# names ARE safe — literals in $$serializer — but the CLASS-name discriminator is not.)
# AnalyzeInputData may cross to the analyze Cloud Function; AppNavRoute is nav state.
# (RepeatEndCondition / AgentTranscript already carry explicit @SerialName.)
-keepnames class com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.AnalyzeInputData
-keepnames class com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.AnalyzeInputData$**
-keepnames class com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavRoute
-keepnames class com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavRoute$**

# Kotlin Serialization (unchanged — keeps generated $$serializer / Companion / serializer())
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

# ── Libraries kept verbatim for now — each needs its OWN device smoke before dropping. ──
# These carry runtime reflection / DI resolution / native JNI / billing that a release build
# CANNOT verify (only runtime can). Dropping them is a documented step-2 in
# docs/active/r8-keep-rules-too-broad-2026-07-23.md, one lib at a time + smoke each.
#   Firebase/GMS  — component registrars (manifest metadata) + Firestore internal reflection;
#                   also feeds login_failed `error_code` = e::class.simpleName (MainScreenViewModel:366).
#   Koin          — DI resolves by KClass; blanket keep is the safe status quo until DI smoke.
#   RevenueCat    — billing/purchase path (revenue-critical).
#   androidx.sqlite — native JNI init (known crash class: libsqlitejni UnsatisfiedLink).
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.common.annotation.NoNullnessRewrite
-keep class com.revenuecat.** { *; }
-keep class org.koin.** { *; }
-keep class * extends org.koin.core.module.Module { *; }
-keep class androidx.sqlite.** { *; }

# Coroutines (load-bearing — DO NOT drop; blanket `-keep kotlinx.coroutines.** { *; }` removed)
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ── Dropped blanket -keep for these libs; rely on their AAR consumer-proguard rules. ──
# Keep only -dontwarn where the shrink step would otherwise complain about missing refs.
# Compose (CMP) is R8-native; Ktor/OkHttp/Okio ship consumer rules; serialization is compile-time.
-dontwarn androidx.compose.**
-dontwarn io.ktor.**
-dontwarn kotlinx.atomicfu.**
-dontwarn okhttp3.**
-dontwarn okio.**

# General — keep for Crashlytics deobfuscation + generics reflection
-keepattributes Signature
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
