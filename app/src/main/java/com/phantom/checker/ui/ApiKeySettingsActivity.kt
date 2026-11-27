package com.phantom.checker.ui

import com.phantom.checker.ui.components.phantomItem
import com.phantom.checker.ui.components.phantomEnter
import androidx.compose.foundation.lazy.itemsIndexed
import android.os.Bundle
import android.view.WindowManager
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.phantom.checker.network.AiClient
import com.phantom.checker.network.AiProviderPreset
import com.phantom.checker.network.AiProviderProfile
import com.phantom.checker.network.ApiKeySource
import com.phantom.checker.ui.components.pressableScale
import com.phantom.checker.util.AiPurpose
import com.phantom.checker.util.PhantomPrefs
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.launch
import java.util.UUID
import com.phantom.checker.R
import androidx.compose.ui.res.stringResource

private fun isValidHttpsBaseUrl(rawUrl: String): Boolean {
    val trimmed = rawUrl.trim()
    if (!trimmed.startsWith("https://", ignoreCase = true)) return false
    val uri = runCatching { android.net.Uri.parse(trimmed) }.getOrNull() ?: return false
    return uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()
}

class ApiKeySettingsActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.phantom.checker.util.AppLocaleHelper.wrapContext(newBase))
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
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
    val resCtx = LocalContext.current
    val ctx = LocalContext.current
    val appCtx = ctx.applicationContext
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var version by remember { mutableIntStateOf(0) }
    val profiles = remember(version) { AiClient.getProfiles(appCtx) }
    val activeId = remember(version) { AiClient.getActiveProviderId(appCtx) }
    val keySource = remember(version) { AiClient.getKeySource(appCtx) }
    val activeProfile = remember(version) { AiClient.getActiveProfile(appCtx) }
    val purposeMap = remember(version) { PhantomPrefs.getPurposeModelMap(appCtx) }

    var testingProfileId by remember { mutableStateOf<String?>(null) }
    var testResults by remember { mutableStateOf<Map<String, Pair<Boolean, String>>>(emptyMap()) }
    var testingPurposeId by remember { mutableStateOf<String?>(null) }
    var purposeTestResults by remember { mutableStateOf<Map<String, Pair<Boolean, String>>>(emptyMap()) }

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
                    snackbarHostState.showSnackbar(resCtx.getString(R.string.api_key_s_986ffa, saved.name))
                }
            }
        )
    }

    com.phantom.checker.ui.components.PhantomPageScaffold(
        title = stringResource(R.string.catalog_ai_providers_title),
        onBack = onBack,
        topBarStyle = com.phantom.checker.ui.components.PhantomTopBarStyle.Medium,
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingProfile = null
                    showEditorDialog = true
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.api_key_s_48a201))
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
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
                            stringResource(R.string.api_key_s_33e4bf, activeProfile?.name ?: resCtx.getString(R.string.api_key_s_3fcc78))
                        ApiKeySource.BUILD_CONFIG ->
                            stringResource(R.string.api_key_s_1ba574)
                        ApiKeySource.NONE ->
                            stringResource(R.string.api_key_s_8de972)
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
                            text = stringResource(R.string.api_key_s_556351, activeProfile.model, tail),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.api_key_s_4cea0e),
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
                            text = stringResource(R.string.api_key_s_cb3133),
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
                            Text(stringResource(R.string.api_key_s_48a201))
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(profiles, key = { _, it -> it.id }) { idx, profile ->
                        val isActive = profile.id == activeId ||
                            (activeId == null && profiles.firstOrNull()?.id == profile.id)
                        val isTestingThis = testingProfileId == profile.id
                        val testResult = testResults[profile.id]

                        Box(modifier = Modifier.animateItem().phantomEnter(idx, key = profile.id)) {
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
                                    snackbarHostState.showSnackbar(resCtx.getString(R.string.api_key_s_14fa43, profile.name))
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
                                        onFailure = { err -> false to (err.message ?: resCtx.getString(R.string.api_key_s_380f60)) }
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
                                    snackbarHostState.showSnackbar(resCtx.getString(R.string.api_key_s_fc4ffb, profile.name))
                                }
                            }
                        )
                        }
                    }

                    phantomItem(profiles.size.coerceAtMost(6), key = "purpose_models_card") {
                        PurposeModelsCard(
                            profiles = profiles,
                            activeProfile = activeProfile,
                            purposeMap = purposeMap,
                            testingPurposeId = testingPurposeId,
                            purposeTestResults = purposeTestResults,
                            glassState = glassState,
                            onSelectPurposeProfile = { purpose, profileId ->
                                PhantomPrefs.setPurposeProfileId(appCtx, purpose.id, profileId)
                                purposeTestResults = purposeTestResults - purpose.id
                                version++
                                val label = if (profileId == null) {
                                    resCtx.getString(R.string.settings_s_4f2580)
                                } else {
                                    profiles.firstOrNull { it.id == profileId }?.name ?: resCtx.getString(R.string.common_selected)
                                }
                                scope.launch {
                                    snackbarHostState.showSnackbar(resCtx.getString(R.string.api_key_s_d1abc6, purpose.label, label))
                                }
                            },
                            onTestPurpose = { purpose ->
                                if (testingPurposeId != null) return@PurposeModelsCard
                                val target = AiClient.getProfileForPurpose(appCtx, purpose)
                                if (target == null) {
                                    purposeTestResults = purposeTestResults + (purpose.id to (false to resCtx.getString(R.string.api_key_s_26bea4)))
                                    return@PurposeModelsCard
                                }
                                scope.launch {
                                    testingPurposeId = purpose.id
                                    val res = AiClient.testConnection(target)
                                    testingPurposeId = null
                                    val mapped = res.fold(
                                        onSuccess = { msg -> true to msg },
                                        onFailure = { err -> false to (err.message ?: resCtx.getString(R.string.api_key_s_380f60)) }
                                    )
                                    purposeTestResults = purposeTestResults + (purpose.id to mapped)
                                }
                            }
                        )
                    }
                }
            }

            Text(
                text = stringResource(R.string.api_key_s_2e4920),
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
    val tail = AiClient.getMaskedKeyTail(profile.apiKey) ?: stringResource(R.string.api_key_s_e71493)
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
                                text = stringResource(R.string.api_key_s_e6aa2c),
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
                    contentDescription = stringResource(R.string.common_delete),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }

        Text(
            text = "Model：${profile.model}・Key：$tail",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        val isHttpsEndpoint = isValidHttpsBaseUrl(profile.baseUrl)
        Text(
            text = fullEndpoint,
            style = MaterialTheme.typography.labelSmall,
            color = if (isHttpsEndpoint) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.warning
            },
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
                    Text(stringResource(R.string.api_key_s_aa968a))
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
                    Text(stringResource(R.string.api_key_s_9c2e54))
                } else {
                    Text(stringResource(R.string.settings_s_48b807))
                }
            }
            OutlinedButton(
                onClick = onEdit,
                modifier = Modifier.pressableScale()
            ) {
                Text(stringResource(R.string.common_edit))
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
    // 不回填既有明碼 API Key，編輯時留空代表沿用既有加密金鑰，杜絕明碼洩漏
    var apiKey by remember { mutableStateOf("") }
    var selectedPreset by remember { mutableStateOf<AiProviderPreset?>(null) }
    var showAltModels by remember { mutableStateOf(false) }

    val existingKeyTail = remember(initial?.apiKey) {
        AiClient.getMaskedKeyTail(initial?.apiKey)
    }
    val hasExistingKey = !initial?.apiKey.isNullOrBlank()
    val isHttpsBaseUrlValid = isValidHttpsBaseUrl(baseUrl)
    val showBaseUrlError = baseUrl.isNotBlank() && !isHttpsBaseUrlValid

    val canSave = name.isNotBlank() &&
        isHttpsBaseUrlValid &&
        apiPath.isNotBlank() &&
        model.isNotBlank() &&
        (apiKey.isNotBlank() || hasExistingKey)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) stringResource(R.string.api_key_s_37d5aa) else stringResource(R.string.api_key_s_c91195)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.api_key_s_f22843),
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
                        Text(if (showAltModels) stringResource(R.string.api_key_s_1015e5) else stringResource(R.string.api_key_s_4b21e5, presetForModels.name))
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
                    label = { Text(stringResource(R.string.api_key_s_e0d245)) },
                    placeholder = { Text(stringResource(R.string.api_key_s_a74d09)) }
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = showBaseUrlError,
                    label = { Text("Base URL (HTTPS)") },
                    placeholder = { Text("https://api.mistral.ai/") },
                    supportingText = if (showBaseUrlError) {
                        {
                            Text(
                                text = "HTTPS (https://...)",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    } else null
                )
                OutlinedTextField(
                    value = apiPath,
                    onValueChange = { apiPath = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.api_key_s_a45647)) },
                    placeholder = { Text("v1/chat/completions") }
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Model") },
                    placeholder = { Text("mistral-small-2603") }
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.assistant_hub_s_8b7c08)) },
                    placeholder = {
                        Text(existingKeyTail ?: stringResource(R.string.api_key_s_da07b7))
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        autoCorrectEnabled = false
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!isValidHttpsBaseUrl(baseUrl)) return@Button
                    val resolvedApiKey = apiKey.trim().ifEmpty { initial?.apiKey?.trim().orEmpty() }
                    if (resolvedApiKey.isEmpty()) return@Button
                    val normalizedUrl = baseUrl.trim().let {
                        if (it.endsWith("/")) it else "$it/"
                    }
                    onSave(
                        AiProviderProfile(
                            id = initial?.id ?: UUID.randomUUID().toString(),
                            name = name.trim(),
                            baseUrl = normalizedUrl,
                            model = model.trim(),
                            apiKey = resolvedApiKey,
                            apiPath = apiPath.trim().ifEmpty { AiProviderProfile.DEFAULT_API_PATH }
                        )
                    )
                },
                enabled = canSave
            ) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

