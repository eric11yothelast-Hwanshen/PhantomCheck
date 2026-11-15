package com.phantom.checker.ui

import android.app.TimePickerDialog
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import com.phantom.checker.service.AutoReplyConfig
import kotlinx.coroutines.launch
import java.util.Locale

class AutoReplySettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AutoReplyConfig.ensureLoaded(applicationContext)
        setContent {
            MaterialTheme {
                AutoReplySettingsScreen(onBack = { finish() })
            }
        }
    }
}

private fun formatMinuteOfDay(totalMinutes: Int): String {
    val normalized = ((totalMinutes % 1440) + 1440) % 1440
    val hour = normalized / 60
    val minute = normalized % 60
    return String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoReplySettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val appCtx = ctx.applicationContext
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var enabled by remember { mutableStateOf(AutoReplyConfig.enabled) }
    var startMinute by remember { mutableIntStateOf(AutoReplyConfig.startMinute) }
    var endMinute by remember { mutableIntStateOf(AutoReplyConfig.endMinute) }
    var smsTemplate by remember { mutableStateOf(AutoReplyConfig.smsTemplate) }

    fun persist(
        newEnabled: Boolean = enabled,
        newStartMinute: Int = startMinute,
        newEndMinute: Int = endMinute,
        newSmsTemplate: String = smsTemplate
    ) {
        AutoReplyConfig.save(
            ctx = appCtx,
            enabled = newEnabled,
            startMinute = newStartMinute,
            endMinute = newEndMinute,
            smsTemplate = newSmsTemplate
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("自動回覆設定") },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "LINE/IG 等通訊 App 受系統限制無法代回覆（會觸發已讀），此設定僅對 SMS 生效",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "啟用 SMS 自動回覆",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (enabled) "於設定時段內自動回覆簡訊" else "目前已停用自動回覆",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = { checked ->
                            enabled = checked
                            persist(newEnabled = checked)
                        }
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "自動回覆時段",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    val windowSummary = if (startMinute <= endMinute) {
                        "每日 ${formatMinuteOfDay(startMinute)} ～ ${formatMinuteOfDay(endMinute)}"
                    } else {
                        "每日 ${formatMinuteOfDay(startMinute)} ～ 隔日 ${formatMinuteOfDay(endMinute)}"
                    }
                    Text(
                        text = windowSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                TimePickerDialog(
                                    ctx,
                                    { _, hour, minute ->
                                        val mins = hour * 60 + minute
                                        startMinute = mins
                                        persist(newStartMinute = mins)
                                    },
                                    startMinute / 60,
                                    startMinute % 60,
                                    true
                                ).show()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("開始：${formatMinuteOfDay(startMinute)}")
                        }
                        OutlinedButton(
                            onClick = {
                                TimePickerDialog(
                                    ctx,
                                    { _, hour, minute ->
                                        val mins = hour * 60 + minute
                                        endMinute = mins
                                        persist(newEndMinute = mins)
                                    },
                                    endMinute / 60,
                                    endMinute % 60,
                                    true
                                ).show()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("結束：${formatMinuteOfDay(endMinute)}")
                        }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "SMS 回覆範本",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    OutlinedTextField(
                        value = smsTemplate,
                        onValueChange = { value ->
                            smsTemplate = value
                            persist(newSmsTemplate = value)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 5,
                        label = { Text("自動回覆內容") },
                        placeholder = { Text("輸入收到簡訊時要自動回覆的文字…") }
                    )
                    Spacer(Modifier.height(2.dp))
                    Button(
                        onClick = {
                            persist()
                            scope.launch {
                                snackbarHostState.showSnackbar("自動回覆設定已儲存")
                            }
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("儲存設定")
                    }
                }
            }
        }
    }
}
