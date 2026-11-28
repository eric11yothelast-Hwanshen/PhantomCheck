[English](README.md) | **繁體中文**

# 幻訊 PhantomCheck

Android 智慧防詐訊息管理 App：在不觸發原 App 已讀的前提下攔截通知預覽，結合關鍵字過濾、AI 詐騙風險分析、詐騙警示中心與 SMS 自動回覆，守護日常通訊安全。

---

## 隱私與免責聲明

本 App 採完全本機儲存架構，無自家伺服器、無帳號系統、無遙測追蹤；AI 分析與情報查詢僅於您啟用並設定後依需求連線第三方服務。
使用前請詳閱 [隱私權政策（PRIVACY.md）](PRIVACY.md) 與 [免責聲明（DISCLAIMER.md）](DISCLAIMER.md)。

---

## 系統需求

- **作業系統**：Android 8.0（API 26）以上
- **液態玻璃模糊效果**：Android 12（API 31）以上支援背景即時模糊；更低版本或省電模式自動降級為半透明壓色
- **必要權限**：通知存取權（Notification Listener）；Android 13+ 建議開啟通知權限以顯示詐騙警示與常駐統計

---

## 核心功能

- 📩 **通知攔截預覽**：透過 `NotificationListenerService` 即時讀取通知內容，無需開啟 LINE、IG 或簡訊等原 App，不觸發「已讀」。
- 🕵️ **公開防詐情報預查（零 Token）**：AI 分析前先抽取 URL、短連結與加密貨幣地址（BTC/ETH/TRON），依序查詢本地 72 小時快取、`fraud.tw`（165 通報）、Google Safe Browsing v4 及可選 PhishTank / Chainabuse（不傳送訊息全文，命中直接警示且不消耗每日 AI 額度）。
- 🛡️ **多 Provider AI 詐騙分析、分用途路由與離線補分析**：支援 Mistral、OpenAI、Groq、OpenRouter、Gemini（OpenAI 相容）、DeepSeek 等多組 AI Profile，可為各用途獨立指定模型（自動備忘錄跟隨訊息分析模型），並於 AI 助手與深度分析處即時切換並同步對應用途模型；暫時性網路/伺服器失敗自動進入離線補分析佇列（WorkManager 連網重試）。
- 🚫 **智慧通知去重與過濾**：自動過濾持續性通知、群組摘要與斷線重播重複通知，支援同 Key 重複節流與個別 App 覆寫（`0 秒 = 永不節流`；通訊類通知若 `postTime` 更新則收錄同內容新訊息）、關鍵字過濾（含指定 App）與 App 靜音清單。
- 📊 **常駐統計通知、5 Tab 導覽（大螢幕 NavigationRail）與搜尋優先設定中心**：自訂 `RemoteViews` 常駐通知左側固定顯示幻訊圖示、右側「左新右舊」呈現最近通知 App 並標記最新一則，點擊遵循原 App／幻訊設定（滑掉無縫原地重建 + 15 分鐘心跳 + Shizuku 跨行程看門狗）；App 內提供「訊息、AI 助手、記事、記憶、洞察」5 Tab 液態玻璃介面（其中「洞察」整合「夥伴｜統計」頂部二級分頁，大螢幕 `>= 600dp` 自動切換為左側 `NavigationRail` 導覽軌）與搜尋優先之 8 大分類設定中心（支援雙模關鍵字／AI 語意搜尋、預設 6 項可編輯釘選常用設定、最近調整紀錄，以及 24 項進階設定＋Shizuku 預設折疊並於搜尋命中時自動展開高亮）。
- 🤖 **AI 助手、AI 夥伴（AI 日記／長程目標／五階親密度）、主動情報中樞與聯網查詢、早晚簡報與里程碑主動推播**：內建唯讀 RAG 對話助手（側邊歷史滑出抽屜含預設收合＋即時未讀徽章之專屬主動訊息聊天室、氣泡右下角 `HH:mm` 時間戳、頂部未決事項膠囊列與 7 天過期壓縮錨點、自動擷取非敏感無衝突記憶並附小字提醒與刪除 BottomSheet、圖片視覺檢視、思考鏈「推演思路」折疊顯示、`LocalDataConnector` 直連本機日曆（具體行程一律逐字來自本機日曆查詢結果，查無誠實拒答）、`UsageStatsManager` 使用情況查詢與 **Health Connect 健康摘要查詢**（僅在對話詢問健康狀況且開啟「允許 AI 讀取健康摘要」時注入近 7 日匯總數值，原始生理採樣序列絕不出本機，未授權誠實拒答）、**聊天頁「🔍 聯網」查詢開關**（Gemini 3+ 支援原生 Google Search Grounding 並解析來源連結，免費 Gemini key 遇 400 自動無縫回退免費聯網搜尋引擎，其他 provider 未設 BYOK 端點亦預設支援 DuckDuckGo／Wikipedia 免費聯網查詢）、單意圖單卡工具調用與確認卡）、`PhantomMarkdownText` 渲染與「昨日簡報」；新增**主動情報中樞**（預設關閉；開啟後每日以 `AiPurpose.PROACTIVE` 獨立額度從非敏感記憶與對話萃取待確認關注候選 `watch_topics`，經使用者確認後僅送出關注主題短字串定時聯網查詢、12h 快取與 SHA-256 去重整理，有新訊才推送至專屬主動聊天室與通知並附「💬 深入追問」與「不再關注」）；最右「洞察」分頁（「夥伴」子分頁）顯示置頂去卡片化全寬助手頭像與名稱橫幅（支援獨立自訂區域背景）、陪伴天數、**五階親密度詳情（Lv1–Lv5「知己 Kindred」，門檻 `50/160/420/900`）**、**AI 日記／每日回顧**（每日 00:30 零 token SQL 聚合時間軸＋月份總覽，選配收合式 AI 註記）、**長程目標追蹤**（對話結構訊號確認卡建立、拆解週期子待辦並由 `ReminderScheduler` 單點觸發完成與查重防護、`max(今天, 上期 dueAt + cadenceDays)` 錨定下期、專屬持久化連 3 期逾期自動降頻一級與里程碑真值化追蹤，支援純離線手動管理）、近 7 天動態與「我對你的瞭解」畫像卡；支援早 08:45／晚 22:25 個人簡報（晨間簡報整合 **Health Connect 生理數據**本機查表式規則建議）、**事件里程碑式去重**主動推播（同一待辦或行程於 24h 前／提前 N 分／逾期各僅提醒一次＋10 分鐘內容雜湊雙防護，附 👍／👎 回饋；並含 **HealthObservationPolicy 主動健康觀察提醒**，針對連 2 晚睡眠 `<6h`、連 3 日步數 `<2000`、心率較 7 日均值高 `>=12bpm` 以純本機查表觸發，每規則每日最多 1 次、每週最多 2 次）、`CalendarInsightEngine` 定時日曆情報（預設 60 分鐘、僅以標題＋日期 ≤60 字查詢外部情報並嚴格去重）、獨立勿擾與簡報／主動提醒獨立每日上限。
- 🔗 **連結應用程式中心、宣告式受阻自動改道、權限授予中心與定時任務模板**：`LinkedAppsActivity` 由宣告式 `LinkedAppActionRegistry` 單一來源驅動（鬧鐘計時器即用、本機日曆／使用情況統計、GitHub PAT 15 分鐘輪詢含 sync/error 狀態與 Discord Webhook 唯讀／轉發，金鑰經 `EncryptedByokStore` 加密儲存）及「確認後執行」跨 App 動作卡；新增 `IntentCapabilityRegistry` **宣告式能力表與受阻自動改道**（`REMINDER`／`MEMO`／`CALENDAR_ADD`／`OPEN_APP` 依既定安全路徑序於權限或系統限制受阻時自動降級替代並告知，全敗附白話原因與直達權限中心按鈕，絕不隱式執行）；`PermissionHubActivity` 集中管理通知、SMS、通訊錄、日曆、精確鬧鐘、電池優化、使用情況與 **Health Connect**（含「允許 AI 讀取健康摘要」獨立開關）權限及四廠牌受限設定導引；`AssistantHubActivity` 集中收納簡報、主動日曆情報、定時任務模板（`TaskTemplateEngine` 三步嚮導＋所見即所得預覽，聚合段零 AI）與通訊錄整合（原始通訊錄絕不外送 AI）。
- 📝 **記事待辦、11 款桌面小工具、本機日曆日／週／月檢視、自動備忘錄與行程分流**：支援自然語言與手動編輯待辦提醒（優先級、備註、每日/每週重複、篩選排序，同步鬧鐘排程、桌面小工具與「記事」分頁）、**11 款桌面小工具**（既有 7 款統計／AI 摘要／記事／待辦提醒／防詐快捷工具全面支援 `targetCellWidth/Height`、`previewLayout` 與專屬向量預覽圖，並新增**今天待辦、目標進度、快捷記事、健康快照** 4 款小工具）、**記事分頁本機日曆日／週／月三檢視與設定頁帳號分組批次勾選**（預設過濾顯示中日曆並支援一鍵全選顯示中）、AI 自動備忘錄（支援從訊息時間線索智慧推算並提前 30 分鐘提醒），行程類通知預設只建立本機日曆建議卡（避免重複寫入待辦／記事，可於設定 `schedule_memo_route` 切回舊行為），以及透過對話提出新增/刪除白名單或標籤規則（強制彈出確認對話框＋DB 回讀驗證）。
- 🏷️ **AI 智慧標記、雙門檻防詐降誤判決策與白名單規則管理**：單次 AI 分析支援 0~3 個標籤＋零 token App 分類規則自動疊加；`DecisionEngine` 以確定性訊號優先搭配 AI 語義上限（`threshold - 10`）與可逆／不可逆矩陣，**嚴格收斂自動標記 `scam` 條件**（須具備情報命中、加密貨幣地址或具體詐騙操作訊號；一般連結或與 30 天高信譽寄件者／熟人衝突一律降級為確認卡，並收斂省 token 可疑特徵觸發詞以大幅降低日常通知誤判），支援「此判斷有誤」四類回饋一鍵寫回規則庫、寄件者信譽與加密良性網域 LRU；白名單採全量查詢與統一去重寫入，設定頁內建可依 App 分組收合的白名單管理卡片。
- 💬 **App 分組、卡片排列模式、寄件者頭像與自己回覆氣泡**：依 App 與 `MessagingStyle` 對話鍵分組瀏覽（首頁與記事分頁皆共用「一般條列（圓角分段群組清單）／中央堆疊」兩種卡片排列模式：中央堆疊採 `LazyColumn` 連續慣性滑動＋阻尼彈簧吸附、緊湊 `6dp` 邊距貼頂、滑動線性 `lerp` 置中與上下對稱 `contentPadding` 保留上方 2~3 張穩定繪製，動態 `zone` 避免短清單重疊沉底，搭配 `92dp` 緊湊單卡（大字體上限 `104dp`）、`40dp` 露出帶、近焦縮放緩衝（`0.94f`→`1.0f`）與 `0.04f` 遞減（下限 `0.82f`）及焦點陰影漸變），支援寄件者頭像擷取與三層 fallback 顯示（通知頭像 → App 圖示 → 首字母彩色頭像）、我的最愛與 VIP 寄件者置頂、對話頁直接回覆並於右側氣泡持久化顯示（含 3 分鐘自我回覆 echo 抑制與「我的名稱」群組自我身分偵測）、keyset 分頁載入（LIMIT 200）、左滑氣泡刪除、多選批次管理（上限 100，詐騙保護排除）、固定表頭＋獨立捲動訊息清單、3 顆智慧篩選膠囊＋「篩選 ▾」面板（含星標／已記事／已待辦／標籤／排序），以及頂部常駐全域搜尋膠囊鈕、清單頂端下拉超界手勢（移除常駐 22dp 把手視覺）與全螢幕全域／AI 語意搜尋（支援全部／訊息／設定／記憶／記事範圍篩選）。
- 🧭 **視覺化新手與設定教學**：8 步主頁核心高光引導＋5 步設定中心導覽（舊使用者升級不重置完成旗標，僅於設定中心顯示一次性 v4.0 快速導覽提示橫幅），依步驟自動切換分頁並支援無訊息合併提示、Android 13+ 系統權限彈窗後再顯示、各廠牌「允許受限制的設定」疑難排解、直達 App 通知存取詳情頁與 Shizuku 一鍵授權；字卡自動上下避讓不遮擋高亮區域，支援淺色、深色、動態取色三模式高對比閱讀與洞內觸控穿透。
- 🔋 **省 Token 五合一與省電／穩定性優化**：支援 Prompt Cache Key 與靜態前綴設計、雙模型信心路由（先以低價模型初判，模糊區間再升級重判）、增量情境摘要、圖片尺寸動態估算、省電自動停用玻璃模糊，以及 KeepAlive（45 分鐘）／Shizuku（600 秒）節能輪詢、Heartbeat `KEEP` 防抖動、熄屏查詢早退與前景服務異常防護。
- 🚨 **詐騙警示中心、勿擾時段與 SMS 自動回覆**：集中顯示高風險詐騙訊息、情報來源與趨勢比較（可由訊息頁風險卡或統計頁 Top 5 卡進入）；支援勿擾時段與 SMS 自動回覆（簡訊記錄由通知攔截路徑單一收錄去重，接收器專職自動回覆，含 per-sender 4 小時冷卻、單日上限 3 次與範本自回防護）。
- 📄 **CSV 匯出、保留政策、空間維護與加密金鑰**：一鍵匯出 CSV 至 `Downloads`；設定頁「資料管理」提供儲存空間明細與「立即壓縮資料庫」（定期 `VACUUM` 與過期資料／頭像快取上限 300 筆清理）；`EncryptedSharedPreferences` 本機加密儲存 AI、情報源（Safe Browsing／PhishTank／Chainabuse）及 GitHub PAT／Discord Webhook／外部搜尋端點金鑰，支援設定頁直接輸入（BYOK，無 BuildConfig fallback）。