@Composable
private fun PurposeModelsCard(
    profiles: List<AiProviderProfile>,
    activeProfile: AiProviderProfile?,
    purposeMap: Map<String, String>,
    testingPurposeId: String?,
    purposeTestResults: Map<String, Pair<Boolean, String>>,
    glassState: HazeState,
    onSelectPurposeProfile: (AiPurpose, String?) -> Unit,
    onTestPurpose: (AiPurpose) -> Unit
) {
    val resCtx = LocalContext.current
    PhantomGlassCard(
        glassState = glassState,
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        contentPadding = 14.dp
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val purposeList = remember { AiPurpose.entries.filter { it != AiPurpose.AUTO_MEMO } }
            Text(
                text = stringResource(R.string.api_key_s_32231d, purposeList.size),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.api_key_s_a234d9),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            purposeList.forEachIndexed { idx, purpose ->
                if (idx > 0) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                }
                val mappedId = purposeMap[purpose.id]
                val mappedProfile = mappedId?.let { id -> profiles.firstOrNull { it.id == id } }
                val effectiveProfile = mappedProfile ?: activeProfile
                val displayModel = if (mappedProfile != null) {
                    "${mappedProfile.name}（${mappedProfile.model}）"
                } else {
                    resCtx.getString(R.string.api_key_s_00a4fd, activeProfile?.model ?: resCtx.getString(R.string.settings_s_79aacc))
                }
                var dropdownExpanded by remember(purpose.id) { mutableStateOf(false) }
                val isTesting = testingPurposeId == purpose.id
                val testResult = purposeTestResults[purpose.id]

                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = purpose.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedButton(
                                onClick = { dropdownExpanded = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "$displayModel ▾",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            DropdownMenu(
                                expanded = dropdownExpanded,
                                onDismissRequest = { dropdownExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = (if (mappedProfile == null) "✓ " else "   ") + stringResource(R.string.settings_s_4f2580),
                                            fontWeight = if (mappedProfile == null) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        dropdownExpanded = false
                                        onSelectPurposeProfile(purpose, null)
                                    }
                                )
                                profiles.forEach { profile ->
                                    val isSelected = mappedProfile?.id == profile.id
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = (if (isSelected) "✓ " else "   ") + "${profile.name}（${profile.model}）",
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            dropdownExpanded = false
                                            onSelectPurposeProfile(purpose, profile.id)
                                        }
                                    )
                                }
                            }
                        }

                        TextButton(
                            onClick = { onTestPurpose(purpose) },
                            enabled = !isTesting && effectiveProfile != null
                        ) {
                            if (isTesting) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Text(resCtx.getString(R.string.settings_s_48b807), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }

                    if (testResult != null) {
                        val (ok, msg) = testResult
                        Text(
                            text = if (ok) "✓ $msg" else "✕ $msg",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}
