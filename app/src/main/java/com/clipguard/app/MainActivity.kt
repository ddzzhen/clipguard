package com.clipguard.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clipguard.app.service.ClipboardGuardService
import com.clipguard.app.shizuku.ShizukuBridge
import com.clipguard.app.ui.ClipboardScreen
import com.clipguard.app.ui.HomeScreen
import com.clipguard.app.ui.PermissionScreen
import com.clipguard.app.ui.theme.ClipGuardTheme
import kotlinx.coroutines.flow.MutableStateFlow

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

        // 初始化 Shizuku
        ShizukuBridge.init()

        setContent {
            ClipGuardTheme {
                val shizukuAvailable by ShizukuBridge.isAvailable.collectAsStateWithLifecycle()
                val serviceRunning by _serviceRunning.collectAsStateWithLifecycle()

                // 从服务获取事件日志
                val eventState = remember { mutableStateOf(0) } // 用于触发重组

                ClipGuardNav(
                    shizukuAvailable = shizukuAvailable,
                    serviceRunning = serviceRunning,
                    eventCount = eventState.value,
                    onToggleService = { enable ->
                        if (enable) {
                            checkAndStartService()
                        } else {
                            ClipboardGuardService.stop(this@MainActivity)
                            _serviceRunning.value = false
                        }
                    },
                    onRefreshEvents = { eventState.value++ }
                )
            }
        }
    }

    private fun checkAndStartService() {
        // Android 13+ 需要通知权限
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
    eventCount: Int,
    onToggleService: (Boolean) -> Unit,
    onRefreshEvents: () -> Unit
) {
    var currentScreen by remember { mutableStateOf("home") }

    when (currentScreen) {
        "clipboard" -> {
            // 为了演示，使用空列表；实际需绑定到 ClipboardGuardService.eventLog
            ClipboardScreen(
                events = emptyList(),
                onClearLog = { /* TODO */ },
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
                eventCount = eventCount,
                onToggleService = onToggleService,
                onNavigateToClipboard = { currentScreen = "clipboard" },
                onNavigateToPermissions = { currentScreen = "permissions" }
            )
        }
    }
}