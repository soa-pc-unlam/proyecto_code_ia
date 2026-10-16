# Keep Gson model classes (data layer) so JSON (de)serialization keeps working
# even if minification is enabled.
-keep class com.soa.stocksecurity.data.** { *; }

# Retrofit / OkHttp
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-keepattributes Signature
-keepattributes *Annotation*