- 🎨 **個人化三層背景圖片與卡片底色模式**：支援全域／AI 聊天／對話頁三層背景圖自訂（逐層 fallback、降採樣儲存防 OOM）、暗色可讀性遮罩（scrim）、液態玻璃卡片強度滑桿與卡片底色三模式（跟隨玻璃／透明滑桿 ≥15%／深色底）。
- 🔍 **自動與手動檢查更新（Snooze 狀態機）**：支援開啟 App 且網路可用時自動檢查 GitHub Releases 最新版本，只要未處於延後（snooze）期間即必現彈窗與通知提醒（點擊「稍後更新」才依設定延後，直接關閉或滑掉不吞提醒，單一進程失敗最多自動重試一次），並保留手動檢查與持久化狀態追蹤（`DownloadManager` 雙軌輪詢＋`ON_RESUME` 重查＋失敗重試＋Android 8+ 未知來源安裝一步引導＋`FileProvider` APK 安裝流程）。
- 💬 **通知欄連續快速回覆**：透過 AI 助手回覆後可於通知欄保留 `MessagingStyle` 代理對話通知，直接連續輸入多則回覆並顯示歷史與誠實失敗狀態，支援滑掉、關閉與閒置自動收起。
- 📝 **自動記事／待辦規則引擎**：支援右上角懸浮視窗管理手動、自然語言（AI 解析＋確認卡）與「不再自動記事／待辦」三範圍規則，採 `never > todo > note` 優先序與 AI 語意關鍵字比對控制自動備忘錄寫入，並支援待辦一鍵回溯來源訊息。
- 📈 **自訂統計儀表板、Health Connect 7 日生理趨勢與每日 App 排行榜**：統計頁支援頂部純本機「本週重點」自然語言摘要條、早晚簡報卡、**Health Connect 7 日睡眠／步數迷你條趨勢圖**（純本機聚合零 AI）、今日 App 訊息量排行榜 Top 5、7 日風險趨勢折線、未讀／已讀比例環形、各 App 佔比橫條與待辦完成率環形，每週 AI 摘要支援錯過補跑、未完句自動續寫、全文展開與失敗重試，並可於設定中心「資料與備份」自訂統計卡顯示與否。

- 🌐 **中英雙語介面、App 內三態語言切換與多語系 AI／防詐功能層（i18n）**：內建繁體中文（`values-zh-rTW`）與英文預設（`values/`，fallback = `en`）雙語資源桶（2,932 條 UI 與 XML `@string` 資源）；設定中心支援**「語言」三態切換（跟隨系統／繁體中文／English；API 33+ 走 `LocaleManager` 與系統 per-app 設定同步，API 26–32 走 `attachBaseContext` 平滑重建）**，通知、桌面小工具、設定搜尋目錄與 AI 自然語言輸出（風險分析、自動備忘錄、每週摘要、深度分析、早晚簡報、回顧註記、主動情報）全面跟隨 App 語言設定，且規則層防詐引擎（`IndicatorExtractor`／`MemoryEngine`／`ContactContext`）同步支援中英文常見詐騙話術離線偵測。
- 🖥️ **桌面端伴侶（`0.9.0`）與加密區網連線（`PROTOCOL_VERSION = 2`）**：Compose Multiplatform Windows 桌面端（採 `-setup.exe` 單一安裝通道，支援單實例防雙開、開機自啟驗證、通知中心四合一、系統匣常駐、淺色／深色玻璃主題、10 項系統＋16+ 項自訂設定、語言切換即時生效、**啟動自動檢查更新、更新提示對話框與「關於」分頁**）。採用**電腦端開啟 WSS 伺服器（`wss://<lan-ip>:49515/link`）、手機端主動連線**架構：手機可透過**掃描電腦 QR Code**（內建 ML Kit 模型，無 Google Play 服務亦可離線掃描）或**手動輸入電腦區網位址**進行配對，首次配對須輸入 **6 位數單次配對碼**並綁定 **TLS SHA-256 憑證指紋**（支援指紋主鍵動態解析 mDNS 位址），支援雙向對話串同步、星標、標籤、待辦記事完整 CRUD、快速回覆、深度分析與多會話 AI 聊天。

---

## 版本紀錄

完整變更紀錄請參閱 [CHANGELOG.md](CHANGELOG.md)。

### v5.3.0 更新重點（versionCode 62，桌面端 0.9.0，2026-11-28）

- **桌面端 UI 現代化（#589）**：側欄可拖曳調整寬度（120–320dp，持久化）並支援 Ctrl+B 收合、分組與 tooltip；新增 Ctrl+K 全域快速搜尋（對話、記事、待辦、設定，支援上下鍵與 Enter 跳轉）；首頁未連線時顯示三步驟連線引導，電量、Wi-Fi、儲存與最後同步改為語意狀態 chips；QR 配對改為三步精靈（成功動畫、指紋與區域網路安全說明、分類錯誤引導）；會話列表與訊息氣泡排版對齊市面慣例（未讀語意色、氣泡最大寬度、hover 複製與從此檢視移除、載入更多指示）；EXPANDED 視窗設定頁改為兩欄式分類；新增快捷鍵說明面板（?）並對齊 Ctrl+1..8；桌面端統一間距／圓角 design token 與空狀態風格。未變更連線、TLS、更新器或協定邏輯。

### v5.2.1 更新重點（versionCode 61，桌面端 0.8.1，2026-11-27）

- **桌面端更新通道修復（#583）**：將桌面端伴侶版號由 `0.8.0` 遞增至 `0.8.1`（同步更新 `desktopVersion`、`CURRENT_DESKTOP_VERSION` 與 Inno Setup 預設 `MyAppVersion`），使既有 `0.8.0` 使用者可正常透過 `DesktopUpdater` 收到更新提示並安裝 R15／R16／R17a 之桌面端修復（含 Fail-Closed 更新器與 48dp `PhantomDialog`）。
- **文件同步（#583）**：於 `README.zh-TW.md` 補齊 `v5.2.0` 與 `v5.2.1` 更新重點段落，並將 `README.md` 與 `README.zh-TW.md` 核心功能總覽中過期的桌面端伴侶版號更新至 `0.8.1`。

### v5.2.0 更新重點（versionCode 60，桌面端 0.8.0，2026-11-27）

- **健檢修復完成（R1–R17，239 項缺失，20 個 PR）**：跨端健檢列管之全部 P0（44）、P1（143）與 P2（52）缺失已依相依順序分 17 輪修復完畢——涵蓋共用資料層與 DAO 擴充、util 與系統橋接、通知風險管線、排程與背景 Worker、主動情報與 Shizuku、跨端連線、小工具刷新管線、CI/CD 供應鏈、動效與共用元件、主畫面與訊息流程、聊天核心、設定中心、管理頁群、分頁 UI、桌面核心、桌面 UI，以及全倉庫多語系與清理收尾。
- **安全**：`DesktopLinkClient` 改採常數時間指紋比對與完整 SHA-256 憑證釘選；`opencode.yml` 新增 `author_association` 白名單並將 actions 釘選至完整 commit SHA；桌面更新器改採 Fail-Closed 策略，缺少相符 `.sha256` 校驗檔即拒絕安裝；設定 `allowBackup=false` 並搭配 `dataExtractionRules`；未簽章的 release 建置立即失敗。
- **效能與可靠性**：以 DAO 區間與聚合查詢取代全表載入，SQLite 批次刪除依 500 筆變數分塊處理，並修復多處 OOM 與主執行緒 I/O 路徑；前景服務遵守 10 秒 `startForeground` 規則，Worker 獨立重拋 `CancellationException`，精確鬧鐘改先檢查 `canScheduleExactAlarms()` 並提供非精確降級備援，並補齊中國廠牌 OEM ROM 防禦組合。
- **多語系與清理**：將 86 則使用者可見字串移入 `values`／`values-zh-rTW`，清除 641 個未使用 imports，將 17 處正則表達式提升為檔案級常數並快取動態樣式，正則驗證錯誤訊息同步多語化。

### v5.1.0 更新重點（versionCode 59，桌面端 0.8.0，2026-11-26）

- **訊息同步、標題修復與訊息頁群組報告入口（#531、#527）**：桌面訊息列表新增分頁載入（每頁 60 筆、觸底自動續載、同步計數與時間、斷線誠實錯誤與手動重試）、同 `id` 合併不覆蓋 `isRead`／`isStarred`、標題三段回退鏈及「對話檢視 / 依 App 分組」切換；手機訊息頁新增圖示＋文字按鈕開啟群組報告 `ModalBottomSheet`（共用 `GroupReportsViewer`）。
- **懸浮球根因修復與彈窗背景圖自訂（#528、#529）**：新增 `DesktopScreenMetrics`（AWT px ↔ Compose dp）修正多 DPI 拖曳漂移，將懸停資訊卡與通知氣泡移至獨立第二視窗根治抽搐回饋環，拖曳改同步定位並新增 6 種自訂懸停動作；設定中心新增彈窗背景圖（≤5MB）、遮罩濃度滑桿（0.30–0.90）、文字 HEX 色票（6 組預設）與即時預覽，右下角彈窗改 `remainingMs` 懸停暫停／續計、新增倒數角標開關與 3–20 秒自由調整。
- **Splash 啟動畫面與雙軌開機自啟（#530）**：新增純 JDK AWT 無邊框置中啟動畫面（8 秒保險自動關閉、`--minimized`／`--tray` 不顯示）；開機自啟動升級雙軌（HKCU `Run` ＋ per-user Task Scheduler `PhantomCheckDesktopLogon`，免管理員/免 UAC），解除安裝同步清除並於設定頁顯示雙軌狀態。
- **主動情報 v3 與雙端同步稽核（#532、#533）**：省 Token 模式改為降級不停機（每日 1 輪、Google News RSS＋本機規則層判決、零 AI Token），支援 `<48h` 新鮮度、SHA-256 去重、診斷面板、桌面推播鏡射（≤60 字節錄）與桌面「不再關注」靜音回寫，並同步補齊 `PRIVACY.md` 第六節；完成 16 項雙端功能同步稽核、文案修整與雙語字串對齊。
- **行為變更**：開機自啟動改雙軌（新增排程器工作）；協定附加 `proactive.event.push`／`watchTopic.mute`（向下相容不 bump）；開啟主動情報後新增 `news.google.com` 公開 RSS 請求；彈窗顯示時長由 `3|5|8` 秒改為 `3–20` 秒自由調整。

### v5.0.0 更新重點（versionCode 58，桌面端 0.7.0，2026-11-25）

- **跨端功能巨批（#509、#511、#512、#516、#517、#518）**：新增純 Worker 事後聚合之群組報告（#509，App／群組兩層挑選、每日＋N 則雙觸發、雙輸出模式、Room v23→v24 非破壞性遷移與 AI 額度閘門）；主訊息頁長按新增跨 App 批次刪除（#511，完整保留既有防護慣例）；常駐通知新增連線狀態副標（#512）；mDNS 區網探索導入 `MulticastLock`、自動重啟防卡死、重試上限與掃描狀態 UI（#516）；手機裝置狀態（電量與充電狀態）零輪詢雙向同步（#517）；跨端功能包（#518）一次到位——雙向剪貼簿同步（預設關閉）、快速筆記、桌面發送通知到手機、尋找手機響鈴與網址互開——並新增桌面側欄 REPORTS 與 TOOLS 頁。
- **淺色全域對比度修復與高對比開關（#510）**：全面修復淺色主題（視窗背景、狀態列、玻璃卡不透明度 ≥0.92、淺色 scrim），新增深淺兩套之高對比開關（正文對比目標 7:1），以 `phantomSurfaceAlpha()` 取代 35+ 處半透明表面色。
- **桌面彈窗、懸浮球與全螢幕體驗（#513、#514、#515）**：新增 16 個彈窗自訂設定鍵與即時預覽、各通道顯示頻率與側欄／系統匣／首頁一鍵勿擾（#513）；修復 125–200% DPI 與多螢幕下之懸浮球拖曳飄移並新增 8px spring 邊緣吸附（#514）；修復懸浮球 AI 訊息計數、新增雙向聊天同步、180° 半圓快捷選單、Enter 送出之迷你 AI 面板與全螢幕自動隱藏（#515）。
- **效能稽核與 Phantom Aurora III 響應式重編（#519、#520）**：完成雙端全庫效能稽核（#519，`ThreadLocal` 時間格式、Regex 抽離、逾時復原、零新增常駐輪詢），並依 `DesktopWindowClass` 全面重編桌面五頁（#520），統一 `PhantomDialog` 外殼與 WCAG 驗證對比測試（一般 ≥4.5:1、HC ≥7:1）。
- **行為變更**：新增 `normal` 權限 `ACCESS_WIFI_STATE` 與 `CHANGE_WIFI_MULTICAST_STATE`（#516）；Room 資料庫 v23→v24 非破壞性遷移（#509）；剪貼簿同步預設關閉（#518）。

### v4.9.0 更新重點（versionCode 57，桌面端 0.6.0，2026-11-25）

- **#494–#504 雙端功能升級與健檢（PR #506）**：
  - **桌面訊息、通知四合一與淺色主題（#494、#497、#498、#504）**：桌面訊息左欄依 `packageName::conversationKey` 對話串化並支援智慧捲動與批次多選（已讀／靜音 App）；新增通知體驗四合一（自動收回頂部橫幅、全螢幕抑止右下 Toast、可拖曳吸邊懸浮助手球、8 項系統匣右鍵選單）；新增 `DesktopIcons` 向量圖示、`PackageManager` 友善 App 名稱統計（7 日／30 日／每小時、今日活躍 App、本週 Top 5）、淺色玻璃主題開關與 `Alt+1..6` 快捷鍵。
  - **單實例防雙開、開機自啟驗證與 MSI 退場（#495、#496，行為變更明示）**：新增 `FileLock`＋loopback IPC 單實例防護與 Windows `HKCU\...\Run` 讀回驗證／`StartupApproved\Run` 停用偵測；`.msi` 通道正式退場，Windows 僅提供 `PhantomCheckDesktop-0.6.0-setup.exe`，安裝器自動靜默清理舊 MSI/舊 Inno 並完整保留 `%APPDATA%\PhantomCheckDesktop` 設定與配對。
  - **記事待辦雙端同步、手機連線與過濾與專注（#499、#500、#501、#503）**：修復手機端 `NotesTab` 記事點擊編輯並升級桌面待辦／記事完整 CRUD 與 AI 引用最新通知；手機連線新增 60 秒 mDNS TTL、換網 `NetworkCallback` 重掃、憑證指紋動態解析與六大失敗診斷；雙端「規則與靜音」更名「**過濾與專注 / Filters & Focus**」（新增 1 小時臨時靜音、標籤管理與手機設定「進階與系統」收合群組）；協定加法式擴充 `DESKTOP_SETTINGS_SYNC` 並新增事件驅動 `WidgetUpdater`（≤5 秒刷新＋30 分鐘兜底）。
  - **英文文案本地化審校（#502，行為變更明示）**：審校手機端 `values/strings.xml`（88 處）與桌面端 `DesktopStrings.kt`（13 處）英文慣用語（繁體中文語意不變）；`PROTOCOL_VERSION` 維持 `2` 且全部新增 DTO 具預設值，確保新舊版本混連相容。

