package com.vanta.remote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.vanta.remote.model.ButtonActionType
import com.vanta.remote.model.OverlayButton

private data class KeyPreset(
    val label: String,
    val vk: Short = 0,
    val isMouse: Boolean = false,
    val mouseBtn: Byte = 0
)

private val COMMON_PRESETS = listOf(
    KeyPreset("W", 0x57), KeyPreset("A", 0x41), KeyPreset("S", 0x53), KeyPreset("D", 0x44),
    KeyPreset("Space", 0x20), KeyPreset("Shift", 0x10), KeyPreset("Ctrl", 0x11), KeyPreset("Alt", 0x12),
    KeyPreset("E", 0x45), KeyPreset("F", 0x46), KeyPreset("Q", 0x51), KeyPreset("R", 0x52),
    KeyPreset("C", 0x43), KeyPreset("Tab", 0x09), KeyPreset("Esc", 0x1B), KeyPreset("Enter", 0x0D),
    KeyPreset("1", 0x31), KeyPreset("2", 0x32), KeyPreset("3", 0x33), KeyPreset("4", 0x34),
    KeyPreset("ЛКМ", isMouse = true, mouseBtn = 0),
    KeyPreset("ПКМ", isMouse = true, mouseBtn = 1)
)

@Composable
fun AddButtonDialog(
    onDismiss: () -> Unit,
    onAdd: (OverlayButton) -> Unit
) {
    var selectedPreset by remember { mutableStateOf(COMMON_PRESETS[0]) }
    var selectedSize by remember { mutableStateOf(54) }
    var isHoldable by remember { mutableStateOf(true) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1E222D),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Добавить кнопку на экран",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Выберите действие:",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp
                )

                // Grid of presets
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.height(180.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(COMMON_PRESETS) { preset ->
                        val isSelected = selectedPreset == preset
                        Surface(
                            onClick = { selectedPreset = preset },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) Color(0xFF2563EB) else Color(0xFF0F172A),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) Color(0xFF60A5FA) else Color(0xFF334155)
                            )
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp)
                            ) {
                                Text(
                                    text = preset.label,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                // Size selector
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(text = "Размер:", color = Color(0xFF94A3B8), fontSize = 13.sp)
                    listOf(46 to "S", 54 to "M", 64 to "L").forEach { (sz, title) ->
                        FilterChip(
                            selected = selectedSize == sz,
                            onClick = { selectedSize = sz },
                            label = { Text(title) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF2563EB),
                                selectedLabelColor = Color.White,
                                containerColor = Color(0xFF0F172A),
                                labelColor = Color(0xFF94A3B8)
                            )
                        )
                    }
                }

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Отмена", color = Color(0xFF94A3B8))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val newBtn = if (selectedPreset.isMouse) {
                                OverlayButton(
                                    label = selectedPreset.label,
                                    actionType = ButtonActionType.MOUSE_CLICK,
                                    mouseButton = selectedPreset.mouseBtn,
                                    rx = 0.5f,
                                    ry = 0.5f,
                                    sizeDp = selectedSize,
                                    isHoldable = isHoldable
                                )
                            } else {
                                OverlayButton(
                                    label = selectedPreset.label,
                                    actionType = ButtonActionType.KEY,
                                    vkCode = selectedPreset.vk,
                                    rx = 0.5f,
                                    ry = 0.5f,
                                    sizeDp = selectedSize,
                                    isHoldable = isHoldable
                                )
                            }
                            onAdd(newBtn)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                    ) {
                        Text("Добавить", color = Color.White)
                    }
                }
            }
        }
    }
}
