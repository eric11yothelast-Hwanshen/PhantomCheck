package com.phantom.checker.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import com.phantom.checker.ui.components.glassSource
import com.phantom.checker.ui.components.glassSurface
import com.phantom.checker.ui.components.pressableScale
import dev.chrisbanes.haze.HazeState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.phantom.checker.network.ApiKeySource
import com.phantom.checker.network.MistralClient
import kotlinx.coroutines.launch

class ApiKeySettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MistralClient.init(applicationContext)
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

    var inputKey by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var statusVersion by remember { mutableIntStateOf(0) }

    val keySource = remember(statusVersion) { MistralClient.getKeySource(appCtx) }
    val activeKey = remember(statusVersion) { MistralClient.getActiveApiKey(appCtx) }
    val maskedTail = remember(statusVersion) { MistralClient.getMaskedKeyTail(activeKey) }
    val hasStoredKey = remember(statusVersion) { MistralClient.getStoredApiKey(appCtx) != null }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mistral API Key 設定") },
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
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        val glassState = remember { HazeState() }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .glassSource(glassState)
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 目前 API Key 狀態卡片（僅顯示尾 4 碼）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(spring())
                    .glassSurface(
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
                        ApiKeySource.ENCRYPTED_PREFS -> "目前狀態：已設定（App 加密儲存）"
                        ApiKeySource.BUILD_CONFIG -> "目前狀態：已設定（local.properties）"
                        ApiKeySource.NONE -> "目前狀態：未設定"
                    }
                    Text(
                        text = statusTitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (keySource == ApiKeySource.NONE) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        }
                    )
                    Spacer(Modifier.height(4.dp))
                    if (maskedTail != null) {
                        Text(
                            text = "Key 尾碼：$maskedTail",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    } else {
                        Text(
                            text = "尚未設定 Mistral API Key，AI 詐騙分析功能無法使用。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            OutlinedTextField(
                value = inputKey,
                onValueChange = {
                    inputKey = it
                    testResult = null
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Mistral API Key") },
                placeholder = { Text("請輸入您的 Mistral API Key…") },
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val trimmed = inputKey.trim()
                        if (trimmed.isEmpty()) return@Button
                        MistralClient.saveApiKey(appCtx, trimmed)
                        inputKey = ""
                        showKey = false
                        testResult = null
                        statusVersion++
                        scope.launch {
                            snackbarHostState.showSnackbar("已加密儲存 API Key")
                        }
                    },
                    enabled = inputKey.isNotBlank() && !isTesting,
                    modifier = Modifier
                        .weight(1f)
                        .pressableScale()
                ) {
                    Text("儲存")
                }

                OutlinedButton(
                    onClick = {
                        val keyToTest = inputKey.trim().ifEmpty { activeKey.orEmpty() }
                        if (keyToTest.isEmpty() || isTesting) return@OutlinedButton
                        scope.launch {
                            isTesting = true
                            testResult = null
                            val result = MistralClient.testConnection(keyToTest)
                            isTesting = false
                            testResult = result.fold(
                                onSuccess = { msg -> true to msg },
                                onFailure = { err -> false to (err.message ?: "連線失敗") }
                            )
                        }
                    },
                    enabled = (inputKey.isNotBlank() || activeKey != null) && !isTesting,
                    modifier = Modifier
                        .weight(1f)
                        .pressableScale()
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("測試中…")
                    } else {
                        Text("測試連線")
                    }
                }
            }

            if (testResult != null) {
                val (success, message) = testResult!!
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(spring())
                        .glassSurface(
                            state = glassState,
                            fallbackColor = if (success) {
                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.90f)
                            } else {
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.90f)
                            },
                            outlineColor = if (success) null else MaterialTheme.colorScheme.error,
                            cornerRadius = 18.dp
                        )
                ) {
                    Text(
                        text = message,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (success) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onErrorContainer
                        }
                    )
                }
            }

            if (hasStoredKey) {
                OutlinedButton(
                    onClick = {
                        MistralClient.clearApiKey(appCtx)
                        testResult = null
                        statusVersion++
                        scope.launch {
                            snackbarHostState.showSnackbar("已清除 App 內儲存的 API Key")
                        }
                    },
                    enabled = !isTesting,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("移除已儲存的 Key")
                }
            }

            Text(
                text = "說明：API Key 會使用 Android EncryptedSharedPreferences 加密儲存於本機。讀取優先順序為「App 內設定 → local.properties → 未設定」。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