### v4.8.0 更新重點（versionCode 56，桌面端 0.5.0，2026-11-24）

- **#487 桌面端 Windows 防火牆自我檢查與一鍵放行（PR #491）**：新增 `DesktopFirewallHelper.kt` 唯讀檢查網路 Profile 與防火牆規則，提供 UAC 提權一鍵新增「目前執行檔＋TCP `49515`＋`-Profile Any`」專屬規則（含 Public profile 與行動熱點 `192.168.137.x` 警示）；手機端新增 15 秒探測冷卻與「立即診斷」按鈕，並於 `PRIVACY.md` 雙語補充說明。
- **#486 相機掃碼 R8 閃退修復與穩定性加固（PR #491）**：於 `app/proguard-rules.pro` 補齊 ML Kit bundled 與 `firebase-components` `ComponentRegistrar.<init>()` 之 R8 full mode keep 規則（經 `retrace` 與 `mapping.txt` 驗證），修復 Release APK 掃碼閃退；`QrScannerOverlay.kt` 補齊後鏡頭預檢、`ON_RESUME` 重綁、`try/finally` `ImageProxy.close()` 與安全關閉順序。
- **#488 桌面端頁面轉場與深淺自訂體驗（PR #491）**：新增 `DesktopThemeTokens`／`LocalDesktopTokens` 自適應高對比文字與邊框、4 種頁面轉場動畫（無／淡入淡出／滑動／縮放）並保留捲動狀態、玻璃深淺與 draw-phase 背景遮罩滑桿，以及 `DesktopBackgroundHelper.kt` 自訂背景選圖。
- **#489 雙端效能與省電優化（PR #491）**：桌面端隔離呼吸動效（縮匣暫停）、快取上限、`refreshAllOnConnect` 節流、`remember` 快取 Markdown 與圖表轉換並新增 60s WSS Ping 選項；手機端熄屏暫停 mDNS、退避封頂 60s 整段睡眠、推播／UI 輪詢降頻（亮屏立即恢復）。
- **#490 雙端全域稽核修復 E1–E10（PR #491）**：序列化 `NsdManager.resolveService` 與退避重試、配對碼 5 次錯碼鎖定 30 秒與審計、`DesktopUpdater.kt` 本機 SHA-256 與 optional `.sha256` sidecar 驗證、`AGENTS.md` 納入 R8 冒煙與 `mapping.txt` 不公開決策、斷線／逾時狀態復位、3024 組雙語字串對齊驗證與補齊診斷日誌。

### v4.7.0 更新重點（versionCode 55，桌面端 0.4.0，2026-11-24）

- **#476 連線架構反轉與憑證指紋綁定（PR #481）**：桌面端改為內建 Ktor `3.6.0` Netty WSS 伺服器（`DesktopLinkServer.kt`、`DesktopTls.kt`、`DesktopMdnsBroadcaster.kt`，埠 `49515`），手機端改為 OkHttp WebSocket 主動連線（`DesktopLinkClient.kt`）並驗證 TLS SHA-256 指紋，支援 `1s→30s` 退避、`NsdManager` 掃描與階段／分類錯誤診斷；`PROTOCOL_VERSION` 升至 `2`（需重新配對）。
- **#477 手機掃描電腦端 QR Code 配對（PR #481）**：桌面端透過 `zxing:core:3.5.4` 生成 3 分鐘單次效期 QR Code（`DesktopQrDialog.kt`）；手機端透過 **bundled** `mlkit:barcode-scanning:17.3.0`（無 Google Play 服務亦可離線掃描）＋ CameraX `1.6.2` 掃碼（`QrScannerOverlay.kt`、`QrPairingParser.kt`），並保留手動輸入電腦區網位址備援。
- **#478 桌面端啟動自動檢查更新、更新提示與關於頁（PR #481）**：新增 `DesktopUpdater.kt`（啟動單次比對 `PhantomCheckDesktop-<semver>-setup.exe`/`.msi` 資產）、`DesktopUpdateDialog.kt`（Markdown 更新內容＋下載並啟動安裝器）與設定中心「關於」分頁（版本、相容協定、專案連結與最近 10 個 Releases）。
- **#479 桌面端語言切換即時生效與自訂擴充 8→16（PR #481）**：修正 `DesktopStrings.isZh` 為 Compose `mutableStateOf` 實現全視窗／系統匣即時切換、全面在地化設定 enum，自訂設定擴充至 16 項（自訂 HEX 強調色、12/24h 時間、訊息卡圓角、玻璃模糊強度、聊天字級、通知音效、啟動自動接收已配對手機、啟動預設分頁），並升級支援跨日 DND 時間選擇器。
- **#480 雙端十項穩定性稽核修復（PR #481）**：補齊 8.5s / 130s 指令逾時回饋、連線例外診斷記錄、`RATE_LIMIT_EXCEEDED` 連續 3 次斷線與首發 11 指令 120ms 間隔、UTF-8 `Properties` 相容遷移、聊天複合唯一 key、全分類配對錯誤雙語文案、JmDNS 失敗診斷、QR JSON 防呆、`1024x680` 最小視窗支援 `125%` 字級縮放與 `CAMERA` 生命週期清理。

### v4.6.0 更新重點（versionCode 54，桌面端 0.3.0，2026-11-24）

- **#465 Netty 修復桌面連線閃退（PR #473）**：手機端 WSS 伺服器由 `ktor-server-cio` 改為 `ktor-server-netty` `3.6.0`（`1/2/4` 執行緒池）、指令限流改 Token Bucket（5/秒＋突發 12）搭配桌機初始 100ms 間隔、新增埠佔用預檢、`markRead` 專用 DAO 與 ProGuard/packaging 規則。
- **#466 手機底部欄「連線」分頁（PR #474）**：`MainTab` 由 5 欄擴充為 6 欄新增「連線」分頁，抽出共用 `LinkHubContent.kt` 與 `LinkHubActivity` 共用 `DesktopLinkServer` singleton 並定時 `touchActivity()` 防閒置誤停。
- **#467 7 項雙向同步擴充（PR #474）**：新增星標雙向、訊息標籤套用/移除、待辦完整 CRUD（同步 `ReminderScheduler`）、記事清單與新增、單則深度分析（計額度與失敗退款）、靜音 App 管理、對話串檢視（上限 200 字預覽）＋手機電量/狀態＋多會話聊天（`PROTOCOL_VERSION` 維持 1）。
- **#468 桌面設定中心（10 系統＋8 自訂）與玻璃 UI／圖表／Markdown 渲染（PR #474）**：新增 10 項系統設定（語言、Windows 開機自啟 `--minimized`、關閉行為、勿擾原生通知、退避/心跳/預覽長度、配置管理、手動檢查更新、3 份旋轉日誌）＋8 項自訂設定（強調色、密度、字級、側欄、卡片/鏡射模式、背景圖、動畫）；支援 `1180x760` Master-Detail、Canvas 7 日／24 小時圖表、Markdown 渲染 AI 助手與鍵盤捷徑。
- **#469 桌面系統匣常駐（預設縮匣，PR #474）**：以 Compose `Tray` API 將 `DesktopLinkClient` 與 `MdnsScanner` 生命週期拉升至 `application {}`，關閉視窗預設縮至系統匣維持連線與原生通知（支援 `--minimized`/`--tray`）。
- **#470 Inno Setup 安裝器與雙資產發布（PR #474）**：新增 `desktop/packaging/windows/PhantomCheckDesktop.iss`（per-user `%LOCALAPPDATA%` 免管理員、預設勾選桌面捷徑與安裝後立即開啟），`desktopVersion = "0.3.0"` 單一來源，同步產出 `PhantomCheckDesktop-0.3.0.msi` 與 `PhantomCheckDesktop-0.3.0-setup.exe`。
- **#471 兩端穩定性稽核修復（PR #474）**：`DesktopPaths.kt` 將 `link.properties`（`hmacSecret`）遷至 per-user ACL 目錄、`sendCommand` 回傳 `Boolean` 斷線提示、`CHAT_SEND` 130 秒逾時、per-client `clientCursors`、`activeSessions.remove(id, sess)`、移出 EDT 關閉 `JmDNS`、`gson.fromJson` 容錯與正確重拋 `CancellationException`。

### v4.5.0 更新重點（versionCode 53，2026-11-23）

- **#454 LinkHubActivity 桌面端設定頁閃退修復（PR #459 / #463）**：調整 `enableEdgeToEdge()` 至 `super.onCreate()` 之後、補齊 `PairedDesktop`/`UnknownAttempt`/`CommandAuditEntry` 與 `qrcode.**` R8 keep 規則，並為 `LinkHubActivity`/`DesktopLinkStore`/`DesktopLinkServer` 全路徑加上 `runCatching` 容錯與 `wss://`/SHA-256 文字備援。
- **#455 聯網搜尋智慧化（PR #460 / #463）**：新增 `AiClient.decideSearchNecessityAndRewrite` 前置搜尋必要性判斷與查詢重寫（融合 RAG 記憶與近 12 則歷史，省 Token 退回規則路由）、同網域去重最多 3 筆乾淨來源顯示，以及 `FreeWebSearchEngine` HTML／編碼淨化、可讀度／長度過濾與網域品質分級。
- **#456 主動情報中樞 v2（PR #461 / #463）**：`WatchTopicEngine` 融合非敏感記憶、授權日曆未來 7 日要點與高階健康趨勢組出 ≤100 字個人化查詢；`DigestComposer` 新增推播前 AI 相關性過濾（`RELEVANCE_JUDGE_PROMPT`）與 3–5 句精簡摘要（最多 2 筆乾淨來源）。
- **#457 桌面端 Windows MSI/EXE 安裝檔管線與深色玻璃 UI（PR #462 / #463）**：桌面端 `version`／`packageVersion` 統一為 `0.2.0` 並設定 `TargetFormat.Msi`／`TargetFormat.Exe` 與 `windows-latest` CI/Release 產出管線；`DesktopApp` 升級深色玻璃介面、Spring 動畫與重連倒數進度條。

### v4.4.0 更新重點（versionCode 52，2026-11-23）

- **#447 免費聯網查詢後端與免費 Gemini Key 400 修復（PR #451）**：新增零金鑰 `FreeWebSearchEngine`（依序查詢 DuckDuckGo Instant Answer → DuckDuckGo HTML 含 202/429 5 分鐘負快取 → Wikipedia API）；免費 Gemini key 開啟聯網查詢遇 HTTP 400（不支援 Google Search Grounding）時，偵測 `isGroundingUnsupported400` 並靜默退還該次失敗扣額、快取旗標後自動回退至免費搜尋引擎＋一般呼叫；非 Gemini provider 未設 BYOK 搜尋端點時改走免費搜尋引擎（已設 BYOK 優先 BYOK、失敗降級免費引擎），全無結果或斷網時誠實回報失敗不幻覺頂替。
- **#448 主動通知全天行程附註去重與通知 ID 互蓋修復（PR #451）**：`ProactiveEngine` 全天行程附註改以 `allDayNote:$dayKey:<allDayIdsFp>` 寫入 `deliveredMilestones` 去重（僅於同日全天事件集合變動時再附加），並修正 `postBriefingNotification` 固定通知 ID 互蓋問題（改用獨立 `effectiveNotifId` `1090..1099` 搭配 `BigTextStyle`）。
- **#449 桌面端連線 M1 連線骨架（PR #451）**：新增 `/shared` 跨平台協定模組與 `/desktop` Compose Multiplatform（`1.12.1`）桌面端骨架、手機內嵌 Ktor WSS 伺服器（software-backed RSA 2048 + BouncyCastle X509v3 + PKCS12 憑證）、mDNS 發現與手動 `host:port` 一鍵複製、6 位數配對碼（TTL 5 分鐘）＋ QR 配對與 HMAC challenge 重連、閒置 30 分鐘自動停止與 `SettingsCatalog` `desktop_link`（零新 Manifest 權限）。
- **#450 桌面端 M2 雙向操作與連線審計（PR #451）**：`/shared` 擴充白名單 `MessageSummaryDto`（無全文、無敏感記憶、無通訊錄/日曆原始內容）；支援 `messages.list`／`messages.subscribe`／`messages.markRead`、`stats.snapshot`、`chat.send`／`chat.history`、`todos.list`／`todos.toggle`（單點複用 `ReminderScheduler.completeTodoWithRecurrence`）、`quickReply.send` 與 `whitelist.*`／`tags.*` 雙向操作；加入 `<=5 cmd/s` 限流與 200 筆環形審計紀錄，完成 `DesktopApp` 雙語深色介面並於 `PRIVACY.md` 新增區域網路連線中英雙語章節。

### v4.3.0 更新重點（versionCode 51，2026-11-23）

