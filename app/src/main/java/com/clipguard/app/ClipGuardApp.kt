package com.clipguard.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class ClipGuardApp : Application() {

    companion object {
        const val CLIPBOARD_CHANNEL_ID = "clipboard_guard_channel"
        const val PERMISSION_CHANNEL_ID = "permission_alert_channel"
        lateinit var instance: ClipGuardApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java) ?: return

        // 剪贴板守护通知渠道
        val clipboardChannel = NotificationChannel(
            CLIPBOARD_CHANNEL_ID,
            "剪贴板守护",
            NotificationManager.IMPORTANCE_LOW // 低打扰，常驻通知
        ).apply {
            description = "剪贴板实时保护服务状态"
            setShowBadge(false)
        }

        // 敏感内容告警渠道
        val alertChannel = NotificationChannel(
            PERMISSION_CHANNEL_ID,
            "隐私告警",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "剪贴板敏感内容检测告警"
            enableVibration(true)
        }

        manager.createNotificationChannel(clipboardChannel)
        manager.createNotificationChannel(alertChannel)
    }
}