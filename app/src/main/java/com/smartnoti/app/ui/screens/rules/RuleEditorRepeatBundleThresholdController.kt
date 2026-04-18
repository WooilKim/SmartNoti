package com.smartnoti.app.ui.screens.rules

data class RepeatBundleThresholdPreset(
    val value: String,
    val label: String,
)

class RuleEditorRepeatBundleThresholdController {
    val presets: List<RepeatBundleThresholdPreset> = listOf(
        preset(2),
        preset(3),
        preset(5),
        preset(8),
    )

    fun normalize(raw: String): String {
        return raw.filter(Char::isDigit).trimStart('0').ifEmpty { "1" }
    }

    fun increment(current: String): String {
        val base = normalize(current).toIntOrNull() ?: 1
        return (base + 1).toString()
    }

    fun decrement(current: String): String {
        val base = normalize(current).toIntOrNull() ?: 1
        return maxOf(1, base - 1).toString()
    }

    private fun preset(value: Int) = RepeatBundleThresholdPreset(
        value = value.toString(),
        label = "${value}회 이상",
    )
}
