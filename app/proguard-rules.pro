# 基本屬性與註解保留（Gson / Retrofit / Room / Compose 反射相依）
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*, RuntimeVisible*Annotation*, RuntimeVisibleParameterAnnotations

# Enum values()/valueOf() 保護
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Gson @SerializedName 欄位保活（確保 R8 full mode 下所有 @SerializedName 欄位與泛型簽章不被裁剪）
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Retrofit 介面與 HTTP 註解方法保活（R8 full mode 下避免動態代理介面或 suspend Continuation 遭裁剪）
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1> {
    @retrofit2.http.* <methods>;
}
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# 專案 Gson 模型欄位保留（AiClient / IntelClient 未標 @SerializedName 之欄位與建構子）
-keep class com.phantom.checker.network.** {
    <init>(...);
    <fields>;
}
-keep class com.phantom.checker.util.PhantomPrefs$ChatRagSessionCache {
    <init>(...);
    <fields>;
}
-keep class com.phantom.checker.util.PhantomPrefs$QuickChatCommand {
    <init>(...);
    <fields>;
}

# Shizuku 跨行程反射載入（ShizukuWatchdogService UserService 與 AIDL stub IShizukuShellService）
-keep class com.phantom.checker.shizuku.** { *; }

# 第三方依賴不必要警告抑制
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-dontwarn rikka.shizuku.**
-dontwarn org.lsposed.hiddenapibypass.**
-dontwarn dev.chrisbanes.haze.**
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.**

# #449 shared 協定 DTO（Gson 反射保留欄位與建構子）
-keep class com.phantom.checker.shared.** {
    <init>(...);
    <fields>;
}

# #454 DesktopLinkStore 序列化 data class（Gson 反射保留欄位與建構子）
-keep class com.phantom.checker.util.PairedDesktop {
    <init>(...);
    <fields>;
}
-keep class com.phantom.checker.util.UnknownAttempt {
    <init>(...);
    <fields>;
}
-keep class com.phantom.checker.util.CommandAuditEntry {
    <init>(...);
    <fields>;
}
-keep class com.phantom.checker.util.DiagnosticLogEntry {
    <init>(...);
    <fields>;
}

# #486 ML Kit bundled barcode-scanning (17.3.0) + AGP 9+ R8 full mode
# firebase-components:16.1.0 consumer rules omit <init>(), causing MlKitContext.get(zzg) == null NPE on main thread.
# Ref: googlesamples/mlkit#1007, googlesamples/mlkit#1018, firebase/firebase-android-sdk#6044
-dontwarn com.google.firebase.components.Component$Instantiation
-dontwarn com.google.firebase.components.Component$ComponentType
-keep class * implements com.google.firebase.components.ComponentRegistrar {
    void <init>();
}
-keep,allowshrinking interface com.google.firebase.components.ComponentRegistrar
-keepclassmembers,allowoptimization,allowobfuscation public class com.google.mlkit.common.** {
    public <init>(...);
}
-keepclassmembers,allowoptimization,allowobfuscation public class com.google.mlkit.vision.barcode.internal.** {
    public <init>(...);
}
