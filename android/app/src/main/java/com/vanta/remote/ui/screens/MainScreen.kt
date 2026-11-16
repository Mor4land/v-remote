package com.vanta.remote.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vanta.remote.network.UdpClient
import com.vanta.remote.ui.components.TouchpadSurface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(udpClient: UdpClient) {
    val isConnected by udpClient.isConnected.collectAsState()
    val latencyMs by udpClient.latencyMs.collectAsState()
    val haptic = LocalHapticFeedback.current

    var host by remember { mutableStateOf("94.190.111.52") }
    var portText by remember { mutableStateOf("8766") }
    var pinText by remember { mutableStateOf("7373") }
    var showConnectDialog by remember { mutableStateOf(!isConnected) }
    var isDragMode by remember { mutableStateOf(false) }
    var sensitivity by remember { mutableFloatStateOf(1.6f) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("V-Remote", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        // Status pill
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isConnected) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFFEF4444).copy(alpha = 0.2f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isConnected) Color(0xFF10B981) else Color(0xFFEF4444)
                            )
                        ) {
                            Text(
                                text = if (isConnected) {
                                    if (latencyMs >= 0) "⚡ $latencyMs ms" else "UDP Онлайн"
                                } else "Офлайн",
                                color = if (isConnected) Color(0xFF10B981) else Color(0xFFEF4444),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { isDragMode = !isDragMode }) {
                        Icon(
                            imageVector = Icons.Default.PanTool,
                            contentDescription = "Удержание",
                            tint = if (isDragMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { showConnectDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = "Подключение",
                            tint = if (isConnected) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = "Настройки")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Quick Keys Scroll Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Windows Key (Distinct primary button)
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        udpClient.sendKeyTap(0x5B) // VK_LWIN
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("⊞ Win", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 12.sp)
                }

                KeyButton("Win+D") {
                    udpClient.sendKeyTap(0x5B)
                }
                KeyButton("Esc") { udpClient.sendKeyTap(0x1B) }
                KeyButton("Tab") { udpClient.sendKeyTap(0x09) }
                KeyButton("Enter") { udpClient.sendKeyTap(0x0D) }
                KeyButton("⌫ Del") { udpClient.sendKeyTap(0x08) }
                KeyButton("Alt+Tab") { udpClient.sendKeyTap(0x12) }
                KeyButton("▲") { udpClient.sendKeyTap(0x26) }
                KeyButton("▼") { udpClient.sendKeyTap(0x28) }
                KeyButton("◄") { udpClient.sendKeyTap(0x25) }
                KeyButton("►") { udpClient.sendKeyTap(0x27) }
                KeyButton("Vol +") { udpClient.sendKeyTap(0xAF.toShort()) }
                KeyButton("Vol -") { udpClient.sendKeyTap(0xAE.toShort()) }
                KeyButton("Mute") { udpClient.sendKeyTap(0xAD.toShort()) }
            }

            // Drag mode indicator
            AnimatedVisibility(visible = isDragMode) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFEF4444).copy(alpha = 0.2f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "🔒 РЕЖИМ УДЕРЖАНИЯ (ЛКМ ЗАЖАТА)",
                        color = Color(0xFFF87171),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }

            // Main Touchpad Area
            TouchpadSurface(
                udpClient = udpClient,
                sensitivity = sensitivity,
                isDragMode = isDragMode,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )

            // Bottom Mouse Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        udpClient.sendMouseButton(0, 0) // Left click
                    },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("ЛЕВАЯ (ЛКМ)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                OutlinedButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        udpClient.sendMouseButton(2, 0) // Middle click
                    },
                    modifier = Modifier.width(60.dp).fillMaxHeight(),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("Колесо", fontSize = 11.sp)
                }

                FilledTonalButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        udpClient.sendMouseButton(1, 0) // Right click
                    },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("ПРАВАЯ (ПКМ)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }

    // Connect Dialog
    if (showConnectDialog) {
        AlertDialog(
            onDismissRequest = { showConnectDialog = false },
            title = { Text("Параметры UDP подключения") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text("IP адрес сервера (ПК)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = portText,
                        onValueChange = { portText = it },
                        label = { Text("UDP порт") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = pinText,
                        onValueChange = { pinText = it },
                        label = { Text("PIN-код") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val port = portText.toIntOrNull() ?: 8766
                    val pin = pinText.toIntOrNull() ?: 0
                    udpClient.connect(host, port, pin)
                    showConnectDialog = false
                }) {
                    Text("Подключить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConnectDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    // Settings Dialog
    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("Настройки тачпада") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Чувствительность: ${String.format("%.1f", sensitivity)}")
                    Slider(
                        value = sensitivity,
                        onValueChange = { sensitivity = it },
                        valueRange = 0.5f..3.5f,
                        steps = 6
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showSettingsDialog = false }) {
                    Text("Готово")
                }
            }
        )
    }
}

@Composable
private fun KeyButton(text: String, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    FilledTonalButton(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}
