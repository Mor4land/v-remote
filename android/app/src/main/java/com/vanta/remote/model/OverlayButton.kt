package com.vanta.remote.model

import org.json.JSONObject
import java.util.UUID

enum class ButtonActionType {
    KEY,         // Virtual Key
    MOUSE_CLICK  // Mouse Button (0=Left, 1=Right, 2=Middle)
}

data class OverlayButton(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val actionType: ButtonActionType = ButtonActionType.KEY,
    val vkCode: Short = 0,
    val mouseButton: Byte = 0,
    val rx: Float = 0.5f,
    val ry: Float = 0.5f,
    val sizeDp: Int = 54,
    val isHoldable: Boolean = true
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("label", label)
        put("actionType", actionType.name)
        put("vkCode", vkCode.toInt())
        put("mouseButton", mouseButton.toInt())
        put("rx", rx.toDouble())
        put("ry", ry.toDouble())
        put("sizeDp", sizeDp)
        put("isHoldable", isHoldable)
    }

    companion object {
        fun fromJson(json: JSONObject): OverlayButton = OverlayButton(
            id = json.optString("id", UUID.randomUUID().toString()),
            label = json.optString("label", "KEY"),
            actionType = try {
                ButtonActionType.valueOf(json.optString("actionType", ButtonActionType.KEY.name))
            } catch (_: Exception) {
                ButtonActionType.KEY
            },
            vkCode = json.optInt("vkCode", 0).toShort(),
            mouseButton = json.optInt("mouseButton", 0).toByte(),
            rx = json.optDouble("rx", 0.5).toFloat(),
            ry = json.optDouble("ry", 0.5).toFloat(),
            sizeDp = json.optInt("sizeDp", 54),
            isHoldable = json.optBoolean("isHoldable", true)
        )
    }
}
