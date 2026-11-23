**English** | [繁體中文](README.zh-TW.md)

# PhantomCheck

An intelligent Android anti-scam message management app: intercepts notification previews without triggering read receipts in the original apps, combining keyword filtering, AI scam risk analysis, a Scam Alert Center, and SMS auto-reply to safeguard daily communications.

---

## Privacy & Disclaimer

This app uses a 100% local storage architecture with no proprietary servers, no account system, and no telemetry tracking. AI analysis and threat-intel lookups connect to third-party services only after you enable and configure them.
Before use, please read the [Privacy Policy (PRIVACY.md)](PRIVACY.md) and [Disclaimer (DISCLAIMER.md)](DISCLAIMER.md).

---

## System Requirements

- **OS**: Android 8.0 (API 26) or higher
- **Liquid Glass Blur**: Real-time background blur is supported on Android 12 (API 31)+; lower versions or Battery Saver mode automatically fall back to translucent tinting
- **Required Permissions**: Notification Access (Notification Listener); on Android 13+, granting Notification permission is recommended to display scam alerts and persistent stats

---

## Core Features

- 📩 **Notification Interception & Preview**: Reads notification content in real time via `NotificationListenerService` without opening LINE, Instagram, SMS, or other source apps, preventing "read" receipts.
- 🕵️ **Public Anti-Scam Threat-Intel Pre-Check (Zero-Token)**: Before AI analysis, extracts URLs, short links, and cryptocurrency addresses (BTC/ETH/TRON) and sequentially queries the local 72-hour cache, `fraud.tw` (165 reports), Google Safe Browsing v4, and optional PhishTank / Chainabuse (never sends full message text; hits trigger immediate alerts without consuming daily AI quota).
- 🛡️ **Multi-Provider AI Scam Analysis, Per-Purpose Routing & Offline Retry Queue**: Supports multiple AI profiles including Mistral, OpenAI, Groq, OpenRouter, Gemini (OpenAI-compatible), and DeepSeek, with per-purpose model assignment (auto-memo follows the message-analysis model) and instant switching/syncing in AI Assistant and Deep Analysis; transient network/server failures automatically enter the offline retry queue (WorkManager retry on reconnect).
- 🚫 **Smart Notification Deduplication & Filtering**: Automatically filters ongoing notifications, group summaries, and disconnect-replay duplicates; supports same-key deduplication throttling with per-app overrides (`0s = never throttle`; messaging notifications with updated `postTime` record new messages with identical content), keyword filtering (including per-app rules), and an app mute list.
- 📊 **Persistent Stats Notification, 5-Tab Navigation (Large-Screen NavigationRail) & Search-First Settings Hub**: Custom `RemoteViews` persistent notification displays the PhantomCheck icon on the left and recent notified apps ("newest on left, oldest on right") on the right with the latest marked, tapping follows per-app/PhantomCheck settings (swipe-dismiss seamlessly rebuilds in place + 15-minute heartbeat + Shizuku cross-process watchdog); in-app 5-tab Liquid Glass UI ("Messages, AI Assistant, Notes, Memory, Insights", where "Insights" merges "Companion | Stats" top sub-tabs, and screens `>= 600dp` automatically switch to a left `NavigationRail`) plus a search-first 8-category Settings Hub (dual-mode keyword / AI semantic search, 6 customizable pinned quick settings, recent adjustments, and 24 advanced settings + Shizuku collapsed by default and auto-expanded/highlighted on search hits).
- 🤖 **AI Assistant, AI Companion (AI Diary / Long-Term Goals / 5-Stage Intimacy), Proactive Intel Hub & Web Search, Morning/Evening Briefings & Milestone Alerts**: Built-in read-only RAG chat assistant (slide-out history drawer with default-collapsed + live unread badge dedicated proactive-message chatroom, `HH:mm` bottom-right bubble timestamps, top unresolved-items pill bar and 7-day expiring continuation anchors, automatic extraction of non-sensitive, conflict-free memories with subtle notice and delete BottomSheet, image vision viewing, collapsible "Reasoning" chain-of-thought display, `LocalDataConnector` direct local calendar access where specific events come verbatim from local calendar queries or honestly refuse if none found, `UsageStatsManager` usage queries, and **Health Connect health summary queries** that inject 7-day aggregated values only when health is asked in chat and "Allow AI to read health summary" is enabled—raw physiological sample series never leave the device, and unauthorized queries honestly refuse—**in-chat "🔍 Web Search" toggle** supporting native Google Search Grounding with source-link parsing on Gemini 3+ and BYOK search endpoints or honest disabled state on other providers, single-intent single-card tool calling with confirmation cards), `PhantomMarkdownText` rendering, and "Yesterday's Briefing"; **Proactive Intel Hub** (off by default; when enabled, uses `AiPurpose.PROACTIVE` daily quota to extract candidate `watch_topics` from non-sensitive memories and chats, and after user confirmation periodically queries short topic strings online with 12h cache and SHA-256 deduplication, pushing only new findings to the proactive chatroom and notifications with "💬 Follow Up" and "Stop Watching"); rightmost "Insights" tab ("Companion" sub-tab) shows a pinned full-width cardless assistant avatar & name banner (with independent custom region background), companion days, **5-stage intimacy details (Lv1–Lv5 "Kindred", thresholds `50/160/420/900`)**, **AI Diary / Daily Review** (daily 00:30 zero-token SQL aggregated timeline + monthly overview with optional collapsible AI annotations), **Long-Term Goal Tracking** (created via structured-signal confirmation cards, broken into recurring sub-todos with single-point `ReminderScheduler` completion trigger and dedup guard, `max(today, previous dueAt + cadenceDays)` next-due anchoring, dedicated persistent auto-downgrade after 3 consecutive overdue periods, milestone truth-tracking, and full offline manual management), 7-day activity, and "What I Know About You" profile card; supports 08:45 morning / 22:25 evening personal briefings (morning briefing integrates **Health Connect physiological data** via local lookup-table rules), **event-milestone deduplicated** proactive notifications (each todo/event reminds at most once at 24h before / N min before / overdue + 10-minute content-hash double guard, with 👍/👎 feedback; includes **HealthObservationPolicy** proactive health alerts for 2 consecutive nights sleep `<6h`, 3 consecutive days steps `<2000`, or resting HR `>=12bpm` above 7-day average via pure local lookup, max 1/day and 2/week per rule), `CalendarInsightEngine` periodic calendar intel (default 60 min, queries external intel using only title + date `<= 60` chars with strict dedup), independent DND, and separate daily caps for briefings and proactive alerts.
- 🔗 **Linked Apps Hub, Declarative Blocked-Action Rerouting, Permission Hub & Scheduled Task Templates**: `LinkedAppsActivity` is driven by a single-source declarative `LinkedAppActionRegistry` (ready-to-use alarms/timers, local calendar/usage stats, GitHub PAT 15-minute polling with sync/error status, and Discord Webhook read-only/forwarding, with keys encrypted via `EncryptedByokStore`) plus "Confirm Before Execute" cross-app action cards; `IntentCapabilityRegistry` **declarative capability table & blocked-action rerouting** (`REMINDER` / `MEMO` / `CALENDAR_ADD` / `OPEN_APP` automatically downgrade to safe fallback paths with user notice when blocked by permissions or OS limits, or show plain-language reasons + direct Permission Hub button if all paths fail—never executing silently); `PermissionHubActivity` centralizes Notification, SMS, Contacts, Calendar, Exact Alarm, Battery Optimization, Usage Stats, and **Health Connect** (including independent "Allow AI to read health summary" toggle) permissions plus 4-brand restricted-settings guides; `AssistantHubActivity` centralizes briefings, proactive calendar intel, scheduled task templates (`TaskTemplateEngine` 3-step wizard + WYSIWYG preview, zero-AI aggregation phase), and Contacts integration (raw contacts are never sent to AI).
- 📝 **Notes & To-Dos, 11 Home-Screen Widgets, Local Calendar Day/Week/Month Views, Auto-Memo & Schedule Routing**: Supports natural-language and manual to-do reminders (priority, notes, daily/weekly recurrence, filtering/sorting, synced with alarm scheduling, home-screen widgets, and the "Notes" tab), **11 home-screen widgets** (7 existing stats/AI summary/notes/to-do/anti-scam quick widgets upgraded with `targetCellWidth/Height`, `previewLayout`, and dedicated vector previews, plus 4 new widgets: **Today's To-Dos, Goal Progress, Quick Note, and Health Snapshot**), **Notes tab local calendar Day/Week/Month views & Settings account-grouped batch selection** (filters visible calendars by default with one-tap "Select All Visible"), AI auto-memo (smartly infers time clues from messages and schedules 30-minute advance reminders), schedule-type notifications default to creating only local calendar suggestion cards (avoiding duplicate to-do/memo writes; revertible via `schedule_memo_route` in Settings), and conversational add/remove whitelist or tag rules (enforces confirmation dialog + DB read-back verification).
- 🏷️ **AI Smart Tagging, Dual-Threshold Anti-Scam False-Positive Reduction & Whitelist Rule Management**: Single AI analysis supports 0–3 tags + zero-token app category rules; `DecisionEngine` prioritizes deterministic signals with an AI semantic cap (`threshold - 10`) and reversible/irreversible matrix, **strictly tightening automatic `scam` tagging** (requires threat-intel hit, crypto address, or concrete scam-operation signals; generic links or conflicts with 30-day high-reputation senders/contacts downgrade to confirmation cards, and token-saving suspicious-feature triggers are tightened to cut daily false positives), supports one-tap 4-category "This judgment is wrong" feedback written back to rules, sender reputation, and encrypted benign-domain LRU; whitelist uses full-table queries and unified dedup writes, with an app-grouped collapsible whitelist manager in Settings.
- 💬 **App Grouping, Card Layout Modes, Sender Avatars & Self-Reply Bubbles**: Browse grouped by app and `MessagingStyle` conversation key (both Home and Notes tabs share "Standard List (rounded segmented group list) / Center Stack" layout modes: Center Stack uses `LazyColumn` continuous inertial scrolling + damped spring snapping, compact `6dp` edge gap, linear `lerp` centering, symmetric `contentPadding` keeping 2–3 cards stably drawn above, dynamic `zone` preventing short-list overlap/sinking, `92dp` compact cards (`104dp` max for large font), `40dp` peek band, near-focus scale easing (`0.94f`→`1.0f`) with `0.04f` step-down (floor `0.82f`) and focus shadow gradient), supports sender avatar extraction with 3-tier fallback (notification avatar → app icon → initial colored avatar), Favorites and VIP sender pinning, direct in-conversation reply persisted as right-aligned bubbles (with 3-minute self-reply echo suppression and "My Name" group self-identity detection), keyset pagination (`LIMIT 200`), swipe-left bubble delete, multi-select batch management (up to 100, scam-protected excluded), fixed header + independently scrolling message list, 3 smart filter pills + "Filter ▾" sheet (Starred / Noted / To-Do / Tags / Sort), and persistent top global search pill button, pull-down overscroll gesture at list top (removed persistent `22dp` handle visual), and full-screen Global / AI Semantic Search (All / Messages / Settings / Memory / Notes scope filters).
- 🧭 **Visual Onboarding & Settings Walkthrough**: 8-step Home core spotlight tour + 5-step Settings Hub tour (upgrading users keep their completion flags and see a one-time v4.0 quick-tour banner in Settings), auto-switching tabs per step, merging empty-DB message actions, waiting for Android 13+ system permission dialogs, offering per-brand "Allow restricted settings" troubleshooting, direct app Notification Access detail page links, and one-tap Shizuku authorization; callout cards avoid covering highlighted areas and support Light, Dark, and Dynamic Color high-contrast reading with spotlight touch-through.
- 🔋 **5-in-1 Token Saver & Battery / Stability Optimization**: Prompt Cache Key & static prefix design, dual-model confidence routing (cheap model first, escalate on ambiguous range), incremental context summary, dynamic image-size estimation, automatic blur disable on Battery Saver, plus KeepAlive (45 min) / Shizuku (600 s) power-saving polling, Heartbeat `KEEP` debouncing, screen-off early exit, and foreground-service exception guards.
- 🚨 **Scam Alert Center, Do Not Disturb & SMS Auto-Reply**: Centralizes high-risk scam messages, intel sources, and trend comparisons (accessible from message risk cards or Stats Top 5); supports DND schedules and SMS auto-reply (SMS records are ingested and deduplicated solely via notification interception while the receiver handles auto-reply, with 4-hour per-sender cooldown, 3/day cap, and template self-reply guard).
- 📄 **CSV Export, Retention Policy, Storage Maintenance & Encrypted Keys**: One-tap CSV export to `Downloads`; Settings "Data Management" provides storage breakdown and "Compact Database Now" (periodic `VACUUM` + expired data / 300-entry avatar cache cleanup); `EncryptedSharedPreferences` locally encrypts AI, threat-intel (Safe Browsing / PhishTank / Chainabuse), GitHub PAT / Discord Webhook / external search endpoint keys, editable directly in Settings (BYOK, no BuildConfig fallback).
- 🎨 **3-Layer Custom Backgrounds & Card Surface Modes**: Independent Global / AI Chat / Conversation background images (layered fallback, downsampled storage to prevent OOM), dark readability scrim, Liquid Glass card intensity slider, and 3 card surface modes (Follow Glass / Transparency Slider `>= 15%` / Dark Surface).
- 🔍 **Auto & Manual Update Checker (Snooze State Machine)**: Automatically checks GitHub Releases on app launch when online, always showing dialog + notification when not snoozed (only tapping "Later" sets snooze; dismissing/swiping never swallows alerts; at most 1 auto-retry per process on failure), alongside manual check and persistent state tracking (`DownloadManager` dual-path polling + `ON_RESUME` re-check + failure retry + Android 8+ unknown-sources one-step guide + `FileProvider` APK install flow).
- 💬 **Continuous Quick Reply from Notification Shade**: After replying via AI Assistant, keeps a `MessagingStyle` proxy conversation notification in the shade for continuous multi-reply input with history and honest failure status, supporting swipe-dismiss, close, and idle auto-collapse.
- 📝 **Auto-Memo / To-Do Rule Engine**: Floating top-right overlay to manage manual, natural-language (AI-parsed + confirmation card), and "Never Auto-Memo / To-Do" 3-scope rules with `never > todo > note` priority and AI semantic keyword matching, plus one-tap jump from to-dos back to source messages.
- 📈 **Custom Stats Dashboard, Health Connect 7-Day Trends & Daily App Leaderboard**: Stats tab features a local "Weekly Highlights" natural-language bar, Morning/Evening Briefing card, **Health Connect 7-day sleep/steps mini-bar trend chart** (pure local aggregation, zero AI), Today's Top 5 App Message Leaderboard, 7-day risk trend line, unread/read donut, per-app share bars, and to-do completion ring; weekly AI summary supports missed-run catch-up, auto-continuation on incomplete sentences, full-text expand, and retry, with per-card visibility toggles in Settings "Data & Backup".
- 🌐 **Bilingual UI, In-App 3-State Language Switch & Multilingual AI / Anti-Scam Layer (i18n)**: Built-in Traditional Chinese (`values-zh-rTW`) and default English (`values/`, fallback = `en`) resource buckets (2,932 UI and XML `@string` resources); Settings Hub supports **3-state "Language" switching (Follow System / 繁體中文 / English; API 33+ syncs via `LocaleManager` per-app settings, API 26–32 smoothly recreates via `attachBaseContext`)**; notifications, widgets, Settings search catalog, and AI natural-language outputs (risk analysis, auto-memo, weekly summary, deep analysis, briefings, diary annotations, proactive intel) all follow the app language setting, while rule-based scam detection (`IndicatorExtractor` / `MemoryEngine` / `ContactContext`) supports both Chinese and English scam lexicons offline.

---

## Release History

For the complete changelog, see [CHANGELOG.md](CHANGELOG.md).

### v4.3.0 Release Highlights (versionCode 51, 2026-11-23)

- **#437 AI Transport Layer Hardening (PR #442, Behavior Change)**: GitHub Models was fully retired on 2026-07-30; `AiClient` now blocks retired hosts (`models.github.ai`, `models.inference.ai.azure.com`) before sending invalid requests and throws an exception with the retirement notice URL and migration guidance; unifies `Accept: application/json`; non-JSON responses throw explicit exceptions preserving HTTP status code, `Content-Type`, and the first 300 characters of the body; `testConnection` payload shape is unchanged.
- **#440 Insights Tab Long-Term Goal Tracking (PR #442, Behavior Change)**: Completion is unified through `ReminderScheduler.completeTodoWithRecurrence` as the single trigger for `GoalEngine.onTodoCompleted` (with `(goalId, todoId)` dedup guard before write), removing direct `NotesTab` calls and goal-progress Toasts; overdue downgrade periods persist in dedicated `phantom_goal_engine` `SharedPreferences`; next-period generation anchors on `max(today, previous dueAt + cadenceDays)`; milestones consume `milestoneJson` as the source of truth.
- **#441 Intimacy Stage 5 (PR #442, Behavior Change)**: Thresholds updated from `30/100/250` to `50/160/420/900`, adding Lv5 "Kindred (知己)"; existing users' effective level may adjust downward as expected; the `2/1/1` weight formula is unchanged.
- **#438 In-App 3-State Language Switch (PR #442, Behavior Change)**: Added 3-state "Language" setting (Follow System / 繁體中文 / English; API 33+ syncs via `LocaleManager` per-app settings, API 26–32 smoothly recreates via `attachBaseContext`); notifications, widgets, Settings catalog, and AI outputs all follow the app language; `zh-TW` system + Follow System behaves identically to v4.2.0.
- **#439 Widgets (PR #442)**: Upgraded all 7 existing widgets with `targetCellWidth/Height`, `previewLayout`, dedicated vector previews, `goAsync` 8.5s timeout, and `onDisabled` cleanup; added 4 new widgets: Today's To-Dos, Goal Progress, Quick Note, and Health Snapshot (zero new permissions, zero new network calls, `exported=false`, `FLAG_IMMUTABLE`, 30-minute health cache, onboarding state when unauthorized).

### v4.2.0 Release Highlights (versionCode 50, 2026-11-23)

- **Behavior Change: Default English UI & AI Output for Non-`zh-TW` Devices**:
  - Dual resource buckets (`values/` default English fallback and `values-zh-rTW/` Traditional Chinese): **When the device locale is not Traditional Chinese (`zh-TW`), both UI strings and AI natural-language outputs (analysis, auto-memo, weekly summary, deep analysis, briefings, diary annotations, proactive intel) switch to English**; `zh-TW` devices remain byte-for-byte unchanged.
- **#429 Full UI String Extraction & XML `@string` Externalization (PR #432)**:
  - Extracted 2,898 user-visible strings across Compose, Activities, Dialogs, Onboarding, widgets, notification channels, `SettingsCatalog` (138 items), `AndroidManifest.xml`, and `res/layout/*.xml` into `strings.xml` with `@string` references, and established the UI string extraction rule in `AGENTS.md` (zero behavior change).
- **#430 Default English UI (`values/`) & Traditional Chinese (`values-zh-rTW/`) (PR #433)**:
  - Moved all 2,898 Traditional Chinese strings byte-for-byte into `values-zh-rTW/strings.xml` and translated the default `values/strings.xml` bucket into concise Android UI English aligned with core terminology (`PhantomCheck`, `AI Assistant`, `Memo`, `To-do`, `Whitelist`, `Scam Protection`, `Memory Bank`).
- **#431 Locale-Aware AI Output & Bilingual Scam Lexicon (PR #434)**:
  - AI prompts across risk analysis, auto-memo, weekly summary, deep analysis, briefings, diary annotations, proactive intel, and chat automatically follow the system locale (`zh-TW` vs. English), while `IndicatorExtractor`, `MemoryEngine`, and `ContactContext` add English scam lexicons (unpaid toll, parcel redelivery, bank ATO / safe account, Hi Mum impersonation, crypto pig-butchering, OTP harvesting) for offline rule-based detection.

---

## Installation Guide (No Compilation Required)

1. **Download APK from GitHub Releases (Official Release)**
   - Go to the **Releases** page on the right side of this repository, select the latest version (e.g., `v4.3.0`), and download the attached `.apk` file.

2. **Or Download Latest Dev APK from GitHub Actions**
   - Open the GitHub repository page and click the **Actions** tab at the top.
   - Select **Android Build Check** on the left, and click the latest passing (✓) run on the `main` branch.
   - Scroll down to **Artifacts**, download `phantomcheck-debug-<number>`, and extract `app-debug.apk`.

3. **Install APK on Your Android Device**
   - Transfer the APK to your Android phone and tap to install; if prompted about unknown sources, follow the guide to enable "Allow from this source".

4. **Grant Notification Access & Configure API Key**
   - Follow the in-app onboarding to grant "Notification Access", and enter any supported AI Provider Key in "Settings → API Key Settings" (keys are encrypted locally only).

5. **Enter Google Safe Browsing API Key (Threat-Intel BYOK)**
   - Create a project in [Google Cloud Console](https://console.cloud.google.com/), enable "Safe Browsing API", and generate an API key under "Credentials → Create API Key" (stored encrypted locally only).
   - Paste the key in the app under "Settings → Anti-Scam Intel → Google Safe Browsing v4" to enable it; when left blank, Safe Browsing is skipped without affecting other intel sources.

---

## Technical Architecture

| Layer | Technology | Description |
|---|---|---|
| **Language & UI** | Kotlin + Jetpack Compose (Material 3) | Declarative UI, Phantom Light/Dark theme system, and reactive state management |
| **Local Database** | Room Database | Stores intercepted messages (`messages`) and filter rules (`filter_rules`), with versioned migrations |
| **Background Scheduling** | WorkManager | Periodically runs message retention cleanup for expired data |
| **Networking & AI** | Retrofit / OkHttp + Multi-Provider AI APIs | Calls AI models for scam semantic analysis and risk scoring |
| **Secrets & Security** | EncryptedSharedPreferences (AndroidX Security Crypto) | Locally encrypts user-entered API keys; zero hardcoded secrets |
| **CI Pipeline** | GitHub Actions | Every PR and `main` push automatically runs `gitleaks` secret scanning and `./gradlew :app:assembleDebug`, uploading APK artifacts on `main` |

---

## Security Policy

- All secrets (API keys, tokens, passwords) are **never written into code, documentation, or Git history**.
- For local development, test keys may be placed in `local.properties` (excluded by `.gitignore`) or entered directly in the in-app Settings.
- For full security guidelines and vulnerability reporting, see [SECURITY.md](SECURITY.md).

---

## Development Collaboration

This project is co-maintained by multiple AI agents and human collaborators. For detailed guidelines, see:

- Collaboration rules & role division: [AGENTS.md](AGENTS.md)
- Contribution guide & PR workflow: [CONTRIBUTING.md](CONTRIBUTING.md)
- Local development setup: [docs/LOCAL_SETUP.md](docs/LOCAL_SETUP.md)

---

## License

This project is licensed under [GPL-3.0](LICENSE): anyone may freely use, modify, and distribute it, provided derivative works are open-sourced under the same terms with this notice preserved.
