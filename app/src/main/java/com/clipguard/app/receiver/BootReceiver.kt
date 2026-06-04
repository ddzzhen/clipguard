package com.clipguard.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.clipguard.app.service.ClipboardGuardService

/**
 * 开机自启：设备启动后自动拉起剪贴板守护服务
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            ClipboardGuardService.start(context)
        }
    }
}