- **#437 AI 傳輸層硬化（PR #442，行為變更明示）**：GitHub Models 已於 2026-07-30 全面退役，`AiClient` 直接攔退役 host（`models.github.ai`、`models.inference.ai.azure.com`）不發無效請求，拋附公告連結與遷移建議的例外；統一 `Accept: application/json`；非 JSON 回應拋保留 HTTP 狀態碼、`Content-Type` 與前 300 字本文的明確例外；`testConnection` payload 形狀不變。
- **#440 洞察頁長期目標追蹤（PR #442，行為變更明示）**：完成路徑統一由 `ReminderScheduler.completeTodoWithRecurrence` 單點觸發 `GoalEngine.onTodoCompleted`（寫入前 `(goalId,todoId)` 查重防護），`NotesTab` 直呼移除、目標進度 Toast 移除；逾期降頻改以 `phantom_goal_engine` 專屬 `SharedPreferences` 持久化期數；下期派生錨定 `max(今天, 上期 dueAt + cadenceDays)`；里程碑真值化消費 `milestoneJson`。
- **#441 親密度第五階段（PR #442，行為變更明示）**：門檻 `30/100/250` 改 `50/160/420/900`，新增 Lv5「知己（Kindred）」，既有用戶等效等級可能下修屬預期；權重公式 `2/1/1` 不變。
- **#438 App 內三態語言切換（PR #442，行為變更明示）**：設定新增「語言」三態（跟隨系統／繁體中文／English；API 33+ 走 `LocaleManager` 與系統 per-app 設定同步，API 26–32 走 `attachBaseContext` 平滑重建）；通知、小工具、設定目錄與 AI 輸出語系全跟隨 App 設定；`zh-TW` 系統＋跟隨系統表現與 v4.2.0 一致。
- **#439 小工具（PR #442）**：既有 7 工具補 `targetCellWidth/Height`、`previewLayout`、專屬向量預覽圖、`goAsync` 8.5s timeout、`onDisabled` 清理；新增今天待辦／目標進度／快捷記事／健康快照四個（零新權限、零新網路、`exported=false`、`FLAG_IMMUTABLE`、健康快取 30 分鐘、未授權顯示教學態）。

### v4.2.0 更新重點 / Release Highlights（versionCode 50，2026-11-23）

- **行為變更：預設介面與 AI 輸出改為英文（Behavior Change: Default English UI & AI Output for Non-`zh-TW` Devices）**：
  - 僅提供雙語資源桶（預設 `values/` 為英文 `en`、`values-zh-rTW/` 為繁體中文）；**當裝置語言非繁體中文時，UI 介面與 AI 輸出（分析、備忘錄、週報、深度分析、簡報、日記註記、主動情報）一律改為英文**，繁體中文（`zh-TW`）裝置顯示與輸出維持逐 byte 不變。
  - Dual resource buckets (`values/` default English fallback and `values-zh-rTW/` Traditional Chinese): **When the device locale is not Traditional Chinese (`zh-TW`), both UI strings and AI natural-language outputs switch to English**; `zh-TW` devices remain byte-for-byte unchanged.
- **#429 全庫 UI 字串抽離與 XML 全 `@string` 化（PR #432） / Full UI String Extraction & XML `@string` Externalization**：
  - 全庫 Compose、Activity、Dialog、Onboarding、桌面小工具、通知渠道、`SettingsCatalog`（138 項）與 `AndroidManifest.xml`／`res/layout/*.xml` 全數抽離為 2,898 條 `@string` 資源，並於 `AGENTS.md` 確立 UI 字串抽離紀律（零行為變更）。
  - Extracted 2,898 user-visible strings across Compose, Activities, Manifest, widgets, notifications, and `SettingsCatalog` into `strings.xml` with `@string` references (zero behavior change).
- **#430 預設介面改英文（`values/` 為英文、`values-zh-rTW` 為繁中）（PR #433） / Default English UI (`values/`) & Traditional Chinese (`values-zh-rTW/`)**：
  - 原 2,898 條繁中字串原封不動遷入 `values-zh-rTW/strings.xml`；預設 `values/strings.xml` 全面英文化，對齊核心術語（`PhantomCheck`、`AI Assistant`、`Memo`、`To-do`、`Whitelist`、`Scam Protection`、`Memory Bank`）與格式化佔位符。
  - Moved all 2,898 Traditional Chinese strings byte-for-byte into `values-zh-rTW/strings.xml` and translated the default `values/strings.xml` bucket into concise Android UI English.
- **#431 功能層 AI 輸出與詐騙詞庫多語系支援（PR #434） / Locale-Aware AI Output & Bilingual Scam Lexicon**：
  - AI 風險分析、自動備忘錄、每週摘要、深度分析、早晚簡報、日記回顧註記、主動情報與聊天助手全面跟隨系統語系切換繁中／英文輸出；`IndicatorExtractor`、`MemoryEngine` 與 `ContactContext` 擴充英文詐騙話術詞庫（unpaid toll、parcel redelivery、bank ATO / safe account、Hi Mum impersonation、crypto pig-butchering、OTP harvesting），離線規則層同步支援中英雙語偵測。
  - AI prompts across analysis, auto-memo, weekly summary, deep analysis, briefings, diary annotations, proactive intel, and chat automatically follow the system locale (`zh-TW` vs. English), while rule-based scam detection adds English scam lexicons based on 2025–2026 FTC/IC3/FCC advisories.

### v4.1.0 更新重點（versionCode 49，2026-11-23）

- **#416 訊息頁統計卡重新排版（PR #426）**：單行三段式（今日／未讀／詐騙）＋ 1dp 垂直分隔線，「全部標已讀」移至同行尾端，unread 99→0 高度零跳動。
- **#417 強制深色修正（PR #426）**：視窗層 theme 改 `android:Theme.Material` 並設定 `android:windowBackground`／`android:colorBackground` 為深色（phantom_window_bg #FF0E0A18），消除 Activity 轉場白底閃現；Compose 層維持高對比。
- **#418 記憶頁統計卡與次級分頁分離（PR #426）**：記憶頁「長期／短期／衝突／免疫」統計卡降低視覺重量（alpha 0.35），與「記憶｜規則｜防詐免疫」次級分頁以 10dp 間距明確分離。
- **#419 AI 健康查詢零幻覺（PR #426）**：Health Connect 擴充距離（DistanceRecord）與消耗熱量（TotalCaloriesBurnedRecord）讀取，改為 per-type 權限查詢；未授權型別顯示「未授權」、無資料顯示「無紀錄／無資料」，AI 拒答幻覺正反例與 health_query 動作落地。
- **#420 統計頁健康明細卡（PR #426）**：日／週／月切換（當日健康快照、近 7 日彙總＋可滑動每日明細表、近 30 日彙總與趨勢），本機聚合零 token。
- **#421 AI 回顧註記修復（PR #426）**：新增 AI 用途 DIARY_ANNOTATION（ai_model_diary_annotation 可綁定獨立模型，未綁定沿用預設 profile）；不可用時三態診斷（省 token 模式／未設定供應商／同類註記被停權）各附捷徑按鈕；新增「省 token 模式下仍生成回顧註記」開關（diary_annotation_in_saver，預設關閉）。
- **#422 主動聊天室與一般聊天室切換重疊修復（PR #426）**：切換前清空訊息、以 key(currentSessionId) 重建訊息列表、coroutine 內二次確認防止快速切換競態。
- **#423 設定健檢與搜尋擴展（PR #426）**：catalogue 新增 diary_annotation_in_saver、ai_model_diary_annotation、proactive_intel_enabled、proactive_extract_enabled 條目（皆有對稱讀寫實作）；同義詞組新增「深色／主題」組、健康組擴充距離／熱量；全域設定搜尋改用 SETTINGS_SEARCH 用途 profile。
- **#424 AI 更智能與主動（PR #426）**：早晚簡報逐段誠實降級（缺資料明確標「無資料」）；回饋驅動節流（近 7 日同主題 👎≥2 等待窗 ×1.5，👍≥2 且無 👎 ×0.75，無回饋不變）；15 分鐘內有對話不投遞主動訊息（標記 dirty 併入下一輪）；聊天室動態建議 chips（狀態驅動、純本機、零 token、最多 4 顆）。
- **#425 全天行程誤判撞期修復（PR #426）**：全天（ALL_DAY）事件不計入定時行程衝突判定；偵測到全天事件時顯示全天事件資訊卡（新增日曆事件與通知轉日程皆適用）。

### v4.0.0 更新重點（versionCode 48，2026-11-23）

- **v4.0.0 全域資訊架構（IA）大改版與搜尋優先設定中心（#409 / PR #413）**：① **導覽 6→5（「洞察」整併「夥伴｜統計」，D1）**：底部導覽精簡為「訊息、AI 助手、記事、記憶、洞察」5 分頁（64dp 高度、N5 指示器寬度與位移彈簧），「洞察」頂部以 `PhantomSecondaryTabRow` 切換「夥伴｜統計」，既有通知／小工具／教學深連結全數相容映射；② **設定中心重構（D2）**：新增 `SettingsHomeScreen` 與 `SettingsCategoryRegistry`，改為頂部 56dp 雙模搜尋框（本地關鍵字＋AI 語意搜尋）＋預設 6 項可編輯釘選常用設定雙欄磁貼（開關類支援內嵌 `FluidSwitch` 直切）＋最近調整膠囊列＋8 大分類頁（將原個人化與外觀合併為「外觀與個人化」，同 Activity 內以 N1／N2 共享 X 軸轉場切換並相容 `highlight_card_key` 直達）；③ **訊息首頁減法與中央堆疊保留（D3）**：`CenterFocusCardStack` 中央聚焦堆疊幾何 0 diff 完整保留，首頁頂部改為圓角三欄統計卡與 3 顆智慧篩選膠囊＋「篩選 ▾」BottomSheet，一般條列升級為圓角分段群組清單；④ **進階設定預設折疊可搜尋展開（D4）**：24 項低頻／專家設定與 Shizuku 預設收納於各分類底部 `SettingsAdvancedSection`，搜尋命中自動展開並脈衝高亮（N7）；⑤ **移除常駐 22dp 把手視覺、保留下拉手勢（D5）**：移除頂部常駐 22dp 把手橫條，保留清單頂端下拉超界手勢觸發搜尋並於 56dp 頂部列常駐搜尋膠囊鈕；⑥ **教學精簡（D6）**：主頁 20→8 步、設定 9→5 步，舊使用者完成旗標不重置；⑦ **大螢幕導覽軌（D7）**：寬度 `>= 600dp` 自動切換左側 `NavigationRail`＋置中 `840dp` 主容器；⑧ **新元件、動效 N1–N7 與零回歸保證**：新增 `PhantomPageScaffold`、`GlobalSearchOverlay`（5 範圍 chips／最近搜尋／常用動作／釘選）、`SettingsRow` 與僅存設定 ID 之 `UiLayoutPrefs`（`phantom_ui_v400`），`PhantomMotion` 追加 N1–N7 draw-phase 常數並於設定分類頁與全域搜尋接入 `PredictiveBackHandler`；`service/`、`data/`、`network/`、`util/` 四目錄 **0 diff**，純 UI 層改版無資料與背景行為變更。

### v3.8.0 更新重點（versionCode 47，2026-11-23）

- **七案實機回報修復與 Health Connect 匯總查詢／主動觀察（#402–#408 / PR #410）**：① **本週 AI 摘要不再截斷（#402）**：`summarizeWeekly` `maxTokens` 提升至 `2400`（Groq `1600`），偵測 `finish_reason == "length"` 或未完句時自動續寫收尾，統計頁週摘要卡移除 `.take(70)` 硬截斷並支援點擊「展開全文」；② **Health Connect 聊天匯總注入與主動健康觀察（#403，隱私行為變更）**：於對話詢問睡眠／步數／心率／疲勞且開啟「允許 AI 讀取健康摘要」（預設開，位於權限中心與設定目錄）時，僅注入近 7 日每日匯總數值與今日晨間快照送往使用者設定之 AI 服務商（原始生理採樣序列絕不出本機，已載明於 `PRIVACY.md`；未授權時誠實拒答），並於 `ProactiveMonitorWorker` 掛載純本機查表 `HealthObservationPolicy`（連 2 晚睡眠 `<6h`、連 3 日步數 `<2000`、靜止心率高於 7 日均值 `>=12bpm`，每規則每日最多 1 次、每週最多 2 次，`NotifIds.HEALTH_PROACTIVE = 1014`）；③ **主動訊息統一發送系統通知（#404）**：早晚簡報（`1008`/`1009`）與所有背景主動提醒統一走 `deliverProactiveMessage` 全套紀律、點擊通知深層連結直達專屬主動訊息聊天室並清除未讀圓點，`assistant_briefing` 通知頻道被停用時於聊天室頂部顯示一次性開啟引導；④ **設定 AI 語意搜尋修復（#405）**：啟用 `json_object` 模式（含 HTTP 400 自動退回）＋剝除 code fence／括號平衡／寬鬆 schema 容錯解析鏈＋失敗嚴格重試一次，區分查無結果與解析錯誤並渲染可點擊直達清單；⑤ **設定關鍵字搜尋補強（#406）**：新增虛詞去除、雙字元切詞、16 組同義詞擴展與加權計分，並為全部 136 個 catalog 項目補齊 3–5 個口語化 `aliases`（單一關鍵字行為不變）；⑥ **主動訊息與預覽不再半句截斷（#407）**：移除 `ProactiveEngine`、`TaskTemplateEngine` 與話題接續錨點之句中 `.take(...)` 硬截斷，`generateBriefingText` 截斷時自動退回完整模板全文，接續列、待辦膠囊與夥伴頁簡報卡均支援點擊展開全文；⑦ **更新下載完成自動彈出安裝頁（#408）**：持久化 `downloadId`＋`version`（版本不符自動清舊檔）、`BroadcastReceiver`＋`Dispatchers.IO` 輪詢 `DownloadManager.query`＋`ON_RESUME` 重查、失敗原因顯示與重試，以及 Android 8+ `canRequestPackageInstalls()` 未知來源安裝權限一步引導。

### v3.7.0 更新重點（versionCode 46，2026-11-23）

