package com.phantom.checker.ui

import com.phantom.checker.ui.components.pressableScale
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.phantom.checker.R
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
    val resCtx = LocalContext.current
    val ctx = LocalContext.current
    val appCtx = ctx.applicationContext
    val scope = rememberCoroutineScope()
    val db = remember(appCtx) { AppDatabaseHolder.db(appCtx) }

    var hasContactsPerm by remember { mutableStateOf(ContactBridge.hasReadContactsPermission(appCtx)) }
    val aliases by remember(db) { db.contactAliasDao().observeAll() }.collectAsState(initial = emptyList())
    var groups by remember { mutableStateOf<List<ContactBridge.ContactGroupInfo>>(emptyList()) }
    var vipMode by remember { mutableStateOf(PhantomPrefs.getContactsVipMode(appCtx)) }
    var vipGroupId by remember { mutableStateOf(PhantomPrefs.getContactsVipGroupId(appCtx)) }

    var showAllAliases by remember { mutableStateOf(false) }
    var showAllGroups by remember { mutableStateOf(false) }
    var showAddAliasDialog by remember { mutableStateOf(false) }
    var showImportWizard by remember { mutableStateOf(false) }
    var importCandidates by remember { mutableStateOf<List<ContactBridge.ImportCandidate>>(emptyList()) }
    var selectedImportKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isBusy by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, appCtx) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val latest = ContactBridge.hasReadContactsPermission(appCtx)
                if (latest != hasContactsPerm) {
                    hasContactsPerm = latest
                    ContactContext.invalidateCache()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

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
            if (hasContactsPerm) resCtx.getString(R.string.contacts_s_9599ea) else resCtx.getString(R.string.contacts_s_83389c),
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
            text = stringResource(R.string.contacts_s_629632),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = stringResource(R.string.contacts_s_4bdcd2),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (!hasContactsPerm) {
            Row(
                modifier = Modifier.fillMaxWidth().pressableScale(0.97f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.contacts_s_335af1),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { contactsPermLauncher.launch(Manifest.permission.READ_CONTACTS) }) {
                    Text(stringResource(R.string.settings_s_45df2d))
                }
                if (onOpenPermissionHub != null) {
                    TextButton(onClick = onOpenPermissionHub) {
                        Text(stringResource(R.string.chat_s_37a4b3))
                    }
                }
            }
        }

        // A. 暱稱綁定管理（不強制要求通訊錄權限也可手動綁定）
        Row(
            modifier = Modifier.fillMaxWidth().pressableScale(0.97f),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.contacts_s_035adb, aliases.size),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            TextButton(onClick = { showAddAliasDialog = true }) {
                Text(stringResource(R.string.contacts_s_904472))
            }
        }
        if (aliases.isEmpty()) {
            Text(
                text = stringResource(R.string.contacts_s_f3eb79),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            val displayedAliases = if (showAllAliases) aliases else aliases.take(8)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                displayedAliases.forEach { entry ->
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
                            val appLabel = entry.packageName
                                ?.takeIf { it.isNotBlank() }
                                ?.let { stringResource(R.string.contacts_app_prefix, it) }
                            val sub = listOfNotNull(
                                appLabel,
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
                                    Toast.makeText(ctx, resCtx.getString(R.string.contacts_s_46f879), Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = resCtx.getString(R.string.contacts_s_f6b564),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                if (aliases.size > 8) {
                    TextButton(onClick = { showAllAliases = !showAllAliases }) {
                        Text(
                            if (showAllAliases) {
                                stringResource(R.string.settings_s_52c158)
                            } else {
                                "${stringResource(R.string.common_view_all)} (${aliases.size})"
                            }
                        )
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
                    text = stringResource(R.string.contacts_s_163c69),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = stringResource(R.string.contacts_s_078ea6),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(
                enabled = hasContactsPerm && !isBusy,
                onClick = {
                    if (isBusy) return@OutlinedButton
                    isBusy = true
                    scope.launch {
                        try {
                            val cands = ContactBridge.buildRelationshipImportCandidates(appCtx, db)
                            importCandidates = cands
                            selectedImportKeys = cands.mapIndexed { idx, cand ->
                                importCandidateKey(cand, idx)
                            }.toSet()
                            if (cands.isEmpty()) {
                                Toast.makeText(ctx, resCtx.getString(R.string.contacts_s_a0b225), Toast.LENGTH_SHORT).show()
                            } else {
                                showImportWizard = true
                            }
                        } finally {
                            isBusy = false
                        }
                    }
                }
            ) {
                Text(stringResource(R.string.contacts_s_b9d98a))
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 4.dp))

        // C. VIP 信任圈連動白名單
        Text(
            text = stringResource(R.string.contacts_s_93b357),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = stringResource(R.string.contacts_s_90a7f7),
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
                label = { Text(stringResource(R.string.contacts_s_d989e5)) }
            )
            FilterChip(
                selected = vipMode == PhantomPrefs.CONTACTS_VIP_MODE_STARRED,
                onClick = {
                    vipMode = PhantomPrefs.CONTACTS_VIP_MODE_STARRED
                    PhantomPrefs.setContactsVipMode(appCtx, vipMode)
                    PhantomSettingsBus.notifyChanged()
                },
                label = { Text(stringResource(R.string.contacts_s_12bc94)) }
            )
            FilterChip(
                selected = vipMode == PhantomPrefs.CONTACTS_VIP_MODE_GROUP,
                onClick = {
                    vipMode = PhantomPrefs.CONTACTS_VIP_MODE_GROUP
                    PhantomPrefs.setContactsVipMode(appCtx, vipMode)
                    PhantomSettingsBus.notifyChanged()
                },
                label = { Text(stringResource(R.string.contacts_s_184cd5)) }
            )
        }
        if (vipMode == PhantomPrefs.CONTACTS_VIP_MODE_GROUP && groups.isNotEmpty()) {
            val displayedGroups = if (showAllGroups) groups else groups.take(10)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                displayedGroups.forEach { grp ->
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
            if (groups.size > 10) {
                TextButton(onClick = { showAllGroups = !showAllGroups }) {
                    Text(
                        if (showAllGroups) {
                            stringResource(R.string.settings_s_52c158)
                        } else {
                            "${stringResource(R.string.common_view_all)} (${groups.size})"
                        }
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
                    if (isBusy) return@Button
                    isBusy = true
                    scope.launch {
                        try {
                            val res = ContactBridge.syncVipTrustCircle(
                                ctx = appCtx,
                                db = db,
                                mode = vipMode,
                                groupId = vipGroupId,
                                force = true
                            )
                            Toast.makeText(
                                ctx,
                                resCtx.getString(R.string.contacts_s_0a4809, res.added, res.removed, res.totalVip),
                                Toast.LENGTH_SHORT
                            ).show()
                        } finally {
                            isBusy = false
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.contacts_s_35dfd7))
            }
            OutlinedButton(
                enabled = !isBusy,
                onClick = {
                    if (isBusy) return@OutlinedButton
                    isBusy = true
                    scope.launch {
                        try {
                            val removed = ContactBridge.revokeVipTrustCircle(db)
                            vipMode = PhantomPrefs.CONTACTS_VIP_MODE_OFF
                            PhantomPrefs.setContactsVipMode(appCtx, vipMode)
                            PhantomSettingsBus.notifyChanged()
                            Toast.makeText(
                                ctx,
                                resCtx.getString(R.string.contacts_s_750de9, removed),
                                Toast.LENGTH_SHORT
                            ).show()
                        } finally {
                            isBusy = false
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.contacts_s_7d25e1))
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
            title = { Text(stringResource(R.string.contacts_s_d9c5a0)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = aliasInput,
                        onValueChange = { aliasInput = it },
                        label = { Text(stringResource(R.string.contacts_s_f5f303)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = displayNameInput,
                        onValueChange = { displayNameInput = it },
                        label = { Text(stringResource(R.string.contacts_s_24f229)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = pkgInput,
                        onValueChange = { pkgInput = it },
                        label = { Text(stringResource(R.string.contacts_s_7bb28a)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = noteInput,
                        onValueChange = { noteInput = it },
                        label = { Text(stringResource(R.string.contacts_s_45dc68)) },
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
                            Toast.makeText(ctx, resCtx.getString(R.string.contacts_s_f09ffa, a, d), Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text(stringResource(R.string.contacts_s_29e0a7))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddAliasDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    // 關係單位匯入嚮導對話框
    if (showImportWizard) {
        AlertDialog(
            onDismissRequest = { showImportWizard = false },
            title = { Text(stringResource(R.string.contacts_s_fa31f2, selectedImportKeys.size, importCandidates.size)) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.contacts_s_bc6431),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    importCandidates.forEachIndexed { idx, cand ->
                        val candKey = importCandidateKey(cand, idx)
                        val checked = candKey in selectedImportKeys
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedImportKeys = if (checked) {
                                        selectedImportKeys - candKey
                                    } else {
                                        selectedImportKeys + candKey
                                    }
                                }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { isChecked ->
                                    selectedImportKeys = if (isChecked) {
                                        selectedImportKeys + candKey
                                    } else {
                                        selectedImportKeys - candKey
                                    }
                                }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = cand.displayName + if (cand.isUpdate) resCtx.getString(R.string.contacts_s_23d80e) else resCtx.getString(R.string.contacts_s_7cfa5b),
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
                        val toImport = importCandidates.filterIndexed { idx, cand ->
                            importCandidateKey(cand, idx) in selectedImportKeys
                        }
                        showImportWizard = false
                        scope.launch {
                            val imported = ContactBridge.importSelectedCandidates(db, toImport)
                            Toast.makeText(ctx, resCtx.getString(R.string.contacts_s_ba3c47, imported), Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text(stringResource(R.string.settings_s_7ddb23))
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportWizard = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

private fun importCandidateKey(cand: ContactBridge.ImportCandidate, index: Int): String =
    if (cand.contactId > 0L) "id:${cand.contactId}" else "idx:$index:${cand.subject}"
