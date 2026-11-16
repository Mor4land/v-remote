package com.vanta.remote.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vanta.remote.network.UdpClient

@Composable
fun TouchpadSurface(
    udpClient: UdpClient,
    sensitivity: Float,
    isDragMode: Boolean,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val surfaceColor = MaterialTheme.colorScheme.surface
    val dotColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(surfaceColor)
            .pointerInput(sensitivity, isDragMode) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val dx = (dragAmount.x * sensitivity).toInt().toShort()
                    val dy = (dragAmount.y * sensitivity).toInt().toShort()
                    if (dx != 0.toShort() || dy != 0.toShort()) {
                        udpClient.sendMouseMoveRel(dx, dy)
                    }
                }
            }
            .pointerInput(isDragMode) {
                detectTapGestures(
                    onTap = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        udpClient.sendMouseButton(0, 0) // Left click
                    },
                    onDoubleTap = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        udpClient.sendMouseButton(0, 0)
                        udpClient.sendMouseButton(0, 0)
                    },
                    onLongPress = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        udpClient.sendMouseButton(1, 0) // Right click
                    }
                )
            }
    ) {
        // Dot Grid Pattern Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val step = 32.dp.toPx()
            val dotRadius = 1.2.dp.toPx()
            var x = step / 2
            while (x < size.width) {
                var y = step / 2
                while (y < size.height) {
                    drawCircle(color = dotColor, radius = dotRadius, center = Offset(x, y))
                    y += step
                }
                x += step
            }
        }

        // Fast Scroll Strip on Right
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(42.dp)
                .fillMaxHeight()
                .background(Color.White.copy(alpha = 0.03f))
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val deltaY = (-dragAmount.y * 0.15f).toInt().coerceIn(-127, 127).toByte()
                        if (deltaY != 0.toByte()) {
                            udpClient.sendMouseScroll(deltaY)
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "⚡ SCROLL",
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                fontSize = 9.sp,
                modifier = Modifier
            )
        }
    }
}