- **Phantom Aurora 全域 UI/UX 視覺與動效大改版（#398 / PR #399）**：新增共用動效核心 `PhantomMotion.kt`（Material 3 Emphasized 曲線、`phantomEnter`/`phantomItem` 清單交錯進場與 `LocalPhantomEnterRegistry` 防重播、`phantomPopIn` 彈出進場、`PhantomChevron` 彈性旋轉箭頭、`phantomSheen` 玻璃高光描邊、`PhantomAnimatedCount` 數值滾動與系統動畫縮放＝0 自動靜態退場）；升級 `FluidSwitch`（draw-phase 連續變色、開啟外暈、thumb 動態勾勾描繪）、`LiquidGlassTabBar`（選中圖示 spring 彈跳與色彩過渡）與 `pressableScale`（`Animatable`＋`touchSlop`/捲動提前釋放＋按壓主色邊光）；`PhantomGlassCard`、`PhantomFloatingSurface`、`MessageCard`、`PhantomSearchBarPill` 疊加 `phantomSheen` 高光邊，`PhantomDialog` 與 `EmptyStateView` 套用彈出進場；主畫面 6 分頁套用 `AnimatedContent` 方向感過場（保留 `SaveableStateProvider` 狀態）並完成五大分頁、設定主頁與全部 12 子頁、新手教學 spotlight `spring` 滑移之動效一致化；聊天室氣泡 `animateItem()`、使用者氣泡高光（完整沿用 `8–24dp` 圓角與 `70%–95%` 寬度自訂）、`TypingDotsIndicator` 重寫為 draw-phase 波浪彈跳消除每幀重組；`PhantomMarkdownText` 引用塊／程式碼塊／表格斑馬紋視覺精修；新增全域 Activity 視窗轉場 XML（`styles.xml`＋4 支 `res/anim`）。全輪零新增依賴、`CenterFocusCardStack` 堆疊物理零 diff、37 檔變更全數位於 `ui/` 與 `res/`、零功能回歸。

### v3.5.0 更新重點（versionCode 44，2026-11-22）

- **主動提醒里程碑式去重與回饋擴充（#372、#377 / PR #383，行為變更）**：主動提醒由狀態式改為事件里程碑式去重（同一待辦／日曆事件於 24h 前、提前 N 分鐘、逾期各僅提醒一次，跨日自動清理）並統一匯入 10 分鐘內容雜湊閘門；新增主動訊息「👍 有幫助／👎 打擾」回饋、簡報與主動提醒獨立每日上限，以及提前分鐘數、全天提醒時刻、監控間隔、去重冷卻等可調設定。
- **本機日曆多檢視與帳號分組治理（#373 / PR #383）**：增讀 `VISIBLE`/`SYNC_EVENTS` 預設過濾顯示中日曆，設定頁支援依帳號分組、批次全選與一鍵全選顯示中；記事分頁新增日／週／月檢視切換與未勾選導引卡，空檔查詢附註已勾選／未勾選日曆數。
- **更新提醒 Snooze 狀態機（#374 / PR #383，行為變更）**：移除每日每版一次 gate，改為 `update_snooze_until_ms` 狀態機（有網＋有新版＋未 snooze 開 App 必現對話框與通知；僅點「稍後更新」設 snooze，滑掉不設；單進程失敗最多重試一次）。
- **防詐自動判定收斂與降誤判（#375 / PR #383，行為變更）**：將 `urlPresent`（弱訊號）與 `intelHit`/`cryptoAddressPresent`（強訊號）分離，自動標記 `scam` 須具備情報命中、加密地址或具體詐騙操作訊號；一般連結或與 30 天高信譽寄件者衝突一律降級確認卡；收斂 `hasSuspiciousFeature` 裸詞觸發並支援誤判回饋寫回信譽與加密良性網域 LRU。
- **聊天室訊息右下角時間戳（#376 / PR #383）**：使用者／AI／主動／系統事件氣泡右下角統一顯示 `10sp` `HH:mm`（沿用 `CHAT_TIME_FORMATTER` `Locale.US`），支援大字體與窄寬不裁切及設定開關。
- **AI 日記／每日回顧與長程目標追蹤（#378、#379 / PR #383）**：Room 非破壞性升版 v21 → v22（新增 `diary_days`、`goals`、`goal_progress`）；每日 00:30 零 token SQL 聚合昨日時間軸與月份總覽（選配收合式 AI 註記）；長程目標經結構訊號確認卡拆解週期子待辦、連 3 期未互動自動降頻一級並納入夥伴親密度統計（無 API key 皆 100% 可用）。
- **宣告式 Agentic 能力表與受阻自動改道（#380 / PR #383）**：新增 `IntentCapabilityRegistry` 全枚舉路徑表（`REMINDER`／`MEMO`／`CALENDAR_ADD`／`OPEN_APP`），受阻自動降級安全替代路徑並告知，全敗附白話原因與直達權限中心按鈕，確認卡新增「執行方式」說明。
- **Health Connect 生理數據整合與多廠牌健檢（#381、#382 / PR #383）**：新增 `androidx.health.connect:connect-client:1.1.0`，晨間簡報整合本機睡眠／步數／心率查表式單句規則建議（原始數值不出本機）、統計頁新增 7 日睡眠／步數趨勢圖、補齊 Android 13/14+ Health Connect 權限說明入口（無 GMS 誠實降級），並修復 `BootReceiver` 重開機逾期待辦重複通知問題。

### v3.4.0 更新重點（versionCode 43，2026-11-21）

- **白名單全量比對與統一去重修復（#362 / PR #369）**：`AiRuleDao.activeWhitelistAll()` 解除 `LIMIT 20` 截斷；`WhitelistRuleHelper.upsertWhitelist` 統一 6 處寫入路徑進行去重與停用規則重新啟用；深度分析重查 DB 白名單並標註 provenance，並以 `dedupWhitelistRulesIfNeeded` 完成一次性精確重複列合併。
- **記憶頁篩選 chips 遮擋修復（#363 / PR #369）**：`MemoryTab` 展開模式獨立頂部控制列與兩排全寬 `LazyRow`（`clipToBounds()`＋trailing `contentPadding`），全元件統一 `heightIn(min = 34.dp)` 支援 `fontScale = 1.3` 與 `360dp` 窄螢幕不截字、不重疊。
- **AI 工具調用精度、防幻覺與本地直連查詢（#364 / PR #369）**：`ChatScreenParts` 注入系統能力宣告與單意圖單卡優先序；新增 `LocalDataConnector` 直連本機日曆與 `UsageStatsBridge`；實作 `usage_query` 查詢 `UsageStatsManager` 真實前景時間與啟動次數，未授權誠實引導授權。
- **主動日曆情報引擎（`CalendarInsightEngine`）（#365 / PR #369）**：`CalendarInsightWorker`（預設 60 分鐘，支援 30／60／120／180 分鐘 `UPDATE` 切換）針對未來 48 小時勾選日曆事件僅以「標題＋日期（≤60 字）」查詢外部情報並以 `eventId|beginMs|intelHash` 去重，嚴守 BYOK／省 token／每日上限／獨立勿擾設定。
- **主動訊息聊天室、對話自動記憶擷取與連續性元件（#366 / PR #369）**：專屬主動訊息聊天室預設收合並顯示即時未讀徽章（`99+`，清除全部對話保護不誤刪）；對話自動擷取至多 2 筆非敏感無衝突記憶並附小字提醒與刪除 BottomSheet（敏感／衝突 100% 維持確認卡）；新增頂部未決事項膠囊列（至多 3 顆）與 7 天過期壓縮錨點。
- **雙門檻自動決策層（`DecisionEngine`）與誤判回饋（#367 / PR #369）**：確定性訊號優先，AI 語義單獨上限 `confirmThreshold - 10`（預設 60），集中定義可逆／不可逆矩陣與 provenance 標記；「此判斷有誤」四類回饋一鍵寫回規則庫並失效快取。
- **定時任務模板、宣告式 Action Registry 與狀態修復（#368 / PR #369）**：Room 非破壞性升版 v20 → v21（`task_template`）；`TaskTemplateEngine` 分離零 AI 聚合段與選配潤飾段，於 `AssistantHubActivity` 提供三步嚮導＋所見即所得預覽；新增 `LinkedAppActionRegistry` 單一來源（含 `discord_digest_stats_readonly` 示範連接器）並補齊 `GitHubMonitorWorker` 同步與異常狀態寫入。

### v3.3.0 更新重點（versionCode 42，2026-11-21）

- **記憶與日曆寫入確認開關（#347、#348 / PR #359）**：新增 `memory_write_confirm`（敏感／衝突／刪除仍一律確認）與 `calendar_write_confirm`（關閉時仍檢查撞期），設定頁本機日曆清單支援收合顯示 `N/M` 概覽。
- **統計頁「早晚簡報卡」與每日 AI 摘要移除（#349 / PR #359，行為變更）**：統計頁新增「早晚簡報卡」並將 `stats_summary` 自動遷移為 `stats_briefing`；簡報點閱改以 `messageId` 三路徑去重計分；整體移除每日 AI 摘要功能（排程、推播與專用模型用途全移，每週摘要不受影響且通知改由省 token 模式控管，既有 `ai_summary` 資料保留）。
- **親密度詳情頁與夥伴／記憶頁重排版（#350、#351、#357 / PR #359）**：夥伴頁親密度卡可點擊開啟四階段旅程與積分來源明細，並移除重複個性／詳細度切換、統一頭像膠囊徽章與等高事件卡；記憶頁頂部新增四格圖像統計總覽與「記憶／規則」兩排分層篩選。
- **設定頁「權限與 AI 夥伴」樞紐卡與教學對齊（#354、#355 / PR #359）**：將「權限授予中心」與「AI 夥伴與主動推播」整併為「權限與 AI 夥伴」收合卡（舊深連結自動轉址），主頁 20 步／設定 9 步教學與 `SettingsCatalog` 文案同步白話化。
- **權限、通知授權前檢與安全性強化（#345、#346、#352、#353、#356、#358 / PR #359）**：Manifest 新增 `PACKAGE_USAGE_STATS` 權限；主動通知發送前檢查系統通知授權並提供警告引導；Discord Webhook 改 Gson `JsonObject` 組裝並移除 Log 回應內容；用途模型動態計數與深淺色對比度全面修正。

### v3.2.0 更新重點（versionCode 41，2026-11-20）

- **行程類備忘分流修正（#333 / PR #342，行為變更）**：行程類通知預設改為只建立本機日曆建議卡（`schedule_memo_route = suggest_calendar`），不再重複建立待辦／記事或排程提醒鬧鐘；可於設定頁切回舊行為。
- **v3.0.0–v3.1.0 全功能健檢與修復（#334 / PR #342）**：修正 `CalendarBridge.queryFreeSlotsSummary` 對 `ALL_DAY` 事件 UTC 日期跨日誤判空檔，並修正 `ChatScreen` `memory_view`/`memory_list` 排除敏感記憶 `content` 比對。
- **記憶 × 防詐可視化補全（#335 / PR #342）**：「記憶」分頁新增「防詐免疫」篩選 chip 與專區（直達來源訊息），聊天寫入 `scam_immunity` 後 Snackbar 提供「查看」按鈕一鍵跳轉。
- **通訊錄整合（#336 / PR #342）**：Room 升版至 v20（非破壞性新增 `contact_aliases` 表）；支援本機暱稱綁定、零 token 關係單位匯入與 VIP 信任圈同步白名單，並強化熟人冒充離線偵測（原始通訊錄絕不外送 AI）。
- **主動式推播與個人簡報引擎（#337 / PR #342）**：早 08:45／晚 22:25 個人簡報、15 分鐘零 token SHA-256 變動偵測主動推播（獨立勿擾與每日上限）、「我對你的瞭解」畫像卡，以及選配 `ACCESS_COARSE_LOCATION` 一次性地區反解與 BYOK 外部搜尋端點。
- **AI 夥伴分頁（#338 / PR #342）**：新增第 6 分頁「AI 夥伴」（顯示自訂助手名稱與頭像），提供陪伴天數、親密度公式、近 7 天動態卡與畫像卡。
- **連結應用程式中心與跨 App 動作卡（#339 / PR #342）**：新增 `LinkedAppsActivity`（6 項本機能力卡、GitHub PAT 15 分鐘輪詢與 Discord Webhook 加密儲存）及「確認後執行」跨 App 動作卡（郵件、導航、鬧鐘、開啟 App）。
- **設定重整與 20/10 步教學（#340、#341 / PR #342）**：新增 `PermissionHubActivity`（集中 7 項權限，Shizuku 保留獨立卡片）與 `AssistantHubActivity`；主頁教學重寫為 20 步、設定教學擴充為 10 步。

### v3.1.0 更新重點（versionCode 40，2026-11-20）

- **AI 回應相容修正與思考鏈折疊顯示（#326 / PR #330）**：多型回應 chunk 清單／純字串全面相容（修正 `zai-glm-5-3` 等模型崩潰）、思考鏈回應新增預設收合「推演思路」折疊區、移除圖片視覺誤判閘門（附加圖片一律送出、錯誤如實回報）、Room v18 → v19 非破壞性遷移保留歷史對話。
- **記憶頁 UI 優化（#327 / PR #330）**：篩選標籤列支援收合與狀態持久化、頂部搜尋框改為按鈕觸發展開並自動聚焦、頂部列排版與 48dp 觸控目標統一。
- **記憶 × 防詐交叉檢查與防詐免疫（#328 / PR #330）**：新增 `ContactContext` 通訊錄熟人冒充離線偵測（權限拒絕自動降級純記憶比對）、情境矛盾交叉檢查注入風險分析、`scam_immunity` 防詐免疫記憶分類（警示中心一鍵加入＋每週自動彙總，一律經確認卡才寫入）、分析快取納入記憶／通訊錄版本戳記，並提供總開關（預設開啟；關閉時行為與 v3.0.0 完全一致）。
- **本機日曆整合（#329 / PR #330）**：設定頁勾選開放日曆（預設全不勾）、AI 助手查詢本機空檔、`calendar_add` 確認卡含撞期警示、行程類通知於詳情頁提供「📅 加入日曆」建議卡，無寫入權限時走 `ACTION_INSERT` 系統日曆預填 fallback。

### v3.0.0 更新重點（versionCode 39，2026-11-20）

