# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.tkos.miam.**$$serializer { *; }
-keepclassmembers class com.tkos.miam.** { *** Companion; }
-keepclasseswithmembers class com.tkos.miam.** { kotlinx.serialization.KSerializer serializer(...); }
