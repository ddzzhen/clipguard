package com.clipguard.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.clipguard.app.service.ClipboardAccessibilityService
import com.clipguard.app.service.ClipboardGuardService
import com.clipguard.app.shizuku.ShizukuBridge
import com.clipguard.app.ui.ClipboardScreen
import com.clipguard.app.ui.HomeScreen
import com.clipguard.app.ui.PermissionScreen
import com.clipguard.app.ui.theme.ClipGuardTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    // 服务是否运行中
    private val _serviceRunning = MutableStateFlow(false)

    // 通知权限请求
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startClipboardService()
        } else {
            Toast.makeText(this, "需要通知权限才能运行前台服务", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 请求必要权限（Android 13+ 通知权限）
        requestRequiredPermissions()

        // 延迟初始化 Shizuku，避免 decorView 空指针和 binder 未就绪
        lifecycleScope.launch {
            delay(200)
            try {
                ShizukuBridge.init()
            } catch (_: Throwable) {}
            delay(600)
            try {
                ShizukuBridge.requestPermission(this@MainActivity)
            } catch (_: Throwable) {}
        }

        setContent {
            ClipGuardTheme {
                val shizukuAvailable by ShizukuBridge.isAvailable.collectAsStateWithLifecycle()
                val serviceRunning by _serviceRunning.collectAsStateWithLifecycle()
                val accessibilityEnabled = remember { mutableStateOf(isAccessibilityServiceEnabled()) }

                // 定期检查 AccessibilityService 状态
                LaunchedEffect(Unit) {
                    while (true) {
                        accessibilityEnabled.value = isAccessibilityServiceEnabled()
                        delay(2000)
                    }
                }

                val eventState = remember { mutableStateOf(0) }

                ClipGuardNav(
                    shizukuAvailable = shizukuAvailable,
                    serviceRunning = serviceRunning,
                    accessibilityEnabled = accessibilityEnabled.value,
                    eventCount = eventState.value,
                    onToggleService = { enable ->
                        if (enable) {
                            checkAndStartService()
                        } else {
                            ClipboardGuardService.stop(this@MainActivity)
                            _serviceRunning.value = false
                        }
                    },
                    onNavigateToAccessibilitySettings = {
                        openAccessibilitySettings()
                    },
                    onRefreshEvents = { eventState.value++ }
                )
            }
        }
    }

    /**
     * 检查 AccessibilityService 是否已开启
     */
    private fun isAccessibilityServiceEnabled(): Boolean {
        val serviceName = "$packageName/${ClipboardAccessibilityService::class.java.name}"
        return try {
            val enabledServices = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            enabledServices?.contains(serviceName) == true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 打开无障碍设置页面
     */
    private fun openAccessibilitySettings() {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, "请在列表中找到 ClipGuard 并开启", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "无法打开无障碍设置", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 在应用启动时请求必要权限
     */
    private fun requestRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                lifecycleScope.launch {
                    delay(1500) // 等待 UI 完全就绪
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    private fun checkAndStartService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        startClipboardService()
    }

    private fun startClipboardService() {
        ClipboardGuardService.start(this)
        _serviceRunning.value = true
    }
}

@Composable
fun ClipGuardNav(
    shizukuAvailable: Boolean,
    serviceRunning: Boolean,
    accessibilityEnabled: Boolean,
    eventCount: Int,
    onToggleService: (Boolean) -> Unit,
    onNavigateToAccessibilitySettings: () -> Unit,
    onRefreshEvents: () -> Unit
) {
    var currentScreen by remember { mutableStateOf("home") }

    when (currentScreen) {
        "clipboard" -> {
            val events by ClipboardAccessibilityService.eventLog.collectAsStateWithLifecycle()
            ClipboardScreen(
                events = events,
                onClearLog = { ClipboardAccessibilityService.clearLog() },
                onBack = { currentScreen = "home" }
            )
        }
        "permissions" -> {
            PermissionScreen(
                shizukuAvailable = shizukuAvailable,
                onBack = { currentScreen = "home" }
            )
        }
        else -> {
            HomeScreen(
                shizukuAvailable = shizukuAvailable,
                serviceRunning = serviceRunning,
                accessibilityEnabled = accessibilityEnabled,
                eventCount = eventCount,
                onToggleService = onToggleService,
                onNavigateToClipboard = { currentScreen = "clipboard" },
                onNavigateToPermissions = { currentScreen = "permissions" },
                onNavigateToAccessibilitySettings = onNavigateToAccessibilitySettings
            )
        }
    }
}
