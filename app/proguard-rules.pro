# Keep Kotlin serialization
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * { kotlinx.serialization.SerialName *; }
-keep class kotlinx.serialization.** { *; }
-keep class kotlinx.serialization.internal.** { *; }
