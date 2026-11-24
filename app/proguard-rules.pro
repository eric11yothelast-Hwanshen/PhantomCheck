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

# #454 qrcode-kotlin QR 生成保護
-keep class qrcode.** { *; }
-dontwarn qrcode.**

# #449 / #465 Ktor 3.6.0 server/Netty/WebSockets 與 BouncyCastle R8 規則（KTOR-7539 自補）
-keep class io.ktor.** { *; }
-keep class io.netty.** { *; }
-keep class kotlinx.coroutines.** { *; }
-keep class org.bouncycastle.** { *; }
-dontwarn io.ktor.**
-dontwarn io.netty.**
-dontwarn org.bouncycastle.**
-dontwarn org.slf4j.**
-dontwarn java.lang.instrument.**
-dontwarn sun.**
