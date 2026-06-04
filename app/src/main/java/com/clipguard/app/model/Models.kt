package com.clipguard.app.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 剪贴板事件记录
 */
@Serializable
data class ClipboardEvent(
    val id: Long = System.currentTimeMillis(),
    val content: String,
    val detectedTypes: List<DetectedType>,
    val sourcePackage: String?,
    val timestamp: Long = System.currentTimeMillis(),
    val actionTaken: ActionTaken
) {
    @Serializable
    enum class DetectedType {
        ID_CARD,        // 身份证号
        BANK_CARD,      // 银行卡号
        PHONE_NUMBER,   // 手机号
        PASSWORD,       // 疑似密码
        EMAIL,          // 邮箱
        TOKEN,          // API Token / JWT
        IP_ADDRESS,     // IP地址
        NONE            // 无敏感内容
    }

    @Serializable
    enum class ActionTaken {
        ALLOWED,        // 放行
        WARNED,         // 告警但保留
        CLEARED         // 已自动清除
    }
}

/**
 * 应用权限信息
 */
@Serializable
data class AppPermission(
    val packageName: String,
    val appLabel: String,
    val permissions: List<PermissionState>
) {
    @Serializable
    data class PermissionState(
        val name: String,
        val label: String,
        val granted: Boolean,
        val dangerous: Boolean
    )
}

/**
 * 应用信息简要
 */
@Serializable
data class AppInfo(
    val packageName: String,
    val label: String,
    val isSystemApp: Boolean,
    val iconBase64: String? = null
)

val clipJson = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    encodeDefaults = true
}

inline fun <reified T> T.toJson(): String = clipJson.encodeToString(this)