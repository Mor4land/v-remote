package com.vanta.remote.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vanta.remote.model.OverlayButton
import com.vanta.remote.network.UdpClient
import com.vanta.remote.network.VideoStreamClient
import com.vanta.remote.storage.ButtonLayoutStorage
import com.vanta.remote.ui.components.AddButtonDialog
import com.vanta.remote.ui.components.DesktopSurface
import com.vanta.remote.ui.components.OverlayButtonsLayer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    udpClient: UdpClient,
    videoClient: VideoStreamClient
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val isConnected by udpClient.isConnected.collectAsState()
    val latencyMs by udpClient.latencyMs.collectAsState()
    val frame by videoClient.currentFrame.collectAsState()
    val fps by videoClient.fps.collectAsState()

    var host by remember { mutableStateOf("94.190.111.52") }
    var tcpPortText by remember { mutableStateOf("8765") }
    var udpPortText by remember { mutableStateOf("8765") }
    var pinText by remember { mutableStateOf("7373") }

    var isMenuExpanded by remember { mutableStateOf(false) }
    var isDragMode by remember { mutableStateOf(false) }
    var isEditMode by remember { mutableStateOf(false) }
    var showAddButtonDialog by remember { mutableStateOf(false) }
    var showConnectDialog by remember { mutableStateOf(!isConnected) }
    var showTextDialog by remember { mutableStateOf(false) }
    var textInput by remember { mutableStateOf("") }
    var isLandscape by remember { mutableStateOf(false) }

    // Loaded button layout from storage
    var buttons by remember { mutableStateOf(ButtonLayoutStorage.loadButtons(context)) }

    fun doConnect() {
        val tcpPort = tcpPortText.toIntOrNull() ?: 8765
        val udpPort = udpPortText.toIntOrNull() ?: 8765
        val pin = pinText.toIntOrNull() ?: 7373

        udpClient.connect(host, udpPort, pin)
        videoClient.connect(host, tcpPort, pinText)
        showConnectDialog = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 1. Fullscreen Live Desktop Viewport with Direct Touch
        DesktopSurface(
            frame = frame,
            udpClient = udpClient,
            isDragMode = isDragMode,
            modifier = Modifier.fillMaxSize()
        )

        // 2. Custom On-Screen Button Overlay (Multi-touch in Game mode, Drag-and-Drop in Edit mode)
        OverlayButtonsLayer(
            buttons = buttons,
            isEditMode = isEditMode,
            udpClient = udpClient,
            onButtonMoved = { id, nrx, nry ->
                buttons = buttons.map { if (it.id == id) it.copy(rx = nrx, ry = nry) else it }
            },
            onButtonDeleted = { id ->
                buttons = buttons.filter { it.id != id }
                ButtonLayoutStorage.saveButtons(context, buttons)
            },
            onButtonSizeChanged = { id, newSize ->
                buttons = buttons.map { if (it.id == id) it.copy(sizeDp = newSize) else it }
                ButtonLayoutStorage.saveButtons(context, buttons)
            },
            modifier = Modifier.fillMaxSize()
        )

        // 3. Top-Left Status Pill (Translucent, compact)
        if (!isEditMode) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.Black.copy(alpha = 0.65f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 12.dp, top = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(if (isConnected) Color(0xFF10B981) else Color(0xFFEF4444), CircleShape)
                    )
                    Text(
                        text = if (isConnected) {
                            val pingStr = if (latencyMs >= 0) "⚡ $latencyMs ms" else "UDP"
                            "$pingStr • $fps FPS"
                        } else "Офлайн",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // 4. Edit Mode Top Toolbar
        if (isEditMode) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF1E222D).copy(alpha = 0.95f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3B82F6)),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "✏️ Редактор кнопок",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Button(
                        onClick = { showAddButtonDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("+ Кнопка", fontSize = 11.sp)
                    }
                    TextButton(
                        onClick = {
                            buttons = ButtonLayoutStorage.getDefaultButtons()
                            ButtonLayoutStorage.saveButtons(context, buttons)
                        },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Text("Сброс", color = Color(0xFF94A3B8), fontSize = 11.sp)
                    }
                    Button(
                        onClick = {
                            ButtonLayoutStorage.saveButtons(context, buttons)
                            isEditMode = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("✓ Готово", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 5. Floating Overlay Toolbar (Bottom Center)
        if (!isEditMode) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Drag mode warning badge
                if (isDragMode) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFEF4444).copy(alpha = 0.85f),
                        modifier = Modifier.padding(bottom = 2.dp)
                    ) {
                        Text(
                            text = "🔒 ЗАЖАТИЕ ЛКМ (DRAG)",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                        )
                    }
                }

                // Expanded Controls Drawer
                AnimatedVisibility(
                    visible = isMenuExpanded,
                    enter = fadeIn() + slideInVertically { it / 2 },
                    exit = fadeOut() + slideOutVertically { it / 2 }
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF161B26).copy(alpha = 0.92f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                        shadowElevation = 8.dp,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // ⊞ Windows Key
                            Button(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    udpClient.sendKeyTap(0x5B)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("⊞ Win", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 12.sp)
                            }

                            // Button layout editor toggle
                            Button(
                                onClick = {
                                    isEditMode = true
                                    isMenuExpanded = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text("🎮 Редактор кнопок", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }

                            ActionButton("Win+D") { udpClient.sendKeyTap(0x5B) }
                            ActionButton("Esc") { udpClient.sendKeyTap(0x1B) }
                            ActionButton("Tab") { udpClient.sendKeyTap(0x09) }
                            ActionButton("Enter") { udpClient.sendKeyTap(0x0D) }
                            ActionButton("⌫ Del") { udpClient.sendKeyTap(0x08) }
                            ActionButton("Alt+Tab") { udpClient.sendKeyTap(0x12) }
                            ActionButton("Текст") { showTextDialog = true }

                            // Toggle Drag
                            FilterChip(
                                selected = isDragMode,
                                onClick = {
                                    isDragMode = !isDragMode
                                    if (!isDragMode) {
                                        udpClient.sendMouseButton(0, 2)
                                    }
                                },
                                label = { Text("Удержание") }
                            )

                            // Rotate screen
                            IconButton(onClick = {
                                val activity = context as? Activity
                                isLandscape = !isLandscape
                                activity?.requestedOrientation = if (isLandscape) {
                                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                } else {
                                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                }
                            }) {
                                Icon(Icons.Default.ScreenRotation, contentDescription = "Повернуть", tint = Color.White)
                            }

                            // Connect settings
                            IconButton(onClick = { showConnectDialog = true }) {
                                Icon(Icons.Default.Settings, contentDescription = "Настройки", tint = Color.White)
                            }
                        }
                    }
                }

                // Minimalist Floating Trigger Pill
                Surface(
                    onClick = { isMenuExpanded = !isMenuExpanded },
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF1E2638).copy(alpha = 0.85f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                    shadowElevation = 6.dp
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = if (isMenuExpanded) Icons.Default.Close else Icons.Default.Menu,
                            contentDescription = "Меню управления",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (isMenuExpanded) "Скрыть" else "Управление",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Add Button Dialog
        if (showAddButtonDialog) {
            AddButtonDialog(
                onDismiss = { showAddButtonDialog = false },
                onAdd = { newBtn ->
                    buttons = buttons + newBtn
                    ButtonLayoutStorage.saveButtons(context, buttons)
                    showAddButtonDialog = false
                }
            )
        }

        // Connection Dialog
        if (showConnectDialog) {
            AlertDialog(
                onDismissRequest = { showConnectDialog = false },
                title = { Text("Параметры подключения к ПК") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = host,
                            onValueChange = { host = it },
                            label = { Text("Внешний IP адрес ПК") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = tcpPortText,
                                onValueChange = { tcpPortText = it },
                                label = { Text("TCP порт") },
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = udpPortText,
                                onValueChange = { udpPortText = it },
                                label = { Text("UDP порт") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        OutlinedTextField(
                            value = pinText,
                            onValueChange = { pinText = it },
                            label = { Text("PIN безопасности") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = { doConnect() }) {
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

        // Text sender dialog
        if (showTextDialog) {
            AlertDialog(
                onDismissRequest = { showTextDialog = false },
                title = { Text("Отправить текст на ПК") },
                text = {
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        label = { Text("Любой язык / раскладка") },
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        textInput.forEach { ch ->
                            udpClient.sendUnicodeChar(ch)
                        }
                        textInput = ""
                        showTextDialog = false
                    }) {
                        Text("Отправить")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showTextDialog = false }) {
                        Text("Отмена")
                    }
                }
            )
        }
    }
}

@Composable
private fun ActionButton(text: String, onClick: () -> Unit) {
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
