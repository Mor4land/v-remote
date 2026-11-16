package com.vanta.remote.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import com.vanta.remote.network.UdpClient

@Composable
fun DesktopSurface(
    frame: Bitmap?,
    udpClient: UdpClient,
    isDragMode: Boolean,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var surfaceSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { surfaceSize = it }
            .pointerInput(isDragMode) {
                detectTapGestures(
                    onTap = { offset ->
                        if (surfaceSize.width > 0 && surfaceSize.height > 0) {
                            val rx = offset.x / surfaceSize.width
                            val ry = offset.y / surfaceSize.height
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            udpClient.sendMouseMoveAbs(rx, ry)
                            udpClient.sendMouseButton(0, 0) // Left Click
                        }
                    },
                    onDoubleTap = { offset ->
                        if (surfaceSize.width > 0 && surfaceSize.height > 0) {
                            val rx = offset.x / surfaceSize.width
                            val ry = offset.y / surfaceSize.height
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            udpClient.sendMouseMoveAbs(rx, ry)
                            udpClient.sendMouseButton(0, 0)
                            udpClient.sendMouseButton(0, 0) // Double Click
                        }
                    },
                    onLongPress = { offset ->
                        if (surfaceSize.width > 0 && surfaceSize.height > 0) {
                            val rx = offset.x / surfaceSize.width
                            val ry = offset.y / surfaceSize.height
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            udpClient.sendMouseMoveAbs(rx, ry)
                            udpClient.sendMouseButton(1, 0) // Right Click
                        }
                    }
                )
            }
            .pointerInput(isDragMode) {
                detectDragGestures(
                    onDragStart = { offset ->
                        if (isDragMode && surfaceSize.width > 0 && surfaceSize.height > 0) {
                            val rx = offset.x / surfaceSize.width
                            val ry = offset.y / surfaceSize.height
                            udpClient.sendMouseMoveAbs(rx, ry)
                            udpClient.sendMouseButton(0, 1) // Left Down
                        }
                    },
                    onDragEnd = {
                        if (isDragMode) {
                            udpClient.sendMouseButton(0, 2) // Left Up
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        if (surfaceSize.width > 0 && surfaceSize.height > 0) {
                            val rx = change.position.x / surfaceSize.width
                            val ry = change.position.y / surfaceSize.height
                            udpClient.sendMouseMoveAbs(rx, ry)
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        if (frame != null) {
            Image(
                bitmap = frame.asImageBitmap(),
                contentDescription = "Desktop Stream",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Text(
                    text = "Подключение к экрану...",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            }
        }
    }
}