- **AI 記憶庫核心引擎（#314 / PR #319）**：長短期分層、衝突確認卡、隱私分級、批次忘記；舊自動備忘錄／規則整併記憶庫管理。
- **記憶分頁（#315）與自訂 AI 助手（名字／個性／詳細度／頭像）**、省 token 快捷開關（雙模式預算：正常 8 筆／省 token 4 筆）。
- **Safe Browsing key 改 BYOK（#316 / PR #318）**：移除 BuildConfig／`local.properties` fallback，改設定頁加密輸入。
- **教學 auto-skip 雙條件與 Safe Browsing 卡文案修正（#316 / PR #320）**。

### v2.20.0 更新重點（versionCode 38）

- **記事頁待辦事項展開區可捲動與高度上限（#310 / PR #311）**：`NotesTab` 待辦展開區上限改為 `BoxWithConstraints` 可用高度之 2/3（短清單維持依內容 wrap-content）；篩選膠囊列固定於頂部、清單段支援卡內上下捲動並於切換篩選時重置回頂部，解決待辦過多擠壓下方記事區的問題（收合精簡顯示維持不變）。

### v2.19.0 更新重點（versionCode 37）

- **中央堆疊 v6 首尾邊界填滿與短清單模式（#298 / PR #302）**：`CenterFocusCardStack` 新增首尾邊界填滿補償（`pAbove`／`pBelow` 對稱 fill pitch）與短清單置頂緊貼排列，`edgeGap` 調整為 `6dp`（首卡距我的最愛、尾卡距底部導覽列皆為 `6dp`）。
- **空間優化與資料庫維護（#299 / PR #303、#306 / PR #307）**：`release` 建置啟用 R8 程式碼縮減與資源壓縮大幅縮小 APK 體積；Room 升版至 v17（`MIGRATION_16_17`）補齊查詢索引；新增定期 `VACUUM` 維護、頭像快取上限 300 筆與 `intel_cache` 過期清理；設定頁「資料管理」新增儲存空間明細與「立即壓縮資料庫」按鈕（並修正已排隊時的等待邏輯）。
- **電量與穩定性優化（#300 / PR #304）**：放寬 KeepAlive（15→45 分鐘）與 Shizuku（300→600 秒）輪詢、Heartbeat 改採 `KEEP` 防抖動並於關閉常駐時保留低頻重連檢查、強化 `StatsForegroundService` 啟動防護與 rescue 指數退避、熄屏查詢早退及熱路徑 Regex／時間格式常數化。
- **介面元件拆分（#301 / PR #305）**：將 `MainActivity` 與 `ChatScreen` 拆分為獨立分頁／元件檔案（`MessagesTab.kt`、`NotesTab.kt`、`StatsTab.kt`、`TodoSection.kt`、`ChatScreenParts.kt`），零行為變更。

### v2.18.0 更新重點（versionCode 36）

- **移除內建個人化預設，App 完全通用化（#292 / PR #293）**：「我的名稱」不再預帶任何預設顯示名稱；不再自動寫入個人化 AI 規則，並於升級後一次性清除舊版自動寫入的電信白名單與購物標籤規則（使用者自建規則不受影響）；標籤／白名單範例文案與 AI 分類 prompt 去除特定品牌引用。
- **設定頁衝突文案修正（#292 / PR #293）**：快速設定「AI 風險分析」副標改為動態顯示觸發字數門檻（可調 1–30 字），不再寫死 10 字元；「每日 AI 額度上限」與 AI 助手可調範圍對齊（0–100 次、步進 10 次），單位統一為「次」且非預設值會顯示為可選項；省 token 模式生效時，受暫停影響的開關副標明確標示。

### v2.17.0 更新重點（versionCode 35）

- **主頁／設定頁新手教學修復、各廠牌受限引導與 Shizuku 一鍵授權（#288 / PR #289）**：
  - Android 13+ 等系統通知權限彈窗結束後再顯示新手教學，不再重疊；空資料庫時將四個訊息操作步驟合併為單一提示，依步驟自動切換分頁並補齊設定齒輪、自動記事規則、新增待辦與置中膠囊搜尋把手高亮。
  - 權限引導卡新增「開關變灰／無法開啟？」疑難排解（含小米／Redmi／POCO、Samsung 自動封鎖程式、OPPO／realme／OnePlus、vivo／iQOO 四廠牌解鎖說明與「前往應用程式資訊」按鈕），並支援直達本 App 專屬通知存取詳情頁（Android 11+，不支援則自動改開總清單）及 Shizuku 已連線時一鍵授權通知存取權限。
  - 設定頁教學修復為完整 8 步導覽，期間自動清空搜尋並正確展開個人化／Shizuku 卡片與捲動定位白名單卡片。
- **隱私權政策與免責聲明（#284 / PR #286）**：新增 `PRIVACY.md` 與 `DISCLAIMER.md`（純文件新增）。
- **GPL-3.0 開源授權（#285 / PR #287）**：新增根目錄 `LICENSE`（GNU GPL v3.0 官方全文）與 README 授權說明段落。

### v2.16.0 更新重點（versionCode 34）

- **中央堆疊 v5 微調（#280 / PR #281）**：
  - **上下留白緊湊化**：`CenterFocusCardStack` 新增 `edgeGap = 4dp`（取代 `8dp`）延伸上下可視範圍；訊息首頁 `CENTER` 模式外層區塊間距 `12dp → 8dp`，記事頁 `CENTER` 模式標題列下間距 `8dp → 4dp`（非 `CENTER` 模式與共用外框維持不變）。
  - **阻尼彈簧吸附與切換動畫絲滑化**：改採官方 `snapFlingBehavior` 搭配 `spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)`；於 `graphicsLayer` draw phase 內加入 `FastOutSlowInEasing` 近焦縮放緩衝（`0.94f → 1.0f`，`absU = 1` 連續銜接 `0.04f` 遞減至下限 `0.82f`）與焦點卡 `6dp` 漸變陰影，並保留 `translationY` 線性跟手與 v3/v4 全數幾何不變量。

### v2.15.0 更新重點（versionCode 33）

- **中央堆疊 v4 修正（#276 / PR #277）**：
  - **修復滑動時焦點上方卡片被裁切／快甩後缺繪**：改採對稱 `contentPadding`（`leading >= 3 * exposure` 且與 `trailing` 互補，總捲動區間 `(n - 1) * 40dp` 與吸附手感不變），確保焦點上方 2~3 張卡片原生落入可見範圍穩定組合繪製、露出頂帶可辨識 App 圖示與名稱；唯一 `clipToBounds` 僅保留於最外層堆疊容器（我的最愛下緣至底部導覽上緣）。
  - **初始貼頂與線性過渡**：`firstVisibleItemIndex == 0` 且 `offset == 0` 時錨點 `8dp` 貼頂零留白；頭尾過渡由 `smoothstep` 改為線性 `lerp`（以 `zone` 正規化），連續單調無布林切換。
  - **記事短清單幾何修正**：過渡區寬度改為動態 `zone = min(1.5f, (n - 1) / 2f)`，根治 `n <= 4` 時頭尾過渡區重疊造成第 2 則記事沉底；記事頁續用共用堆疊元件、高度動態計算。
  - **待辦事項到期副標單行**：`NotesTodoRow` 到期資訊加上 `maxLines = 1`、`softWrap = false` 與 `Ellipsis`，不再被右側按鈕擠成兩行（展開／收合兩狀態同時生效）。

### v2.14.0 更新重點（versionCode 32）

- **中央堆疊卡片緊湊化（#270 / PR #272）**：單卡高度鎖定 `92dp`（大字體上限 `104dp`）、`12dp` 內距三段式佈局消除卡片內空黑底，堆疊錯位與 `edgeFade` 改為 `40dp`、`scaleStep = 0.04f`（縮放下限 `0.82f`），底部 `40dp` 露出帶顯示 App 名稱＋未讀數／記事標籤，訊息頁與記事頁視覺完全一致。
- **自動檢查更新改為每次開啟 App 一次（#271 / PR #273）**：移除每日檢查 gate 與彈窗去重，開啟開關且網路可用時每次啟動皆檢查 GitHub 最新版並提示；通知欄維持每日每版本最多一則，同一行程旋轉／返回不重複請求。

### v2.13.0 更新重點（versionCode 31）

- **訊息／記事中央堆疊 v3（#263 / PR #264）**：改採 `LazyColumn`＋`SnapPosition.Start` 統一吸附支援連續慣性跨多張快滑，頂／中／底三態錨點 smoothstep 連續 lerp 插值根治瞬移，搭配動態層數、`8dp` 邊距與動態 `contentPadding` 達成零留白全卡填滿，露出 `44dp`、`scaleStep = 0.035f`、viewport 內維持不透明。
- **SMS 雙路徑重複記錄修正（PR #268）**：`SmsAutoReplyReceiver` 專職自動回覆不再寫入資料庫，簡訊記錄統一由通知攔截路徑單一記錄與去重（issue #106 待實機驗證後關閉）。

### v2.12.0 更新重點（versionCode 30）

- **訊息頁中央置頂堆疊重構（#249）**：重構 `CenterFocusCardStack`（v2），固定卡高 `112dp`、相鄰卡 `24dp` 等距露出與 `0.06f` 縮放遞減；初始狀態貼齊頂部 `10dp`、滑動過半平滑過渡至垂直置中，全數以 `graphicsLayer` 計算不觸發重組。
- **記事頁共用堆疊並移除「往下露頂部」（#250）**：記事「中央堆疊」改共用新版 `CenterFocusCardStack`（固定 `112dp`、內文 2 行省略、角上小圖示操作）；移除 `PEEK_TOP`（舊設定 `peek_top` 自動遷移為 `CENTER`），設定頁與 `SettingsCatalog` 同步精簡為「一般條列／中央堆疊」兩項。
- **AI 自動待辦智慧時間推斷與提前 30 分鐘提醒（#252）**：`buildSystemPrompt` 注入現在時間推算 `todo_due`，結合 `ClientTimeParser`（含跨年 `MM/dd` 進位）與 `+2h` fallback，事件時間一律提前 30 分鐘排程提醒（含 duplicate 更新重排）。
- **記事置頂保留原時間（#251）**：`onTogglePinNote` 僅更新 `pinned` 欄位，不再覆寫 `updatedAt`。
- **深度分析／每日摘要模型 chip 分用途綁定同步（#253）**：詳情頁與統計頁 chip 改寫 `setPurposeProfileId`、讀 `getProfileForPurpose`，隨 `PhantomSettingsBus` 與 `ON_RESUME` 即時同步，並補上專屬用途標籤前綴。
- **每日與每週摘要準時性補強（#254）**：新增 `maybeCatchUp` 錯過補跑、每日失敗 `Result.retry()`（最多 3 次、backoff 10 分鐘）、週摘要改以排程時刻錨定週區間，並放寬週摘要限制為 `NetworkType.CONNECTED`。

### v2.11.0 更新重點（versionCode 29）

- **首頁卡片排列模式重寫為中央聚焦垂直滾輪堆疊（#240）**：移除壞掉的「往下露頂部／往上露底部」模式，改為 `VerticalPager` 中央聚焦滾輪（焦點卡 scale=1.0/alpha=1.0 置中 zIndex 最高、上下各最多 2 張對稱縮小堆疊 0.92f/0.84f、更遠自動隱藏、鬆手吸附、首末卡可至正中央），設定頁精簡為「一般條列／中央堆疊」兩選項（舊設定自動遷移）。
- **記事分頁大改版（#241）**：待辦區可收合（預設收合、狀態持久化，收合只顯示最快到期未逾期的 1 則未完成待辦）；待辦智慧排序（未逾期近者在上 → 已逾期自動下移 → 已完成殿後）；記事卡片新增「一般條列／往下露頂部（撲克牌式）／中央堆疊」三種排列模式（獨立設定於個人化）。
- **設定頁子區塊可收合（#242）**：「個別 App 節流覆寫」與「自訂常用指令」可收合且狀態獨立持久化，大分類卡片收合重開不會重置。
- **浮動層直角黑底根除（#243）**：Dialog window 與 `ModalBottomSheet` 外層一律全透明（`PhantomDialog`、`containerColor = Transparent`），深色不透明底只畫在圓角容器層，圓角框線外 100% 透出桌布。
- **介面與設定同步、摘要準時（#244, #246）**：排列模式與收合狀態經 `PhantomSettingsBus` 即時生效；每日／本週摘要改 `OneTimeWorkRequest` 自我重新排程，修改時間後準時觸發不再漂移。
- **首頁篩選標籤列可收合成單行＋AI 可調新設定（#245, #247）**：篩選 chips 收合不換行；AI 助手／設定搜尋新增排列模式等項目，AI 可對話直接切換首頁與記事排列模式。

### v2.10.0 更新重點（versionCode 28）

- **首頁卡片排列模式三選一（#230）**：取代舊版開關與 `StackedAppCard`，提供「一般條列」、「往下露頂部（撲克牌式）」與「往上露底部（資料夾式）」三模式（含列表層 `SnapFlingBehavior` 吸附與重疊防透字加強），同步接入 `SettingsCatalog`（`personal_home_card_layout`）與 `PhantomSettingsBus`。
- **寄件者頭像擷取與顯示（#231）**：Room 升版至 v16（`MIGRATION_15_16`）新增 `sender_avatars` 表，擷取通知 `Person` 頭像降採樣壓縮入庫（30 天自動清理），於對話列表（40dp）與非己方對話氣泡（28dp）顯示（頭像 → App 圖示 → 首字母彩色圓形）。
- **自己回覆抑制與自我身分偵測（#237）**：新增 `own_reply_echo` 表抑制 3 分鐘內回覆 echo 重複入庫，並於設定頁「訊息與通知」新增「我的名稱」（`notif_my_display_names`）編輯入口，群組通知寄件者命中時自動標記為己方回覆。
- **浮動層、手勢與搜尋細節修復（#229, #232, #233, #234）**：修正抽屜與規則面板圓角安全間距、統一遮罩為 `0.45f`；記事搜尋膠囊緊湊置左；抽屜拖曳加入 `VelocityTracker` 速度投影與 0.35 門檻；強化自動待辦相似去重（`dice ≥ 0.9`）。
- **水滴分頁欄與全域流暢度優化（#235, #236）**：`LiquidGlassTabBar` 重用 `Path`/`Brush`、調快水滴彈簧與同步拖曳；`MainActivity` 以 `rememberSaveableStateHolder` 保留分頁狀態，完成全庫流暢度稽核。

