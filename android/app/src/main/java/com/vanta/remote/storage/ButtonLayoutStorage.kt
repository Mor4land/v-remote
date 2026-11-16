package com.vanta.remote.storage

import android.content.Context
import com.vanta.remote.model.ButtonActionType
import com.vanta.remote.model.OverlayButton
import org.json.JSONArray

object ButtonLayoutStorage {

    private const val PREFS_NAME = "v_remote_layout_prefs"
    private const val KEY_LAYOUT = "custom_buttons_layout"

    fun loadButtons(context: Context): List<OverlayButton> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val rawJson = prefs.getString(KEY_LAYOUT, null)
        if (!rawJson.isNullOrBlank()) {
            try {
                val array = JSONArray(rawJson)
                val list = mutableListOf<OverlayButton>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(OverlayButton.fromJson(obj))
                }
                return list
            } catch (_: Exception) {}
        }
        return getDefaultButtons()
    }

    fun saveButtons(context: Context, buttons: List<OverlayButton>) {
        val array = JSONArray()
        for (btn in buttons) {
            array.put(btn.toJson())
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LAYOUT, array.toString()).apply()
    }

    fun getDefaultButtons(): List<OverlayButton> {
        return listOf(
            // Movement (Left side under thumb)
            OverlayButton(label = "W", vkCode = 0x57, rx = 0.14f, ry = 0.58f, sizeDp = 52),
            OverlayButton(label = "A", vkCode = 0x41, rx = 0.07f, ry = 0.70f, sizeDp = 52),
            OverlayButton(label = "S", vkCode = 0x53, rx = 0.14f, ry = 0.70f, sizeDp = 52),
            OverlayButton(label = "D", vkCode = 0x44, rx = 0.21f, ry = 0.70f, sizeDp = 52),
            OverlayButton(label = "Shift", vkCode = 0x10, rx = 0.07f, ry = 0.83f, sizeDp = 52),
            OverlayButton(label = "Space", vkCode = 0x20, rx = 0.18f, ry = 0.83f, sizeDp = 58),

            // Actions (Right side under thumb)
            OverlayButton(label = "ЛКМ", actionType = ButtonActionType.MOUSE_CLICK, mouseButton = 0, rx = 0.82f, ry = 0.70f, sizeDp = 58),
            OverlayButton(label = "ПКМ", actionType = ButtonActionType.MOUSE_CLICK, mouseButton = 1, rx = 0.91f, ry = 0.60f, sizeDp = 54),
            OverlayButton(label = "E", vkCode = 0x45, rx = 0.76f, ry = 0.58f, sizeDp = 50),
            OverlayButton(label = "F", vkCode = 0x46, rx = 0.84f, ry = 0.48f, sizeDp = 50),
            OverlayButton(label = "Esc", vkCode = 0x1B, rx = 0.92f, ry = 0.16f, sizeDp = 46, isHoldable = false)
        )
    }
}
