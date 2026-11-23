package com.phantom.checker.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * #381 / #419 Health Connect 生理數據整合橋接層（androidx.health.connect:connect-client:1.1.0）：
 * 1. 增強不依賴：未安裝 HC（含無 GMS 機型）或未授權時誠實降級，絕不 crash（getOrCreate 必 catch IllegalStateException）。
 * 2. 原始生理數值永不出本機：僅於本機 aggregate 聚合，採 per-type 權限查詢，已授 3 權限的既有用戶不回退。
 * 3. 無 API key 時讀取與 Stats 趨勢／明細照常運作。
 */
object HealthConnectBridge {

    private const val HC_PROVIDER_PACKAGE = "com.google.android.apps.healthdata"

    val PERM_READ_SLEEP: String = HealthPermission.getReadPermission(SleepSessionRecord::class)
    val PERM_READ_STEPS: String = HealthPermission.getReadPermission(StepsRecord::class)
    val PERM_READ_HEART_RATE: String = HealthPermission.getReadPermission(HeartRateRecord::class)
    val PERM_READ_DISTANCE: String = HealthPermission.getReadPermission(DistanceRecord::class)
    val PERM_READ_TOTAL_CALORIES: String = HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class)

    /** 既有用戶核心三權限（保持不變以免破壞已授三權限者之行為） */
    val REQUIRED_READ_PERMISSIONS: Set<String> = setOf(
        PERM_READ_SLEEP,
        PERM_READ_STEPS,
        PERM_READ_HEART_RATE
    )

    /** #419 擴充權限（距離、總消耗熱量），採 per-type 漸進式授權 */
    val OPTIONAL_READ_PERMISSIONS: Set<String> = setOf(
        PERM_READ_DISTANCE,
        PERM_READ_TOTAL_CALORIES
    )

    /** 完整 5 項讀取權限（供權限中心請求使用） */
    val ALL_READ_PERMISSIONS: Set<String> = REQUIRED_READ_PERMISSIONS + OPTIONAL_READ_PERMISSIONS

    data class HealthPermissionStatus(
        val sleep: Boolean = false,
        val steps: Boolean = false,
        val heartRate: Boolean = false,
        val distance: Boolean = false,
        val calories: Boolean = false
    ) {
        val anyGranted: Boolean get() = sleep || steps || heartRate || distance || calories
        val allCoreGranted: Boolean get() = sleep && steps && heartRate
        val allGranted: Boolean get() = sleep && steps && heartRate && distance && calories
        val grantedCount: Int get() = listOf(sleep, steps, heartRate, distance, calories).count { it }
    }

    enum class SdkAvailability {
        AVAILABLE,
        PROVIDER_UPDATE_REQUIRED,
        UNAVAILABLE
    }

    data class DailyHealthPoint(
        val dayLabel: String, // MM/dd
        val sleepHours: Float,
        val steps: Long,
        val avgHeartRateBpm: Long,
        val distanceMeters: Double = 0.0,
        val caloriesKcal: Double = 0.0
    )

    data class MorningHealthSnapshot(
        val lastNightSleepHours: Float,
        val yesterdaySteps: Long,
        val yesterdayAvgHeartRate: Long,
        val baseline7dAvgHeartRate: Long
    )

    fun checkAvailability(ctx: Context): SdkAvailability {
        return try {
            when (HealthConnectClient.getSdkStatus(ctx.applicationContext, HC_PROVIDER_PACKAGE)) {
                HealthConnectClient.SDK_AVAILABLE -> SdkAvailability.AVAILABLE
                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> SdkAvailability.PROVIDER_UPDATE_REQUIRED
                else -> SdkAvailability.UNAVAILABLE
            }
        } catch (_: Throwable) {
            SdkAvailability.UNAVAILABLE
        }
    }

    /** 安全取得 HealthConnectClient：明確捕捉 IllegalStateException 與未裝 HC 之無 GMS ROM 例外 */
    fun getClientSafely(ctx: Context): HealthConnectClient? {
        if (checkAvailability(ctx) != SdkAvailability.AVAILABLE) return null
        return try {
            HealthConnectClient.getOrCreate(ctx.applicationContext)
        } catch (_: IllegalStateException) {
            null
        } catch (_: Throwable) {
            null
        }
    }

    /** 取得 5 項健康讀取權限的 per-type 授權狀態 */
    suspend fun getPermissionStatus(ctx: Context): HealthPermissionStatus = withContext(Dispatchers.IO) {
        val client = getClientSafely(ctx) ?: return@withContext HealthPermissionStatus()
        try {
            val granted = client.permissionController.getGrantedPermissions()
            HealthPermissionStatus(
                sleep = PERM_READ_SLEEP in granted,
                steps = PERM_READ_STEPS in granted,
                heartRate = PERM_READ_HEART_RATE in granted,
                distance = PERM_READ_DISTANCE in granted,
                calories = PERM_READ_TOTAL_CALORIES in granted
            )
        } catch (_: Throwable) {
            HealthPermissionStatus()
        }
    }

    suspend fun hasAnyReadPermission(ctx: Context): Boolean = getPermissionStatus(ctx).anyGranted

    /** 核心三權限檢查（保留原簽名與語意，不強制要求新權限，避免破壞既有用戶） */
    suspend fun hasAllReadPermissions(ctx: Context): Boolean = getPermissionStatus(ctx).allCoreGranted

    /** 誠實引導 Intent：Android 14+ 開啟系統 Health Connect 設定；Android 13 導向安裝頁 */
    fun buildInstallOrSettingsIntent(ctx: Context): Intent {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val sysIntent = Intent("android.health.connect.action.HEALTH_HOME_SETTINGS").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (sysIntent.resolveActivity(ctx.packageManager) != null) return sysIntent
        }
        val playIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("market://details?id=$HC_PROVIDER_PACKAGE")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (playIntent.resolveActivity(ctx.packageManager) != null) return playIntent
        return Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/apps/details?id=$HC_PROVIDER_PACKAGE")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** 本機聚合近 7 日健康數據（per-type 查詢：僅查詢已授權型別，未授權型別維持 0） */
    suspend fun readLast7DaysTrend(ctx: Context): List<DailyHealthPoint> = readRecentDaysTrend(ctx, days = 7)

    /** 本機聚合近 N 日健康數據（per-type 查詢，支援 1..30 日） */
    suspend fun readRecentDaysTrend(ctx: Context, days: Int = 7): List<DailyHealthPoint> = withContext(Dispatchers.IO) {
        val client = getClientSafely(ctx) ?: return@withContext emptyList()
        val perm = getPermissionStatus(ctx)
        if (!perm.anyGranted) return@withContext emptyList()
        val clampedDays = days.coerceIn(1, 30)
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val labelFmt = DateTimeFormatter.ofPattern("MM/dd", Locale.US)
        val out = mutableListOf<DailyHealthPoint>()

        for (offset in (clampedDays - 1).toLong() downTo 0L) {
            val day = today.minusDays(offset)
            val start = day.atStartOfDay(zone).toInstant()
            val end = day.plusDays(1).atStartOfDay(zone).toInstant()
            // 睡眠窗口取前晚 18:00 至當日 12:00 以正確涵蓋跨午夜睡眠
            val sleepStart = day.minusDays(1).atTime(LocalTime.of(18, 0)).atZone(zone).toInstant()
            val sleepEnd = day.atTime(LocalTime.of(12, 0)).atZone(zone).toInstant()

            val sleepHours = if (perm.sleep) {
                runCatching {
                    val res = client.aggregate(
                        AggregateRequest(
                            metrics = setOf(SleepSessionRecord.SLEEP_DURATION_TOTAL),
                            timeRangeFilter = TimeRangeFilter.between(sleepStart, sleepEnd)
                        )
                    )
                    val dur = res[SleepSessionRecord.SLEEP_DURATION_TOTAL]
                    if (dur != null) (dur.toMinutes() / 60f) else 0f
                }.getOrDefault(0f)
            } else 0f

            val steps = if (perm.steps) {
                runCatching {
                    val res = client.aggregate(
                        AggregateRequest(
                            metrics = setOf(StepsRecord.COUNT_TOTAL),
                            timeRangeFilter = TimeRangeFilter.between(start, end)
                        )
                    )
                    res[StepsRecord.COUNT_TOTAL] ?: 0L
                }.getOrDefault(0L)
            } else 0L

            val avgHr = if (perm.heartRate) {
                runCatching {
                    val res = client.aggregate(
                        AggregateRequest(
                            metrics = setOf(HeartRateRecord.BPM_AVG),
                            timeRangeFilter = TimeRangeFilter.between(start, end)
                        )
                    )
                    res[HeartRateRecord.BPM_AVG] ?: 0L
                }.getOrDefault(0L)
            } else 0L

            val distanceMeters = if (perm.distance) {
                runCatching {
                    val res = client.aggregate(
                        AggregateRequest(
                            metrics = setOf(DistanceRecord.DISTANCE_TOTAL),
                            timeRangeFilter = TimeRangeFilter.between(start, end)
                        )
                    )
                    res[DistanceRecord.DISTANCE_TOTAL]?.inMeters ?: 0.0
                }.getOrDefault(0.0)
            } else 0.0

            val caloriesKcal = if (perm.calories) {
                runCatching {
                    val res = client.aggregate(
                        AggregateRequest(
                            metrics = setOf(TotalCaloriesBurnedRecord.ENERGY_TOTAL),
                            timeRangeFilter = TimeRangeFilter.between(start, end)
                        )
                    )
                    res[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories ?: 0.0
                }.getOrDefault(0.0)
            } else 0.0

            out.add(
                DailyHealthPoint(
                    dayLabel = day.format(labelFmt),
                    sleepHours = sleepHours,
                    steps = steps,
                    avgHeartRateBpm = avgHr,
                    distanceMeters = distanceMeters,
                    caloriesKcal = caloriesKcal
                )
            )
        }
        out
    }

    /** 格式化距離（自動切換 m / km） */
    fun formatDistance(meters: Double): String {
        if (meters <= 0.0) return "—"
        return if (meters >= 1000.0) {
            String.format(Locale.US, "%.2f km", meters / 1000.0)
        } else {
            "${meters.toLong()} m"
        }
    }

    /** #419 零 token 純本機 health_query 確定性回覆卡（含每日明細小表與未授權型別提示） */
    suspend fun buildHealthQueryReport(ctx: Context, days: Int = 7): String = withContext(Dispatchers.IO) {
        val clampedDays = days.coerceIn(1, 30)
        when (checkAvailability(ctx)) {
            SdkAvailability.UNAVAILABLE ->
                return@withContext "❤️ 查不到健康數據：此裝置未安裝或未支援 Health Connect，無法讀取生理紀錄（可至「權限中心」查看說明）。"
            SdkAvailability.PROVIDER_UPDATE_REQUIRED ->
                return@withContext "❤️ 查不到健康數據：Health Connect 需要安裝或更新，請至「權限中心」點擊安裝／更新後再試。"
            SdkAvailability.AVAILABLE -> Unit
        }
        val perm = getPermissionStatus(ctx)
        if (!perm.anyGranted) {
            return@withContext "❤️ 查不到健康數據：尚未授權任何 Health Connect 讀取權限，請前往「權限中心」開啟 Health Connect 授權。"
        }
        val trend = readRecentDaysTrend(ctx, clampedDays)
        val hasAnyData = trend.any {
            it.sleepHours > 0f || it.steps > 0L || it.avgHeartRateBpm > 0L || it.distanceMeters > 0.0 || it.caloriesKcal > 0.0
        }
        val missingTypes = buildList {
            if (!perm.sleep) add("睡眠")
            if (!perm.steps) add("步數")
            if (!perm.heartRate) add("心率")
            if (!perm.distance) add("距離")
            if (!perm.calories) add("熱量")
        }
        buildString {
            appendLine("❤️ **Health Connect 近 $clampedDays 日健康明細（本機聚合，零額度）**")
            if (!hasAnyData) {
                appendLine("近 $clampedDays 日查無已同步的健康紀錄。")
            } else {
                appendLine()
                appendLine("| 日期 | 睡眠 | 步數 | 均心率 | 距離 | 熱量 |")
                appendLine("| --- | --- | --- | --- | --- | --- |")
                trend.forEach { pt ->
                    val sleepStr = if (!perm.sleep) "—" else if (pt.sleepHours > 0f) String.format(Locale.US, "%.1fh", pt.sleepHours) else "—"
                    val stepsStr = if (!perm.steps) "—" else if (pt.steps > 0L) String.format(Locale.US, "%,d", pt.steps) else "—"
                    val hrStr = if (!perm.heartRate) "—" else if (pt.avgHeartRateBpm > 0L) "${pt.avgHeartRateBpm}" else "—"
                    val distStr = if (!perm.distance) "—" else formatDistance(pt.distanceMeters)
                    val calStr = if (!perm.calories) "—" else if (pt.caloriesKcal > 0.0) "${pt.caloriesKcal.toLong()} kcal" else "—"
                    appendLine("| ${pt.dayLabel} | $sleepStr | $stepsStr | $hrStr | $distStr | $calStr |")
                }
            }
            if (missingTypes.isNotEmpty()) {
                appendLine()
                appendLine("ℹ️ 尚未授權型別（顯示「—」）：${missingTypes.joinToString("、")}；可至「權限中心」補授權。")
            }
        }.trim()
    }

    /** 晨間簡報用本機聚合快照（前一晚睡眠＋昨日步數與心率＋7 日基準心率） */
    suspend fun readMorningSnapshot(ctx: Context): MorningHealthSnapshot? = withContext(Dispatchers.IO) {
        val trend = readLast7DaysTrend(ctx)
        if (trend.isEmpty()) return@withContext null
        val todayPoint = trend.lastOrNull() ?: return@withContext null
        val yesterdayPoint = trend.getOrNull(trend.size - 2) ?: todayPoint
        val hrSamples = trend.mapNotNull { it.avgHeartRateBpm.takeIf { bpm -> bpm > 0L } }
        val baselineHr = if (hrSamples.size >= 3) hrSamples.average().toLong() else 0L
        MorningHealthSnapshot(
            lastNightSleepHours = todayPoint.sleepHours,
            yesterdaySteps = yesterdayPoint.steps,
            yesterdayAvgHeartRate = yesterdayPoint.avgHeartRateBpm,
            baseline7dAvgHeartRate = baselineHr
        )
    }

    /**
     * #381 查表式規則引擎（固定規則驅動，AI 不可自創建議；無命中回傳 null 寧缺勿濫；
     * 嚴格遵守紅線：輸出僅為規則結論一句話，絕不外送原始數值）。
     */
    fun evaluateLookupRule(
        snapshot: MorningHealthSnapshot,
        hasEarlyEventBefore9Am: Boolean
    ): String? {
        return when {
            snapshot.lastNightSleepHours in 0.1f..5.95f && hasEarlyEventBefore9Am ->
                "昨晚睡眠偏短且今早九點前有排定行程，建議今晚提早 30 分鐘就寢，並為早間行程預留緩衝。"
            snapshot.lastNightSleepHours in 0.1f..5.95f ->
                "昨晚睡眠時數偏短，建議今天中午安排短暫休息以維持專注力。"
            snapshot.yesterdayAvgHeartRate > 0L &&
                snapshot.baseline7dAvgHeartRate > 0L &&
                (snapshot.yesterdayAvgHeartRate - snapshot.baseline7dAvgHeartRate) >= 12L ->
                "近期靜止／平均心率較七日基準明顯偏高，建議今日放緩步調並多補充水分。"
            snapshot.yesterdaySteps in 1L..2999L ->
                "昨日活動量偏低，今日可趁空檔起身伸展或散步 10–15 分鐘。"
            snapshot.yesterdaySteps >= 10_000L ->
                "昨日達成萬步目標，保持相當不錯的日常活動節奏！"
            else -> null
        }
    }
}
