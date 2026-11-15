# 本地環境設定（Local Setup）

目標：clone 後 5 分鐘內完成建置。

## 1. 環境需求

- Android Studio（Ladybug 以上）、JDK 17、Android SDK 35
- 詳見 [../CONTRIBUTING.md](../CONTRIBUTING.md)

## 2. 設定 local.properties

`local.properties` 已被 `.gitignore` 排除，**永遠不會進倉庫**，所有機密只放在這裡。

在專案根目錄建立 `local.properties`（若 Android Studio 已自動生成，直接編輯即可），加入以下欄位：

```properties
# Mistral API 設定——只放欄位名稱範例，請填入你自己的值，絕對不要提交真實 key
mistral.apiKey=
mistral.baseUrl=
```

| 欄位 | 說明 |
|---|---|
| `mistral.apiKey` | 你的 Mistral API key。**只存在本機**，不要貼到任何檔案、issue、PR 或聊天訊息中 |
| `mistral.baseUrl` | Mistral API endpoint，選填。留空時程式會使用預設官方 endpoint |

> 若你不小心外洩 key：立即在服務商端撤銷該 key → 換新 key → 參考 [../SECURITY.md](../SECURITY.md) 清除 git 歷史。

## 3. BuildConfig 注入機制

`app/build.gradle.kts` 會在設定階段讀取 `local.properties`，並把值注入 `BuildConfig`：

```kotlin
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

buildConfigField("String", "MISTRAL_API_KEY", "\"${localProps.getProperty("mistral.apiKey", "")}\"")
buildConfigField("String", "MISTRAL_BASE_URL", "\"${localProps.getProperty("mistral.baseUrl", "")}\"")
```

程式碼中透過 `BuildConfig.MISTRAL_API_KEY` / `BuildConfig.MISTRAL_BASE_URL` 讀取（見 `app/src/main/java/com/phantom/checker/network/MistralClient.kt`）。金鑰不會出現在原始碼或版本控制中。

- 沒有 `local.properties` 或欄位留空時，建置仍會成功，`BuildConfig` 值為空字串；AI 防詐功能會因缺少 API key 而無法運作，其他功能不受影響。
- 修改 `local.properties` 後需重新 Sync / Build 才會生效。

## 4. 建置與執行

```bash
./gradlew assembleDebug
```

或在 Android Studio 中直接 Run。建置成功後，將 App 安裝到裝置／模擬器即可。

## 5. 驗收檢查

- [ ] `local.properties` 未被 git 追蹤（`git status` 不應出現它）
- [ ] `./gradlew assembleDebug` 成功
- [ ] 專案內（含 git 歷史）沒有任何真實 API key
