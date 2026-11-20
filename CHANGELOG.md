# 更新日誌（CHANGELOG）

本專案遵循 [Keep a Changelog](https://keepachangelog.com/zh-TW/1.1.0/) 格式，並採用語意化版本控制。

## [3.0.0] - 2026-11-20

### 新增
- **AI 記憶庫核心引擎（#314 / PR #319）**：
  - Room 資料庫升級 v17 → v18（`MIGRATION_17_18`），新增 `memories` 資料表（含 `category`、`subject`、`content`、`layer`、`status`、`confidence`、`sourceType`／`sourceRef`、`privacyTier`、`createdAt`／`updatedAt`、`lastUsedAt`／`useCount` 等欄位與 `(status,layer)`、`(category,subject)`、`(updatedAt)`、`(privacyTier)` 四組索引）。
  - 新增 `MemoryEngine`：同 `subject` 去重與衝突偵測（`status = conflict` 雙筆保留、絕不覆蓋），`resolveConflictGroup` 裁定（勝出筆設為 `active + confirmed`，落敗筆設為 `archived`）；短期記憶 `expiresAt` 預設 7 天，啟動／查詢時 `lazyArchiveExpiredShortTerm` 與每日 `MemoryExpiryWorker` 過期封存。
  - 純本地確定性 Top-K 檢索（0 額外 API 呼叫），依關鍵字、分類意圖與時間條件計分排序，注入 `buildRagSystemPrompt` 的「【長期記憶】」區塊（套用 `AiClient.sanitizeForPrompt` 防注入），命中後更新 `lastUsedAt` 與 `useCount`。
  - `privacyTier = sensitive` 雙重硬性排除閘門（SQL `privacyTier = 'normal'` ＋ Kotlin `.filter { it.privacyTier != PRIVACY_SENSITIVE }`）。
  - `MemoryEngine.loadIntegratedSnapshot` 整合查詢既有備忘錄（`notes (source=auto)`）、`ai_rules`（白名單／標籤）、`user_tag_rules` 與 `memo_rules`，提供統一查詢、啟停與刪除介面（連動 `NotificationCollectorService.invalidateRuleCaches()`，零資料表遷移）。
- **第 5 個分頁「記憶」（#315 / PR #319）**：`MainTab.MEMORY("記憶")` 接入 `LiquidGlassTabBar`（標準／縮小／自動隱藏三模式）、`saveableStateHolder` 與 `PhantomBackgroundBox(layer = GLOBAL)`，完整 `MemoryTab` UI：頂部搜尋列、9 顆篩選 chips（`FlowRow` 自適應大字體與窄螢幕）、8 個可收合區塊（⚠️ 衝突待確認、長期記憶、短期記憶、已封存記憶、自動備忘錄、白名單規則、標籤規則、自動記事／待辦規則）、來源與徽章顯示（`AI 推測` / `已確認` / `🔒 敏感` / 到期時間）、雙按鈕衝突裁定、完整新增／編輯對話框、左滑封存／恢復／刪除、多選批次刪除確認對話框，以及右上角設定齒輪直達設定頁「記憶庫與 AI 助手」卡片。
- **自訂 AI 助手（#315 / PR #319）**：支援自訂名字（預設「幻訊」）、個性 preset（預設／簡潔專業／親切詳細／嚴謹防詐顧問／完全自訂）＋自訂個性欄位、回答詳細度（精簡／適中／詳細），注入【助手設定】嚴格管控 ≤120 tokens，並於 `ChatScreen` 頂列與助手回覆氣泡即時顯示圓形頭像與名字。
- **設定頁新增分類「記憶庫與 AI 助手」（#315 / PR #319）**：可收合卡 `card_memory_assistant`（含記憶擷取、雙模式獨立注入滑桿、自訂助手與圓形頭像裁切、隱私分級說明），並於 `SettingsCatalog` 註冊 `memory_chat_capture`、`memory_normal_top_k`、`memory_normal_per_item_chars`、`memory_saver_top_k`、`assistant_custom_name`、`assistant_personality`、`assistant_verbosity`、`assistant_avatar`、`memory_privacy_tier_info` 共 9 項設定，支援本地搜尋、AI 語意搜尋與聊天 `setting_toggle` / `setting_adjust` / `open_setting`。

### 變更與修正

- **省 token 快捷開關（#315 / PR #319）**：AI 助手頁頂列新增省 token 快捷開關 chip 與 `Info` 資訊按鈕，透過 `PhantomSettingsBus` 與設定頁 100% 雙向即時同步；彈窗逐條對照 `PhantomPrefs.isEffective*` 實際行為。
- **Safe Browsing key 改 BYOK（#316 / PR #318，行為變更）**：移除 `BuildConfig.SAFE_BROWSING_API_KEY`／`local.properties` fallback 路徑，統一僅設定頁 `EncryptedSharedPreferences` 輸入；未設定時誠實跳過該源且 UI 提示一致，release 建置後已無 `SAFE_BROWSING_API_KEY` 欄位殘留。
- **教學 auto-skip 雙條件（#316 / PR #320）**：新手教學 auto-skip 延遲 400ms 後仍需 bounds 已量測且目標仍缺席，才自動跳步，避免極慢機型在分頁切換進行中誤跳。
- **Safe Browsing 卡過時文案移除（#316 / PR #320）**：「Key 狀態：已於 App 加密儲存（優先於 BuildConfig）」改為「Key 狀態：已於 App 加密儲存」，並移除不可達的「Key 狀態：已從 local.properties 注入」分支（`getSafeBrowsingKeySource` 僅回傳 EncryptedPrefs／NONE）。

## [2.20.0] - 2026-11-20

### 修正
- **記事頁待辦事項展開區可捲動與高度上限（#310 / PR #311）**：
  - `NotesTab` 待辦事項展開區（`!todoSectionCollapsed.value`）外層改為 `Column(Modifier.heightIn(max = maxTodoExpandedHeight))`，上限取自 `BoxWithConstraints` 可用高度之 2/3（`maxHeight * 2f / 3f`），避免清單過長時撐高卡片擠壓下方記事區。
  - 篩選膠囊列 `FlowRow` 與 `8.dp` `Spacer` 固定於外層 `Column` 頂部，不隨清單捲動；清單段改為 `Column(Modifier.weight(1f, fill = false).verticalScroll(todoListScrollState))` 支援卡內上下捲動，並以 `key(todoFilter) { rememberScrollState() }` 於切換篩選條件時重置捲動位置至頂部。
  - 短清單或空清單維持依內容 wrap-content 高度呈現，不強制填滿 2/3；收合精簡顯示（`todoSectionCollapsed = true`）與收合狀態持久化維持不變。

## [2.19.0] - 2026-11-20

### 新增
- **儲存空間明細與資料庫壓縮維護（#299 / PR #303）**：
  - 設定頁「資料管理」新增唯讀儲存空間明細（顯示資料庫含 WAL／SHM、背景圖片、快取與合計大小）與「立即壓縮資料庫」按鈕。
  - 新增 `DbMaintenance` 與 `VacuumWorker` 執行 `wal_checkpoint(TRUNCATE)` 與交易外 `VACUUM`（`RetentionWorker` 刪除超過 1000 列且 24 小時節流自動觸發，手動清除已讀／刪除全部亦非同步排程）。

### 變更與修正
- **中央堆疊 v6 首尾邊界填滿與短清單模式（#298 / PR #302）**：
  - `CenterFocusCardStack` 新增首尾邊界填滿補償（`pAbove`／`pBelow` 對稱等效 fill pitch，上限 `0.98 * cardH`），頭尾定點零留白且全幅模式整組展開滿高。
  - `edgeGap` 由 `4.dp` 調整為 `6.dp`（預設值與訊息頁／記事頁兩處呼叫點同步），首卡距我的最愛與尾卡距底部導覽列皆為 `6.dp`。
  - 新增短清單置頂模式（`neededPitch > maxPitch`，約 2–4 則時固定於 `topAnchor` 緊貼標題列下 `6.dp` 堆疊；單筆維持單卡路徑）。
- **空間優化與資料庫索引（#299 / PR #303）**：
  - `release` 建置類型啟用 R8 程式碼縮減（`isMinifyEnabled = true`）與資源壓縮（`isShrinkResources = true`），並於 `proguard-rules.pro` 保留 Gson 序列化模型與 Shizuku 跨行程介面。
  - Room 資料庫升版至 v17（`MIGRATION_16_17`），補齊 `messages(isOwn, timestamp)`、`messages(isOwn, isRead)`、`todos(relatedMessageId)`、`sender_avatars(updatedAt)` 與 `intel_cache(checkedAt)` 索引。
  - `sender_avatars` 頭像快取上限設為 300 筆（保留最新 `updatedAt`），`intel_cache` 72 小時過期列隨 `RetentionWorker` 定期清除。
- **電量與穩定性優化（#300 / PR #304）**：
  - `KeepAlivePolicy` watchdog 間隔放寬為 15→45 分鐘，`ShizukuWatchdogService` 穩態輪詢放寬為 300→600 秒（8 小時安全網、1 秒 rescue、5 秒 rebind 維持不變）。
  - `HeartbeatPolicy` 平時改採 `ExistingWorkPolicy.KEEP` 消除重排抖動，模式切換時以 `UPDATE` 切換 15/30 分鐘；關閉常駐統計通知時仍保留 30 分鐘低頻通知監聽重連檢查（不啟動前景服務與精確鬧鐘）。
  - `StatsForegroundService` 補齊 `startForegroundCompat` 三層例外防護（失敗即 `stopSelf()`）、rescue 指數退避（60 秒窗口第 3 次內 1 秒重試，超過改 30 秒起加倍封頂 5 分鐘），並於熄屏且未逾 staleness 時在 DB 查詢前早退（亮屏由 `ScreenStateReceiver` 補齊）。
  - 熱路徑 Regex 與時間格式常數化：`AiClient`、`NotificationCollectorService`、`ChatScreen` 預編譯 Regex 與 `DateTimeFormatter`，`PhantomPrefs.formatMessageTime` 改用 `ThreadLocal` 快取。
- **主畫面與對話介面元件拆分（#301 / PR #305）**：
  - 將 `MainActivity` 與 `ChatScreen` 拆分為獨立分頁／元件檔案（`MessagesTab.kt`、`NotesTab.kt`、`StatsTab.kt`、`TodoSection.kt`、`ChatScreenParts.kt`），純架構整理、零行為變更。
- **立即壓縮資料庫等待邏輯修正（#306 / PR #307）**：
  - 修正 `DbMaintenance` 於背景 `VACUUM` 任務已排隊時點擊「立即壓縮資料庫」的等待判斷，避免長時間等待或卡住。

## [2.18.0] - 2026-11-19

### 修正
- **移除內建個人化預設與設定頁衝突文案（#292 / PR #293）**：
  - `PhantomPrefs.DEFAULT_MY_DISPLAY_NAMES` 改為空集合，未設定時不預帶任何預設顯示名稱；`isMyDisplayName` 與自我身分偵測於空集合時不再誤判。
  - 移除 `NotificationCollectorService` 內建個人化 AI 規則自動寫入（`seedDefaultAiRulesIfNeeded`），並新增一次性清除機制 `purgePresetAiRulesIfNeeded`（以 `preset_ai_rules_purged_v1` 標記僅刪除舊版自動寫入的兩條預設規則，保留使用者自建規則，資料庫 schema 不變）；同時將 AI prompt 與管理頁範例文案去品牌化。
  - 快速設定「AI 風險分析」副標改為動態引用 `aiMinTriggerLength` 字數門檻，不再寫死 10 字元。
  - 「每日 AI 額度上限」標題與 `SettingsCatalog.NUMERIC_SPECS` 單位統一為「次」，chip 支援顯示並選中非預設值（0–100 次、步進 10 次）。
  - 省 token 模式生效時，「每則訊息 AI 智慧掃描」與「每日 AI 摘要」副標顯示「已暫停」，「AI 風險分析」副標提示「僅命中可疑特徵才分析」。

## [2.17.0] - 2026-11-19

### 新增
- **隱私權政策與免責聲明文件（#284 / PR #286）**：新增 [PRIVACY.md](PRIVACY.md) 隱私權政策與 [DISCLAIMER.md](DISCLAIMER.md) 免責聲明（純文件新增，App 功能無變動）。
- **GPL-3.0 開源授權文件（#285 / PR #287）**：新增根目錄 [LICENSE](LICENSE)（GNU GPL v3.0 官方全文）並於 README 補齊「授權」說明段落（純文件新增）。

### 變更與修正
- **主頁／設定頁新手教學修復、各廠牌通知存取受限引導與 Shizuku 一鍵授權（#288 / PR #289）**：
  - Android 13 以上首次啟動時，新手教學會等候系統通知權限彈窗結束後才顯示，不再與系統視窗重疊。
  - 權限步驟高亮「尚未開啟通知存取權限」引導卡，並新增可展開的「開關變灰／無法開啟？」疑難排解，提供小米／Redmi／POCO、Samsung（自動封鎖程式）、OPPO／realme／OnePlus、vivo／iQOO 四廠牌專屬「允許受限制的設定」解鎖說明與「前往應用程式資訊」按鈕。
  - 資料庫尚無訊息時，教學的四個訊息操作步驟合併為單一提示步，不再連續無亮區空轉。
  - 教學依步驟自動切換分頁（AI 助手／記事／統計／訊息），並新增設定齒輪、自動記事規則與新增待辦的獨立高亮目標。
  - 搜尋把手步驟改為置中膠囊形高亮，指引位置更加明顯可見。
  - 設定頁教學修復為完整 8 步導覽：導覽期間自動清空搜尋列，並正確展開個人化與 Shizuku 卡片、捲動定位白名單卡片，索引不再錯位。
  - 「前往開啟通知存取權限」升級為直達本 App 專屬通知存取詳情頁（Android 11 以上支援；不支援的系統自動改開總清單頁）。
  - Shizuku 已連線時，可點擊「Shizuku 一鍵授權」直接授予通知存取權限並即時刷新狀態。

## [2.16.0] - 2026-11-19

### 變更與修正
- **中央堆疊 v5 微調（#280 / PR #281）**：
  - 訊息／記事堆疊上下留白緊湊化：`CenterFocusCardStack` 新增 `edgeGap = 4.dp`（取代原 `8.dp`）延伸上下可視範圍；`MessagesHomeTab` 於 `CENTER` 模式外層區塊間距由 `12.dp` 緊湊為 `8.dp`（非 `CENTER` 模式維持 `12.dp`）；記事頁堆疊容器上方 `Spacer` 於 `CENTER` 模式由 `8.dp` 縮為 `4.dp`（`LIST` 模式維持 `8.dp`），Scaffold 共用外層內距、我的最愛列與底部導覽列不變。
  - 阻尼彈簧吸附：改採官方 `snapFlingBehavior`（`SnapLayoutInfoProvider(listState, SnapPosition.Start)` ＋ `rememberSplineBasedDecay` ＋ `spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)`），維持 `s_i = i * exposure` 吸附幾何與互補式 `contentPadding`。
  - 近焦縮放緩衝與焦點陰影漸變：於同一 `graphicsLayer` draw phase 內加入 `FastOutSlowInEasing` 近焦縮放緩衝（`absU <= 1f` 由 `0.94f` 平滑過渡至 `1.0f`，`absU > 1f` 沿 `0.04f` 遞減至下限 `0.82f`，於 `absU = 1` 連續銜接，pivot 維持頂緣 `TransformOrigin(0.5f, 0f)`），並新增 `cardShape = RoundedCornerShape(20.dp)` 與 `focusShadowBoost = 6.dp` 隨 `eased` 漸變陰影；`translationY` 線性鎖定、`alpha` 邊緣淡出與 `zIndex` 連續公式皆維持不變。

## [2.15.0] - 2026-11-19

### 變更與修正
- **中央堆疊 v4 修正（#276 / PR #277）**：
  - 修復滑動時焦點上方卡片被裁切／快甩後缺繪：改採對稱 `contentPadding`（`leading >= 3 * exposure` 且與 `trailing` 互補，總捲動區間 `(n - 1) * 40.dp` 與吸附手感不變），確保焦點上方 2~3 張卡片原生落入 `visibleItemsInfo` 穩定組合繪製、露出頂帶可辨識 App 圖示與名稱；唯一 `clipToBounds` 僅保留於最外層堆疊容器（我的最愛下緣至底部導覽上緣）。
  - 初始貼頂與線性過渡：`firstVisibleItemIndex == 0` 且 `offset == 0` 時錨點 `8.dp` 貼頂零留白；頭尾過渡由 `smoothstep` 改為線性 `lerp`（以 `zone` 正規化），連續單調無布林切換。
  - 記事短清單幾何修正：過渡區寬度改為動態 `zone = min(1.5f, (n - 1) / 2f)`，根治 `n <= 4` 時頭尾過渡區重疊造成第 2 則記事沉底；記事頁續用共用堆疊元件、容器高度由 `BoxWithConstraints` 動態計算。
  - 待辦事項到期副標單行：`NotesTodoRow` 到期資訊 `Text` 新增 `maxLines = 1`、`softWrap = false` 與 `TextOverflow.Ellipsis`，不再被右側按鈕擠成兩行（展開／收合兩狀態同時生效）。

## [2.14.0] - 2026-11-19

### 變更與修正
- **中央堆疊卡片緊湊化（#270 / PR #272）**：
  - 單卡高度鎖定 `92.dp`（`fontScale >= 1.3f` 上限 `104.dp`），內距 `12.dp` 三段式佈局撐滿內容、消除卡片內空黑底。
  - 堆疊錯位與 `edgeFade` 調整為 `40.dp`，縮放每層 `0.04f` 遞減（下限 `0.82f`），底部 `40.dp` 露出帶顯示 App 名稱＋未讀數／記事標籤，訊息頁與記事頁表現完全一致。
- **自動檢查更新改為每次開啟 App 一次（#271 / PR #273）**：
  - 移除每日檢查 gate 與彈窗去重，開啟開關且網路可用時每次開啟 App 檢查 GitHub 最新版本並提示；通知欄通知維持每日每版本最多一則。
  - 以 process 級 `AtomicBoolean` 旗標防重入，同一行程螢幕旋轉與返回頁面不重複請求 API；無網路、403/429、逾時靜默處理。

## [2.13.0] - 2026-11-19

### 新增
- **訊息／記事中央堆疊 v3（#263 / PR #264）**：
  - 零留白與全卡填滿：採動態層數、`8.dp` 邊距與動態 `contentPadding`（`bottom = viewport - exposure`），確保全項目可達且消除底部留白。
  - 連續慣性滑動：改用 `LazyColumn`＋`LazyListState`（`cacheWindow 1.0/0.5`）＋`SnapPosition.Start` 統一單調吸附，移除一次一張限制，`rememberSnapFlingBehavior` 支援快滑跨多張後平滑吸附。
  - 連續 lerp 過渡與視覺參數：頂／中／底三態錨點 smoothstep 連續插值，根治第 0 張與頭段瞬移；露出 `44.dp`、頂緣 pivot `scaleStep = 0.035f` 遞減，viewport 內維持不透明並於邊緣像素淡出。

### 變更與修正
- **SMS 雙路徑重複記錄修正（PR #268）**：
  - `SmsAutoReplyReceiver` 專職自動回覆並移除 `db.messageDao().insert` 寫入，簡訊記錄統一由通知攔截路徑（`NotificationCollectorService`）單一記錄與 SHA-256 `dedupKey` 去重；時段判斷、敏感關鍵字、`TW_MOBILE_REGEX`、4 小時冷卻與每日 3 封上限等自動回覆行為維持不變（`shouldSkipReply` 提前 return 省去多餘協程排程）。
  - 附註：issue #106 待實機驗證後關閉。

## [2.12.0] - 2026-11-19

### 新增
- **訊息頁中央置頂堆疊重構（#249）**：
  - 重構 `Components.kt` `CenterFocusCardStack`（v2）：固定卡片高度 `cardHeight = 112.dp`（訊息預覽 `maxLines = 1` + `Ellipsis`、App 圖示 `44.dp`、`contentPadding` `12.dp` / compact `8.dp`），`VerticalPager` 改採 `PageSize.Fixed(cardHeight + 24.dp)` 與動態底部 `contentPadding`（確保末卡可滑動至正中央）。
  - 等距堆疊常數：`exposure = 24.dp`、`scaleStep = 0.06f`（第 n 層縮放 `1 - 0.06n`）、`maxVisibleLayers = 4`（螢幕高 `< 640dp` 時為 `3`，超過層數 `alpha` 歸零）。
  - 頂部對齊（`scroll = 0`，第 0 張貼齊頂部 `topGap = 10.dp`、zIndex 最高）與中央對齊（`|scrollOffset| >= 0.5` 完成過渡、焦點卡置中且上下對稱）間以滾動進度 `f ∈ [0, 1]` 連續內插，全數僅經 `graphicsLayer` 計算不觸發重組；`MainActivity` 訊息區套用並保留一般條列、未讀分頁與點擊／長按手勢。
- **記事頁共用中央置頂堆疊並移除「往下露頂部」模式（#250）**：
  - 記事「中央堆疊」改共用新版 `CenterFocusCardStack`：`NoteCardItem` 固定高度 `112.dp`、內文 `maxLines = 2` + `Ellipsis`，將置頂／編輯／刪除改為右上角小圖示按鈕，底部不再露出按鈕排；待辦收合與逾期排序維持不變。
  - 移除 `PhantomPrefs.NoteCardLayoutMode.PEEK_TOP`，`getNoteCardLayoutMode` 讀到舊值 `"peek_top"` 時自動遷移為 `CENTER`；`MainActivity` 刪除撲克牌式分支。
  - `SettingsActivity` 與 `SettingsCatalog` 同步移除「往下露頂部／撲克牌」描述與關鍵字，`home_card_layout` 與 `notes_card_layout` 的 `valueLabels` 統一為 `["一般條列", "中央堆疊"]`（索引 `LIST → 0`、`CENTER → 1`）。
- **AI 自動待辦智慧時間推斷與提前 30 分鐘提醒（#252）**：
  - `AiClient.buildSystemPrompt` 注入系統時區當前時間，要求 AI 從訊息線索（如「明天 9 點」「週三晚」「10/28 前」「下週一到」）推算具體 `yyyy-MM-dd HH:mm` 填入 `todo_due`，無任何線索才留空。
  - `NotificationCollectorService.saveAutoMemoIfPresent` 採 `parseStructuredDueAt(todoDue)`（過濾過去時間）→ `ClientTimeParser.parseNaturalReminderTime` 解析訊息內容 → `msg.timestamp + 2h` fallback 鏈（全程 `try/catch`）；有事件時間時套用 `REMINDER_LEAD_MINUTES = 30` 提前 30 分鐘提醒（若 `<= now` 則 `max(now + 5min, eventTime - 30min)`）。
  - `ClientTimeParser` 補齊跨年 `MM/dd` 進位（如 12/31 收到 `1/2` 落在明年）；duplicate 待辦更新路徑同步套用該規則並在 `dueAt` 變更且 `> now` 時重排 `ReminderScheduler.scheduleTodo`。

### 變更與修正
- **記事置頂／取消置頂不再覆寫 `updatedAt`（#251）**：
  - `MainActivity.onTogglePinNote` 更新 `NoteEntity` 時僅反轉 `pinned` 欄位，保留原 `updatedAt` 顯示時間，並維持 `NotesWidgetProvider.updateAllIfChanged` 同步刷新。
- **深度分析與每日摘要模型切換 chip 分用途綁定並即時同步（#253）**：
  - `MessageDetailActivity` 深度分析 chip 與 `MainActivity` `StatsTab` 每日摘要 chip 改以 `AiClient.getProfileForPurpose`（`DEEP_ANALYSIS` / `DAILY_SUMMARY`）顯示，選擇時寫入 `PhantomPrefs.setPurposeProfileId`＋`PhantomSettingsBus.notifyChanged()`（不再寫入全域 `setActiveProvider`），並隨 `PhantomSettingsBus.version` 與 `ON_RESUME` 重讀；空 key 檢查同步改為 `getProfileForPurpose`。
  - `ChatScreen.AiProviderSwitcherChip` 新增 `label` 參數（預設 `"對話模型："`），深度分析顯示「深度分析模型：」、每日摘要顯示「每日摘要模型：」。
- **每日與每週摘要準時性補強（#254）**：
  - `DailySummaryPolicy` 與 `WeeklySummaryPolicy` 新增 `maybeCatchUp(ctx)`，於 `MainActivity.onCreate`、`BootReceiver` 與 `SettingsActivity` 儲存設定後檢查並立即補跑錯過之當日／當週摘要。
  - `DailySummaryWorker` 失敗時改回傳 `Result.retry()`（`BackoffPolicy.EXPONENTIAL, 10min`，最多 3 次）不覆寫重試排程，成功或達上限後再重排明日。
  - `WeeklySummaryPolicy.generateWeeklySummary` 新增 `anchorMs` 參數，以排程錨定週（`between(start, start + 7d)`）計算區間與 `weekKeyFor(anchorMs)`，避免 Doze 延遲至週一時誤抓新週；constraints 移除 `RequiresBatteryNotLow`、改為 `NetworkType.CONNECTED`。

## [2.11.0] - 2026-11-18

### 新增
- **首頁卡片排列模式重寫為「中央聚焦垂直滾輪堆疊」（#240）**：
  - `PhantomPrefs.HomeCardLayoutMode` 精簡為 `LIST`（一般條列）／`CENTER`（中央堆疊）兩種，舊 `peek_top`／`peek_bottom` 與 legacy `card_stack_effect_enabled` 自動遷移為 `CENTER`；`SettingsActivity` 對應改為兩顆膠囊選項。
  - `Components.kt` 新增 `CenterFocusCardStack`：`VerticalPager` + `PageSize.FillParentSize`（`beyondViewportPageCount = 2`）可滾動分頁容器，每頁以 `graphicsLayer` 依 `pagerState` 頁偏移動態計算 `translationY`（對稱上下錯開）、`scale`（|q|=1 → 0.92f、|q|=2 → 0.84f）、`alpha`（距離 > 2 隱藏）與 `zIndex`（`derivedStateOf` 隨焦點變化），焦點卡恆在容器垂直正中央、鬆手由 Pager 原生 snap 吸附，首末卡皆可捲動至正中央。
  - 堆疊模式卡片以 `PhantomGlassCard(overlapOpaqueBoost = true)` 提升不透明深色底（`#F6161822` ≥ 0.96，亮色主題 surface 0.96）防後卡文字透出；`MessagesHomeTab` 移除壞掉的 peek 模式、負 spacing 與自訂 snap 佈局。
- **記事分頁待辦收合＋智慧排序、記事卡片三種排列模式（#241）**：
  - `PhantomPrefs` 新增 `NoteCardLayoutMode`（`LIST`／`PEEK_TOP`／`CENTER`，key `note_card_layout_mode`）與 `notes_todo_collapsed`（預設收合）；`MainActivity` `NotesTab` 重構為「搜尋列＋可收合待辦卡＋weight(1f) 記事容器」結構。
  - 待辦卡標題列可點擊切換收合（＋ 34dp 箭頭），`AnimatedVisibility` 平滑過渡且狀態持久化；收合時隱藏篩選膠囊列、只顯示距離現在最近且尚未逾期的 1 則未完成待辦（全部逾期時順延最早逾期那則、無未完成時顯示提示文字）。
  - `ReminderScheduler.sortTodosSmart` 重寫：未完成未逾期（dueAt 升冪）→ 已逾期未完成（dueAt 升冪，過期自動下移）→ 已完成（完成時間降冪）；與 AI 助手、桌面小工具共用同一排序。
  - 記事卡片排列模式：一般條列為原垂直列表；「往下露頂部」為 `LazyColumn` + `SnapLayoutInfoProvider(SnapPosition.Start)` 撲克牌式堆疊（逐卡 `graphicsLayer` 依 `layoutInfo` 錯開 52dp、`zIndex` 遞減）；「中央堆疊」重用 `CenterFocusCardStack`；共用新抽出的 `NoteCardItem`（堆疊模式限 6 行內文、`overlapOpaqueBoost` 防透字）；待辦收起時記事容器自動填滿垂直空間。
- **設定頁子區塊可收合（#242）**：
  - 「個別 App 節流覆寫」與「自訂常用指令（N 則）」改為可收合子區塊（標題列點擊／箭頭切換，預設收合並顯示數量摘要）；展開狀態以新 `PhantomPrefs.isSettingsSubCardExpanded`／`setSettingsSubCardExpanded`（key `settings_expanded_subcards`）持久化，獨立於大分類卡片（`KEY_COLLAPSED_CARDS`），大卡片收合重開不會重置子區塊。
- **首頁篩選標籤列可收合成單行（#245）**：
  - `MessagesHomeTab` 篩選 chip 列收合時僅顯示「全部、未讀」與作用中篩選／標籤 chip，其餘隱藏（單行不換行），右側 34dp 圓角箭頭切換；狀態以 `home_filter_chips_collapsed` 持久化，不再溢出換行。

### 變更與修正
- **根除浮動層外層直角黑底（#243）**：
  - `Components.kt` 新增 `PhantomDialog`：`Dialog(usePlatformDefaultWidth = false)` + `SideEffect` 將 window 背景 `setBackgroundDrawable(ColorDrawable(TRANSPARENT))` 設為全透明，深色不透明底只由內層 `PhantomFloatingSurface`（clip → background → border）繪製，圓角框線外 100% 透出桌布；「手動新增自動記事規則」彈窗由 `AlertDialog` 改用 `PhantomDialog`。
  - `MainActivity`（訊息操作、標籤選擇）、`ConversationScreen`（訊息操作）、`ChatScreen`（待辦提醒清單）共 4 處 `ModalBottomSheet` 一律 `containerColor = Color.Transparent`，內容改包 `PhantomFloatingSurface` 圓角容器。
- **介面與設定模型同步（#244）**：
  - 首頁／記事排列模式與收合狀態統一以 `PhantomSettingsBus.version.collectAsState()` 驅動 `remember` 重新讀取 `PhantomPrefs`，設定頁變更回分頁立即生效。
- **每日／本週摘要準時觸發（#246）**：
  - `DailySummaryPolicy`／`WeeklySummaryPolicy` 由 `PeriodicWorkRequest` + `UPDATE`（不重錨點，改設定後仍按舊時間觸發）改為 `OneTimeWorkRequestBuilder` + `enqueueUniqueWork(REPLACE)`，`initialDelay` 為距下次觸發的精確時間（下限 60 秒），Worker `doWork` 以 `try/finally` 結束時自我重新排程，杜絕排程漂移。
- **AI 助手／設定搜尋支援新設定（#247）**：
  - `SettingsCatalog` 更新 `personal_home_card_layout` 描述與別名，新增 `personal_notes_card_layout`、`personal_notes_todo_collapsed`、`personal_home_filter_chips` 搜尋項；`NumericSettingSpec` 新增 `valueLabels` 選項型數值設定，登錄 `home_card_layout`（0=一般條列／1=中央堆疊）與 `notes_card_layout`（0=一般條列／1=往下露頂部／2=中央堆疊），AI 助手可對話直接切換排列模式，系統提示輸出選項對照與目前值。
- **發佈準備（#247）**：
  - 版號更新：`versionCode` 29、`versionName` "2.11.0"；同步 `CHANGELOG.md` 與 `README.md` 功能總覽。

## [2.10.0] - 2026-11-18

### 新增
- **首頁卡片排列模式三選一（#230）**：
  - `PhantomPrefs` 新增 `HomeCardLayoutMode`（`LIST` 一般條列預設／`PEEK_TOP` 撲克牌式往下露頂部 52dp／`PEEK_BOTTOM` 資料夾式往上露底部邊緣縮放），自動遷移舊 `card_stack_effect_enabled`（`true → PEEK_BOTTOM`、`false → LIST`）。
  - 重疊模式由 `MessagesHomeTab` `LazyColumn` 搭配 `rememberSnapFlingBehavior`＋自訂 `SnapLayoutInfoProvider` 於列表層控制 `zIndex` 與 `graphicsLayer`（`translationY`／`scaleX`）滑動吸附，並透過 `PhantomGlassCard(overlapOpaqueBoost = true)` 提升深色毛玻璃不透明度與邊框陰影防止底層透字；全鏈移除 `StackedAppCard` 與舊開關，並於 `SettingsActivity` 改為三顆並排 `FilterChip`（接入 `SettingsCatalog` `personal_home_card_layout` 與 `PhantomSettingsBus` 即時生效）。
- **寄件者頭像擷取與多層顯示（#231）**：
  - Room 升版至 v16（`MIGRATION_15_16`），新增 `sender_avatars` 表；`NotificationCollectorService` 於 `EXTRA_MESSAGES` 解析處擷取 `MessagingStyle.Message.getSenderPerson()?.getIcon()`，降採樣至 `≤96px` 並壓縮至 `≤16KB` 後 upsert 寫入（自動清理 30 天前舊頭像）。
  - `AppIconHelper` 新增 `SenderAvatarImage`（`LruCache` 128 項快取），顯示優先序為 `sender_avatars` → 圓形裁切 App 圖示 → 寄件者首字母 Material 彩色圓頭像；整合至 `AppThreadsScreen`（40dp）與 `ConversationScreen` 非 `isOwn` 氣泡（28dp 頂端對齊）。
- **自己回覆抑制與自我身分名稱設定（#237）**：
  - 隨 `MIGRATION_15_16` 新增 `own_reply_echo` 表；於通知快速回覆與 SMS fallback 成功送出後寫入抑制列，3 分鐘內 `NotificationCollectorService` 解析新訊息尾巴若命中（正規化全等或 `diceBigramSimilarity ≥ 0.9`）即略過重複入庫並刪除抑制列。
  - `PhantomPrefs` 新增 `my_display_names`（預設含 `"Eric Chiu"`，trim 不分大小寫比對），群組通知寄件者命中時直接標記 `isOwn = true`／`isRead = true` 並跳過詐騙警示；設定頁「訊息與通知」卡片與 `SettingsCatalog`（`notif_my_display_names`）新增編輯入口。

### 變更與修正
- **浮動層黑底範圍與遮罩修復（#229）**：
  - `ChatScreen` 歷史對話抽屜容器補上上下 `10.dp` 垂直 inset 與 `displayCutoutPadding()`，使 `20.dp` 圓角完整落在桌布內；抽屜 scrim 與 `MainActivity` 自動記事規則全螢幕 scrim 統一調降為 `0.45f`，`PhantomFloatingSurface` 深色底調整為 `0xEB12151E`（約 92% 不透明）。
- **記事搜尋膠囊置左（#232）**：
  - `MainActivity` `NotesTab` `notes_search_pill` 移除 `PhantomSearchBarPill` 的 `weight(1f)`，改為緊湊置左並以 `Spacer(weight(1f))` 推開右側 `Tune` 按鈕（保留 48dp 觸控目標與安全區域間距）。
- **AI 歷史對話抽屜速度感知手勢（#233）**：
  - `ChatScreen` `AnchoredDraggableState` `positionalThreshold` 調整為 `0.35f`、`velocityThreshold` 調整為 `400.dp`；拖曳放手改用 `VelocityTracker` 投影（`offset + velocity * 0.15s`）搭配 `400.dp/s` 或 `25%` 寬度判定開啟／關閉，維持 lambda `offset {}` 延遲讀取與所有現有互動零回歸。
- **自動待辦相似去重強化（#234）**：
  - `NotificationCollectorService.saveAutoMemoIfPresent` 強化 `normalizeForSimilarity`（NFKC、小寫、標點與空白正規化），`TodoDao` 新增 `findRecentPendingByAppLabel`，補齊 `diceBigramSimilarity ≥ 0.9` 判定，命中時原地更新既有待辦並重排提醒與小工具而不重複建立。
- **底部分頁欄水滴與全域流暢度優化（#235, #236）**：
  - `LiquidGlassTabBar.kt` 重用 `Path`（`rewind()`）、`Stroke`／`Color`／`Brush.verticalGradient` 快取物件，水滴彈簧調升為 `stiffness = 600f, dampingRatio = 0.8f`，並將 `onHorizontalDrag` 改為同步狀態驅動以消除每幀配置與 coroutine churn。
  - `MainActivity` 以 `rememberSaveableStateHolder`（`SaveableStateProvider(selectedTab.name)`）保留分頁切換狀態；完成全庫 `LazyColumn` `key`、`derivedStateOf`、`graphicsLayer` lambda 與 `StatsDetailWidgetProvider` SQL `GROUP BY`／`AppIconHelper` `LruCache` 慣例稽核。
- **發佈準備（#238）**：
  - 版號更新：`versionCode` 28、`versionName` `"2.10.0"`；復核全依賴為最新穩定版並同步 `CHANGELOG.md` 與 `README.md`。

## [2.9.0] - 2026-11-18

### 新增
- **設定即時同步匯流排 `PhantomSettingsBus`（#225）**：
  - 新增 `util/PhantomSettingsBus`（`MutableStateFlow<Long>` 版本計數＋`notifyChanged()`），`SettingsCatalog.applyBooleanSetting` 與 `applyNumericSetting` 成功寫入偏好後立即發送變更通知。
  - `MainActivity`（含 `StatsTab`）、`ChatScreen` 與 `SettingsActivity`（含 `FilterSettingsScreen` 及子元件 `replyProxyEnabled`／`intelEnabled`／`updateAutoCheck`）即時收集匯流排，AI 對話或設定搜尋修改後不需重進頁面或等待 `ON_RESUME` 即同步反映於 UI。

### 變更與修正
- **浮動層不透明實底與遮罩修復（#222）**：
  - `Components.kt` 新增 `PhantomFloatingSurface`（`.clip(shape)` 置於 `.background()` 之前，深色底色 `Color(0xF012151E)`、淺色 `surface.copy(alpha = 0.95f)`、1dp `outlineVariant` 描邊，不使用 `glassSurface`／blur；頁面內玻璃卡片行為維持不變）。
  - `ChatScreen` 歷史對話抽屜（`cornerRadius = 20.dp`、session 卡 alpha 提升為選中 `0.95f`／未選 `0.9f`）與 `MainActivity` 自動記事規則懸浮面板改用 `PhantomFloatingSurface`，兩者全螢幕 scrim 統一調升至 `0.65f`（抽屜為 `0.65f * progress`）；完成全庫浮動層四點稽核。
- **AI 歷史對話抽屜拖曳效能修復（#223）**：
  - `ChatScreen` 抽屜位移改由 `Modifier.offset { IntOffset(...) }` lambda 於 layout 階段讀取 `drawerState.offset`（含 `NaN` fallback），scrim 透明度改由 `Modifier.drawBehind` 於 draw 階段繪製，可見性由 `derivedStateOf` 控制；`LaunchedEffect` 改用 `snapshotFlow` 收集 `currentValue to isAnimationRunning`，消除拖曳期間每幀重組。
- **訊息卡片堆疊邊緣對比強化（#224）**：
  - `StackedAppCard` 層色改用 `lerp(surfaceVariant, onSurface, 0.25f)`，alpha 調升為層 1 `0.55f`／層 2 `0.35f`，scale 調整為 `0.96f`／`0.92f`，層 1 補上 1dp `outlineVariant.copy(alpha = 0.4f)` 描邊，深淺主題與背景圖情境下皆清晰可辨。
- **自動記事 keyword-only「不再記事」語意化與全鏈健檢（#226）**：
  - `NotificationCollectorService.saveAutoMemoIfPresent` 的字面 `ACTION_NEVER` 閘門改為僅對含 `packageName` 或 `sender` 條件的規則生效，keyword-only `ACTION_NEVER` 交由 AI 語意判斷（涵蓋即時通知與 `PendingAiAnalysisWorker` 離線補佇列）。
  - `AiClient.buildSystemPrompt` 記事規則段強化為結構化條列（`id`／`App`／`寄件者`／`關鍵字`／`動作`）、明確語意命中定義與反例（如 Gogolook 驗證碼規則不誤殺銀行 OTP），快取 key 升至 `memoV4`，並更新記事規則面板說明文案。
- **發佈準備（#227）**：
  - 版號更新：`versionCode` 27、`versionName` `"2.9.0"`；復核全依賴為最新穩定版並同步 `CHANGELOG.md` 與 `README.md`。

## [2.8.0] - 2026-11-18

### 新增
- **訊息卡片堆疊效果與個人化開關（#212）**：
  - `Components.kt` 新增 `StackedAppCard` 包裝層（維持 `ui/components/` 零改動），依訊息數量於主卡後方繪製 0／1／2 層 `surfaceVariant` 視覺堆疊（支援捲動微幅視差、點擊 140ms 收合回饋、靜音半透明與緊湊模式層距減半）。
  - 設定頁「個人化」新增「訊息卡片堆疊效果」`FluidSwitch` 開關（`card_stack_effect_enabled`，預設開啟）並同步寫入 `SettingsCatalog`。
- **AI 助手側邊歷史抽屜與分頁切換行為設定（#215, #216）**：
  - 拆除舊 `ModalBottomSheet`，改為於 `ChatScreen` 內容區自製 `AnchoredDraggableState` 左側抽屜（支援右滑開啟、左滑收回、`BackHandler` 優先關閉、40% 遮罩與 `LazyColumn` 流暢捲動）。
  - 設定頁「AI 助手與分析」新增「切到 AI 助手時接續上次對話」開關（`ai_chat_tab_behavior`，預設開啟新對話，可切換為自動接續最近會話）並同步寫入 `SettingsCatalog`。
- **記事規則懸浮視窗與關鍵字 AI 語意判斷（#217）**：
  - 移除 `NotesTab` 內嵌規則區塊，改於右上角配置 `Tune` 按鈕（48dp＋規則數角標）展開懸浮玻璃規則面板（保留手動新增、自然語言解析確認卡、啟停與刪除）。
  - `AiClient.buildSystemPrompt` 升級為語意同類比對指示（`riskCache` key 由 `memoV2` 升至 `memoV3`），`MemoRuleEngine` 保留字面 `contains` 作為未啟用 AI／離線降級方案。
- **統計頁排行榜、統計設定分類、卡片自訂與 4 張新圖表（#218）**：
  - 新增「今日 App 訊息量排行榜 Top 5」卡片（`todayCountsByApp`＋相對量橫條），並於設定頁「個人化」之後新增獨立「統計」分類卡片（12 張統計卡可個別勾選顯示／隱藏，`PhantomPrefs` `StringSet` 持久化並全數接入 `SettingsCatalog`）。
  - 新增「7 日風險趨勢折線」、「未讀／已讀比例環形」、「各 App 佔比橫條 Top 8」與「待辦完成率環形」4 張純 Compose `Canvas` 圖表（Room 維持 v15 零 migration，僅新增唯讀 `@Query`）。
- **記事桌面小工具（#219）**：
  - 新增 `NotesWidgetProvider`（2x2，支援縮放至 4x2、`goAsync()`、`PendingIntent.FLAG_IMMUTABLE`、`updateAllIfChanged` 差異更新），顯示總筆數與最近 5 則記事（置頂優先、36 字截斷＋相對時間），點擊直達 App 記事分頁；全庫 `noteDao()` 新增／編輯／刪除／釘選與自動備忘錄寫入點即時同步刷新。

### 變更與修正
- **我的最愛橫列角標與未讀排序修復（#213）**：
  - `MessageDao` 新增 `unreadCountsByApp()`（排除 `isOwn`），`AppGroupUi` 與我的最愛角標改用真實未讀數（0 隱藏、>99 顯示 `99+`），同步修正 `HomeSortOrder.UNREAD` 排序基準。
- **AI 助手輸入列鍵盤遮擋修復（#214）**：
  - 於 `MainTab.ASSISTANT` 內容層單一位置補上 `consumeWindowInsets(padding).imePadding()`（避免 double lift），並於鍵盤彈出且清單貼底時平滑捲動至最新訊息。
- **發佈準備與依賴版本稽核（#220）**：
  - 版號更新：`versionCode` 26、`versionName` `"2.8.0"`；完成全依賴穩定版稽核並同步 `CHANGELOG.md` 與 `README.md`。

## [2.7.0] - 2026-11-18

### 新增
- **自動記事／待辦規則引擎與管理介面（#203）**：
  - Room 升版至 v15（單一非破壞性 `MIGRATION_14_15`），新增 `memo_rules` 資料表與 `MemoRuleDao`，支援「永遠記事（`note`）」、「永遠待辦（`todo`）」與「永遠不要自動加入（`never`）」三種動作（優先序 `never > todo > note`）。
  - 新增純函式 `MemoRuleEngine`，於 `NotificationCollectorService.saveAutoMemoIfPresent`（涵蓋即時通知與 `PendingAiAnalysisWorker` 離線補分析）執行前置規則檢查（30 秒 TTL 快取，寫入/切換/刪除即時失效），並於 `AiClient.buildSystemPrompt` 注入規則與將 `memoRuleSig` 納入 `riskCache` key。
  - `ChatScreen` `add_rule` 擴充支援 `kind="memo"`（彈出確認卡寫入 DB）；「記事」分頁新增「自動記事規則」管理卡（列表、開關、刪除、手動新增對話框與自然語言 `AiClient.parseMemoRule` 解析＋確認卡）。
- **已記事／已待辦首頁篩選、待辦來源回溯與三範圍排除（#204）**：
  - `MessageFilter` 新增 `Noted`（已記事）與 `Todoed`（已待辦）篩選按鈕，並加入 `ALL_OPTIONAL_HOME_FILTERS` 與設定頁自訂篩選清單；`MessageCard` 新增 12dp 已記事／已待辦小圖示。
  - 「記事」分頁待辦列與搜尋結果待辦卡新增「查看來源訊息」按鈕（跳轉 `MessageDetailActivity`）；`MessageDetailActivity` 與自動記事長按新增「不再自動記事／待辦」入口，提供三範圍確認對話框（寄件者／App／關鍵字，寫入 `memo_rules` `never` 規則）。
- **自己回覆右側氣泡與持久化（#207）**：
  - `messages` 新增 `isOwn` 欄位（隨 `MIGRATION_14_15` 建立，預設 `0`），於 `ConversationScreen`（通知快速回覆與 SMS fallback）、`ChatScreen` `reply_message` 與 `ReplyProxyReceiver` 三處回覆成功後寫入 `isOwn = true`、`sender = "我"` 之對話紀錄。
  - `ChatBubbleItem` 針對 `isOwn` 渲染靠右對齊、78% 寬度、疊加 `primary` 0.16 alpha 淡色氣泡（隱藏風險點、AI 標籤與 OTP 擷取）；所有統計計數、未讀數、風險分布、ScamCenter、每日／每週摘要節錄與去重查詢全面加入 `isOwn = 0` 排除。
- **新手教學擴充至 18 步（#209）**：
  - 主頁教學由 15 步擴充為 18 步（同步 `KEY_ONBOARDING_STEP_V2` `coerceIn(1, 18)`），`SEARCH_PILL` 改為「下拉搜尋」（錨定頂部把手），新增「快速回覆」、「自動記事規則」與「已記事／已待辦與來源回溯」步驟，並更新 `FILTER_CHIPS` 文案說明固定表頭；舊已完成教學用戶不重跳。

### 變更與修正
- **首頁固定表頭與清單獨立捲動（#206）**：
  - `MessagesHomeTab` 重構為外層 `Column` 固定區（離線橫幅 → 統計卡 → 分類篩選列 → 我的最愛列）搭配 `LazyColumn(weight(1f))` 獨立捲動訊息清單，維持 `listState` 與分頁載入行為不變。
- **頂部下拉搜尋把手與統計卡工具列整合（#205）**：
  - 移除主頁頂部搜尋膠囊與工具列，重新整理／設定按鈕移入統計卡右側欄（48dp 觸控目標）；新增 `PhantomPullSearchHandle`（22dp 全寬、中央 4dp×120dp 膠囊，支援 `anchoredDraggable` 下拉與點擊開啟全螢幕搜尋，AI 助手分頁自動隱藏）。
- **對話頁 IME 過度上移修復（#207）**：
  - `AndroidManifest.xml` 為 `MainActivity` 與 `ConversationActivity` 補齊 `android:windowSoftInputMode="adjustResize"`，解決系統預設 `adjustPan` 疊加 `.imePadding()` 造成的 double lift；鍵盤開啟時僅在清單貼底狀態平滑捲至最新訊息。
- **搜尋頁與全庫文字對比度修復（#208）**：
  - 清除 `MessageCard`、`MessageDetailActivity`、`ScamCenterActivity` 與 `SettingsActivity` 殘留之 `Color.Gray`／`Color.Red` 主題盲色，統一改用 `MaterialTheme.colorScheme.onSurfaceVariant`／`error`；`ChatBubbleItem` 與 `MessageCard` 內文顯式指定 `onSurface`。
  - `PhantomFullScreenSearchOverlay` 背景改為不透明純主題色（alpha `1.0`）；`CardBgMode.TRANSPARENT` 滑桿下限調升至 15%（存量值自動 clamp），玻璃關閉／省電模式 fallback 下限提升至 0.35。
- **全域健檢、OEM 相容稽核與發佈準備（#210）**：
  - 補齊全專案有文字輸入之 Activity（`SettingsActivity`、`AutoReplySettingsActivity`、`ApiKeySettingsActivity`、`AppThreadsActivity`、`MessageDetailActivity`、`TagManagementActivity`、`WhitelistManagementActivity`）`windowSoftInputMode="adjustResize"`；完成無障礙 insets、cutout、Room 完整性與 `NotifIds` 互斥稽核。
  - 版號更新：`versionCode` 25、`versionName` `"2.7.0"`。

## [2.6.0] - 2026-11-18

### 新增
- **對話詳情頁底部快速回覆輸入列（#197）**：
  - `ConversationScreen` 底部新增常駐回覆輸入框（`OutlinedTextField`＋發送按鈕，含 `navigationBarsPadding().imePadding()`、鍵盤彈出自動捲動至最新訊息、多選模式下自動隱藏）。
  - `NotificationCollectorService.sendReplyToNotification` 新增可選 `conversationKey` 參數，優先以 `MessagingStyle` `EXTRA_CONVERSATION_TITLE` 精準定位對話（其次 `EXTRA_TITLE`、再同套件 fallback）；送出成功後呼叫 `ReplyProxyManager.startOrRefreshSession` 維持通知欄連續回覆；SMS 類 App fallback 走 `SmsManager` 既有簡訊回覆路徑，全程具備 `try/catch`＋`PhantomReply` logcat 保護。
- **獨立白名單管理頁面與收合持久化（#199）**：
  - 新增 `WhitelistManagementActivity`（`android:exported="false"`，採 `PhantomCheckTheme`＋`PhantomBackgroundBox` 與頂欄返回鍵），將分組白名單管理 UI 移入獨立頁面；`SettingsActivity` 資料管理區精簡為「白名單管理（N 筆）」入口按鈕。
  - `PhantomPrefs` 新增 `whitelist_collapsed_groups` `StringSet` 持久化各 App 分組收合狀態（跨進出與重啟保留，首次無記錄時預設全部展開）；`SettingsCatalog` `whitelist_management` 同步指向新 Activity。

### 變更與修正
- **全域依賴、編譯工具鏈與目標 SDK 升級（#200）**：
  - 升級 Gradle Wrapper 至 `9.9.1`、AGP 至 `9.5.1`（啟用 AGP 9 內建 Kotlin，移除 `org.jetbrains.kotlin.android` 與 `kotlinOptions`）、Kotlin Compose plugin 至 `2.4.21`、`compileSdk` 升至 `37`（滿足 `core-ktx 1.19.1`／`Compose BOM 2026.09.00`／`lifecycle 2.11.0` 編譯要求）、`targetSdk` 升至 `36`（Android 16）。
  - 導入 Jetpack Compose BOM `2026.09.00`（Material 3 `1.4.0`），升級 `core-ktx` `1.19.1`、`activity-compose` `1.13.0`、`lifecycle-runtime-ktx` `2.11.0`、`work-runtime-ktx` `2.12.0`、`Retrofit` `3.0.0`、`Room` `2.8.5`（完成 `kapt` → `KSP 2.3.14` 遷移）。
  - 升級 `Haze` 至 `2.1.0`（`haze`＋`haze-blur`），重寫 `Glass.kt` 遷移至 `Modifier.hazeBlur`＋`HazeInput.Sources`＋自建 `HazeBlurStyle`（取代已停產的 `haze-materials`），完整保留三種卡片底色模式、省電降級與 API < S fallback。
  - `AGENTS.md` 發佈檢查清單新增第 6 項常態規則：每次發佈須檢查並升級依賴至最新穩定版。
- **全螢幕搜尋覆蓋層層級修正（#193）**：
  - 將 `ConversationScreen` 與 `AppThreadsScreen` 之 `PhantomFullScreenSearchOverlay` 移出 `Scaffold` 改由外層 `Box` 包住整個 `Scaffold`（比照 `MainActivity` 寫法），修正搜尋覆蓋層被頂欄遮擋的問題。
- **迷你玻璃搜尋膠囊與工具列排版統一（#194）**：
  - `PhantomSearchBarPill` 改為 36dp 視覺高度膠囊（左放大鏡圖標＋「搜尋」文字，外層保留 ≥48dp 無障礙觸控目標），`MainActivity` 重新整理與設定齒輪按鈕移至搜尋膠囊下方右側對齊，四個頁面搜尋入口視覺風格統一。
- **左滑刪除列背景透明化（#195）**：
  - 移除 `SwipeToRevealRow` 內容層的全寬不透明底色，外層容器改為完全透明並依滑動位移進度控制右側動作按鈕 `alpha`，讓自訂背景圖在訊息氣泡後方完整可見。
- **回覆鏈與 AI 助手動作全面防崩（#196）**：
  - `ReplyProxyManager` 回覆用 `PendingIntent` 改為 `FLAG_MUTABLE`（符合 Android `RemoteInput` 官方規範；關閉／逾時等動作維持 `FLAG_IMMUTABLE`），`ensureChannel`／通知建立／排程全部加上 `try/catch`＋`PhantomReply` logcat。
  - `ReplyProxyReceiver` 廣播與 `goAsync()` 協程補齊 `try/catch`；`ChatScreen` 動作協程加上 `try/catch`＋`PhantomChatAction` logcat，並將 `messages.first()` 改為 `firstOrNull()` 空集合防禦。
- **待辦事項 Flow 即時同步稽核（#198）**：
  - `TodoDao` 新增 `observeAll(): Flow<List<TodoEntity>>`，`MainActivity` 記事分頁與 `ChatScreen` 待辦選單改採 `collectAsState`＋智慧排序即時觀測，桌面小工具刪除／完成／新增後 App 內立即同步；`MessageDetailActivity` 補齊 `ON_RESUME` 刷新 `remindAt` 顯示。
- **版號更新**：`versionCode` 24、`versionName` `"2.6.0"`。

## [2.5.0] - 2026-11-18

### 新增
- **通知欄連續快速回覆（#183）**：
  - 新增 `ReplyProxyManager` 與 `ReplyProxyReceiver`（`android:exported="false"`），建立通知頻道 `phantom_reply_proxy`（`IMPORTANCE_DEFAULT`，名稱「快速回覆對話」）與 `NotifIds` 專屬區段 `120000..129999`（`120000 + Math.floorMod(sessionHash, 10000)`）。
  - `ChatScreen` `reply_message` 送出成功後啟動／更新 `MessagingStyle` 代理通知（保留原始訊息＋「我」回覆紀錄，上限 8 則；Session 採 in-memory LRU 上限 8 組），提供「回覆」（`RemoteInput` resultKey `proxy_reply`）與「關閉」按鈕，支援滑掉（`deleteIntent`）與 `AlarmManager` 閒置逾時自動收起（15 / 30 / 60 分鐘或不自動收起，預設 30 分鐘）。
  - `ReplyProxyReceiver` 送出失敗時（含簡訊 `sms` / `^09\d{8}$` `SmsManager` fallback 失敗）誠實將錯誤訊息附加至對話紀錄串，不假裝成功；設定頁「訊息與通知」卡新增 `reply_proxy_enabled` 開關（預設開）與逾時選項，並註冊 `notif_reply_proxy` 與 `notif_reply_proxy_timeout` 至 `SettingsCatalog`。
- **AI 送出前重複通知折疊（#185）**：
  - 新增純函式 `NotificationFoldUtil`（不修改資料庫與既有 UI 清單），依同 `packageName`、`sender`、`conversationKey`、`riskLabel`、24 小時鏈式時間窗與 `normalized` 相同或 Dice bigram ≥ 0.85 折疊高頻重複通知為 `[AppLabel] 標題（HH:mm–HH:mm 共重複 N 次）`（`N = 1` 維持原格式）。
  - 保護條款：`scam`／`suspicious` 高風險訊息逐則保留不折疊；內含網址或數字序列（訂單編號／金額／OTP／電話）不一致者不折疊。套用至 `ChatScreen` RAG 訊息區、`DailySummaryPolicy.buildSummaryExcerpts`、`WeeklySummaryPolicy` 節錄與 `MessageDetailActivity` `senderHistorySummary`。
- **開啟 App 自動檢查更新（#186）**：
  - `MainActivity` `ON_RESUME` 每日最多一次背景靜默檢查 GitHub 最新版本（`update_last_check_day`，無網路或失敗靜默）；有新版本時於頻道 `app_updates`（`IMPORTANCE_DEFAULT`，`NotifIds.UPDATE_AVAILABLE = 1007`）發送每版本每日一次通知，並於前景顯示 `AlertDialog`（「前往下載」導向設定頁關於卡＋「稍後再說」，內文含「不想再收到更新提醒？可至 設定 → 關於 關閉自動檢查更新。」）。
  - 設定頁關於卡新增 `update_auto_check_enabled` 開關（預設開），並註冊 `update_auto_check` 至 `SettingsCatalog`（別名含「檢查更新」「更新」「版本」）；手動檢查與下載安裝流程維持不變。
- **自動記事／待辦智慧分流（#188）**：
  - `AiClient` 單則與批次分析 schema 擴充 `memo_type`（`"note" | "todo"`，缺漏寬容 fallback `"note"`）與 `todo_due`（`"yyyy-MM-dd HH:mm"` 或空字串），`riskCache` key 加入 `memoV2` 旗標。
  - `NotificationCollectorService.saveAutoMemoIfPresent` 依 `memo_type` 分流：`todo` 建立 `TodoEntity`（`priority = 1`、`dueAt` 解析 `todo_due` 或 fallback `msg.timestamp + 2h`），insert 前以 `TodoDao` 新增 `@Query`（Room 版本維持 14 無 migration）進行 `relatedMessageId` 與 7 天內未完成同標題去重，並呼叫 `ReminderScheduler.scheduleTodo` 與 `ReminderWidgetProvider.updateAllIfChanged`。
  - 抽出 `ClientTimeParser`（含 `NaturalTimeParseResult`）供 `ChatScreen` 與自動待辦共用。

### 變更與修正
- **對話頁訊息卡片黑底溢出修復（#184）**：
  - 修正 `ui/Components.kt` `PhantomGlassCard` 玻璃分支 `.clip(shape)` 順序，改為 `.glassSurface(...)` 之後 `.clip(shape)` 再繪製 `overlayColor`；同步補齊 `phantomGlassSurface` 全分支（含 `overlayColor == null` 與 API < S）於 `glassSurface` 之後使用單一 `remember(cornerRadius)` `RoundedCornerShape` 裁切（`ui/components/` 維持零改動）。
- **自動記事重複記錄去重（#187）**：
  - `NoteDao` 新增 7 天視窗查詢 `@Query`（Room 版本不變），`saveAutoMemoIfPresent` 於寫入前比對同 `relatedAppLabel` 7 天內自動記事（正規化相同或 Dice bigram ≥ 0.9）改為更新既有記事，並嚴格保護 `pinned` 記事不被覆蓋，手動記事不受影響。
- **桌面小工具通知同步清除與按鈕放大（#189）**：
  - `ReminderScheduler.cancel`／`cancelTodo` 同步呼叫 `NotificationManager.cancel` 清除已發出之 `NotifIds.reminder`／`NotifIds.todoReminder`，並於 `ReminderWidgetProvider` 刪除 `TYPE_MSG` 提醒時補齊呼叫 `ReminderScheduler.cancel`；取消勾選（`done = false`）行為維持不變。
  - `widget_reminder.xml` 動作按鈕放大至 `16sp` bold＋`paddingHorizontal="8dp"`＋`paddingVertical="6dp"`＋`minWidth="28dp"`／`minHeight="32dp"` 與 `?android:attr/selectableItemBackground` 點擊回饋，`reminder_widget_info.xml`（含 `xml-v31`）`minHeight` 調整為 `72dp`。
- **版號更新**：`versionCode` 23、`versionName` `"2.5.0"`。

## [2.4.0] - 2026-11-17

### 新增
- **AI 省 Token 五合一與視覺模型修正（#174, #173）**：
  - `ChatRequest` 支援 `prompt_cache_key`（對話使用穩定 session key、分析使用固定 key），分析系統提示與標籤/規則清單採明確排序且位元組級一致置於最前；記錄 `usage.prompt_tokens_details.cached_tokens`。
  - 重構 `ChatScreen.buildRagSystemPrompt` 為「靜態前綴（系統提示＋規則＋完整上下文）＋動態尾段（時間、本筆查詢）」，RAG 訊息改採舊→新固定順序以逐筆命中前綴快取；並導入增量情境摘要快取（300~500 token，支援跨日/RAG 上限變更失效與降級）。
  - 新增「雙模型信心路由」（`ai_dual_routing_enabled` 預設開、`ai_cheap_model` 預設 `mistral-small-2603`，進 `SettingsCatalog`）：先打便宜模型，risk < 30 或 > 70 直接採用，30~70 才升級原模型重判並取較高風險（不重複扣減額度）。
  - 輸出精簡與視覺 Token 優化：Chat 系統提示加入回覆精簡規範；圖片編碼降採樣並依實際尺寸動態估算 `imageTokenOverhead`；每日／週報摘要去重高風險節錄、補齊未分析訊息並設定 token 上限。
  - 修復 `AiClient.isVisionModelSupported`（#173）：`knownTextOnly` 改為精確清單，`mistral-small-2603`（Mistral Small 4 內建視覺）不再被誤擋。
- **卡片底色模式設定（#175）**：
  - 新增 `CardBgMode`（跟隨玻璃強度／透明模式 0–100% 滑桿／深色底）於外觀設定區，並註冊 `card_bg_mode` 與 `card_bg_transparent_alpha` 至 `SettingsCatalog`。
- **待辦事項全面強化（#176）**：
  - `AppDatabase` 升版至 v14（`MIGRATION_13_14` 註冊於 `AppDatabaseHolder`），`TodoEntity` 新增 `completedAt`、`priority`（低/中/高）、`notes` 與 `repeatRule`（每日/每週，完成後自動派生下一筆並重排提醒）。
  - 記事分頁支援逾期變色與「已逾期 X 天」／「今天到期」標籤、篩選（全部/未完成/已完成/逾期）、智慧排序（逾期 > 到期日 > 優先級，與桌面小工具同步）、手動新增「+」與完整 `TodoEditorDialog`（AI 建立之待辦亦可編輯）。
- **白名單管理介面（#177）**：
  - 設定頁新增可依 App 分組收合的「白名單管理」卡片（顯示寄件者、App 圖示、模式、時間、防重複手動新增、啟用切換、滑動/按鈕刪除確認與空狀態），並註冊 `whitelist_management` 至 `SettingsCatalog`；任何變更統一呼叫 `invalidateRuleCaches()`。
- **桌面 2x1 待辦小工具互動按鈕（#179）**：
  - `ReminderWidgetProvider` 新增每列「完成 ✓」（僅待辦顯示，寫入 `isDone`＋`completedAt`）、「編輯 ✎」（跳轉主頁記事分頁並開啟編輯對話框）、「刪除 ✕」按鈕，採 `PendingIntent.getBroadcast`（request code 含 `itemId` 防多實例衝突）＋ `goAsync()` 非同步寫入與立即刷新（快照 key 納入全項 ID）。
- **統一下拉式玻璃搜尋槓與全螢幕搜尋（#180）**：
  - 於 `ui/Components.kt` 實作 `PhantomSearchBarPill` 與 `PhantomFullScreenSearchOverlay`（fade+slide 動畫、自動聚焦、IME inset、即時 `LazyColumn` 搜尋、保留上次字串、AI 語意搜尋額度入口與 ≥48dp 無障礙觸控目標），替換訊息頁、App 對話清單、對話頁與記事頁搜尋欄。

### 變更與修正
- **卡片黑底溢出修復（#175）**：
  - `ui/Components.kt` `phantomGlassSurface` 與 `PhantomGlassCard` 統一共用單一 `RoundedCornerShape` 實例進行背景與外框裁切，並修正 API < S fallback 雙層深色背景疊加問題（`ui/components/` 目錄維持零改動）。
- **白名單注入與 AI 功能全面健檢（#177, #178）**：
  - `ChatScreen.buildRagSystemPrompt` 靜態前綴常設注入結構化白名單（超過 20 筆依 App 分組壓縮），新增 `whitelist_delete` 確認卡動作與 DB 回讀驗證。
  - 盤點全 7 項 `AiPurpose`：`CHAT` 補齊待辦（含逾期/完成）、備忘錄、VIP 寄件者、靜音 App 與提醒關鍵字上下文；`DEEP_ANALYSIS` 補齊寄件者歷史訊息與風險摘要；統一所有 AI 寫入動作（待辦、白名單、規則、標籤）採「執行→回讀驗證→成敗明確回報」模式。
- **新手與設定教學修復與擴充（#181）**：
  - 移除 `Onboarding.kt` 非必要步驟之連鎖自動跳步（僅權限步驟單次自動跳過，缺目標改提示並手動下一步），新增依洞區中心動態上下避讓之字卡定位；主頁教學擴充至 15 步（納入 `SEARCH_PILL`），設定頁導覽由 6 步擴充至 8 步（納入白名單管理、卡片底色、分用途模型與省 token 設定）。
- **版號更新**：`versionCode` 22、`versionName` `"2.4.0"`。

## [2.3.1] - 2026-11-17

### 變更與修正
- **AI 標籤分發解析容錯與失敗通知改進（#169）**：
  - `AiClient.parseAnalysisJson` 與 `parseBatchAnalysisJson` 強化容錯：依序剝除 Markdown 圍欄、以字串／跳脫安全之平衡括號掃描擷取候選片段（多物件取首個含 `risk_score`/`risk` 者）、嚴格 Gson 解析失敗後才套用寬容預處理（字串外部全形冒號／逗號轉半形、移除 trailing comma），並保留既有正則降級與新增 `PhantomAiParse` logcat 診斷（僅記原因與長度，不記訊息原文）。
  - `buildSystemPrompt` 加強「只輸出一個 JSON 物件、不得使用圍欄與附加說明」規範；支援 `json_object` 之 OpenAI 相容端點（OpenAI／Mistral／Groq／DeepSeek／Gemini）於單則分析帶上 `response_format`；`analyzeMessageOrThrow` 與 `testAutoTagDispatch` 解析失敗時附 corrective 訊息自動重試一次（不重扣每日額度），測試畫面失敗時顯示模型原始回應前 200 字輔助診斷。
  - `NotificationCollectorService` 分發失敗通知改採 24 小時滑動時間窗計數（累積 5 次觸發一次 `IMPORTANCE_LOW`＋`setOnlyAlertOnce` 通知，保留 6 小時冷卻、單次成功不重置計數並尊重勿擾時段）；新增 `ai_tag_failure_notif_enabled` 開關（`PhantomPrefs` 預設開、設定頁 AI 卡可切換、`SettingsCatalog` 註冊搜尋與對話開關）。
- **訊息背景頁黑底修復與選圖可靠度提升（#170）**：
  - `SettingsActivity` 背景圖層選擇狀態 `pickingLayer`（存 `layer.name`）與 `bgStatusMsg` 改用 `rememberSaveable` 保存，防止選圖期間旋轉或行程重建遺失目標圖層；`uri` 非空但圖層遺失時顯示明確提示而非靜默丟棄。
  - `PhantomBackgroundBox` 於 `bgFile == null`、bitmap 載入中或解碼失敗時明確繪製 `MaterialTheme.colorScheme.background`（不再透空露黑），`BackgroundImageHelper.loadBackgroundBitmap` 解碼失敗補齊 `PhantomBackground` logcat 紀錄。
  - Photo Picker 失敗（非使用者取消）或無法開啟時，提供 `ActivityResultContracts.OpenDocument("image/*")` 檔案選擇器備援按鈕並同樣降採樣複製進內部儲存。
- **新手教學擴充與健檢（#171）**：
  - 主頁新手教學 `OnboardingStep` 由 10 步擴充為 14 步（新增「記事與自動備忘錄」、「統計、摘要與離線佇列」、「待辦提醒與批次管理」、「背景圖片、外觀與分用途模型」），同步更新 `PhantomPrefs.KEY_ONBOARDING_STEP_V2` 上限（`1..14`）、`OnboardingTargets`（納入 `NOTES_TAB`、`STATS_TAB`）與目標缺失自動跳過防護（含防重入與版面穩定延遲）；修正設定頁「重新播放新手教學」同步重置步驟索引為 1；已完成教學的舊用戶不被重新打擾。
  - 全面校對 4 Tab（「訊息 / AI 助手 / 記事 / 統計」）教學文案，並更新設定頁 6 步導覽（`AI`、`NOTIF`、`APPEARANCE`）納入 v2.3 新功能。
- **版號更新**：`versionCode` 21、`versionName` `"2.3.1"`。

## [2.3.0] - 2026-11-17

### 新增
- **分用途 AI 模型路由（#163）**：
  - 支援 6 種 AI 用途（`ai_chat`、`ai_message_analysis`、`ai_daily_summary`、`ai_weekly_summary`、`ai_deep_analysis`、`ai_settings_search`）各自綁定獨立 AI Profile 或「跟隨預設」（自動備忘錄跟隨每則訊息風險分析模型）；`ApiKeySettingsActivity` 新增用途模型設定卡（6 列下拉選單與逐列測試連線）。
  - `ChatScreen` 模型切換器改為僅切換 `ai_chat` 用途模型（不再更動全域預設 Provider），並於 `SettingsCatalog` 新增 `ai_model_chat` 等 6 項設定搜尋條目。
- **離線 AI 補分析佇列與自動重試（#164）**：
  - `AppDatabase` 升版至 v13（`MIGRATION_12_13`）新增 `pending_ai_analysis` 資料表；僅針對暫時性失敗（`UnknownHostException`、`SocketTimeoutException`、`ConnectException`、HTTP 5xx、429 耗盡）自動入隊，401/403/404/413 誠實略過。
  - 新增 WorkManager `PendingAiAnalysisWorker`（`NetworkType.CONNECTED`、指數退避、unique work `ai_pending_analysis_worker`），每輪最多補分析 20 則，`attempts >= 3` 放棄並通知；提供低干擾通知（`NotifIds.AI_OFFLINE_QUEUE`）、訊息頁頂部 `AnimatedVisibility` 佇列橫幅（暫停／繼續／立即重試）與設定頁 `ai_offline_retry_enabled` 開關（預設開，關閉即清空佇列）。
- **「記事」分頁與 AI 自動備忘錄（#166）**：
  - 主分頁將「詐騙警示」替換為「記事」（維持 4 Tab），整合待辦事項（共用 `todos` 表）與手動／自動記事 CRUD、釘選與色標（`notes` 表併入 `MIGRATION_12_13`）；詐騙警示中心改由訊息頁風險卡與統計頁 Top 5 卡「查看詐騙警示中心」進入。
  - 設定頁新增 `ai_auto_memo_enabled` 開關（預設關），開啟後 `AiClient` 於風險分析同時產出 40 字內繁中重點摘要，並以 `relatedMessageId` 去重寫入自動備忘錄（`riskCache` 鍵含 `autoMemo` 旗標）。
- **AI 分析字數門檻自訂（#167）**：
  - `PhantomPrefs.getAiMinTriggerLength`（1–30 字，預設 10）取代 `NotificationCollectorService` 硬編碼門檻，設定頁 AI 卡新增滑桿並於 `SettingsCatalog` 註冊 `ai_min_trigger_length`（支援 AI 對話調整）。

### 變更與修正
- **圖片視覺上傳與背景選圖雙重修復（#162）**：
  - `ChatScreen.encodeImageUriToDataUri` 改採 bounds-first `inSampleSize` 降採樣（最長邊 ≤ 1024px，OOM 時加倍 `inSampleSize` 重試一次）、細分錯誤原因誠實回報並記錄 `PhantomImage` log；更新 `AiClient.isVisionModelSupported`（納入 Groq `qwen/qwen3.8-27b` 並將 `deepseek` 納入純文字模型清單）。
  - 修復 `BackgroundImageHelper.saveBackgroundFromUri` 因 `inJustDecodeBounds = true` 回傳 `null` 導致 `use { ... } ?: return failure` 永遠觸發的 bug，恢復全域／AI 聊天／對話頁三層選圖。
- **「清除已讀」入口移至設定頁（#165）**：
  - 移除 `MainActivity` 訊息頁右上角「清除已讀」按鈕與確認對話框，移至設定頁「訊息與通知」卡片，並於 `SettingsCatalog` 新增 `notif_clear_read` 與同步 `data_export_clear` 描述。
- **版號更新**：`versionCode` 20、`versionName` `"2.3.0"`。

## [2.2.0] - 2026-11-17

### 新增
- **關於卡檢查更新與從 GitHub 下載 APK（#157）**：
  - 「關於」卡片作者更新為「作者：幻神（Hwanshen）」，並新增手動觸發「檢查更新」按鈕（`GET https://api.github.com/repos/eric11yothelast-Hwanshen/PhantomCheck/releases/latest`，`User-Agent: PhantomCheck-Android`，逾時 10 秒，與 runtime `versionName` 語意比較；無網路／逾時／API 失敗誠實顯示錯誤並提供「前往 GitHub」替代按鈕）。
  - 偵測到新版本時支援以 `DownloadManager` 下載 `app-release-X.Y.Z.apk` 至 App 專屬 `Download/` 目錄，透過 `FileProvider`（`file_paths.xml` 新增 `updates` `external-files-path`）＋ `ACTION_VIEW`（`application/vnd.android.package-archive` ＋ `FLAG_GRANT_READ_URI_PERMISSION`）呼出系統安裝器；`AndroidManifest.xml` 宣告 `REQUEST_INSTALL_PACKAGES`，安裝前檢查 `canRequestPackageInstalls()` 並引導至 `ACTION_MANAGE_UNKNOWN_APP_SOURCES`；`SettingsCatalog` `about_section` 擴充「檢查更新／更新／update／下載」別名。
- **三層背景圖片自訂與視覺微調滑桿（#159）**：
  - 個人化設定卡新增「背景圖片」三層自訂（全域背景／AI 聊天背景／對話頁背景，未設定時逐層 fallback 至全域背景），透過 `PickVisualMedia` 選圖後以 2 的冪 `inSampleSize` 降採樣至螢幕解析度以下並以 JPEG 85 壓縮儲存至 `filesDir/backgrounds/{global|chat|conversation}.jpg`；`BackgroundImageHelper` 採單張 `LruCache(1)` 並於 `PhantomApplication.onTrimMemory` 釋放。
  - 於 `ui/Components.kt` 新增頁面層包裝 `PhantomBackgroundBox`（`ui/components/` 零改動），在設定背景圖時於底層繪製 `Image(ContentScale.Crop)` ＋暗色可讀性遮罩（scrim，預設日間 35%／夜間 60%，提供 0–100% 滑桿），未設定背景圖時與 v2.1.0 逐像素一致；新增「玻璃卡片強度」滑桿（0–100%，預設 0%，Android 12 以下降級行為不變），並於 `SettingsCatalog` 新增 `bg_global`、`bg_chat`、`bg_conversation`、`bg_scrim`、`glass_strength` 五項設定搜尋條目。
- **AI 助手操作能力擴充（#160）**：
  - 沿用既有 `parseAllChatActions`／`KNOWN_CHAT_ACTIONS`／確認卡佇列／`sanitizeForPrompt` 安全隔離框架新增 9 項動作：唯讀 `search_messages`（`MessageDao.searchFiltered` 預設查詢近 2 天，內嵌最多 5 筆可點擊開啟 `MessageDetailActivity` 的搜尋結果卡）與 `todo_list`（Markdown 表格）；以及一律經確認卡執行的 mutating 動作 `todo_done`、`todo_delete`、`todo_edit`（含模糊比對與 0 筆／多筆候選誠實回報，並重排 `ReminderScheduler` 與刷新小工具）、`vip_pin`（呼叫 `PhantomPrefs.toggleVipSender`）、`mute_app`（靜音清單增刪與候選清單提示）、`mark_read`（`today`/`app`/`all`，嚴格排除 `riskLabel = 'scam'` 並刷新 `StatsForegroundService`）與 `export_messages`（複用 `MessageExporter.exportToDownloads` CSV 匯出與公式注入防護）。

### 變更與修正
- **全域程式碼健檢與效能修正（#158）**：
  - 修復 `ChatScreen` `pendingTodoQueue` 先取 `firstOrNull()` 再過濾 `sessionId` 導致跨會話待辦確認卡被舊會話卡住的 bug，改為先過濾 `currentSessionId` 再取首筆並依物件參考移除已處理項目。
  - `NotificationCollectorService` 新增 `cachedActiveUserTagRules` 30 秒 TTL 快取並於 `invalidateRuleCaches()` 清除，補齊 `ChatScreen`、`TagManagementActivity`、`MessageDetailActivity`、`ScamCenterActivity` 與 `RiskActionWidgetProvider` 寫入／切換／刪除 `ai_rules` 與 `user_tag_rules` 後立即呼叫 `invalidateRuleCaches()`。
  - `ChatScreen` `tag_rule` 目標標籤不存在時改為誠實跳過並記錄 `Log.w`，不再誤 fallback 至 `tags.firstOrNull()`。
  - `TagRuleEngine` 與 `NotificationCollectorService` 熱路徑 `Regex` 預先編譯為常數；並完成 `ReminderWidgetProvider` 純待辦點擊 fallback、`HeartbeatPolicy` `UPDATE` 策略、`PhantomPrefs` 節流快取、7 日 SQL 聚合、`AppIconHelper` 共用快取與 `BootReceiver` 同 ID 覆蓋驗證。
- **版號更新**：`versionCode` 19、`versionName` `"2.2.0"`。

## [2.1.0] - 2026-11-17

### 新增
- **AI 自然語言待辦提醒＋桌面小工具整合（#143）**：`AppDatabase` 升版至 v12（`MIGRATION_11_12`）新增 `todos` 資料表；AI 助手支援 `reminder_add` 與用戶端確定性時間解析 fallback、待辦確認卡與清單 Sheet，透過 `AlarmManager.setAndAllowWhileIdle` 排程（`NotifIds` 互斥區段 `110000..119999`）並於開機由 `BootReceiver` 重排，同步刷新 `ReminderWidgetProvider`。
- **對話頁與對話清單手動多選批量管理（#144）**：`ConversationScreen` 與 `AppThreadsScreen` 新增多選模式（上限 100 則），支援批次已讀／星標／刪除（選取模式中停用 `SwipeToRevealRow` 與單則詳情誤觸）；批次刪除嚴格排除 `riskLabel = 'scam'` 並同步清理 `message_tags` cross-ref。
- **AI 助手預設新對話＋一鍵清除全部對話（#145）**：進入 AI 助手分頁預設為新對話（不自動載入上次會話），歷史對話抽屜新增「清除全部對話」按鈕與二次確認（`ChatDao.clearAllSessions()`，AI 回覆中拒絕清除）。
- **設定頁「關於」區塊（#141）**：於「全部設定」最底新增「關於」卡片，透過 runtime `PackageManager.getPackageInfo` 自動同步顯示 `versionName (versionCode)`、作者、GitHub 倉庫連結、第三方元件與隱私說明，並於 `SettingsCatalog` 註冊搜尋項目 `about_section`。
- **常用指令「跟隨預設」模式與動態解析（#146）**：`QuickChatCommand` 新增 `MODE_FOLLOW`（內建三則預設跟隨）、`resolveQuickCommandMode` 動態解析與設定頁三態切換／即時刷新。

### 變更與修正
- **LINE 群組對話分割修復（#140）**：`AppDatabase` 升版至 v11（`MIGRATION_10_11`）新增 `conversationKey` 欄位，解析 `MessagingStyle`（`EXTRA_CONVERSATION_TITLE`、sender `Person`）區分群組與私聊對話，無 `MessagingStyle` 通知維持原 fallback 行為並同步遷移置頂 `vipSenders`。
- **AI 助手分頁獨立 SnackbarHost（#142）**：`MainActivity` 為 AI 助手分頁配置分頁專屬、內嵌於版面的 `SnackbarHost`（顯示於分頁頂部、脫離 Scaffold 底部 overlay），徹底避免刪除會話等提示遮擋底部輸入框。
- **Release Notes 自動抽取 CHANGELOG 段落（#112）**：`.github/workflows/release.yml` 於發佈步驟前自動抽取對應版本之 `CHANGELOG.md` 段落並與 GitHub 自動 PR 清單共存（本版 tag 首次生效）。
- **版號更新**：`versionCode` 18、`versionName` `"2.1.0"`。

## [2.0.0] - 2026-11-17

### 新增
- **AI 標籤記憶規則與優先序（#131）**：
  - `AppDatabase` 升版至 v10（非破壞性遷移 `MIGRATION_9_10`），新增 `user_tag_rules` 表與 `MessageTagCrossRef.SOURCE_USER_RULE = "user_rule"`；建立確定性零 token `TagRuleEngine`（支援 App package／label、寄件者、關鍵字、正則與語意類別 `otp`/`system`/`bill`/`marketing`/`delivery`）。
  - 標籤套用嚴格遵守 `manual > user_rule > ai_rules > auto > App 分類`，`processBatchAnalysis` 不覆寫手動與使用者規則標籤；支援透過 AI 對話 `tag_rule` 確認卡建立規則與查詢命中鏈，並於 `TagManagementActivity` 提供規則管理與測試。
- **AI 標籤失敗降級通知與分發測試（#132）**：
  - 自動標籤因網路或 HTTP 失敗時自動降回 `app_category_rules`，不中斷訊息入庫與風險流程；新增 `NotifIds.AI_TAG_FAILURE`，首次失敗靜默、連續失敗第 2 次起低優先通知（6 小時冷卻防重複）。
  - 設定頁 AI 卡新增「AI 標籤分發測試」按鈕（計入每日額度並顯示剩餘額度）與 `SettingsCatalog` 搜尋項目 `ai_tag_dispatch_test`。
- **AI 助手雙功能輸入選單：常用指令＋圖片視覺檢視（#133）**：
  - 移除頂部建議問題橫列，於輸入列左側新增單一 48dp `+` 收合選單（「常用指令」支援直接執行／預輸入雙模式，可於設定頁管理或由對話 `quick_command_add` 新增；「上傳圖片讓 AI 檢視」支援 `PickVisualMedia` 縮圖至長邊 1024px 並以 OpenAI 相容 `image_url` 格式送出，對不支援視覺的模型誠實阻擋與提示 token 消耗）。
- **AI 助手版面精簡（#134）**：
  - 切換至 AI 助手分頁時以 `AnimatedVisibility` 平滑隱藏頂部「幻訊 PhantomCheck」標題列，將搜尋／重新整理／設定入口整合至模型切換列旁；縮小對話輸入框（`maxLines = 3`）與精簡送出鈕尺寸。
- **底部分頁欄顯示模式自訂（#135）**：
  - 外觀設定卡與 `SettingsCatalog` 新增三選一模式：「標準」（預設）、「縮小」（80% 高度與圖示）與「自動隱藏」（向下捲動隱藏、向上捲動浮現，保留 `LiquidGlassTabBar` 既有跟手拖曳手勢）。
- **三款非統計桌面小工具（#136）**：
  - 新增 `AiSummaryWidgetProvider`（2x2 最新 AI 摘要）、`RiskActionWidgetProvider`（2x2 高風險一鍵標記詐騙／加入白名單，`goAsync` 執行）與 `ReminderWidgetProvider`（2x1 待辦提醒），均採標準 `RemoteViews`、`updatePeriodMillis="0"`、差異更新與 base + `xml-v31` `previewLayout` 配置。

### 變更與修正
- **AI 數值設定對話執行鏈與生命週期修復（#127）**：補齊 `ai_chat_rag_limit`、`ai_daily_limit`、`notif_throttle` 口語別名與 `resolveNumericIntentFallback` 用戶端確定性 fallback；修復 `appliedSettingCards` 合併追加生命週期。
- **裸 JSON 動作解析與白名單模糊定位（#128）**：強化 `whitelist` `messageRef` 分詞模糊搜尋 fallback。
- **Groq 免費方案 TPM 預算控制與 413/429 自動降級（#129）**：新增 token 估算、Provider 預算上限（Groq 6500 / 其他 32000）、Groq 預設 `maxTokens=1800`、多階段 413 降級（RAG → history → maxTokens）、429 `Retry-After` 退避重試，以及分析失敗退還每日額度。
- **System Prompt 節流與對話歷史壓縮（#130）**：設定清單僅於設定相關查詢條件載入；RAG 依同寄件者相似前綴去重 `(×N)` 與動態字數上限；超過 10 輪歷史壓縮為 `[較早對話摘要]` + 最近 8 則。
- **效能與 M3 版面一致化（#137, #138）**：`NotificationCollectorService` `aiRuleDao().active()` 導入 30 秒 TTL 快取、`PhantomPrefs` 同 key 節流／靜音模式查表導入 30 秒執行緒安全 TTL 快取、`AppIconHelper` 統一為 4 MB 位元組上限 `LruCache`、`StatsForegroundService` 停用時釋放靜態圖示條參照並補齊 Compose `LazyColumn` `contentType`／預計算日期分隔；全專案對齊 M3 12dp 卡片間距與 48dp 最小觸控目標。
- **版號更新**：`versionCode` 17、`versionName` `"2.0.0"`。

## [1.13.1] - 2026-11-17

### 變更與修正
- **HTTP 413 Payload 過大防護與自動重試（#122）**：
  - `AiClient` 各 `HttpException` 分支改為依狀態碼誠實回報（`413` 專屬提示並附上 Provider 原始錯誤訊息，`401/403`、`404`、`422`、`429`、`5xx` 各別明確說明，不再於非認證錯誤誤導「請確認 API Key 或模型設定」）。
  - `chatAssistant` 遇到 `HTTP 413` 時自動將單次閱讀上限減半（最低 `25` 則）重試一次並誠實標注已縮減閱讀範圍；`MessageDetailActivity` 深度分析若遇 `413` 失敗則透過 `PhantomPrefs.refundTodayAiUsage()` 退還當日額度。
- **AI 對話多動作解析與誠實錯誤回報（#123）**：
  - `ChatScreen` 五類動作解析器改為支援多動作與大括號平衡拆解（`splitBalancedJsonObjects`），允許單次回覆同時包含多個獨立動作指令或多個物件；多筆 `whitelist` 提案合併為單張可勾選確認卡逐列顯示並回報每筆結果。
  - 無效動作（`messageId` 為空、`messageRef` 查無、JSON 損壞或不支援動作）一律插入誠實錯誤訊息，不再靜默丟棄；`cleanReply` 為空且無有效提案時不再 fallback 顯示原始 raw JSON 回覆。
- **對話數值設定滑桿確認卡（#124）**：
  - `SettingsCatalog` 新增 `NumericSettingSpec` 與 `NUMERIC_SPECS`（`ai_chat_rag_limit` 50–800 步進 10、`ai_daily_limit` 0–100 步進 10、`notif_throttle` 0–300 步進 10），並將設定頁 `ai_chat_rag_limit` 滑桿對齊步進 10。
  - `ChatScreen` 新增 `setting_adjust` 動作與滑桿確認卡（支援 Slider 與 `+/-` 微調、超範圍自動夾緊提醒、套用後顯示前→後狀態卡），system prompt 同步附上【可調整數值設定清單】。
- **設定導覽正確性與全量目錄防編造（#125）**：
  - 新增 `{"action":"open_setting","settingId":"..."}` 直接開啟 `SettingsActivity` 並定位至目標設定卡；system prompt 附上【全量設定清單摘要】並禁止編造不存在的選單路徑。
  - 未知 `settingId` fallback 改為以 `SettingsCatalog.keywordSearch` 對全目錄關鍵字比對前 5 筆，無匹配則誠實回報；`setting_candidates` 卡對非開關項目顯示「前往」按鈕而非「開／關」；統一 `SettingsCatalog`「AI 風險分析」措辭。
- **版號更新**：`versionCode` 16、`versionName` `"1.13.1"`。

## [1.13.0] - 2026-11-17

### 新增與強化
- **PhantomMarkdownText 語法補全（#114）**：
  - 新增支援 GFM 表格（含對齊行 `:-` / `:-:` / `-:` 解析、欄位依內容比例寬度與寬表橫向捲動）、斜體（`*text*` / `_text_`）、刪除線（`~~text~~`）、可點擊超連結（`[text](url)` 含 `LinkAnnotation.Url` 安全開啟）、引用區塊（`> quote`）、水平分隔線（`---`）、任務清單（`- [ ]` / `- [x]`）與一層巢狀清單縮排。
  - `stripMarkdown()` 同步剝除上述新語法（表格轉為 `cell / cell`），`buildRagSystemPrompt` 同步鼓勵表格與豐富格式排版。
- **AI 預設模型清單更新（#115）**：
  - 更新 `AiClient.DEFAULT_PROVIDER_PRESETS`（既有使用者已儲存之 profile 不受影響，僅影響新建設定）：
    - **Mistral**：`mistral-small-2603`（預設）、`mistral-large-4`（原 `mistral-small-latest` / `mistral-large-latest`）
    - **OpenAI**：`gpt-6.1-terra`（預設）、`gpt-6.1-sol`（原 `gpt-5.2`）
    - **Groq**：`openai/gpt-oss-120b`（預設）、`openai/gpt-oss-20b`、`qwen/qwen3.8-27b`（原 `qwen/qwen3.6-27b` 等）
    - **Gemini**：`gemini-4-hydrogen`（預設）、`gemini-4-carbon`、`gemini-4-argon`、`gemini-3.8-flash`（原 `gemini-3.8-flash` / `gemini-3.5-flash-lite`）
    - **DeepSeek / OpenRouter**：維持不變。
- **AI 助手單次閱讀訊息數設定（#116）**：
  - `PhantomPrefs` 新增 `chatRagMessageLimit`（預設 `400`，範圍 `50–800`），省 token 模式啟用時透過 `getEffectiveChatRagMessageLimit()` 自動降為 `100`（不覆寫使用者原設定值）。
  - 設定頁 AI 卡新增滑桿（步進 50）與 token 消耗提醒，並加入設定搜尋 `ai_chat_rag_limit`。
- **結構化白名單機制（#117）**：
  - `AppDatabase` 升版至 v9（非破壞性遷移 `MIGRATION_8_9` 註冊於 `AppDatabaseHolder`），`AiRuleEntity` 新增 nullable `sender` 與 `packageName`。
  - `ScamCenterActivity` 與 `MessageDetailActivity` 新增「加入白名單」雙選項流程（此寄件者全部訊息／僅此則訊息）；`TagManagementActivity` 顯示白名單寄件者／App 中繼資料。
  - AI 助手支援 `{"action":"whitelist",...}`，先渲染訊息確認卡、使用者確認後才寫入資料庫；命中白名單後自動標記安全（`riskScore = 10`、`riskLabel = "safe"`）並跳過後續 AI 分析、詐騙警示與防詐情報預查。
- **AI 對話直接調整設定（#118）**：
  - 新增共用單一來源 `SettingsCatalog`（`SettingsActivity` 搜尋與 `ChatScreen` 對話調整共用）。
  - AI 助手支援 `setting_toggle`（僅限 boolean 開關白名單，套用與設定頁完全相同之 setter 與前景服務/排程副作用，並顯示「變更前 → 變更後」與「前往設定頁」卡片）與 `setting_candidates`（模糊描述列候選開關卡；非 boolean 設定引導至設定頁）。
- **AI 訊息查找強化與對話執行操作（#119）**：
  - RAG 時間範圍預設改為「今天＋昨天」，新增「前天」與依寄件者名稱過濾（`MessageDao.recentSenders()`），節錄附帶 `[id=...]`。
  - 新增 `reply_message`（透過 `NotificationCollectorService` `activeNotifications` 匹配並以 `RemoteInput` 快速回覆，找不到時誠實回報）、`open_app`、`delete_message`（強制保護 `scam` 詐騙紀錄不予刪除）與 `tag_message`（`SOURCE_MANUAL`），全部先渲染確認卡、確認後執行並於對話回報結果。
- **設定搜尋全面稽核（#120）**：
  - 逐卡核對 `SettingsActivity` 實際 UI 與 `SettingsCatalog.ITEMS` / `TOGGLEABLE_IDS`，修正 `ai_daily_summary` 歸屬至 `quick` 卡、補齊 `ai_daily_summary_notif`（每日摘要排程時間與完成推播）與 `appearance_glass_effect`（液態玻璃模糊效果）搜尋與 boolean 切換支援，並驗證本地關鍵字與 AI 語意搜尋雙路徑。
- **版號更新**：`versionCode` 15、`versionName` `"1.13.0"`。

## [1.12.1] - 2026-11-16

### 變更與修正
- **修正設定搜尋 catalog 五項對錯卡（#109，PR #110）**：
  - 將 `notif_auto_reply` 改為 `ai_auto_reply`（`cardKey = "ai"`、title「SMS 自動回覆設定」，補齊 `sms`／`簡訊`／`回覆`／`時段`／`reply` 搜尋別名），修正「SMS 自動回覆」定位至通知卡的回報主因。
  - 將 `ai_auto_tag` 改為 `cardKey = "quick"`，對應快速設定卡「每則訊息 AI 智慧掃描」開關。
  - 將 `ai_rules` 改為 `cardKey = "data"`、title「自訂標籤管理」。
  - 將 `notif_scam_alert` 拆分為 `quick_scam_popup`（`cardKey = "quick"`，「詐騙警示彈出通知」）與 `ai_alert_keywords`（`cardKey = "ai"`，「即時提醒關鍵字」）。
  - 將 `appearance_widget_tour` 改為 `cardKey = "data"`、title「教學重播與桌面小工具」。
- **新增兩項設定搜尋定位（#109，PR #110）**：
  - 新增 `ai_scam_center`（`cardKey = "ai"`，「詐騙警示中心」）與 `notif_filter_rules`（`cardKey = "notif"`，「過濾關鍵字規則與命中動作」）。
- **AI 定位支援「前往」直達頁面（#109，PR #110）**：
  - `SettingSearchItem` 新增 nullable `page` 欄位，設定 `AutoReplySettingsActivity`（自動回覆設定）、`ScamCenterActivity`（詐騙警示中心）、`TagManagementActivity`（自訂標籤管理）與 `ApiKeySettingsActivity`（API Key 設定，含 `ai_providers` 補齊）。
  - `aiSearchNote` 定位橫幅在第一個命中項目具備 `page` 時顯示「前往」`TextButton`，點擊直接開啟對應設定頁。
- **資料管理卡補齊桌面小工具加入說明（#109，PR #110）**：
  - 於資料管理卡「重新播放設定教學」按鈕下方補上「桌面小工具：桌面長按 → 小工具 → 幻訊統計」提示小字。
- **版號更新**：`versionCode` 14、`versionName` `"1.12.1"`。

## [1.12.0] - 2026-11-16

### 變更與修正
- **設定搜尋「同 key 節流」定位修正（#99）**：
  - 將 `SettingsActivity` `SETTINGS_SEARCH_CATALOG` 中的 `data_throttle_retention` 拆分為 `notif_throttle`（`cardKey = "notif"`，補齊「同 key」「重複通知」「漏訊」「流量」「數字微變」「覆寫」等搜尋別名）與 `data_retention`（`cardKey = "data"`，「訊息保留與清理」）。
  - 同步更新設定頁「個別 App 節流覆寫」副標說明，清楚標示 `0 秒 = 永不節流` 與通訊類通知 `postTime` 更新時收錄同內容訊息的行為。
- **保留清理維護詐騙證據與標籤完整性（#100，免 Room migration）**：
  - `MessageDao.prune` 與 `deleteOlderThanCount` 改以 `AND riskLabel != 'scam'` 直接排除詐騙證據，移除 `RetentionWorker` 重插邏輯以保留原 `id`、`message_tags` 與 `intel_results` 關聯。
  - 新增 `TagDao.deleteOrphanCrossRefs()` 並於 `RetentionWorker` transaction 結尾清理孤兒關聯；`TagDao.allTagCounts()` 補齊 `LEFT JOIN messages` 防護對齊 `todayAiTagCounts()`。
- **通訊類同內容連續訊息不再吞訊（#101，維持被動 listener）**：
  - `NotificationCollectorService` 偵測通訊類通知（`CATEGORY_MESSAGE` / `EXTRA_MESSAGES` / `MessagingStyle`），僅於同 key + 同內容 + `postTime` 未變時視為系統 re-post 丟棄；`postTime` 有更新的同內容訊息即時收錄。非通訊類通知（如系統／流量監控）維持 `exactSame || digitMicroChange` 節流。
  - `dedupCache` 改為僅於 `isSuspectedReplay`（斷線重播補投）時阻擋，一般路徑交由 throttle 層判斷；個別 App 覆寫設為 0 秒時兩層皆放行。
- **通知 ID 集中管理與防撞號（#102）**：
  - 新增 `NotifIds` object 集中管理互斥區段：`1001..1004`（常駐統計／開機提醒／每日摘要／週報）、`scamAlert` `2000..21999`、`importantAlert` `22000..41999`、`reminder` `100000..109999`，消除 `ReminderReceiver` 與 `alertScam` 區間重疊與裸數字常數。
- **AI Prompt Injection 防護（#103）**：
  - 新增 `AiClient.sanitizeForPrompt()`，將 `</message_*`、`</user_*`、`</system`、`</msg` 等隔離標籤的 `<` 轉為全形 `＜`（不影響 `cacheKey` 原始內容計算），並套用於 `analyzeMessage`、`analyzeMessageBatch`、`deepAnalyzeMessage` 與 `ChatScreen.buildRagSystemPrompt`（`chatAssistant` RAG）四個注入點。
- **SMS 自動回覆防回圈冷卻（#104）**：
  - `SmsAutoReplyReceiver` 新增 `canReplyAndRecord()`（per-sender 4 小時冷卻與單日上限 3 次，記錄於 `auto_reply_cooldown_prefs`）及回覆範本自回比對，避免雙向自動回覆無限互發。
- **技術債清理（#105）**：
  - **過期提醒開機即觸發**：新增 `MessageDao.allScheduledReminders()` 與 `ReminderScheduler.deliverReminder()`，`BootReceiver` 對開機期間已過期提醒立即觸發並清除 `remindAt`；`MessageDetailActivity` 改以 `remindAt > System.currentTimeMillis()` 顯示狀態。
  - **排程自癒策略**：`HeartbeatPolicy` 與 `WeeklySummaryPolicy` 的 `ExistingPeriodicWorkPolicy.KEEP` 改為 `UPDATE`。
  - **節流查詢快取**：`PhantomPrefs.getAppSameKeyThrottleMap` 加入 30 秒 TTL 快取（於 `setAppSameKeyThrottle` 失效），減少通知熱路徑重複 Gson 反序列化。
  - **統計 SQL 聚合**：新增 `MessageDao.countByDaySince` 與 `countByHourSince`，取代 `StatsDetailWidgetProvider.loadSevenDayCounts` 與 `MainActivity` 統計頁的 `dao.since()` 全欄位載入。
  - **Widget 圖示共用快取**：新增 `AppIconHelper.loadAppIconAndroidBitmap` 共用 `appIconBitmapCache`（`LruCache`），取代 `StatsDetailWidgetProvider` 重複 decode。
  - **分層解耦**：移除 `PhantomPrefs` 對 `service.NotificationCollectorService` 的反向依賴，改為 `onRuleCachesInvalidated` callback 註冊。

## [1.11.0] - 2026-11-16

### 新增
- 同 Key 節流進階設定（#96）：於設定頁新增「數字微變視為同一則更新」開關（預設開啟）與「依 App 自訂節流秒數」（per-app 節流覆寫與選擇對話框），在全域滑桿外支援個別 App 獨立調整節流行為。
- 連線狀態追蹤與規則快取失效機制（#94）：`NotificationCollectorService` 新增 `isConnected` 狀態追蹤與 `invalidateRuleCaches()`，於設定頁或標籤管理頁修改規則與關鍵字時即時刷新快取。

### 變更與修正
- 連續訊息漏收修復（#96）：
  - `NotificationCollectorService` 改用有界 `sameKeyHistory`（`LruCache(256)`）實作內容感知節流與疊加訊息尾巴提取，在不啟用任何輪詢的前提下，確保同一通知 key 的連續新訊息不被誤攔。
  - 新增 `MessageDao.existsByDedupKeyInWindow` 進行 DB 視窗查證式防重複收錄（`isSuspectedReplay`），避免持續刷新型 App 反覆收錄或重連補收漏訊。
  - 實作 `resolveNotificationTitle` 多層 fallback（`EXTRA_TITLE` → `EXTRA_TITLE_BIG` → `「寄件者：」` 前綴 → 快取 App 名稱），修正空標題通知處理異常。
- AI 訊息助手回應截斷修復（#95）：
  - `AiClient.chatAssistant()` 的 `maxTokens` 調升至 `4096`，並改用專用 `chatOkHttpClient`（`connect 15s / write 30s / read 120s / callTimeout 180s`）；其餘 AI 路徑的 `maxTokens` 與逾時維持不變。
  - `ChatChoice` 解析 `finish_reason`，當回應因長度上限截斷（`finish_reason == "length"`）時於結尾誠實標注「⚠ 本回應因長度上限被截斷」。
- 效能、電量與記憶體最佳化（#94）：
  - `ShizukuWatchdogService` 改採自適應退避（平穩期 300 秒、拉起服務後 10 分鐘內 60 秒），減少閒置喚醒而不影響保活。
  - `WatchdogReceiver` 與 `HeartbeatWorker` 條件化 `requestRebind`（僅於 `!isConnected` 時觸發），並移除 `HeartbeatPolicy.policyScope` 改用 `ExistingPeriodicWorkPolicy.KEEP`。
  - `NotificationCollectorService` 熱路徑導入 `appLabelCache`（`LruCache(64)`）與 30 秒 TTL 過濾規則／App 分類規則／警示關鍵字快取，降低重複資料庫與系統查詢開銷。

## [1.10.0] - 2026-11-16

### 新增
- 訊息標籤全面顯示（#83）：抽離共用 `MessageTagChipRow`（含 `· AI` 標記），統一訊息清單、對話氣泡、App 對話串與詳情頁顯示；標籤採單次 `IN` 批次查詢載入，避免 N+1 拖慢捲動。
- 設定頁搜尋框＋AI 語意搜尋（#88）：支援本地即時關鍵字／別名過濾，以及自然語言 AI 語意導覽（`<user_query>` 隔離與白名單 id 驗證，自動展開捲動並高亮目標卡片，僅導覽不改值；未設定 AI provider 時誠實降級）。
- 個人化設定（#90）：新增卡片密度（舒適／緊湊）、首頁排序（最新／未讀優先／風險優先）、首頁篩選欄自訂顯示、摘要預設收合開關、12/24 小時制切換（全部納入設定頁搜尋）。
- 多尺寸桌面小工具（#87）：新增 2x1 迷你（今日／警示）與 4x2 詳情（今日／未讀／警示＋原生 `RemoteViews` `LinearLayout`/`ImageView` 7 日趨勢比例條＋最近來源 App 圖示），連同既有 4x1 皆支援 `resizeMode`、`xml-v31` `targetCell` 與深淺主題，統一走差異刷新。
- OTP 驗證碼一鍵複製（#91）：強訊號關鍵字＋4–8 位數字偵測，於訊息卡、對話氣泡與詳情頁顯示「複製驗證碼」一鍵複製。
- 統計報告分享圖卡（#91）：純本機 Android `Canvas` 產生 `1080×1350` 靜態週報圖片並經 `FileProvider` 發起 `ACTION_SEND` 分享（不經 LLM、不上傳訊息）。
- 訊息待辦提醒（#91）：支援底頁與詳情頁選擇提醒時間（30 分鐘後／1 小時後／今晚 20:00／明早 09:00），以 `AlarmManager.setAndAllowWhileIdle` 排程通知直開詳情，並於 `BootReceiver` 重開機自動重建。

### 變更與修正
- 每日／本週 AI 摘要卡預設收合（#84）：統計頁摘要卡預設收合為單行預覽並持久化展開狀態，保留生成、複製與分享入口。
- 首頁篩選欄重排（#85）：改用 `FlowRow` 自動換行與緊湊晶片設計，全部篩選與自訂標籤免橫向滑動一次可見。
- 頂部標題列縮小（#86）：各頁面 `TopAppBar` 高度收斂為 52dp、標題改為 `titleMedium`，維持 48dp 按鈕觸控目標。
- 全域排版優化（#89）：依 Material 3 規範統一各頁 16dp 水平邊距與 12dp 卡片間距，詐騙中心改用共用 `EmptyStateView`。
- 資料庫升級：Room 7→8 非破壞性 `MIGRATION_7_8`（新增 `messages.remindAt` 欄位），並補齊 `todayRecentApps` 的 `riskScore` 欄位映射。

## [1.9.0] - 2026-11-16

### 新增
- 桌面小工具（4x1）：今日訊息數／未讀／詐騙警示，點擊開啟幻訊，跟隨系統深淺色，隨常駐通知差異刷新更新（不增高頻鬧鐘）。
- 每週 AI 摘要報告：每週日 22:00 自動生成當週統計與風險趨勢，統計頁可手動生成、查看歷史週報、複製與分享（不受省 token 模式限制）。
- 訊息一鍵 AI 深度分析：訊息詳情頁產生段落式風險解說（詐騙類型、可疑點、建議處理動作），可取消，計入每日 AI 額度。
- 防詐情報金鑰管理：Safe Browsing／PhishTank／Chainabuse 的 key 可直接在設定頁輸入（加密儲存，`local.properties` 為 fallback）。

### 變更與修正
- AI 助手對話逾時修復：Retrofit 改用自訂 OkHttpClient（read 90s／callTimeout 120s），對話逾時自動重試一次並誠實標注。
- 防詐情報四來源連線修復：fraud.tw 改用官方端點 `/api/check?d=`（原先 404）；PhishTank 描述性 UA＋可選 `app_key`（原先 403）；Chainabuse Basic 認證＋`chain` 參數（原先 401）。
- 常駐統計通知版面：移除左側重複彩色圖示，保留單色小圖示，標題不再被卡；右側圖示條排序不變。
- 省電優化：常駐通知差異刷新（數據未變跳過重建）、統計查詢合併為單一 SQL、熄屏時跳過閒置刷新（新通知仍即時更新）、圖示快取。

## [1.8.0] - 2026-11-16

### 新增
- **公開防詐情報庫（`IntelClient`）零 token 預查（#65）**：
  - 抽取層 `IndicatorExtractor` 支援完整 URL（含短網址）與加密貨幣地址（BTC / ETH / TRON）正則抽取，並維持省 token 模式 `hasSuspiciousFeature` 既有行為。
  - 可插拔情報提供者依序查詢：`fraud.tw`（165 通報）→ Google Safe Browsing Lookup API v4（`local.properties` 注入 `BuildConfig.SAFE_BROWSING_API_KEY`，倉庫零機密）→ 可選 PhishTank / Chainabuse（預設關閉）。
  - 本地 72 小時情報快取（`intel_cache`、`sha256(indicators + providerSig)`）與每則訊息查證結果（`intel_results`，Room Migration 6→7 非破壞性升級）。
  - 管線整合：`NotificationCollectorService` 於 `shouldRunAi` 前預查；`ai_rules` 白名單優先跳過；命中直接寫入高風險 `scam`、跳過 LLM 與每日額度並發送警示；未命中或離線靜默降級回原 AI 流程（不造假命中、僅送指標不送全文）。
  - 設定頁新增「防詐情報」卡片（逐源開關、Safe Browsing Key 注入狀態、逐源真實連線測試、清除 72h 快取）；訊息詳情與詐騙中心顯示命中來源與查詢時間。
- **AI 對話／每日摘要即時切換模型與自然語言新增規則（#71）**：
  - 對話頁與統計頁「每日 AI 摘要」卡新增 `AiProviderSwitcherChip`，可即時切換已設定的 AI Provider／模型（未設定時誠實顯示尚未設定）。
  - 對話中明確要求新增白名單或標籤規則時，AI 輸出結構化 `add_rule` 區塊，前端隱藏原始 JSON 並彈出強制確認對話框，經使用者確認後才寫入 `ai_rules`；`buildRagSystemPrompt` 加上 `<message_content>` 分隔與防 Prompt Injection 指示。
- **發佈流程文件化（#70）**：建立 `CHANGELOG.md`、同步 `README.md`，並於 `AGENTS.md` 新增發佈檢查清單。

### 變更與修正
- **震動回饋與液態玻璃門檻修正（#68）**：
  - `rememberPhantomHaptic` 於 API 30+ 使用 `areAllEffectsSupported` 探測預定義效果，不支援或 API 29 時 fallback 至明確振幅 `createWaveform`（點擊 25ms/220、長按 40ms/255），解決部分機型靜音問題。
  - `Glass.kt` 背景模糊門檻從 API 33 修正為 API 31（Android 12+），更低版本維持半透明壓色降級；設定頁補齊廠牌觸感設定路徑提示與液態玻璃版本說明。
- **常駐統計通知版面與點擊行為修正（#69）**：
  - 改用 `RemoteViews`（`notif_stats_collapsed.xml`）固定左側幻訊圖示、右側「左新右舊」水平 App 圖示條並以邊框與角點標示最新 App；保留滑掉重生與 `FLAG_NO_CLEAR` 保活機制。
  - 點擊行為設為「開啟原 App」時改為開啟最新一則訊息所屬 App（無法解析時 fallback 開啟幻訊）。
- **效能優化、省電降級與教學主題適配（#67）**：
  - `MainActivity` 保留分頁狀態與 30 秒 TTL 資料快取；`ConversationScreen` 改用 keyset 分頁（`LIMIT 200` 滾動載入）；`AppIconHelper` 新增跨頁 `LruCache`；`LazyColumn` 補齊 `key` 與 `derivedStateOf`。
  - `PowerManager.isPowerSaveMode` 啟用時自動停用玻璃模糊；移除 `StatsForegroundService` 的 `CONNECTIVITY_CHANGE` 動態廣播註冊以降低耗電。
  - `Onboarding.kt` 與 `SettingsTourOverlay` 改用 `MaterialTheme.colorScheme` 並強化卡片底色與遮罩對比，維持洞內觸控穿透。

## [1.7.0] - 2026-11-15

### 新增與修正
- **Shizuku 偵測修復與開關健檢（#64）**：修正 Shizuku binder/授權狀態偵測、新增設定頁 6 步高亮導覽教學、每日摘要複製與分享、氣泡長按複製、AI 回覆 Markdown 渲染（`PhantomMarkdownText`）與助手上下文放寬，以及震動回饋基礎修正。

## [1.6.0] - 2026-11-15

### 新增與修正
- **保活強化與 Shizuku 整合（#60）**：整合 Shizuku 進階保活與看門狗（`ShizukuKeepAlive` / `ShizukuWatchdogService`）、凍結高風險 App 能力、修正通知圖示渲染，並於介面顯示 AI 標籤。

## [1.5.0] - 2026-11-14

### 新增與修正
- **AI 助手、10 步教學與規則系統（#55）**：新增「AI 助手」RAG 唯讀分頁、10 步穿透式新手引導、常駐通知 400ms 節流原地重建（`repostDirectly`）、零 token App 分類規則與 `ai_rules` 白名單、省 token 模式與多項外觀/通知微調開關，Room Migration 5→6。

## [1.4.0] - 2026-11-13

### 新增與修正
- **互動強化、AI 智慧標記與玻璃手感（#52）**：移植 `LiquidGlassTabBar`、`GlassCard`、`FluidSwitch`、`SwipeToRevealRow`；新增勿擾時段、摘要完成推播、可疑連結警告、詐騙週趨勢與搜尋高亮、批次多選與 VIP 置頂，Room Migration 4→5。

## [1.3.0] - 2026-11-12

### 新增與修正
- **全面強化與前景統計服務（#50）**：新增 `StatsForegroundService` 常駐統計通知、訊息詳情頁、標籤系統、CSV 匯出與設定頁重構，Room Migration 3→4。

## [1.2.0] - 2026-11-11

### 新增與修正
- **訊息中心改版（#48）**：依 App 與寄件者分組對話、聊天式對話頁與群組摘要通知過濾修正。

## [1.1.0] - 2026-11-10

### 新增與修正
- 正式簽署版本與基礎 CI/CD 建置發佈管線。

## [1.0.0] - 2026-11-09

### 新增
- 幻訊 PhantomCheck 初始版本：`NotificationListenerService` 通知攔截預覽、Mistral AI 詐騙風險分析、關鍵字過濾與 Room 本地資料庫。
