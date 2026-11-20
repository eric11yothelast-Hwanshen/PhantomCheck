package com.phantom.checker.ui

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.phantom.checker.data.ContactAliasEntity
import com.phantom.checker.service.AppDatabaseHolder
import com.phantom.checker.util.ContactBridge
import com.phantom.checker.util.ContactContext
import com.phantom.checker.util.PhantomPrefs
import com.phantom.checker.util.PhantomSettingsBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * #336 通訊錄整合設定元件（暱稱綁定、關係單位匯入嚮導、VIP 信任圈連動）：
 * - 可獨立嵌入 SettingsActivity 與 AssistantHubActivity（#340）。
 * - 嚴守隱私紅線：原始通訊錄絕不外送 AI，純本機比對與匯入。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ContactsIntegrationSection(
    onOpenPermissionHub: (() -> Unit)? = null
) {
    val ctx = LocalContext.current
    val appCtx = ctx.applicationContext
    val scope = rememberCoroutineScope()
    val db = remember(appCtx) { AppDatabaseHolder.db(appCtx) }

    var hasContactsPerm by remember { mutableStateOf(ContactBridge.hasReadContactsPermission(appCtx)) }
    val aliases by remember(db) { db.contactAliasDao().observeAll() }.collectAsState(initial = emptyList())
    var groups by remember { mutableStateOf<List<ContactBridge.ContactGroupInfo>>(emptyList()) }
    var vipMode by remember { mutableStateOf(PhantomPrefs.getContactsVipMode(appCtx)) }
    var vipGroupId by remember { mutableStateOf(PhantomPrefs.getContactsVipGroupId(appCtx)) }

    var showAddAliasDialog by remember { mutableStateOf(false) }
    var showImportWizard by remember { mutableStateOf(false) }
    var importCandidates by remember { mutableStateOf<List<ContactBridge.ImportCandidate>>(emptyList()) }
    var selectedImportKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isBusy by remember { mutableStateOf(false) }

    val contactsPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasContactsPerm = granted || ContactBridge.hasReadContactsPermission(appCtx)
        ContactContext.invalidateCache()
        if (hasContactsPerm) {
            ContactBridge.registerContactsObserverIfPermitted(appCtx)
        }
        Toast.makeText(
            ctx,
            if (hasContactsPerm) "已授予通訊錄讀取權限" else "未授予通訊錄權限，相關功能維持停用",
            Toast.LENGTH_SHORT
        ).show()
    }

    LaunchedEffect(hasContactsPerm) {
        if (hasContactsPerm) {
            ContactBridge.registerContactsObserverIfPermitted(appCtx)
            groups = ContactBridge.queryGroups(appCtx)
        } else {
            groups = emptyList()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "通訊錄整合（純本機存取，絕不外送 AI）",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "支援通訊軟體暱稱綁定本名、零 AI token 關係單位匯入，以及將通訊錄最愛／群組同步為 VIP 信任圈白名單。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (!hasContactsPerm) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "通訊錄權限：未授予",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { contactsPermLauncher.launch(Manifest.permission.READ_CONTACTS) }) {
                    Text("授予權限")
                }
                if (onOpenPermissionHub != null) {
                    TextButton(onClick = onOpenPermissionHub) {
                        Text("權限中心")
                    }
                }
            }
        }

        // A. 暱稱綁定管理（不強制要求通訊錄權限也可手動綁定）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "暱稱 ↔ 本名綁定（${aliases.size} 筆）",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            TextButton(onClick = { showAddAliasDialog = true }) {
                Text("＋ 新增綁定")
            }
        }
        if (aliases.isEmpty()) {
            Text(
                text = "尚未綁定暱稱。綁定後防詐比對會將該暱稱視為熟人寄件者。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                aliases.take(8).forEach { entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${entry.alias} → ${entry.displayName}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val sub = listOfNotNull(
                                entry.packageName?.takeIf { it.isNotBlank() }?.let { "App：$it" },
                                entry.note?.takeIf { it.isNotBlank() }
                            ).joinToString(" · ")
                            if (sub.isNotBlank()) {
                                Text(
                                    text = sub,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        db.contactAliasDao().deleteById(entry.id)
                                    }
                                    ContactContext.invalidateCache()
                                    Toast.makeText(ctx, "已刪除暱稱綁定", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "刪除綁定",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 4.dp))

        // B. 關係與單位資訊匯入嚮導（零 AI token）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "關係與單位資訊匯入",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "從通訊錄讀取公司、職稱與群組，僅列出新增或有變動項目（零 AI token）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(
                enabled = hasContactsPerm && !isBusy,
                onClick = {
                    isBusy = true
                    scope.launch {
                        val cands = ContactBridge.buildRelationshipImportCandidates(appCtx, db)
                        importCandidates = cands
                        selectedImportKeys = cands.map { it.subject }.toSet()
                        isBusy = false
                        if (cands.isEmpty()) {
                            Toast.makeText(ctx, "通訊錄沒有新的或有變動的關係單位資訊", Toast.LENGTH_SHORT).show()
                        } else {
                            showImportWizard = true
                        }
                    }
                }
            ) {
                Text("掃描匯入")
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 4.dp))

        // C. VIP 信任圈連動白名單
        Text(
            text = "VIP 信任圈連動白名單",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = "將通訊錄「我的最愛」或指定群組同步為信任白名單（僅管理 contacts_vip 來源，不動手動規則）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = vipMode == PhantomPrefs.CONTACTS_VIP_MODE_OFF,
                onClick = {
                    vipMode = PhantomPrefs.CONTACTS_VIP_MODE_OFF
                    PhantomPrefs.setContactsVipMode(appCtx, vipMode)
                    PhantomSettingsBus.notifyChanged()
                },
                label = { Text("停用") }
            )
            FilterChip(
                selected = vipMode == PhantomPrefs.CONTACTS_VIP_MODE_STARRED,
                onClick = {
                    vipMode = PhantomPrefs.CONTACTS_VIP_MODE_STARRED
                    PhantomPrefs.setContactsVipMode(appCtx, vipMode)
                    PhantomSettingsBus.notifyChanged()
                },
                label = { Text("我的最愛（STARRED）") }
            )
            FilterChip(
                selected = vipMode == PhantomPrefs.CONTACTS_VIP_MODE_GROUP,
                onClick = {
                    vipMode = PhantomPrefs.CONTACTS_VIP_MODE_GROUP
                    PhantomPrefs.setContactsVipMode(appCtx, vipMode)
                    PhantomSettingsBus.notifyChanged()
                },
                label = { Text("指定群組") }
            )
        }
        if (vipMode == PhantomPrefs.CONTACTS_VIP_MODE_GROUP && groups.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                groups.take(10).forEach { grp ->
                    FilterChip(
                        selected = vipGroupId == grp.groupId,
                        onClick = {
                            vipGroupId = grp.groupId
                            PhantomPrefs.setContactsVipGroupId(appCtx, grp.groupId)
                        },
                        label = { Text(grp.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                enabled = hasContactsPerm && vipMode != PhantomPrefs.CONTACTS_VIP_MODE_OFF && !isBusy,
                onClick = {
                    isBusy = true
                    scope.launch {
                        val res = ContactBridge.syncVipTrustCircle(
                            ctx = appCtx,
                            db = db,
                            mode = vipMode,
                            groupId = vipGroupId,
                            force = true
                        )
                        isBusy = false
                        Toast.makeText(
                            ctx,
                            "VIP 同步完成：新增 ${res.added} 筆、移除 ${res.removed} 筆（共 ${res.totalVip} 位）",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("立即同步 VIP")
            }
            OutlinedButton(
                enabled = !isBusy,
                onClick = {
                    isBusy = true
                    scope.launch {
                        val removed = ContactBridge.revokeVipTrustCircle(db)
                        vipMode = PhantomPrefs.CONTACTS_VIP_MODE_OFF
                        PhantomPrefs.setContactsVipMode(appCtx, vipMode)
                        isBusy = false
                        Toast.makeText(
                            ctx,
                            "已整批撤銷 $removed 筆通訊錄 VIP 白名單（手動規則不受影響）",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("整批撤銷")
            }
        }
    }

    // 新增暱稱綁定對話框
    if (showAddAliasDialog) {
        var aliasInput by remember { mutableStateOf("") }
        var displayNameInput by remember { mutableStateOf("") }
        var pkgInput by remember { mutableStateOf("") }
        var noteInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddAliasDialog = false },
            title = { Text("新增暱稱綁定") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = aliasInput,
                        onValueChange = { aliasInput = it },
                        label = { Text("通訊軟體暱稱（必填）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = displayNameInput,
                        onValueChange = { displayNameInput = it },
                        label = { Text("對應本名／聯絡人名（必填）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = pkgInput,
                        onValueChange = { pkgInput = it },
                        label = { Text("來源 App 套件名（可留空套用全部）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = noteInput,
                        onValueChange = { noteInput = it },
                        label = { Text("備註（選填）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = aliasInput.isNotBlank() && displayNameInput.isNotBlank(),
                    onClick = {
                        val a = aliasInput.trim()
                        val d = displayNameInput.trim()
                        val p = pkgInput.trim().takeIf { it.isNotEmpty() }
                        val n = noteInput.trim().takeIf { it.isNotEmpty() }
                        showAddAliasDialog = false
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                db.contactAliasDao().insert(
                                    ContactAliasEntity(
                                        alias = a,
                                        displayName = d,
                                        packageName = p,
                                        note = n
                                    )
                                )
                            }
                            ContactContext.invalidateCache()
                            Toast.makeText(ctx, "已綁定「$a → $d」", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("儲存綁定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddAliasDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 關係單位匯入嚮導對話框
    if (showImportWizard) {
        AlertDialog(
            onDismissRequest = { showImportWizard = false },
            title = { Text("通訊錄關係單位匯入（${selectedImportKeys.size}/${importCandidates.size}）") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "勾選要匯入為「人物」一般記憶的項目（來源標記為通訊錄匯入，可隨時於記憶頁刪除）：",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    importCandidates.forEach { cand ->
                        val checked = cand.subject in selectedImportKeys
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedImportKeys = if (checked) {
                                        selectedImportKeys - cand.subject
                                    } else {
                                        selectedImportKeys + cand.subject
                                    }
                                }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { isChecked ->
                                    selectedImportKeys = if (isChecked) {
                                        selectedImportKeys + cand.subject
                                    } else {
                                        selectedImportKeys - cand.subject
                                    }
                                }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = cand.displayName + if (cand.isUpdate) "（更新）" else "（新增）",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = cand.summary,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = selectedImportKeys.isNotEmpty(),
                    onClick = {
                        val toImport = importCandidates.filter { it.subject in selectedImportKeys }
                        showImportWizard = false
                        scope.launch {
                            val imported = ContactBridge.importSelectedCandidates(db, toImport)
                            Toast.makeText(ctx, "已匯入 $imported 筆人物關係記憶", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("確認匯入")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportWizard = false }) {
                    Text("取消")
                }
            }
        )
    }
}
