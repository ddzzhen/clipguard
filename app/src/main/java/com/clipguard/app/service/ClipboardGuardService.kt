package com.clipguard.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.clipguard.app.ClipGuardApp
import com.clipguard.app.MainActivity
import com.clipguard.app.detector.SensitiveDataDetector
import com.clipguard.app.model.ClipboardEvent
import com.clipguard.app.model.ClipboardEvent.ActionTaken
import com.clipguard.app.model.ClipboardEvent.DetectedType
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 剪贴板守护前台服务
 *
 * 通过 ClipboardManager.OnPrimaryClipChangedListener 实时监听剪贴板变化，
 * 对内容进行敏感信息检测，根据策略自动清除或告警。
 */
class ClipboardGuardService : Service() {

    companion object {
        private const val TAG = "ClipboardGuard"
        private const val NOTIFICATION_ID = 1001
        private const val ALERT_NOTIFICATION_ID = 1002

        fun start(context: Context) {
            val intent = Intent(context, ClipboardGuardService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ClipboardGuardService::class.java))
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var clipboardManager: ClipboardManager

    // 事件日志（UI 可观察）
    private val _eventLog = MutableStateFlow<List<ClipboardEvent>>(emptyList())
    val eventLog: StateFlow<List<ClipboardEvent>> = _eventLog.asStateFlow()

    private val maxLogSize = 200
    private var lastClipText: String? = null

    // ── 策略开关（可从设置读取） ──
    var autoClearEnabled: Boolean = true          // 自动清除高风险内容
    var warnOnSensitive: Boolean = true           // 敏感内容通知告警
    var monitorEnabled: Boolean = true            // 总开关

    override fun onCreate() {
        super.onCreate()
        clipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        Log.i(TAG, "ClipboardGuardService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildForegroundNotification()
        startForeground(NOTIFICATION_ID, notification)

        // 注册剪贴板监听
        clipboardManager.addPrimaryClipChangedListener(clipListener)
        Log.i(TAG, "Clipboard listener registered")

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        clipboardManager.removePrimaryClipChangedListener(clipListener)
        serviceScope.cancel()
        Log.i(TAG, "ClipboardGuardService destroyed")
        super.onDestroy()
    }

    // ── 剪贴板监听器 ──

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        if (!monitorEnabled) return@OnPrimaryClipChangedListener

        val clip = clipboardManager.primaryClip ?: return@OnPrimaryClipChangedListener
        if (clip.itemCount == 0) return@OnPrimaryClipChangedListener

        // Android 10+ 限制：后台应用可能读取到空文本
        val text = clip.getItemAt(0).text?.toString()
        if (text.isNullOrEmpty()) {
            Log.d(TAG, "Clipboard change detected but content unreadable (Android 10+ background restriction)")
            return@OnPrimaryClipChangedListener
        }
        if (text == lastClipText) return@OnPrimaryClipChangedListener

        lastClipText = text
        handleClipboardChange(text)
    }

    /**
     * 处理剪贴板变化事件
     */
    private fun handleClipboardChange(text: String) {
        val detectedTypes = SensitiveDataDetector.detect(text)

        if (DetectedType.NONE in detectedTypes && detectedTypes.size == 1) {
            addEvent(ClipboardEvent(
                content = text.take(100),
                detectedTypes = listOf(DetectedType.NONE),
                sourcePackage = getForegroundPackage(),
                actionTaken = ActionTaken.ALLOWED
            ))
            return
        }

        Log.w(TAG, "Sensitive content detected: $detectedTypes")

        val action = if (autoClearEnabled && SensitiveDataDetector.shouldAutoClear(detectedTypes)) {
            clearClipboard()
            ActionTaken.CLEARED
        } else {
            ActionTaken.WARNED
        }

        addEvent(ClipboardEvent(
            content = SensitiveDataDetector.mask(text, detectedTypes),
            detectedTypes = detectedTypes,
            sourcePackage = getForegroundPackage(),
            actionTaken = action
        ))

        if (warnOnSensitive) {
            sendAlertNotification(detectedTypes, action)
        }
    }

    /**
     * 清除剪贴板内容
     */
    private fun clearClipboard() {
        try {
            val emptyClip = ClipData.newPlainText("", "")
            clipboardManager.setPrimaryClip(emptyClip)
            lastClipText = ""
            Log.i(TAG, "Clipboard cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear clipboard", e)
        }
    }

    /**
     * 尝试获取当前前台应用包名
     */
    private fun getForegroundPackage(): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as? android.app.usage.UsageStatsManager
                val now = System.currentTimeMillis()
                val stats = usageStatsManager?.queryUsageStats(
                    android.app.usage.UsageStatsManager.INTERVAL_DAILY,
                    now - 10000,
                    now
                )
                stats?.maxByOrNull { it.lastTimeUsed }?.packageName
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun addEvent(event: ClipboardEvent) {
        val current = _eventLog.value.toMutableList()
        current.add(0, event)
        if (current.size > maxLogSize) {
            current.removeAt(current.lastIndex)
        }
        _eventLog.value = current
    }

    // ── 通知构建 ──

    private fun buildForegroundNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, ClipGuardApp.CLIPBOARD_CHANNEL_ID)
            .setContentTitle("ClipGuard 守护中")
            .setContentText("正在保护您的剪贴板隐私")
            .setSmallIcon(android.R.drawable.ic_menu_manage) // 替换为自有图标
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun sendAlertNotification(detectedTypes: List<DetectedType>, action: ActionTaken) {
        val typeNames = detectedTypes.joinToString("、") { typeDisplayName(it) }
        val title = when (action) {
            ActionTaken.CLEARED -> "已清除敏感内容"
            ActionTaken.WARNED -> "检测到敏感内容"
            else -> "剪贴板活动"
        }
        val body = "检测到：$typeNames"

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                putExtra("navigate_to", "clipboard")
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, ClipGuardApp.PERMISSION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(ALERT_NOTIFICATION_ID, notification)
    }

    private fun typeDisplayName(type: DetectedType): String = when (type) {
        DetectedType.ID_CARD -> "身份证"
        DetectedType.BANK_CARD -> "银行卡"
        DetectedType.PHONE_NUMBER -> "手机号"
        DetectedType.PASSWORD -> "密码"
        DetectedType.EMAIL -> "邮箱"
        DetectedType.TOKEN -> "API密钥"
        DetectedType.IP_ADDRESS -> "IP地址"
        DetectedType.NONE -> "无"
    }
}