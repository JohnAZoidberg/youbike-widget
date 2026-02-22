# Ktor
-keep class io.ktor.** { *; }
-keepclassmembers class io.ktor.** { *; }
-dontwarn io.ktor.util.debug.**

# Missing JVM classes referenced by Ktor (not available on Android)
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class me.danielschaefer.android.youbike.**$$serializer { *; }
-keepclassmembers class me.danielschaefer.android.youbike.** {
    *** Companion;
}
-keepclasseswithmembers class me.danielschaefer.android.youbike.** {
    kotlinx.serialization.KSerializer serializer(...);
}