### v2.9.0 更新重點（versionCode 27）

- **浮動層渲染與遮罩修復（#222）**：新增 `PhantomFloatingSurface`（不使用 blur、`.clip(shape)` 先於 `.background()`、深色 `0xF012151E`／淺色 `0.95f` 實底＋`0.65f` scrim），修正歷史對話抽屜與記事規則面板圓角溢出與底層透字問題，頁面內玻璃卡片行為維持不變。
- **AI 歷史對話抽屜拖曳效能修復（#223）**：抽屜位移與 scrim 透明度延遲至 layout／draw 階段讀取，搭配 `derivedStateOf` 與 `snapshotFlow` 收集，拖曳全程零重組風暴。
- **訊息卡片堆疊可見性強化（#224）**：`StackedAppCard` 層色改用 `lerp(surfaceVariant, onSurface, 0.25f)`、alpha 調升至 `0.55f`／`0.35f`、scale 調整為 `0.96f`／`0.92f` 並於層 1 加上 1dp `outlineVariant` 描邊。
- **AI 變更設定即時生效（#225）**：新增 `PhantomSettingsBus`，透過 AI 對話或設定搜尋修改任何支援項目後，主頁、統計、AI 助手與設定頁 UI 立即同步更新。
- **記事規則 keyword-only「不再記事」語意化（#226）**：`saveAutoMemoIfPresent` 僅對含 App／寄件者條件的 `never` 規則套用字面閘門，keyword-only `never` 交由 AI 依場景語意判斷（`memoV4`），避免誤殺同字面不同服務的訊息。

### v2.8.0 更新重點（versionCode 26）

- **訊息卡片堆疊效果（可開關）（#212）**：`StackedAppCard` 依訊息數於卡片後方呈現 0/1/2 層立體堆疊（含捲動微幅視差與點擊收合回饋），設定頁「個人化」支援切換開關。
- **我的最愛角標與 IME 修復（#213, #214）**：修正我的最愛橫列角標與 `HomeSortOrder.UNREAD` 改用真實未讀數（0 隱藏、>99 顯示 `99+`）；修復 AI 助手輸入列被鍵盤遮擋（單一 `imePadding`＋貼底自動捲動）。
- **AI 助手側邊歷史抽屜與接續對話設定（#215, #216）**：歷史會話改為左側滑出玻璃抽屜（支援手勢拖曳與 `BackHandler`）；新增 `ai_chat_tab_behavior` 設定可選擇開啟新對話或接續上次對話。
- **記事規則懸浮視窗與 AI 語意比對（#217）**：記事規則改由右上角 `Tune` 按鈕展開懸浮玻璃面板，`AiClient` 支援關鍵字語意同類比對（`memoV3`）。
- **統計頁自訂卡片、每日 App 排行榜與 4 張新圖表（#218）**：新增「今日 App 訊息量排行榜 Top 5」與 4 張純 Canvas 新圖表，設定頁新增「統計」分類自訂 12 張統計卡可見性。
- **記事桌面小工具（#219）**：新增 2x2「幻訊記事」小工具（`NotesWidgetProvider`，置頂優先最多 5 筆、點擊開記事分頁），全庫寫入點即時差異同步。

### v2.7.0 更新重點（versionCode 25）

- **自動記事／待辦規則與來源回溯（#203, #204）**：Room 升版至 v15（`MIGRATION_14_15`）新增 `memo_rules` 表與 `MemoRuleEngine`（`never > todo > note`）；「記事」分頁新增「自動記事規則」管理卡（支援自然語言 AI 解析＋確認卡與手動新增），`ChatScreen` `add_rule` 支援 `kind="memo"`；首頁新增「已記事／已待辦」篩選標籤與小圖示，待辦列可一鍵跳轉來源訊息，`MessageDetailActivity` 與自動記事長按提供三範圍「不再自動記事／待辦」。
- **頂部下拉搜尋把手與首頁固定表頭（#205, #206）**：`MessagesHomeTab` 重構為固定表頭（統計卡／分類篩選／我的最愛）＋獨立捲動訊息清單；移除舊搜尋膠囊，將重新整理與設定按鈕移入統計卡右側欄，並以 22dp `PhantomPullSearchHandle`（支援下拉手勢與點擊）開啟全螢幕搜尋。
- **對話頁 IME 修復與自己回覆右側氣泡（#207）**：補齊 `windowSoftInputMode="adjustResize"` 解決鍵盤雙重上推，三處回覆入口寫入 `isOwn = true` 並於對話頁渲染右側 primary 淡色氣泡；所有統計、未讀、ScamCenter、摘要與去重查詢嚴格排除 `isOwn`。
- **搜尋頁與全庫主題對比度修復（#208）**：清除全庫 `Color.Gray`／`Color.Red` 主題盲色、顯式指定內文 `onSurface`、`PhantomFullScreenSearchOverlay` 改為完全不透明底色，並設定 `CardBgMode.TRANSPARENT` 滑桿最低 15%（fallback ≥ 0.35）。
- **18 步新手教學與全域 OEM 健檢（#209, #210）**：主頁教學擴充至 18 步涵蓋下拉搜尋、快速回覆、記事規則與來源回溯（舊用戶不重跳）；補齊全專案文字輸入 Activity `adjustResize` 與 OEM／insets 稽核。

### v2.6.0 更新重點（versionCode 24）

- **對話詳情頁底部快速回覆（#197）**：`ConversationScreen` 底部新增常駐回覆輸入列（自動避讓鍵盤與捲動、多選時隱藏），`sendReplyToNotification` 支援以 `conversationKey` 精準定位 `MessagingStyle` 對話並銜接 `ReplyProxyManager` 通知欄連續回覆與 SMS fallback。
- **回覆鏈與 AI 動作全面防崩（#196）**：`ReplyProxyManager` `RemoteInput` `PendingIntent` 修正為 `FLAG_MUTABLE`，補齊 `ReplyProxyReceiver`、`ReplyProxyManager`、`ChatScreen` 動作協程與空集合 `firstOrNull()` 防禦，並統一輸出 `PhantomReply`／`PhantomChatAction` logcat。
- **獨立白名單管理頁與收合持久化（#199）**：新增 `WhitelistManagementActivity`（`exported="false"`）承載分組白名單管理，設定頁精簡為入口按鈕；`PhantomPrefs` 持久化分組收合狀態並連動 `SettingsCatalog` 搜尋導覽。
- **待辦事項 Flow 即時同步（#198）**：`TodoDao.observeAll()` 讓記事分頁與 `ChatScreen` 待辦選單即時響應桌面小工具變更，`MessageDetailActivity` 補齊 `ON_RESUME` 刷新 `remindAt`。
- **搜尋與透明背景 UI 優化（#193, #194, #195）**：修正 `ConversationScreen` 與 `AppThreadsScreen` 全螢幕搜尋覆蓋層被頂欄遮擋；`PhantomSearchBarPill` 統一為 36dp 迷你玻璃膠囊（觸控目標 ≥48dp）；移除 `SwipeToRevealRow` 不透明滿版底色讓自訂背景完整透出。
- **全域依賴、AGP 9 與 targetSdk 36 升級（#200）**：升級 Gradle `9.9.1`、AGP `9.5.1`（內建 Kotlin）、Kotlin `2.4.21`、Compose BOM `2026.09.00`、Room `2.8.5`（kapt → KSP `2.3.14`）、Retrofit `3.0.0`、WorkManager `2.12.0`、`compileSdk` `37`／`targetSdk` `36`，並遷移 `Glass.kt` 至 Haze `2.1.0` `hazeBlur` API。

### v2.5.0 更新重點（versionCode 23）

- **通知欄連續快速回覆（#183）**：新增 `ReplyProxyManager`／`ReplyProxyReceiver`（`exported="false"`）與 `NotifIds` `120000..129999` 區段，`ChatScreen` 回覆成功後啟動 `MessagingStyle` 代理通知（歷史上限 8 則、Session LRU 上限 8 組、支援 SMS fallback 與 `AlarmManager` 閒置逾時 15/30/60/關閉），設定頁與 `SettingsCatalog` 同步新增開關與逾時選項。
- **對話頁玻璃黑底修復（#184）**：修正 `Components.kt` `PhantomGlassCard` 與 `phantomGlassSurface` 裁切順序，統一於 `glassSurface(...)` 之後執行 `.clip(shape)`（維持 `ui/components/` 零改動）。
- **AI 送出前重複通知折疊（#185）**：新增純函式 `NotificationFoldUtil`（24h 鏈式窗、Dice ≥ 0.85、高風險與關鍵數字/URL 不合併），套用至 Chat RAG、每日摘要、每週摘要與深度分析歷史。
- **每日自動檢查更新（#186）**：`MainActivity` `ON_RESUME` 每日一次靜默檢查，`UpdateAvailable` 發送每版本每日一次通知（`NotifIds.UPDATE_AVAILABLE = 1007`）＋彈窗提醒，關於卡新增 `update_auto_check_enabled` 開關並進 `SettingsCatalog`。
- **自動記事去重與待辦智慧分流（#187, #188）**：同 App 7 天內相似自動記事（Dice ≥ 0.9）改為更新且不覆蓋 `pinned`；`AiClient` 擴充 `memo_type`（`note|todo`）與 `todo_due`，`todo` 經去重後建立 `TodoEntity` 並排程提醒＋刷新小工具，並抽出共用 `ClientTimeParser`。
- **桌面小工具通知同步與按鈕放大（#189）**：完成或刪除待辦／訊息提醒時同步清除已發出通知，`widget_reminder.xml` 按鈕放大至 `16sp` bold＋`minHeight="32dp"`＋ripple 回饋，`reminder_widget_info` `minHeight` 提升至 `72dp`。

### v2.4.0 更新重點（versionCode 22）

- **AI 省 Token 五合一與視覺修復（#174, #173）**：實作 `prompt_cache_key` 與位元組級靜態前綴快取、對話增量情境摘要、雙模型信心路由（預設先打 `mistral-small-2603`，僅 30~70 分區間升級且不重複扣額度）、圖片尺寸動態估算與每日／週報去重上限；修復 `mistral-small-2603` 誤判為純文字模型的問題。
- **卡片黑底溢出修復與底色模式（#175）**：統一 `PhantomGlassCard` 單一裁切形狀與修正 API < S 雙層疊加問題，並於外觀區新增「卡片底色模式」（跟隨玻璃／透明滑桿／深色底）。
- **待辦強化（#176）**：Room 升版至 v14（`MIGRATION_13_14`），新增 `completedAt`、優先級（低/中/高）、備註與每日/每週重複派生；記事分頁支援篩選、智慧排序、手動新增與完整編輯表單。
- **白名單修復與管理 UI（#177）**：Chat 系統提示常設注入白名單（過長依 App 壓縮）、新增 `whitelist_delete` 動作與設定頁分組白名單管理卡，全路徑寫入即時失效規則快取。
- **AI 功能全面健檢（#178）**：補齊 `CHAT`（待辦/備忘錄/VIP/靜音/關鍵字）與 `DEEP_ANALYSIS`（寄件者歷史）上下文，統一所有 AI 寫入動作之 DB 回讀驗證。
- **桌面小工具互動（#179）**：2x1 待辦小工具支援 per-item「完成 ✓ / 編輯 ✎ / 刪除 ✕」按鈕，採獨立 `PendingIntent` 與 `goAsync()` 即時同步刷新。
- **統一下拉式搜尋（#180）**：新增自製玻璃搜尋槓與全螢幕搜尋 overlay（支援即時過濾與 AI 語意搜尋入口），整合至訊息、對話、記事與分類頁。
- **教學修復與擴充（#181）**：移除連鎖自動跳步、字卡依洞區動態上下避讓，主頁擴充至 15 步（含 `SEARCH_PILL`）、設定頁擴充至 8 步導覽。

### v2.3.1 更新重點（versionCode 21）

- **AI 標籤分發解析容錯與失敗通知改進（#169）**：`parseAnalysisJson`／`parseBatchAnalysisJson` 支援圍欄剝除、平衡括號多物件擷取與寬容預處理；單則分析支援 OpenAI 相容 `json_object` 與一次 corrective 重試（不重扣額度），測試按鈕顯示原始回應前 200 字診斷；失敗通知改採 24 小時時間窗（5 次觸發、6 小時冷卻）並新增 `ai_tag_failure_notif_enabled` 開關。
- **訊息背景頁黑底修復與選圖備援（#170）**：`PhantomBackgroundBox` 於無圖／載入中／解碼失敗時明確繪製主題底色（不再透空露黑）；`SettingsActivity` 選圖狀態改用 `rememberSaveable` 防旋轉遺失，Photo Picker 失敗時提供檔案選擇器（`OpenDocument`）備援。
- **新手教學擴充與健檢（#171）**：主頁教學擴充為 14 步（涵蓋記事／自動備忘錄、統計摘要／離線佇列、待辦提醒／批次管理、三層背景外觀與分用途模型），同步校對 4 Tab 文案、設定頁 6 步導覽與重播步驟重置（舊用戶不被重新打擾）。

### v2.3.0 更新重點（versionCode 20）

