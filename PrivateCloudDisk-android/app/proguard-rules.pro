# PrivateCloudDisk Android ProGuard Rules

# ── Keep Data Models ──
-keep class com.privateclouddisk.android.data.model.** { *; }
-keepclassmembers class com.privateclouddisk.android.data.model.** { *; }

# ── Retrofit ──
-keepattributes Signature
-keepattributes Exceptions
-keepattributes *Annotation*
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * { @retrofit2.http.* <methods>; }

# ── Gson ──
-keep class com.google.gson.** { *; }
-keepattributes SerializedName

# ── OkHttp ──
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# ── Glide ──
-keep public class * extends com.bumptech.glide.module.AppGlideModule
-keep class com.bumptech.glide.GeneratedAppGlideModuleImpl

# ── Room ──
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# ── Hilt ──
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper

# ── WebRTC ──
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**

# ── EventBus ──
-keepattributes *Annotation*
-keepclassmembers class * { @org.greenrobot.eventbus.Subscribe <methods>; }
-keep enum org.greenrobot.eventbus.ThreadMode { *; }

# ── Timber ──
-dontwarn timber.log.**

# ── ExoPlayer ──
-keep class com.google.android.exoplayer2.** { *; }

# ── Common ──
-keepclassmembers class * implements android.os.Parcelable { public static final android.os.Parcelable$Creator *; }
-keep class * implements android.os.Parcelable { *; }