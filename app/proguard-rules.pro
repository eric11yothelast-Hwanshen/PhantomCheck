# 基本屬性與註解保留（Gson / Retrofit / Room / Compose 反射相依）
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*, RuntimeVisible*Annotation*, RuntimeVisibleParameterAnnotations

# Enum values()/valueOf() 保護
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
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
