package com.smartnoti.app.data.rules

import com.smartnoti.app.domain.model.RuleActionUi
import com.smartnoti.app.domain.model.RuleTypeUi
import com.smartnoti.app.domain.model.RuleUiModel
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object RuleStorageCodec {
    fun encode(rules: List<RuleUiModel>): String {
        return rules.joinToString("\n") { rule ->
            listOf(
                rule.id,
                rule.title,
                rule.subtitle,
                rule.type.name,
                rule.action.name,
                rule.enabled.toString(),
                rule.matchValue,
            ).joinToString("|") { value -> value.escape() }
        }
    }

    fun decode(encoded: String): List<RuleUiModel> {
        if (encoded.isBlank()) return emptyList()

        return encoded.lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val parts = line.split("|").map { it.unescape() }
                if (parts.size < 7) return@mapNotNull null

                RuleUiModel(
                    id = parts[0],
                    title = parts[1],
                    subtitle = parts[2],
                    type = RuleTypeUi.valueOf(parts[3]),
                    action = RuleActionUi.valueOf(parts[4]),
                    enabled = parts[5].toBoolean(),
                    matchValue = parts[6],
                )
            }
            .toList()
    }

    private fun String.escape(): String = URLEncoder.encode(this, StandardCharsets.UTF_8.toString())

    private fun String.unescape(): String = URLDecoder.decode(this, StandardCharsets.UTF_8.toString())
}