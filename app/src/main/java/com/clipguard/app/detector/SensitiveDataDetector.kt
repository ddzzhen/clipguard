package com.clipguard.app.detector

import com.clipguard.app.model.ClipboardEvent.DetectedType

/**
 * 敏感数据检测引擎
 * 基于正则表达式匹配，识别剪贴板中的隐私敏感内容
 */
object SensitiveDataDetector {

    // ── 正则规则库 ──────────────────────────────────────

    /** 18位身份证号（含末位X校验） */
    private val ID_CARD_REGEX = Regex(
        """\b[1-9]\d{5}(?:19|20)\d{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\d|3[01])\d{3}[\dXx]\b"""
    )

    /** 银行卡号（16-19位数字，常见发卡行 BIN 范围） */
    private val BANK_CARD_REGEX = Regex(
        """\b(?:62|60|4[0-9]|5[1-5]|35[2-8]|37[0-2]|5[06]|3[47]|65|70|64|50|56|58|57)\d{14,17}\b"""
    )

    /** 中国大陆手机号 */
    private val PHONE_REGEX = Regex(
        """\b1[3-9]\d{9}\b"""
    )

    /** 邮箱地址 */
    private val EMAIL_REGEX = Regex(
        """\b[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}\b"""
    )

    /** IPv4 地址 */
    private val IP_REGEX = Regex(
        """\b(?:(?:25[0-5]|2[0-4]\d|[01]?\d\d?)\.){3}(?:25[0-5]|2[0-4]\d|[01]?\d\d?)\b"""
    )

    /** 常见API Key / Token 模式 */
    private val TOKEN_PATTERNS = listOf(
        Regex("""\bsk-[A-Za-z0-9]{32,}\b"""),                    // OpenAI
        Regex("""\b(?:ghp|gho|ghu)_[A-Za-z0-9]{36,}\b"""),      // GitHub
        Regex("""\bAKIA[0-9A-Z]{16}\b"""),                       // AWS Access Key
        Regex("""\bAIza[0-9A-Za-z\-_]{35}\b"""),                // Google API
        Regex("""\beyJ[A-Za-z0-9\-_]+\.eyJ[A-Za-z0-9\-_]+\.[A-Za-z0-9\-_]*\b""") // JWT
    )

    /** 疑似密码特征：短字符串含特殊字符 */
    private val PASSWORD_SUSPECT_REGEX = Regex(
        """\b(?=.*[A-Za-z])(?=.*\d)(?=.*[!@#$%^&*()_+\-=\[\]{};':"\\|,.<>\/?])[^\s]{6,32}\b"""
    )

    // ── 公开方法 ────────────────────────────────────────

    /**
     * 检测剪贴板文本，返回识别到的所有敏感类型。
     * 若未检测到任何敏感内容，返回 [DetectedType.NONE]。
     */
    fun detect(text: String): List<DetectedType> {
        if (text.isBlank()) return listOf(DetectedType.NONE)

        val found = mutableListOf<DetectedType>()

        // 按优先级检测：先检测高确定性模式
        if (ID_CARD_REGEX.containsMatchIn(text)) found.add(DetectedType.ID_CARD)
        if (BANK_CARD_REGEX.containsMatchIn(text)) found.add(DetectedType.BANK_CARD)
        if (PHONE_REGEX.containsMatchIn(text)) found.add(DetectedType.PHONE_NUMBER)
        if (EMAIL_REGEX.containsMatchIn(text)) found.add(DetectedType.EMAIL)
        if (IP_REGEX.containsMatchIn(text)) found.add(DetectedType.IP_ADDRESS)

        // Token 检测（多种模式逐个匹配）
        for (tokenPattern in TOKEN_PATTERNS) {
            if (tokenPattern.containsMatchIn(text)) {
                found.add(DetectedType.TOKEN)
                break
            }
        }

        // 最后检测低确定性模式（密码）
        if (PASSWORD_SUSPECT_REGEX.containsMatchIn(text)) {
            found.add(DetectedType.PASSWORD)
        }

        return if (found.isEmpty()) listOf(DetectedType.NONE) else found.distinct()
    }

    /**
     * 对敏感内容进行脱敏显示
     */
    fun mask(text: String, types: List<DetectedType>): String {
        var masked = text
        for (type in types) {
            masked = when (type) {
                DetectedType.ID_CARD ->
                    masked.replace(ID_CARD_REGEX) { it.value.take(4) + "****" + it.value.takeLast(4) }
                DetectedType.BANK_CARD ->
                    masked.replace(BANK_CARD_REGEX) { it.value.take(4) + " **** **** " + it.value.takeLast(4) }
                DetectedType.PHONE_NUMBER ->
                    masked.replace(PHONE_REGEX) { it.value.take(3) + "****" + it.value.takeLast(4) }
                DetectedType.EMAIL ->
                    masked.replace(EMAIL_REGEX) { mr ->
                        val parts = mr.value.split("@")
                        parts.first().take(2) + "***@" + parts.last()
                    }
                DetectedType.TOKEN ->
                    masked.replace(TOKEN_PATTERNS.first()) { it.value.take(6) + "..." + it.value.takeLast(4) }
                else -> masked
            }
        }
        return masked
    }

    /**
     * 判断某类敏感内容是否应自动清除（高危险级别）
     */
    fun shouldAutoClear(types: List<DetectedType>): Boolean {
        val highRisk = setOf(DetectedType.PASSWORD, DetectedType.TOKEN, DetectedType.BANK_CARD)
        return types.any { it in highRisk }
    }
}