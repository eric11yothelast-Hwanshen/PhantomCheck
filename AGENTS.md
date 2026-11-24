# AGENTS.md — AI 協作開發規則

本倉庫由多位 AI agent 協作開發。所有 agent（包括人類協作者）必須遵守以下規則。

## 角色分工

| Agent | 模型 | 負責範圍 | Branch 前綴 |
|---|---|---|---|
| Google Antigravity | Gemini / Claude | 主要功能開發、大型重構、新模組 | feature/ |
| Mistral Code (GLM Flash) | GLM-5.3 Flash | 小型修改、bug 修正、文件、樣式微調 | fix/、docs/ |
| Vibe（管理對話） | GLM | 任務分派、code review、PR 合併、倉庫管理 | —（不直接寫程式） |

## 鐵則（違反 = PR 直接退回）

1. **絕不提交機密**：API key、密碼、token 一律不得寫入任何檔案。
   機密只存在 local.properties（已 gitignore），程式經 BuildConfig 讀取。
2. **一個任務一個 branch**：不同 agent 不得推到彼此的 branch，更不得直接推 main。
3. **所有變更走 PR**：PR 標題格式 [feature|fix|docs] 簡述，填寫 PR template。
4. **開工前先看 issue**：每個任務先在 GitHub issue 建立/認領，branch 名對應 issue 編號，如 feature/12-scam-alert。
5. **不留未完成合併**：PR 描述必須註明「完成」或「WIP（缺什麼）」，交接資訊寫在 PR 或 issue。
6. **UI 字串抽離紀律**：新 UI 字串一律寫進 res/values/strings.xml，不得在 .kt／res XML／Manifest 硬寫自然語言字面；資源 key 用 snake_case、相同字面共用 key。
7. **文件雙語紀律（2026-11-23）**：之後所有 CHANGELOG 新條目、README 變更（`README.md` 與 `README.zh-TW.md`）、Release notes、新增功能的面向使用者描述，**一律中英雙語同步撰寫（中文在前、English 隨後）**，缺任一語言即 review 退回。

## 工作流程

1. issue 建立（Vibe 分派）
2. agent 認領 → 開 branch → 實作 → push → 開 PR
3. CI（gitleaks + build）通過
4. review（Vibe / 另一 agent）
5. squash merge 進 main → 關閉 issue

## 交接清單（PR 必填）
- 做了什麼、為什麼
- 動了哪些檔案
- 沒做完的部分與下一步建議
- 已通過的測試/驗證方式

## 衝突預防
- 兩個 agent 同時開工時，任務範圍不得重疊（Vibe 分派時保證）。
- 開工前先 git pull origin main rebase。
- 共用檔案（build.gradle.kts、AndroidManifest.xml、AppDatabase.kt）的修改屬「小型修改」以上，需在 issue 註明。

## 發佈檢查清單（未完成前不得打 tag）
1. **版號 bump**：確認 `app/build.gradle.kts` 之 `versionCode` 與 `versionName` 已遞增並與發佈版號一致。
2. **CHANGELOG.md 新條目**：依 Keep a Changelog 格式補齊本次版本的所有新增、變更與修復條目（繁體中文）。
3. **README.md 同步**：確認功能總覽、系統需求、版本重點與安裝說明已對齊最新版本。
4. **Tag release notes 含變更說明**：建立 GitHub Release / tag 時，release notes 須附上完整變更說明（說明書）。
5. **Release 資產驗證**：確認 CI（`gitleaks` + `build`）全綠燈，並驗證產出之 APK 資產完整可下載。
6. **依賴版本更新**：依賴版本須檢查並升級至當下最新穩定版（Google Maven / Maven Central 為準）。
7. **R8 Release 冒煙（#490 E4）**：凡變更 `app/` 或 `app/proguard-rules.pro`，發佈前必須執行 `:app:assembleRelease` 零錯誤，確認 R8 full mode 下無縮減崩潰（含 ML Kit `ComponentRegistrar`、`kotlinx.coroutines` 與 Gson DTO）。
8. **`mapping.txt` 保留策略（#490 E5 決策）**：本倉庫為公開倉庫，`release.yml` **不上傳** `mapping.txt` 為公開 Release/CI artifact（避免暴露混淆映射表）；需還原 release 堆疊時，請以對應 tag commit 於本機執行 `:app:minifyReleaseWithR8` 取得 `mapping.txt` 並以 Android SDK `retrace` CLI 離線還原。

