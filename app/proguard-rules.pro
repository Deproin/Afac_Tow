# Keep Room Database Models and DAOs
-keep class com.example.data.model.** { *; }
-keep class com.example.data.dao.** { *; }

# Keep Retrofit & Moshi API Data Classes
-keep class com.example.api.** { *; }
-dontwarn com.example.api.**

# Keep Jackson/Moshi reflections
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.squareup.moshi.** { *; }

# Keep Compose Material Icons used in UI
-keep class androidx.compose.material.icons.** { *; }

# Google API Client Rules (Prevents 'key error' in Release mode due to obfuscation)
-keep class com.google.api.client.** { *; }
-keep class com.google.api.services.drive.** { *; }
-keepattributes Signature,RuntimeVisibleAnnotations,AnnotationDefault
-keepclassmembers class * {
  @com.google.api.client.util.Key <fields>;
}
-dontwarn com.google.api.client.**
-dontwarn com.google.api.services.drive.**

# Ignore missing standard Java classes used by Apache HTTP Client (transitive dependency of Google API Client)
-dontwarn javax.naming.**
-dontwarn org.ietf.jgss.**
-dontwarn org.apache.http.**
