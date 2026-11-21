package com.phantom.checker.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.phantom.checker.data.AiRuleEntity
import com.phantom.checker.data.AppDatabase
import com.phantom.checker.data.MemoryEngine
import com.phantom.checker.data.MemoryEntity
import com.phantom.checker.service.AppDatabaseHolder
import com.phantom.checker.service.NotificationCollectorService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 本機通訊錄整合橋接層（#336）：
 * - 純本機 ContactsContract.Data MIMETYPE 查詢（StructuredName / Nickname / Organization / GroupMembership / Note）。
 * - 全程 Dispatchers.IO；無權限或例外誠實降級回傳空集合，絕不 crash。
 * - 隱私紅線：原始聯絡人姓名、電話、公司職稱絕不外送 AI API。
 */
object ContactBridge {

    private const val VIP_SYNC_THROTTLE_MS = 10L * 60L * 1000L // ≥10 分鐘節流
    private val observerRegistered = AtomicBoolean(false)
    private val observerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    data class ContactRecord(
        val contactId: Long,
        val displayName: String,
        val nickname: String? = null,
        val company: String? = null,
        val title: String? = null,
        val note: String? = null,
        val starred: Boolean = false,
        val groupIds: Set<Long> = emptySet()
    )

    data class ContactGroupInfo(
        val groupId: Long,
        val title: String
    )

    data class ImportCandidate(
        val displayName: String,
        val subject: String,
        val summary: String,
        val isUpdate: Boolean
    )

    data class VipSyncResult(
        val added: Int,
        val removed: Int,
        val totalVip: Int,
        val throttled: Boolean = false
    )

    data class AliasCandidate(
        val alias: String,
        val matchedDisplayName: String,
        val packageName: String?
    )

    fun hasReadContactsPermission(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /** 讀取裝置通訊錄群組清單（Dispatchers.IO，無權限回傳空） */
    suspend fun queryGroups(ctx: Context): List<ContactGroupInfo> = withContext(Dispatchers.IO) {
        if (!hasReadContactsPermission(ctx)) return@withContext emptyList()
        val result = mutableListOf<ContactGroupInfo>()
        val seenTitles = HashSet<String>()
        val projection = arrayOf(
            ContactsContract.Groups._ID,
            ContactsContract.Groups.TITLE,
            ContactsContract.Groups.DELETED
        )
        try {
            ctx.contentResolver.query(
                ContactsContract.Groups.CONTENT_URI,
                projection,
                "${ContactsContract.Groups.DELETED} = 0",
                null,
                "${ContactsContract.Groups.TITLE} ASC"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val title = cursor.getString(1)?.trim().orEmpty()
                    if (title.isNotEmpty() && !title.startsWith("System Group:", ignoreCase = true) && seenTitles.add(title.lowercase(Locale.ROOT))) {
                        result.add(ContactGroupInfo(groupId = id, title = title))
                    }
                }
            }
        } catch (_: SecurityException) {
            return@withContext emptyList()
        } catch (_: Exception) {
            return@withContext emptyList()
        }
        result
    }

    /** 透過 ContactsContract.Contacts 與 ContactsContract.Data MIMETYPE 查詢完整聯絡人資訊（Dispatchers.IO） */
    suspend fun queryContacts(ctx: Context): List<ContactRecord> = withContext(Dispatchers.IO) {
        if (!hasReadContactsPermission(ctx)) return@withContext emptyList()

        data class MutableRecord(
            val contactId: Long,
            var displayName: String,
            var nickname: String? = null,
            var company: String? = null,
            var title: String? = null,
            var note: String? = null,
            var starred: Boolean = false,
            val groupIds: MutableSet<Long> = LinkedHashSet()
        )

        val map = LinkedHashMap<Long, MutableRecord>()
        try {
            ctx.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(
                    ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                    ContactsContract.Contacts.STARRED
                ),
                null,
                null,
                "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val name = cursor.getString(1)?.trim().orEmpty()
                    val starred = cursor.getInt(2) == 1
                    if (name.isNotEmpty()) {
                        map[id] = MutableRecord(contactId = id, displayName = name, starred = starred)
                    }
                }
            }

