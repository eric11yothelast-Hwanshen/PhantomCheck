package com.phantom.checker.util

object PresetKeywords {
    val keywords: List<String> = listOf(
        "投資",
        "保證獲利",
        "穩賺不賠",
        "飆股",
        "老師帶單",
        "高報酬",
        "零風險",
        "解除分期",
        "解除設定",
        "ATM",
        "操作轉帳",
        "猜猜我是誰",
        "點數錯誤",
        "加盟托售",
        "小額借款",
        "代辦貸款",
        "中獎通知",
        "免費領取",
        "刷單兼職",
        "家庭代工",
        "帳戶凍結",
        "洗錢防制",
        "司法機關",
        "檢察官",
        "法院傳票",
        "水電費催繳",
        "ETC欠費",
        "包裹未領",
        "貨到付款",
        "色情應召"
    )

    val englishKeywords: List<String> = listOf(
        "guaranteed profit",
        "risk-free investment",
        "crypto giveaway",
        "wire transfer",
        "gift card payment",
        "account suspended",
        "verify your account",
        "urgent action required",
        "unclaimed package",
        "lottery winner",
        "click to claim",
        "remote job deposit",
        "tax refund",
        "confirm your PIN",
        "share verification code"
    )

    val allKeywords: List<String>
        get() = keywords + englishKeywords

    fun keywordsForLocale(isEnglish: Boolean): List<String> =
        if (isEnglish) englishKeywords else keywords
}
