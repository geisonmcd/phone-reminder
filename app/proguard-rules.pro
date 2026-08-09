# Google API Client / Drive models use reflection via @Key fields.
-keepattributes Signature,RuntimeVisibleAnnotations,AnnotationDefault
-keepclassmembers class * {
  @com.google.api.client.util.Key <fields>;
}
-keep class com.google.api.client.** { *; }
-keep class com.google.api.services.drive.** { *; }
-keep class com.google.api.client.googleapis.** { *; }
-dontwarn com.google.api.client.**
-dontwarn com.google.api.services.drive.**
-dontwarn org.apache.http.**
-dontwarn com.google.android.apps.common.proguard.**
