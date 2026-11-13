# kotlinx.serialization
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class io.apimeter.wear.**$$* { *; }
-keepclassmembers enum io.apimeter.wear.** { *; }
# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
