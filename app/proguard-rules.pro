# =====================================================================
# MyTube ProGuard / R8 Configuration Rules
# =====================================================================

# Keep basic annotations and class signatures
-keepattributes *Annotation*,InnerClasses,Signature,EnclosingMethod,SourceFile,LineNumberTable

# ---------------------------------------------------------------------
# NewPipeExtractor & Mozilla Rhino JavaScript Engine
# ---------------------------------------------------------------------
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.tools.**
-keep class org.schabi.newpipe.extractor.** { *; }
-dontwarn org.schabi.newpipe.extractor.**

# ---------------------------------------------------------------------
# Kotlinx Serialization
# ---------------------------------------------------------------------
-dontwarn kotlinx.serialization.**
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}

# ---------------------------------------------------------------------
# AndroidX Room Database & Entities
# ---------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
-keep class vn.lobie.mytube.data.local.db.entity.** { *; }
-keep interface vn.lobie.mytube.data.local.db.dao.** { *; }

# ---------------------------------------------------------------------
# AndroidX Media3 / ExoPlayer
# ---------------------------------------------------------------------
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# ---------------------------------------------------------------------
# OkHttp & Okio
# ---------------------------------------------------------------------
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# ---------------------------------------------------------------------
# Coil 3 Image Loading
# ---------------------------------------------------------------------
-dontwarn coil3.**
