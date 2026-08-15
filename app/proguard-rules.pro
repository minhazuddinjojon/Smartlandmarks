# ---------------------------------------------------------------------------
# Retrofit / OkHttp
# ---------------------------------------------------------------------------
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# ---------------------------------------------------------------------------
# Gson — DTOs are reflected over, so keep their field names.
# ---------------------------------------------------------------------------
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.example.smartlandmarks.data.remote.dto.** { *; }

# ---------------------------------------------------------------------------
# Room entities
# ---------------------------------------------------------------------------
-keep class com.example.smartlandmarks.data.local.entity.** { *; }

# ---------------------------------------------------------------------------
# osmdroid
# ---------------------------------------------------------------------------
-dontwarn org.osmdroid.**
-keep class org.osmdroid.** { *; }

# ---------------------------------------------------------------------------
# Hilt / Dagger
# ---------------------------------------------------------------------------
-dontwarn dagger.hilt.**
