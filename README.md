# 幻訊 PhantomCheck

Android 訊息管理 App：通知預覽（不觸發已讀）、跨 App 訊息搜尋、關鍵字過濾、AI 防詐偵測、自動回覆。

## 功能
- 📩 通知攔截預覽 — 讀取訊息通知但不開啟原 App，不觸發已讀
- 🔍 跨 App 搜尋 — 所有被攔截的訊息集中在本地 Room DB，一鍵搜尋
- 🚫 關鍵字過濾 — 廣告、促銷通知自訂屏蔽
- 🛡️ AI 防詐偵測 — 接 Mistral API 分析詐騙風險（0-100 分）
- ⏰ 自動回覆 — SMS 時段自動回覆；通訊 App 提供通知快速回覆

## 技術
Kotlin + Jetpack Compose + Room + Retrofit（Mistral API）。

## 安全
API key 透過 local.properties 注入 BuildConfig，**永不寫入程式碼或倉庫**。詳見 SECURITY.md。

## 開發協作
本專案由多個 AI agent 協作開發，工作規則見 AGENTS.md。

- 貢獻指南（工作流程、PR 規則）：[CONTRIBUTING.md](CONTRIBUTING.md)
- 本地環境設定（local.properties / API key 設定）：[docs/LOCAL_SETUP.md](docs/LOCAL_SETUP.md)
