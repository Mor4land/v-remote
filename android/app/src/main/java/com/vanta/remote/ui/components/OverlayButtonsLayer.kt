package com.vanta.remote.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vanta.remote.model.ButtonActionType
import com.vanta.remote.model.OverlayButton
import com.vanta.remote.network.UdpClient
import kotlin.math.roundToInt

@Composable
fun OverlayButtonsLayer(
    buttons: List<OverlayButton>,
    isEditMode: Boolean,
    udpClient: UdpClient,
    onButtonMoved: (id: String, newRx: Float, newRy: Float) -> Unit,
    onButtonDeleted: (id: String) -> Unit,
    onButtonSizeChanged: (id: String, newSize: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var selectedButtonId by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
    ) {
        if (containerSize.width > 0 && containerSize.height > 0) {
            buttons.forEach { btn ->
                DraggableOverlayButton(
                    button = btn,
                    isEditMode = isEditMode,
                    isSelected = isEditMode && selectedButtonId == btn.id,
                    containerWidth = containerSize.width,
                    containerHeight = containerSize.height,
                    udpClient = udpClient,
                    onSelect = { selectedButtonId = btn.id },
                    onMoved = { nrx, nry -> onButtonMoved(btn.id, nrx, nry) },
                    onDelete = { onButtonDeleted(btn.id) },
                    onSizeChange = { sz -> onButtonSizeChanged(btn.id, sz) }
                )
            }
        }
    }
}

@Composable
private fun DraggableOverlayButton(
    button: OverlayButton,
    isEditMode: Boolean,
    isSelected: Boolean,
    containerWidth: Int,
    containerHeight: Int,
    udpClient: UdpClient,
    onSelect: () -> Unit,
    onMoved: (Float, Float) -> Unit,
    onDelete: () -> Unit,
    onSizeChange: (Int) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var isPressed by remember { mutableStateOf(false) }

    val btnPx = (button.sizeDp * 2.7f).roundToInt()
    val posX = (button.rx * (containerWidth - btnPx)).coerceIn(0f, (containerWidth - btnPx).toFloat())
    val posY = (button.ry * (containerHeight - btnPx)).coerceIn(0f, (containerHeight - btnPx).toFloat())

    Box(
        modifier = Modifier
            .offset { IntOffset(posX.roundToInt(), posY.roundToInt()) }
            .size(button.sizeDp.dp)
    ) {
        // Main Button Surface
        Surface(
            shape = RoundedCornerShape(if (button.sizeDp > 54) 16.dp else 12.dp),
            color = when {
                isEditMode && isSelected -> Color(0xFF2563EB).copy(alpha = 0.85f)
                isEditMode -> Color(0xFF1E2638).copy(alpha = 0.75f)
                isPressed -> Color(0xFF3B82F6).copy(alpha = 0.85f)
                else -> Color(0xFF0F172A).copy(alpha = 0.55f)
            },
            border = androidx.compose.foundation.BorderStroke(
                width = if (isSelected || isPressed) 2.dp else 1.dp,
                color = when {
                    isSelected -> Color(0xFF60A5FA)
                    isPressed -> Color(0xFF93C5FD)
                    isEditMode -> Color(0x8894A3B8)
                    else -> Color(0x44FFFFFF)
                }
            ),
            shadowElevation = if (isPressed) 2.dp else 6.dp,
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isEditMode) {
                        Modifier
                            .clickable { onSelect() }
                            .pointerInput(button.id) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    onSelect()
                                    val newPxX = posX + dragAmount.x
                                    val newPxY = posY + dragAmount.y
                                    val newRx = (newPxX / (containerWidth - btnPx)).coerceIn(0f, 1f)
                                    val newRy = (newPxY / (containerHeight - btnPx)).coerceIn(0f, 1f)
                                    onMoved(newRx, newRy)
                                }
                            }
                    } else {
                        Modifier.pointerInput(button.id) {
                            detectTapGestures(
                                onPress = {
                                    isPressed = true
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)

                                    if (button.actionType == ButtonActionType.KEY) {
                                        if (button.isHoldable) {
                                            udpClient.sendKeyDown(button.vkCode)
                                        } else {
                                            udpClient.sendKeyTap(button.vkCode)
                                        }
                                    } else {
                                        udpClient.sendMouseButton(button.mouseButton, 1) // Down
                                    }

                                    tryAwaitRelease()

                                    isPressed = false
                                    if (button.actionType == ButtonActionType.KEY) {
                                        if (button.isHoldable) {
                                            udpClient.sendKeyUp(button.vkCode)
                                        }
                                    } else {
                                        udpClient.sendMouseButton(button.mouseButton, 2) // Up
                                    }
                                }
                            )
                        }
                    }
                )
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Text(
                    text = button.label,
                    color = Color.White,
                    fontSize = if (button.label.length > 3) 10.sp else 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Edit Mode Delete Button Tag
        if (isEditMode && isSelected) {
            Surface(
                shape = CircleShape,
                color = Color(0xFFEF4444),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 6.dp, y = (-6).dp)
                    .size(22.dp)
                    .clickable { onDelete() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Удалить",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}