            if (map.isEmpty()) return@withContext emptyList()

            val mimeTypes = arrayOf(
                ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,
                ContactsContract.CommonDataKinds.Nickname.CONTENT_ITEM_TYPE,
                ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE,
                ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE,
                ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE
            )
            val placeholders = mimeTypes.joinToString(",") { "?" }
            ctx.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(
                    ContactsContract.Data.CONTACT_ID,
                    ContactsContract.Data.MIMETYPE,
                    ContactsContract.Data.DATA1,
                    ContactsContract.Data.DATA4
                ),
                "${ContactsContract.Data.MIMETYPE} IN ($placeholders)",
                mimeTypes,
                null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val cid = cursor.getLong(0)
                    val rec = map[cid] ?: continue
                    val mime = cursor.getString(1) ?: continue
                    val data1 = cursor.getString(2)?.trim().orEmpty()
                    val data4 = cursor.getString(3)?.trim().orEmpty()
                    when (mime) {
                        ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE -> {
                            if (rec.displayName.isBlank() && data1.isNotBlank()) rec.displayName = data1
                        }
                        ContactsContract.CommonDataKinds.Nickname.CONTENT_ITEM_TYPE -> {
                            if (data1.isNotBlank() && rec.nickname.isNullOrBlank()) rec.nickname = data1
                        }
                        ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE -> {
                            if (data1.isNotBlank() && rec.company.isNullOrBlank()) rec.company = data1
                            if (data4.isNotBlank() && rec.title.isNullOrBlank()) rec.title = data4
                        }
                        ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE -> {
                            data1.toLongOrNull()?.let { rec.groupIds.add(it) }
                        }
                        ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE -> {
                            if (data1.isNotBlank() && rec.note.isNullOrBlank()) rec.note = data1.take(80)
                        }
                    }
                }
            }
        } catch (_: SecurityException) {
            return@withContext emptyList()
        } catch (_: Exception) {
            return@withContext emptyList()
        }

        map.values.map {
            ContactRecord(
                contactId = it.contactId,
                displayName = it.displayName,
                nickname = it.nickname,
                company = it.company,
                title = it.title,
                note = it.note,
                starred = it.starred,
                groupIds = it.groupIds.toSet()
            )
        }
    }

    /**
     * 關係與單位匯入嚮導（零 AI token）：
     * 讀取具公司/職稱/群組/備註之聯絡人，先與既有 contacts 匯入記憶 diff，僅回傳新增或有變動者。
     */
    suspend fun buildRelationshipImportCandidates(
        ctx: Context,
        db: AppDatabase
    ): List<ImportCandidate> = withContext(Dispatchers.IO) {
        val contacts = queryContacts(ctx)
        if (contacts.isEmpty()) return@withContext emptyList()
        val groupNameById = queryGroups(ctx).associate { it.groupId to it.title }
        val existingContactsMemories = db.memoryDao().all().filter {
            it.category == MemoryEntity.CATEGORY_PERSON &&
                it.status == MemoryEntity.STATUS_ACTIVE &&
                (it.sourceType == MemoryEntity.SOURCE_CONTACTS || it.sourceRef?.startsWith("通訊錄匯入") == true)
        }
        val existingByNormSubject = existingContactsMemories.associateBy {
            MemoryEngine.normalizeSubjectKey(it.subject)
        }

        val candidates = mutableListOf<ImportCandidate>()
        for (c in contacts) {
            val parts = mutableListOf<String>()
            if (!c.company.isNullOrBlank()) parts.add("單位：${c.company}")
            if (!c.title.isNullOrBlank()) parts.add("職稱：${c.title}")
            val groupNames = c.groupIds.mapNotNull { groupNameById[it] }.distinct().take(3)
            if (groupNames.isNotEmpty()) parts.add("群組：${groupNames.joinToString("、")}")
            if (!c.nickname.isNullOrBlank()) parts.add("暱稱：${c.nickname}")
            if (!c.note.isNullOrBlank()) parts.add("備註：${c.note.replace("\n", " ").take(40)}")
            if (parts.isEmpty()) continue

            val summary = parts.joinToString("；")
            val normKey = MemoryEngine.normalizeSubjectKey(c.displayName)
            val existing = existingByNormSubject[normKey]
            if (existing != null && MemoryEngine.isSubstantiallySameContent(existing.content, summary)) {
                continue // 無變動，跳過避免重複清單轟炸
            }
            candidates.add(
                ImportCandidate(
                    displayName = c.displayName,
                    subject = c.displayName,
                    summary = summary,
                    isUpdate = existing != null
                )
            )
        }
        candidates
    }

    /** 批次寫入使用者勾選之通訊錄關係記憶（privacyTier 保持 normal、provenance=通訊錄匯入、零 AI token） */
    suspend fun importSelectedCandidates(
        db: AppDatabase,
        selected: List<ImportCandidate>
    ): Int = withContext(Dispatchers.IO) {
        var count = 0
        for (cand in selected) {
            MemoryEngine.writeMemoryWithConflictDetection(
                db = db,
                category = MemoryEntity.CATEGORY_PERSON,
                subject = cand.subject,
                content = cand.summary,
                layer = MemoryEntity.LAYER_LONG_TERM,
                privacyTier = MemoryEntity.PRIVACY_NORMAL,
                confidence = MemoryEntity.CONFIDENCE_CONFIRMED,
                sourceType = MemoryEntity.SOURCE_CONTACTS,
                sourceRef = "通訊錄匯入",
                forceReplaceConflict = cand.isUpdate
            )
            count++
        }
        if (count > 0) {
            ContactContext.invalidateCache()
        }
        count
    }

    /**
     * VIP 信任圈連動白名單（AiRuleEntity kind=whitelist，ruleText 前綴 [contacts_vip]）：
     * 僅增刪 contacts_vip 來源規則，不動使用者手動規則；自動觸發節流 ≥10 分鐘。
     */
    suspend fun syncVipTrustCircle(
        ctx: Context,
        db: AppDatabase,
        mode: String = PhantomPrefs.getContactsVipMode(ctx),
        groupId: Long? = PhantomPrefs.getContactsVipGroupId(ctx),
        force: Boolean = false
    ): VipSyncResult = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!force && (now - PhantomPrefs.getContactsVipLastSyncAt(ctx)) < VIP_SYNC_THROTTLE_MS) {
            val currentCount = db.aiRuleDao().all().count {
                it.kind == AiRuleEntity.KIND_WHITELIST &&
                    it.ruleText.startsWith(AiRuleEntity.CONTACTS_VIP_RULE_PREFIX)
            }
            return@withContext VipSyncResult(added = 0, removed = 0, totalVip = currentCount, throttled = true)
        }
        if (mode == PhantomPrefs.CONTACTS_VIP_MODE_OFF || !hasReadContactsPermission(ctx)) {
            return@withContext VipSyncResult(added = 0, removed = 0, totalVip = 0)
        }

        val contacts = queryContacts(ctx)
        val vipNames = contacts.filter { c ->
            when (mode) {
                PhantomPrefs.CONTACTS_VIP_MODE_STARRED -> c.starred
                PhantomPrefs.CONTACTS_VIP_MODE_GROUP -> groupId != null && groupId in c.groupIds
                else -> false
            }
        }.map { it.displayName.trim() }.filter { it.isNotEmpty() }.distinct()
        val vipSetLower = vipNames.map { it.lowercase(Locale.ROOT) }.toSet()

        val allRules = db.aiRuleDao().all()
        val existingVipRules = allRules.filter {
            it.kind == AiRuleEntity.KIND_WHITELIST &&
                it.ruleText.startsWith(AiRuleEntity.CONTACTS_VIP_RULE_PREFIX)
        }
        val existingSendersLower = existingVipRules.mapNotNull { it.sender?.trim()?.lowercase(Locale.ROOT) }.toSet()

        var added = 0
        var removed = 0
        for (rule in existingVipRules) {
            val s = rule.sender?.trim()?.lowercase(Locale.ROOT).orEmpty()
            if (s.isEmpty() || s !in vipSetLower) {
                db.aiRuleDao().deleteById(rule.id)
                removed++
            }
        }
        for (name in vipNames) {
            if (name.lowercase(Locale.ROOT) !in existingSendersLower) {
                val res = com.phantom.checker.data.WhitelistRuleHelper.upsertWhitelist(
                    dao = db.aiRuleDao(),
                    ruleText = "${AiRuleEntity.CONTACTS_VIP_RULE_PREFIX}通訊錄 VIP 信任圈：$name",
                    sender = name,
                    packageName = null
                )
                if (res.status == com.phantom.checker.data.WhitelistRuleHelper.Status.INSERTED ||
                    res.status == com.phantom.checker.data.WhitelistRuleHelper.Status.REACTIVATED
                ) {
                    added++
                }
            }
        }
        PhantomPrefs.setContactsVipLastSyncAt(ctx, now)
        if (added > 0 || removed > 0) {
            NotificationCollectorService.invalidateRuleCaches()
            ContactContext.invalidateCache()
        }
        VipSyncResult(added = added, removed = removed, totalVip = vipNames.size)
    }

    /** 整批撤銷 contacts_vip 來源白名單（不動使用者手動規則） */
    suspend fun revokeVipTrustCircle(db: AppDatabase): Int = withContext(Dispatchers.IO) {
        val existingVipRules = db.aiRuleDao().all().filter {
            it.kind == AiRuleEntity.KIND_WHITELIST &&
                it.ruleText.startsWith(AiRuleEntity.CONTACTS_VIP_RULE_PREFIX)
        }
        existingVipRules.forEach { db.aiRuleDao().deleteById(it.id) }
        if (existingVipRules.isNotEmpty()) {
            NotificationCollectorService.invalidateRuleCaches()
            ContactContext.invalidateCache()
        }
        existingVipRules.size
    }

    /** 本機比對寄件者暱稱是否疑似某通訊錄聯絡人（零 AI 外送，供聊天確認卡綁定） */
    suspend fun findAliasCandidateForSender(
        ctx: Context,
        db: AppDatabase,
        sender: String,
        packageName: String? = null
    ): AliasCandidate? = withContext(Dispatchers.IO) {
        val cleanSender = sender.trim()
        if (cleanSender.length < 2 || !hasReadContactsPermission(ctx)) return@withContext null
        if (db.contactAliasDao().findByAlias(cleanSender, packageName) != null) return@withContext null

        val contacts = queryContacts(ctx)
        val normSender = cleanSender.lowercase(Locale.ROOT)
        val matched = contacts.firstOrNull { c ->
            val nick = c.nickname?.trim()?.lowercase(Locale.ROOT)
            val disp = c.displayName.trim().lowercase(Locale.ROOT)
            (nick != null && nick.length >= 2 && (nick == normSender || nick.contains(normSender) || normSender.contains(nick))) ||
                (disp != normSender && disp.length >= 2 && (disp.contains(normSender) || normSender.contains(disp)))
        } ?: return@withContext null

        AliasCandidate(
            alias = cleanSender,
            matchedDisplayName = matched.displayName,
            packageName = packageName
        )
    }

    /** 註冊通訊錄 ContentObserver（變更時節流 ≥10min 自動刷新 VIP 信任圈與快取） */
    fun registerContactsObserverIfPermitted(ctx: Context) {
        val appCtx = ctx.applicationContext
        if (!hasReadContactsPermission(appCtx)) return
        if (!observerRegistered.compareAndSet(false, true)) return
        try {
            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    ContactContext.invalidateCache()
                    if (PhantomPrefs.getContactsVipMode(appCtx) != PhantomPrefs.CONTACTS_VIP_MODE_OFF) {
                        observerScope.launch {
                            runCatching {
                                syncVipTrustCircle(appCtx, AppDatabaseHolder.db(appCtx), force = false)
                            }
                        }
                    }
                }
            }
            appCtx.contentResolver.registerContentObserver(
                ContactsContract.Contacts.CONTENT_URI,
                true,
                observer
            )
        } catch (_: Exception) {
            observerRegistered.set(false)
        }
    }
}
