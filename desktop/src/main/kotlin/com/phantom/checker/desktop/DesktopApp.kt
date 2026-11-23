package com.phantom.checker.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun DesktopApp() {
    val scanner = remember { MdnsScanner() }
    val client = remember { DesktopLinkClient() }
    val devices by scanner.devices.collectAsState()
    val isScanning by scanner.isScanning.collectAsState()
    val uiState by client.state.collectAsState()

    var manualHostPort by remember { mutableStateOf("") }
    var manualCode by remember { mutableStateOf("") }
    var selectedFingerprintPrefix by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        scanner.start()
        if (uiState.isPaired) {
            client.connectSaved()
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            scanner.stop()
            client.disconnect()
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Color(0xFF0E1117),
            surface = Color(0xFF171C26),
            surfaceVariant = Color(0xFF1F2633),
            primary = Color(0xFF6EA8FE),
            onBackground = Color(0xFFE6EDF3),
            onSurface = Color(0xFFE6EDF3)
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header + Status bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = DesktopStrings.appTitle,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    val statusText = when (uiState.connState) {
                        LinkConnState.CONNECTED -> DesktopStrings.statusConnected + uiState.pairedDeviceName
                        LinkConnState.RECONNECTING -> DesktopStrings.statusReconnecting
                        LinkConnState.CONNECTING -> DesktopStrings.statusScanning
                        LinkConnState.DISCONNECTED -> if (uiState.isPaired) DesktopStrings.statusReconnecting else DesktopStrings.statusUnpaired
                    }
                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(statusText, style = MaterialTheme.typography.labelMedium)
                    }
                }

                if (!uiState.lastError.isNullOrBlank()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF3A1D24)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                            Text(uiState.lastError.orEmpty(), color = Color(0xFFFFB4AB), style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(DesktopStrings.firewallHint, style = MaterialTheme.typography.bodySmall, color = Color(0xFFD0D7DE))
                        }
                    }
                }

                if (uiState.connState == LinkConnState.CONNECTED) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(DesktopStrings.m1SkeletonNote, style = MaterialTheme.typography.bodyMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(onClick = { client.disconnect() }) {
                                    Text(DesktopStrings.disconnectBtn)
                                }
                                TextButton(onClick = { client.clearPairing() }) {
                                    Text(DesktopStrings.unpairBtn, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }

                // Discovered devices
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = if (isScanning) DesktopStrings.statusScanning else DesktopStrings.discoveredDevices,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (devices.isEmpty()) {
                            Text(DesktopStrings.noDevicesFound, style = MaterialTheme.typography.bodySmall, color = Color(0xFF9BA3B4))
                        } else {
                            devices.forEach { dev ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(dev.friendlyName, fontWeight = FontWeight.Medium)
                                        Text(
                                            "${dev.host}:${dev.port} · fp:${dev.fingerprintPrefix}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = Color(0xFF9BA3B4)
                                        )
                                    }
                                    OutlinedButton(onClick = {
                                        manualHostPort = "${dev.host}:${dev.port}"
                                        selectedFingerprintPrefix = dev.fingerprintPrefix
                                    }) {
                                        Text("Select")
                                    }
                                }
                            }
                        }
                    }
                }

                // Manual pairing fallback
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(DesktopStrings.manualPairTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(
                            value = manualHostPort,
                            onValueChange = { manualHostPort = it },
                            label = { Text(DesktopStrings.hostPortLabel) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = manualCode,
                            onValueChange = { manualCode = it.filter { ch -> ch.isDigit() }.take(6) },
                            label = { Text(DesktopStrings.pairCodeLabel) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                if (manualHostPort.isNotBlank() && manualCode.length == 6) {
                                    client.pairAndConnect(manualHostPort, manualCode, selectedFingerprintPrefix)
                                }
                            },
                            enabled = manualHostPort.isNotBlank() && manualCode.length == 6
                        ) {
                            Text(DesktopStrings.pairBtn)
                        }
                    }
                }
            }
        }
    }
}