- **圖片視覺上傳與背景選圖修復（#162）**：`ChatScreen` 圖片改採 bounds-first `inSampleSize` 降採樣（≤ 1024px，OOM 自動加倍重試）與誠實錯誤分類、更新 Groq/DeepSeek 視覺模型支援清單；並修復 `BackgroundImageHelper` `inJustDecodeBounds` 回傳 `null` 導致三層背景選圖失敗的 bug。
- **分用途 AI 模型路由（#163）**：6 種 AI 用途可各自綁定獨立 Profile 或跟隨預設（自動備忘錄跟隨訊息分析模型），於 `ApiKeySettingsActivity` 提供 6 列下拉設定與測試連線；`ChatScreen` 頂部切換器僅切換對話用途模型。
- **離線 AI 補分析佇列（#164）**：Room 升版至 v13（`MIGRATION_12_13`）新增 `pending_ai_analysis` 表，暫時性失敗自動入隊並由 WorkManager `PendingAiAnalysisWorker` 於連網後指數退避補分析（每輪最多 20 則、上限 3 次），搭配低干擾通知與訊息頁頂部控制橫幅。
- **「記事」分頁與 AI 自動備忘錄（#166）**：以「記事」取代「詐騙警示」Tab（整合待辦事項與手動／自動記事 CRUD、釘選與色標，詐騙警示中心改由訊息頁／統計頁進入），並支援 `ai_auto_memo_enabled` 自動摘要寫入備忘錄。
- **體驗微調與門檻設定（#165, #167）**：將「清除已讀」入口由訊息頁移至設定頁「訊息與通知」卡片；新增 AI 分析字數門檻滑桿（1–30 字，預設 10）與 `SettingsCatalog` 全目錄連動。

### v2.2.0 更新重點（versionCode 19）

- **關於卡更新檢查與 APK 下載安裝（#157）**：作者更新為「幻神（Hwanshen）」，新增手動「檢查更新」（GitHub REST API，10 秒逾時）與 `DownloadManager` ＋ `FileProvider` ＋ `ACTION_VIEW` 系統安裝器整合（含 `REQUEST_INSTALL_PACKAGES` 與未知來源授權引導）。
- **個人化多層背景圖與視覺滑桿（#159）**：支援全域／AI 聊天／對話頁三層背景圖（`PickVisualMedia` ＋ JPEG 85 降採樣與單張 `LruCache`）、背景暗色遮罩強度（0–100%，預設日間 35%／夜間 60%）與玻璃卡片強度滑桿。
- **AI 助手操作能力擴充（#160）**：新增 `search_messages`（內嵌可點擊結果卡）、`todo_list`／`todo_done`／`todo_delete`／`todo_edit`、`vip_pin`、`mute_app`、`mark_read`（保留詐騙紀錄）與 `export_messages`（複用 `MessageExporter` CSV 匯出），mutating 動作一律經確認卡執行。
- **全域程式碼健檢修正（#158）**：修復跨會話 `pendingTodoQueue` 阻塞、補齊 `user_tag_rules` 30 秒 TTL 快取與全路徑規則寫入即時失效、修正 `tag_rule` 缺失標籤改為誠實跳過、預編譯熱路徑 Regex 並完成多項排程與快取驗證。

### v2.1.0 更新重點（versionCode 18）

- **AI 自然語言待辦提醒與小工具整合（#143）**：Room 升版至 v12（`MIGRATION_11_12`）新增 `todos` 資料表；AI 助手支援 `reminder_add` 與確定性時間解析 fallback、待辦確認卡與管理 Sheet，透過 `AlarmManager.setAndAllowWhileIdle` 排程與 `BootReceiver` 開機重排，並同步刷新 `ReminderWidgetProvider`。
- **對話頁／對話清單多選批量管理（#144）**：補齊 `ConversationScreen` 與 `AppThreadsScreen` 多選批次操作（已讀／星標／刪除，上限 100 則），選取模式停用左滑與詳情誤觸，批次刪除嚴格保護詐騙訊息並清理 `message_tags` cross-ref。
- **AI 助手與常用指令體驗改善（#142, #145, #146）**：進入 AI 助手預設開啟新對話並於歷史抽屜提供「清除全部對話」；AI 分頁改用獨立內嵌 `SnackbarHost` 不再遮擋輸入框；常用指令新增「跟隨預設」模式並動態解析預設行為。
- **群組對話分割、關於區塊與發佈流程（#140, #141, #112）**：Room v11（`MIGRATION_10_11`）導入 `MessagingStyle` `conversationKey` 修復 LINE 群組對話分割；設定頁最底新增「關於」區塊（runtime 自動同步版本號）；`release.yml` 改為抽取 `CHANGELOG.md` 版本段落搭配自動 PR 清單。

### v2.0.0 更新重點（versionCode 17）

- **AI 標籤記憶與降級防護（#131, #132）**：Room 升版至 v10（`MIGRATION_9_10`），新增確定性零 token `TagRuleEngine` 與 `user_rule` 標籤記憶（遵守 `manual > user_rule > ai_rules > auto > App 分類`）；AI 標籤失敗自動降回 App 分類並採雙重防吵低優先通知，設定頁新增「AI 標籤分發測試」。
- **AI 助手可靠度與 Token 最佳化（#127–#130）**：修復裸 JSON 與數值設定執行鏈、Groq 免費方案 TPM 預算控管與 413/429 多階段自動降級、條件式設定目錄載入、RAG 去重與長對話摘要壓縮。
- **多模態輸入、版面精簡與自訂導覽（#133–#135）**：輸入區新增「常用指令（直接執行／預輸入）」與「圖片上傳視覺檢視」雙功能選單；切到 AI 分頁平滑隱藏頂部標題列並精簡輸入框；外觀設定新增底部分頁欄「標準／縮小／自動隱藏」三模式。
- **三款非統計桌面小工具與全域最佳化（#136–#138）**：新增「最新 AI 摘要 (2x2)」、「高風險處置 (2x2)」與「待辦提醒 (2x1)」桌面小工具；完成通知熱路徑快取、圖示 `LruCache` 統一與 M3 版面 8dp/48dp 一致性巡檢。

### v1.13.1 更新重點（versionCode 16）

- **AI 對話穩定性與多動作修正（#122, #123）**：`AiClient` 新增 `HTTP 413` 專屬錯誤訊息與 chat 單次閱讀上限減半自動重試（最低 25 則），深度分析遇 `413` 不消耗每日額度；`ChatScreen` 支援多動作解析、大宗白名單合併勾選確認卡，無效動作誠實回報且不回退顯示 raw JSON。
- **對話數值調整與設定導覽（#124, #125）**：新增 `setting_adjust` 滑桿確認卡直接調整單次閱讀上限、每日 AI 額度與同 key 節流秒數；新增 `open_setting` 導覽至設定頁目標卡片，system prompt 補齊全量設定清單摘要杜絕編造 UI 路徑，並修正未知設定 fallback 與非開關候選按鈕。

### v1.13.0 更新重點（versionCode 15）

- **AI 對話全方位強化（#114, #116, #118, #119）**：`PhantomMarkdownText` 補全表格（含對齊與橫向捲動）、斜體、刪除線、連結、引用、分隔線、任務清單與巢狀清單；新增 AI 助手單次閱讀訊息數滑桿（50–800，預設 400，省 token 模式自動降為 100）；支援透過對話直接切換 boolean 設定開關（共用 `SettingsCatalog`）；RAG 預設改為今天＋昨天並支援寄件者過濾，新增通知快速回覆、開啟原 App、刪除訊息（保護詐騙證據）與貼標籤確認卡操作。
- **結構化白名單與模型預設更新（#115, #117）**：更新各家 AI Provider 預設模型清單（Mistral `mistral-small-2603`、OpenAI `gpt-6.1-terra`、Groq `openai/gpt-oss-120b`、Gemini `gemini-4-hydrogen` 等）；Room 升版至 v9（`MIGRATION_8_9`），支援寄件者／單則訊息結構化白名單，命中即標記安全並跳過後續分析、警示與情報預查。
- **設定搜尋全面稽核（#120）**：逐卡對位 `SettingsCatalog` 與實際 UI，補齊每日摘要推播與液態玻璃特效條目並驗證本地／AI 雙路徑搜尋。

### v1.12.1 更新重點（versionCode 14）

- **設定搜尋 catalog 修正與直達頁面（#109）**：修正設定搜尋目錄五項對錯卡（含 SMS 自動回覆改定位至 AI 卡）、新增「詐騙警示中心」與「過濾關鍵字規則與命中動作」搜尋定位、AI 定位列支援「前往」直達對應頁面（自動回覆設定、詐騙警示中心、自訂標籤管理、API Key 設定），並於資料管理卡補齊桌面小工具加入說明。

### v1.12.0 更新重點（versionCode 13）

- **設定搜尋與通知收錄修正**（#99, #101）：拆分設定搜尋目錄的「同 key 重複節流」至通知卡並對齊副標說明；通訊類通知若 `postTime` 更新即收錄同內容連續訊息（`dedupCache` 僅擋斷線重播，非通訊類維持節流）。
- **資料完整性與安全強化**（#100, #102, #103, #104）：保留清理直接排除詐騙證據並清理孤兒標籤；`NotifIds` 集中管理互斥通知 ID；`AiClient.sanitizeForPrompt` 防護四個 AI Prompt Injection 注入點；SMS 自動回覆新增 4 小時冷卻、單日上限 3 次與範本自回防護。
- **技術債清理與效能優化**（#105）：過期提醒開機即觸發、`HeartbeatPolicy`/`WeeklySummaryPolicy` 改用 `UPDATE`、`PhantomPrefs` 節流表 30 秒快取與分層解耦、7 日／小時統計 SQL 聚合與 Widget 共用圖示 `LruCache`。

### v1.11.0 更新重點

- **連續訊息漏收修復與節流自訂**（#96）：實作內容感知同 Key 節流、疊加訊息尾巴提取、DB 視窗查證式防重複收錄與多層標題 fallback；設定頁新增「數字微變視為同一則更新」開關與 per-app 節流秒數覆寫。
- **AI 訊息助手回應截斷修復**（#95）：`chatAssistant` `maxTokens` 調升至 `4096` 並放寬 chat 專用逾時（`read 120s / callTimeout 180s`），偵測 `finish_reason == "length"` 時誠實標注截斷提示。
- **看門狗與熱路徑省電最佳化**（#94）：`ShizukuWatchdogService` 改為 5 分鐘／60 秒自適應退避、`requestRebind` 僅於未連線時觸發，並於通知熱路徑導入 `appLabelCache`（`LruCache(64)`）與 30 秒 TTL 規則快取。

---

## 安裝指南（免編譯直接安裝）

1. **從 GitHub Releases 下載 APK（正式發佈版）**
   - 前往本倉庫右側 **Releases** 頁面，選擇最新版本（如 `v2.1.0`）下載隨附的 `.apk` 檔案。

2. **或從 GitHub Actions 下載最新開發版 APK**
   - 開啟本倉庫的 GitHub 頁面，點選上方 **Actions** 分頁。
   - 在左側選擇 **Android Build Check**，點入最新一筆 `main` 分支且狀態為綠燈（✓）的執行紀錄。
   - 捲動至頁面底部的 **Artifacts** 區塊，下載 `phantomcheck-debug-<編號>` 壓縮檔並解壓縮，取得 `app-debug.apk`。

3. **在 Android 裝置安裝 APK**
   - 將 APK 傳至 Android 手機點擊安裝；若系統提示未知來源應用程式，請依引導開啟「允許來自此來源的應用程式」。

4. **開啟通知讀取權限與設定 API Key**
   - 依 App 內新手引導開啟「通知存取權」，並於「設定 → API Key 設定」輸入任意支援之 AI Provider Key（金鑰僅加密儲存於本機）。

5. **輸入 Google Safe Browsing API Key（情報源 BYOK）**
   - 於 [Google Cloud Console](https://console.cloud.google.com/) 建立專案、啟用「Safe Browsing API」，並於「憑證 → 建立 API 金鑰」取得 key（金鑰僅加密儲存於本機）。
   - 於 App 內「設定 → 防詐情報 → Google Safe Browsing v4」欄位貼上該 key，加密儲存後即啟用；未輸入 key 時會誠實跳過 Safe Browsing 源，不影響其他情報來源。

6. **Windows 桌面端安裝（`setup.exe`）與舊版升級疑難排解**
   - `PhantomCheckDesktop-<版本>-setup.exe`（Inno Setup）現為**唯一官方 Windows 安裝與更新通道**（早期 `.msi` 套件已正式退場）。
   - **已裝舊版 `.msi`（v0.2.0–v0.4.0）的使用者**：直接執行新版 `setup.exe` 一次即可。安裝程式會自動優雅關閉背景程式、靜默清理舊版 `.msi` 或衝突安裝項目，並**完整保留 `%APPDATA%\PhantomCheckDesktop` 中的所有設定與配對指紋**。

---

## 技術架構

| 層級 | 技術選型 | 說明 |
|---|---|---|
| **語言與 UI** | Kotlin + Jetpack Compose（Material 3） | 宣告式 UI、Phantom 深淺主題系統與響應式狀態管理 |
| **本地資料庫** | Room Database | 儲存攔截訊息（`messages`）與過濾規則（`filter_rules`），含版本 Migration |
| **背景排程** | WorkManager | 定期執行訊息保留政策清理過期資料 |
| **網路與 AI** | Retrofit / OkHttp + Mistral API | 呼叫 Mistral 模型進行詐騙語意分析與風險評分 |
| **機密與安全** | EncryptedSharedPreferences（AndroidX Security Crypto） | 本機加密儲存使用者輸入的 API Key；無硬編碼機密 |
| **CI 管線** | GitHub Actions | 每個 PR 與 `main` push 自動執行 `gitleaks` 機密掃描與 `./gradlew :app:assembleDebug` 建置，並於 `main` 上傳 APK artifact |

---

## 安全政策

- 所有機密（API key、token、密碼）**永不寫入程式碼、文件或 Git 歷史**。
- 在地端開發時可將測試用 key 放於 `local.properties`（已由 `.gitignore` 排除），或直接透過 App 內設定頁輸入。
- 完整資安規範與漏洞回報方式請見 [SECURITY.md](SECURITY.md)。

---

## 開發協作

本專案由多位 AI agent 與人類協作者共同維護，詳細規範請參閱：

- 協作規則與角色分工：[AGENTS.md](AGENTS.md)
- 貢獻指南與 PR 流程：[CONTRIBUTING.md](CONTRIBUTING.md)
- 本地開發環境設定：[docs/LOCAL_SETUP.md](docs/LOCAL_SETUP.md)

---

## 授權

本專案採用 [GPL-3.0](LICENSE) 授權：任何人可自由使用、修改與散布，惟衍生作品必須以相同條款開源並保留本聲明。
