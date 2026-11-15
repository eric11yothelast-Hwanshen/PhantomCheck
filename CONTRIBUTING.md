# 貢獻指南（CONTRIBUTING）

歡迎參與幻訊 PhantomCheck 開發！本專案由多位 AI agent 協作開發，所有貢獻者（人類或 agent）都必須遵守以下規則。完整規則見 [AGENTS.md](AGENTS.md)，資安政策見 [SECURITY.md](SECURITY.md)。

## 開發環境需求

| 工具 | 版本 |
|---|---|
| Android Studio | Ladybug 或以上（含 AGP 8.7.x 支援） |
| JDK | 17 |
| Android SDK | compileSdk / targetSdk 35，minSdk 26 |
| Kotlin | 2.0.21（由專案 Gradle 設定鎖定） |

在地端建置前，請先完成 [docs/LOCAL_SETUP.md](docs/LOCAL_SETUP.md) 的 `local.properties` 設定，否則建置可以成功，但 Mistral API 相關功能（AI 防詐偵測）會無法使用。

## 鐵則（違反 = PR 直接退回）

1. **絕不提交機密**：API key、密碼、token 一律不得寫入任何檔案、commit 訊息、issue 或 PR。機密只存在 `local.properties`（已被 `.gitignore` 排除），經 Gradle BuildConfig 注入。
2. **一個任務一個 branch**：不同協作者不得推到彼此的 branch，更不得直接推 `main`。branch 命名對應 issue 編號，例如 `feature/12-scam-alert`、`fix/7-crash-on-start`、`docs/3-contributing`。
3. **所有變更走 PR**：PR 標題格式 `[feature|fix|docs] 簡述`，並依照 `.github/pull_request_template.md` 填寫所有欄位。
4. **開工前先看 issue**：每個任務先在 GitHub issue 建立／認領，branch 名對應 issue 編號。
5. **不留未完成合併**：PR 描述必須註明「完成」或「WIP（缺什麼）」，交接資訊寫在 PR 或 issue。

## 工作流程

1. 在 GitHub issue 認領任務（或由管理流程分派）。
2. 確認本地 `main` 最新：`git checkout main && git pull origin main`。
3. 從最新 `main` 開 branch：`git checkout -b <prefix>/<issue編號>-描述`。
4. 實作變更，只動任務範圍內的檔案。
5. Push branch 並開 PR（填寫 PR template，寫上 `Closes #issue編號`）。
6. CI（gitleaks 機密掃描 + build）必須通過。
7. 經 review 後 squash merge 進 `main`，issue 自動關閉。

## PR 必填清單

- 做了什麼、為什麼
- 動了哪些檔案
- 沒做完的部分與下一步建議
- 已通過的測試／驗證方式

## 共用檔案衝突預防

- 兩個 agent 同時開工時，任務範圍不得重疊。
- 開工前先 rebase 最新 `main`。
- 修改共用檔案（`build.gradle.kts`、`AndroidManifest.xml`、`AppDatabase.kt`）屬於「小型修改」以上等級，需先在 issue 註明。

## 回報安全漏洞

請勿開公開 issue。請透過 GitHub 的 Security → Report a vulnerability 提交 security advisory，詳見 [SECURITY.md](SECURITY.md)。
