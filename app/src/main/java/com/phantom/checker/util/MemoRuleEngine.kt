package com.phantom.checker.util

import com.phantom.checker.data.MemoRuleEntity

/**
 * #203 自動記事/待辦規則純函式匹配引擎。
 * 優先序：never > todo > note；同優先序依 id 升序第一條命中即止。
 */
object MemoRuleEngine {

    fun isValid(rule: MemoRuleEntity): Boolean =
        !rule.packageName.isNullOrBlank() ||
            !rule.sender.isNullOrBlank() ||
            !rule.keyword.isNullOrBlank()

    fun matches(
        rule: MemoRuleEntity,
        packageName: String,
        sender: String,
        content: String
    ): Boolean {
        if (!rule.isEnabled || !isValid(rule)) return false
        val pkg = rule.packageName?.trim()?.takeIf { it.isNotEmpty() }
        if (pkg != null && pkg != packageName) return false

        val snd = rule.sender?.trim()?.takeIf { it.isNotEmpty() }
        if (snd != null && !snd.equals(sender.trim(), ignoreCase = true)) return false

        val kw = rule.keyword?.trim()?.takeIf { it.isNotEmpty() }
        if (kw != null && !content.contains(kw, ignoreCase = true)) return false

        return true
    }

    private fun priority(action: String): Int = when (action) {
        MemoRuleEntity.ACTION_NEVER -> 3
        MemoRuleEntity.ACTION_TODO -> 2
        MemoRuleEntity.ACTION_NOTE -> 1
        else -> 0
    }

    /**
     * 從啟用中的規則清單中選出最高優先序的第一條命中規則（never > todo > note，同優先序按 id 升序）。
     */
    fun match(
        rules: List<MemoRuleEntity>,
        packageName: String,
        sender: String,
        content: String
    ): MemoRuleEntity? {
        if (rules.isEmpty()) return null
        return rules
            .asSequence()
            .filter { matches(it, packageName, sender, content) }
            .sortedWith(compareByDescending<MemoRuleEntity> { priority(it.action) }.thenBy { it.id })
            .firstOrNull()
    }

    // TODO(R17): 硬編碼繁中 UI 文案留待 R17 抽離至 strings.xml
    fun actionLabel(action: String): String = when (action) {
        MemoRuleEntity.ACTION_NEVER -> "永不記事/待辦"
        MemoRuleEntity.ACTION_TODO -> "一律加入待辦"
        MemoRuleEntity.ACTION_NOTE -> "一律加入記事"
        else -> action
    }

    // TODO(R17): 硬編碼繁中 UI 文案留待 R17 抽離至 strings.xml
    fun formatConditionSummary(rule: MemoRuleEntity, appLabelOverride: String? = null): String {
        val parts = mutableListOf<String>()
        val pkg = rule.packageName?.trim()?.takeIf { it.isNotEmpty() }
        if (pkg != null) parts.add("App ${appLabelOverride ?: pkg}")
        val snd = rule.sender?.trim()?.takeIf { it.isNotEmpty() }
        if (snd != null) parts.add("寄件者 $snd")
        val kw = rule.keyword?.trim()?.takeIf { it.isNotEmpty() }
        if (kw != null) parts.add("含「$kw」")
        return parts.joinToString("／").ifBlank { "（無條件）" }
    }
}
