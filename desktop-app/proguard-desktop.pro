# Keep the Coil image pipeline and its ServiceLoader-registered network fetcher:
# packaged (ProGuard) builds lost okhttp3/coil3.network classes and the
# META-INF/services entry, breaking all avatar/attachment loads on Desktop.
-keep class coil3.** { *; }
-keepclassmembers class coil3.** { *; }
-keepclasseswithmembers class * implements coil3.util.FetcherServiceLoaderTarget { *; }
-keep class okhttp3.** { *; }
-keepclassmembers class okhttp3.** { *; }
-keep class okio.** { *; }
-keepclassmembers class okio.** { *; }
-dontwarn coil3.**
-dontwarn okhttp3.**
-dontwarn okio.**
