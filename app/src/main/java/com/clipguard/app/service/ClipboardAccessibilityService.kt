package com.clipguard.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Notification
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
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
 * 基于 AccessibilityService 的剪贴板守护
 *
 * AccessibilityService 可以在 Android 10+ 上更可靠地读取剪贴板内容，
 * 因为它具有更高的系统权限层级。
 *
 * 同时监听 AccessibilityEvent 来检测文本选择和复制操作，
 * 在这些事件发生时主动检查剪贴板内容。
 */
class ClipboardAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "ClipboardAccessibility"
        private const val NOTIFICATION_ID = 2001
        private const val ALERT_NOTIFICATION_ID = 2002

        /** 通过静态引用让 UI 层可以观察事件 */
        private val _eventLog = MutableStateFlow<List<ClipboardEvent>>(emptyList())
        val eventLog: StateFlow<List<ClipboardEvent>> = _eventLog.asStateFlow()

        private val maxLogSize = 200

        /** 判断 AccessibilityService 是否正在运行 */
        var isRunning: Boolean = false
            private set

        fun start(context: Context) {
            // AccessibilityService 通过系统设置开启，这里仅作记录
            Log.i(TAG, "AccessibilityService start requested")
        }

        fun stop(context: Context) {
            // AccessibilityService 无法通过代码停止，用户需在系统设置中关闭
            Log.i(TAG, "AccessibilityService stop requested")
        }

        fun clearLog() {
            _eventLog.value = emptyList()
        }

        private fun addEvent(event: ClipboardEvent) {
            val current = _eventLog.value.toMutableList()
            current.add(0, event)
            if (current.size > maxLogSize) {
                current.removeAt(current.lastIndex)
            }
            _eventLog.value = current
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var clipboardManager: ClipboardManager
    private var lastClipText: String? = null

    // 策略开关
    var autoClearEnabled: Boolean = true
    var warnOnSensitive: Boolean = true
    var monitorEnabled: Boolean = true

    // 剪贴板监听器（作为辅助手段）
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        if (!monitorEnabled) return@OnPrimaryClipChangedListener
        checkClipboard()
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        clipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        Log.i(TAG, "ClipboardAccessibilityService created")

        // 注册剪贴板监听器作为辅助
        try {
            clipboardManager.addPrimaryClipChangedListener(clipListener)
            Log.i(TAG, "Clipboard listener registered")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register clipboard listener", e)
        }

        // 启动前台通知
        startForegroundNotification()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        // 配置 AccessibilityService 信息
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            notificationTimeout = 100
        }
        serviceInfo = info
        Log.i(TAG, "AccessibilityService connected and configured")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !monitorEnabled) return

        // 在文本选择变化或窗口内容变化时检查剪贴板
        // 这些事件通常与复制操作相关
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED -> {
                // 用户可能在选择文本准备复制
                // 延迟检查：给系统时间完成复制操作
                serviceScope.launch {
                    delay(300) // 等待剪贴板更新
                    checkClipboard()
                }
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // 窗口切换时检查（应用切换可能导致剪贴板被读取）
                val packageName = event.packageName?.toString()
                if (packageName != null && packageName != packageName) {
                    Log.d(TAG, "Window changed to: $packageName")
                }
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "AccessibilityService interrupted")
    }

    override fun onDestroy() {
        isRunning = false
        try {
            clipboardManager.removePrimaryClipChangedListener(clipListener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove clipboard listener", e)
        }
        serviceScope.cancel()
        Log.i(TAG, "ClipboardAccessibilityService destroyed")
        super.onDestroy()
    }

    // ── 剪贴板检查 ──

    /**
     * 主动检查剪贴板内容
     * AccessibilityService 环境下，有机会读取到其他 app 写入的内容
     */
    private fun checkClipboard() {
        try {
            val clip = clipboardManager.primaryClip ?: return
            if (clip.itemCount == 0) return

            val text = clip.getItemAt(0).text?.toString()
            if (text.isNullOrEmpty()) {
                Log.d(TAG, "Clipboard content unreadable (may be background restriction)")
                return
            }
            if (text == lastClipText) return

            lastClipText = text
            Log.d(TAG, "Clipboard content captured: ${text.take(50)}...")
            processClipboardContent(text)
        } catch (e: Exception) {
            Log.e(TAG, "checkClipboard failed", e)
        }
    }

    /**
     * 处理捕获到的剪贴板内容
     */
    private fun processClipboardContent(text: String) {
        val detectedTypes = SensitiveDataDetector.detect(text)

        // 无敏感内容
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
     * 清除剪贴板
     */
    private fun clearClipboard() {
        try {
            val emptyClip = ClipData.newPlainText("", "")
            clipboardManager.setPrimaryClip(emptyClip)
            lastClipText = ""
            Log.i(TAG, "Clipboard cleared by AccessibilityService")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear clipboard", e)
        }
    }

    /**
     * 获取前台应用包名
     */
    private fun getForegroundPackage(): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as? android.app.usage.UsageStatsManager
                val now = System.currentTimeMillis()
                val stats = usageStatsManager?.queryUsageStats(
                    android.app.usage.UsageStatsManager.INTERVAL_DAILY,
                    now - 10_000,
                    now
                )
                stats?.maxByOrNull { it.lastTimeUsed }?.packageName
            } else null
        } catch (e: Exception) {
            null
        }
    }

    // ── 通知 ──

    private fun startForegroundNotification() {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, ClipGuardApp.CLIPBOARD_CHANNEL_ID)
            .setContentTitle("ClipGuard 守护中")
            .setContentText("无障碍服务正在保护您的剪贴板隐私")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(NOTIFICATION_ID, notification)
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
