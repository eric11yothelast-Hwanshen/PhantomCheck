package com.phantom.checker.ui

import com.phantom.checker.ui.components.phantomEnter
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
import androidx.compose.material3.TopAppBar
import com.phantom.checker.ui.components.FluidSwitch
import com.phantom.checker.ui.components.pressableScale
import dev.chrisbanes.haze.HazeState
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
import com.phantom.checker.R
import androidx.compose.ui.res.stringResource

class AutoReplySettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AutoReplyConfig.ensureLoaded(applicationContext)
        setContent {
            PhantomCheckTheme {
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
    val resCtx = LocalContext.current
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

    com.phantom.checker.ui.components.PhantomPageScaffold(
        title = stringResource(R.string.auto_reply_s_941de9),
        onBack = onBack,
        topBarStyle = com.phantom.checker.ui.components.PhantomTopBarStyle.Medium,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        val glassState = remember { HazeState() }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .phantomGlassSource(glassState)
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PhantomGlassCard(
                glassState = glassState,
                modifier = Modifier.phantomEnter(0, key = "ar_card_0").fillMaxWidth(),
                cornerRadius = 20.dp,
                contentPadding = 12.dp
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.auto_reply_s_d7045d),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            PhantomGlassCard(
                glassState = glassState,
                modifier = Modifier.phantomEnter(1, key = "ar_card_1").fillMaxWidth(),
                cornerRadius = 20.dp,
                contentPadding = 16.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.auto_reply_s_2ba0b2),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (enabled) stringResource(R.string.auto_reply_s_e048b7) else stringResource(R.string.auto_reply_s_878ac8),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    FluidSwitch(
                        checked = enabled,
                        onCheckedChange = { checked ->
                            enabled = checked
                            persist(newEnabled = checked)
                        }
                    )
                }
            }

            PhantomGlassCard(
                glassState = glassState,
                modifier = Modifier.phantomEnter(2, key = "ar_card_2").fillMaxWidth(),
                cornerRadius = 20.dp,
                contentPadding = 16.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.auto_reply_s_8abae6),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    val windowSummary = if (startMinute <= endMinute) {
                        stringResource(R.string.auto_reply_s_0e4f2d, formatMinuteOfDay(startMinute), formatMinuteOfDay(endMinute))
                    } else {
                        stringResource(R.string.auto_reply_s_58106f, formatMinuteOfDay(startMinute), formatMinuteOfDay(endMinute))
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
                            modifier = Modifier
                                .weight(1f)
                                .pressableScale()
                        ) {
                            Text(stringResource(R.string.auto_reply_s_8f7bea, formatMinuteOfDay(startMinute)))
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
                            modifier = Modifier
                                .weight(1f)
                                .pressableScale()
                        ) {
                            Text(stringResource(R.string.auto_reply_s_036dea, formatMinuteOfDay(endMinute)))
                        }
                    }
                }
            }

            PhantomGlassCard(
                glassState = glassState,
                modifier = Modifier.phantomEnter(3, key = "ar_card_3").fillMaxWidth(),
                cornerRadius = 20.dp,
                contentPadding = 16.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.auto_reply_s_7cb485),
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
                        label = { Text(stringResource(R.string.auto_reply_s_bf06b5)) },
                        placeholder = { Text(stringResource(R.string.auto_reply_s_e41a47)) }
                    )
                    Spacer(Modifier.height(2.dp))
                    Button(
                        onClick = {
                            persist()
                            scope.launch {
                                snackbarHostState.showSnackbar(resCtx.getString(R.string.auto_reply_s_c243c5))
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.End)
                            .pressableScale()
                    ) {
                        Text(stringResource(R.string.auto_reply_s_bcc070))
                    }
                }
            }
        }
    }
}
