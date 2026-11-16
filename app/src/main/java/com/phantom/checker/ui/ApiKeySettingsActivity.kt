package com.phantom.checker.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.phantom.checker.network.AiClient
import com.phantom.checker.network.AiProviderPreset
import com.phantom.checker.network.AiProviderProfile
import com.phantom.checker.network.ApiKeySource
import com.phantom.checker.ui.components.pressableScale
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.launch
import java.util.UUID

class ApiKeySettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AiClient.init(applicationContext)
        setContent {
            PhantomCheckTheme {
                ApiKeySettingsScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiKeySettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val appCtx = ctx.applicationContext
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var version by remember { mutableIntStateOf(0) }
    val profiles = remember(version) { AiClient.getProfiles(appCtx) }
    val activeId = remember(version) { AiClient.getActiveProviderId(appCtx) }
    val keySource = remember(version) { AiClient.getKeySource(appCtx) }
    val activeProfile = remember(version) { AiClient.getActiveProfile(appCtx) }

    var testingProfileId by remember { mutableStateOf<String?>(null) }
    var testResults by remember { mutableStateOf<Map<String, Pair<Boolean, String>>>(emptyMap()) }

    var showEditorDialog by remember { mutableStateOf(false) }
    var editingProfile by remember { mutableStateOf<AiProviderProfile?>(null) }

    if (showEditorDialog) {
        ProviderEditorDialog(
            initial = editingProfile,
            onDismiss = {
                showEditorDialog = false
                editingProfile = null
            },
            onSave = { saved ->
                AiClient.upsertProfile(appCtx, saved, setActive = profiles.isEmpty())
                showEditorDialog = false
                editingProfile = null
                version++
                scope.launch {
                    snackbarHostState.showSnackbar("已加密儲存 ${saved.name}")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                expandedHeight = 52.dp,
                title = {
                    Text(
                        text = "多 Provider AI 設定",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingProfile = null
                    showEditorDialog = true
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = "新增 Provider")
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        val glassState = remember { HazeState() }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .phantomGlassSource(glassState)
                .padding(padding)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(spring())
                    .phantomGlassSurface(
                        state = glassState,
                        fallbackColor = if (keySource == ApiKeySource.NONE) {
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.90f)
                        } else {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                        },
                        outlineColor = if (keySource == ApiKeySource.NONE) {
                            MaterialTheme.colorScheme.error
                        } else {
                            null
                        },
                        cornerRadius = 20.dp
                    )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    val statusTitle = when (keySource) {
                        ApiKeySource.ENCRYPTED_PREFS ->
                            "目前使用中：${activeProfile?.name ?: "未選擇"}（App 加密儲存）"
                        ApiKeySource.BUILD_CONFIG ->
                            "目前使用中：Mistral（local.properties）"
                        ApiKeySource.NONE ->
                            "目前狀態：尚未設定 AI Provider"
                    }
                    Text(
                        text = statusTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (keySource == ApiKeySource.NONE) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        }
                    )
                    Spacer(Modifier.height(4.dp))
                    if (activeProfile != null) {
                        val tail = AiClient.getMaskedKeyTail(activeProfile.apiKey) ?: "••••"
                        Text(
                            text = "Model：${activeProfile.model}・Key 尾碼：$tail",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    } else {
                        Text(
                            text = "請點擊右下角「+」新增 Mistral、OpenAI、Groq、Gemini、DeepSeek 等相容端點。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            if (profiles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "尚未新增任何 AI Provider 設定",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = {
                                editingProfile = null
                                showEditorDialog = true
                            },
                            modifier = Modifier.pressableScale()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("新增 Provider")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(profiles, key = { it.id }) { profile ->
                        val isActive = profile.id == activeId ||
                            (activeId == null && profiles.firstOrNull()?.id == profile.id)
                        val isTestingThis = testingProfileId == profile.id
                        val testResult = testResults[profile.id]

                        ProviderProfileCard(
                            profile = profile,
                            isActive = isActive,
                            isTesting = isTestingThis,
                            testResult = testResult,
                            glassState = glassState,
                            onSetActive = {
                                AiClient.setActiveProvider(appCtx, profile.id)
                                version++
                                scope.launch {
                                    snackbarHostState.showSnackbar("已切換使用 ${profile.name}")
                                }
                            },
                            onTest = {
                                if (testingProfileId != null) return@ProviderProfileCard
                                scope.launch {
                                    testingProfileId = profile.id
                                    val res = AiClient.testConnection(profile)
                                    testingProfileId = null
                                    val mapped = res.fold(
                                        onSuccess = { msg -> true to msg },
                                        onFailure = { err -> false to (err.message ?: "連線失敗") }
                                    )
                                    testResults = testResults + (profile.id to mapped)
                                }
                            },
                            onEdit = {
                                editingProfile = profile
                                showEditorDialog = true
                            },
                            onDelete = {
                                AiClient.deleteProfile(appCtx, profile.id)
                                testResults = testResults - profile.id
                                version++
                                scope.launch {
                                    snackbarHostState.showSnackbar("已刪除 ${profile.name}")
                                }
                            }
                        )
                    }
                }
            }

            Text(
                text = "所有 API Key 皆以 EncryptedSharedPreferences 加密儲存於本機，支援自訂 Base URL 與 API 路徑。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ProviderProfileCard(
    profile: AiProviderProfile,
    isActive: Boolean,
    isTesting: Boolean,
    testResult: Pair<Boolean, String>?,
    glassState: HazeState,
    onSetActive: () -> Unit,
    onTest: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val tail = AiClient.getMaskedKeyTail(profile.apiKey) ?: "未填"
    val fullEndpoint = AiClient.buildFullUrl(profile.baseUrl, profile.resolvedApiPath())

    PhantomGlassCard(
        glassState = glassState,
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        contentPadding = 12.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isActive) {
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                text = "使用中",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "刪除",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }

        Text(
            text = "Model：${profile.model}・Key：$tail",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = fullEndpoint,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!isActive) {
                Button(
                    onClick = onSetActive,
                    modifier = Modifier
                        .weight(1f)
                        .pressableScale()
                ) {
                    Text("設為使用中")
                }
            }
            OutlinedButton(
                onClick = onTest,
                enabled = !isTesting,
                modifier = Modifier
                    .weight(1f)
                    .pressableScale()
            ) {
                if (isTesting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("測試中")
                } else {
                    Text("測試連線")
                }
            }
            OutlinedButton(
                onClick = onEdit,
                modifier = Modifier.pressableScale()
            ) {
                Text("編輯")
            }
        }

        if (testResult != null) {
            val (ok, msg) = testResult
            Spacer(Modifier.height(6.dp))
            Text(
                text = msg,
                style = MaterialTheme.typography.labelSmall,
                color = if (ok) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProviderEditorDialog(
    initial: AiProviderProfile?,
    onDismiss: () -> Unit,
    onSave: (AiProviderProfile) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var baseUrl by remember { mutableStateOf(initial?.baseUrl ?: "") }
    var apiPath by remember { mutableStateOf(initial?.resolvedApiPath() ?: AiProviderProfile.DEFAULT_API_PATH) }
    var model by remember { mutableStateOf(initial?.model ?: "") }
    var apiKey by remember { mutableStateOf(initial?.apiKey ?: "") }
    var showKey by remember { mutableStateOf(false) }
    var selectedPreset by remember { mutableStateOf<AiProviderPreset?>(null) }
    var showAltModels by remember { mutableStateOf(false) }

    val canSave = name.isNotBlank() &&
        baseUrl.trim().startsWith("http") &&
        apiPath.isNotBlank() &&
        model.isNotBlank() &&
        apiKey.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "新增 AI Provider" else "編輯 AI Provider") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "快速填入預設模板（2026-11 最新模型）：",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AiClient.PRESETS.forEach { preset ->
                        FilterChip(
                            selected = baseUrl.trim() == preset.baseUrl,
                            onClick = {
                                selectedPreset = preset
                                name = preset.name
                                baseUrl = preset.baseUrl
                                apiPath = preset.apiPath
                                model = preset.defaultModel
                            },
                            label = { Text(preset.name) }
                        )
                    }
                }

                val presetForModels = selectedPreset ?: AiClient.PRESETS.firstOrNull {
                    it.baseUrl == baseUrl.trim()
                }
                if (presetForModels != null && presetForModels.alternativeModels.size > 1) {
                    TextButton(
                        onClick = { showAltModels = !showAltModels },
                        modifier = Modifier.align(Alignment.Start)
                    ) {
                        Text(if (showAltModels) "收起常見模型" else "展開 ${presetForModels.name} 其他常見模型")
                    }
                    if (showAltModels) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            presetForModels.alternativeModels.forEach { m ->
                                FilterChip(
                                    selected = model.trim() == m,
                                    onClick = { model = m },
                                    label = { Text(m) }
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("名稱") },
                    placeholder = { Text("例如：Mistral / OpenAI / Gemini") }
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Base URL") },
                    placeholder = { Text("https://api.mistral.ai/") }
                )
                OutlinedTextField(
                    value = apiPath,
                    onValueChange = { apiPath = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("API 路徑") },
                    placeholder = { Text("v1/chat/completions") }
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Model") },
                    placeholder = { Text("mistral-small-latest") }
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("API Key") },
                    placeholder = { Text("請輸入 API Key…") },
                    visualTransformation = if (showKey) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        TextButton(onClick = { showKey = !showKey }) {
                            Text(if (showKey) "隱藏" else "顯示")
                        }
                    }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val normalizedUrl = baseUrl.trim().let {
                        if (it.endsWith("/")) it else "$it/"
                    }
                    onSave(
                        AiProviderProfile(
                            id = initial?.id ?: UUID.randomUUID().toString(),
                            name = name.trim(),
                            baseUrl = normalizedUrl,
                            model = model.trim(),
                            apiKey = apiKey.trim(),
                            apiPath = apiPath.trim().ifEmpty { AiProviderProfile.DEFAULT_API_PATH }
                        )
                    )
                },
                enabled = canSave
            ) {
                Text("儲存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
