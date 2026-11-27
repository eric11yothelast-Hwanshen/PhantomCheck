package com.phantom.checker.ui

import com.phantom.checker.ui.components.phantomEnter
import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import com.phantom.checker.ui.components.FluidSwitch
import com.phantom.checker.ui.components.pressableScale
import dev.chrisbanes.haze.HazeState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.phantom.checker.service.AutoReplyConfig
import kotlinx.coroutines.launch
import java.util.Locale
import com.phantom.checker.R
import androidx.compose.ui.res.stringResource

class AutoReplySettingsActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.phantom.checker.util.AppLocaleHelper.wrapContext(newBase))
    }
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

private fun hasSmsAutoReplyPermissions(ctx: Context): Boolean =
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

private fun openSmsPermissionSettings(ctx: Context) {
    val detailsIntent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", ctx.packageName, null)
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        ctx.startActivity(detailsIntent)
        return
    } catch (_: Exception) {
    }
    try {
        ctx.startActivity(Intent(ctx, PermissionHubActivity::class.java))
        return
    } catch (_: Exception) {
    }
    Toast.makeText(ctx, ctx.getString(R.string.perm_hub_open_settings_failed), Toast.LENGTH_SHORT).show()
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
    val lifecycleOwner = LocalLifecycleOwner.current

    var smsPermissionGranted by remember { mutableStateOf(hasSmsAutoReplyPermissions(appCtx)) }
    var pendingEnableAfterPermission by remember { mutableStateOf(false) }

    var enabled by remember { mutableStateOf(AutoReplyConfig.enabled && smsPermissionGranted) }
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

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val granted = hasSmsAutoReplyPermissions(appCtx)
                smsPermissionGranted = granted
                if (!granted && enabled) {
                    enabled = false
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val smsPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        val granted = hasSmsAutoReplyPermissions(appCtx)
        smsPermissionGranted = granted
        if (granted) {
            if (pendingEnableAfterPermission) {
                pendingEnableAfterPermission = false
                enabled = true
                persist(newEnabled = true)
                scope.launch {
                    snackbarHostState.showSnackbar(resCtx.getString(R.string.auto_reply_s_c243c5))
                }
            }
        } else {
            pendingEnableAfterPermission = false
            enabled = false
            persist(newEnabled = false)
            scope.launch {
                val res = snackbarHostState.showSnackbar(
                    message = resCtx.getString(
                        R.string.chat_s_c1aacd,
                        resCtx.getString(R.string.perm_hub_s_becc64),
                        resCtx.getString(R.string.linked_apps_s_3df23c)
                    ),
                    actionLabel = resCtx.getString(R.string.perm_hub_s_50912e),
                    duration = SnackbarDuration.Long
                )
                if (res == SnackbarResult.ActionPerformed) {
                    openSmsPermissionSettings(ctx)
                }
            }
        }
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
            if (!smsPermissionGranted) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.warningContainer
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.warning,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(
                                    R.string.chat_s_c1aacd,
                                    stringResource(R.string.perm_hub_s_becc64),
                                    stringResource(R.string.linked_apps_s_3df23c)
                                ),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onWarningContainer
                            )
                        }
                        Text(
                            text = stringResource(R.string.perm_hub_s_30a359),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onWarningContainer
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    smsPermLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.RECEIVE_SMS,
                                            Manifest.permission.SEND_SMS
                                        )
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .pressableScale()
                            ) {
                                Text(stringResource(R.string.perm_hub_s_4665c7))
                            }
                            OutlinedButton(
                                onClick = { openSmsPermissionSettings(ctx) },
                                modifier = Modifier
                                    .weight(1f)
                                    .pressableScale()
                            ) {
                                Text(stringResource(R.string.perm_hub_s_50912e))
                            }
                        }
                    }
                }
            }

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
                            if (checked) {
                                val granted = hasSmsAutoReplyPermissions(appCtx)
                                smsPermissionGranted = granted
                                if (!granted) {
                                    pendingEnableAfterPermission = true
                                    smsPermLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.RECEIVE_SMS,
                                            Manifest.permission.SEND_SMS
                                        )
                                    )
                                } else {
                                    enabled = true
                                    persist(newEnabled = true)
                                }
                            } else {
                                pendingEnableAfterPermission = false
                                enabled = false
                                persist(newEnabled = false)
                            }
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
                                    DateFormat.is24HourFormat(ctx)
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
                                    DateFormat.is24HourFormat(ctx)
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
