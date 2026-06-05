package com.clipguard.app.shizuku

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku

/**
 * Shizuku 桥接层
 *
 * 管理 Shizuku binder 生命周期，提供 shell 权限命令执行和剪贴板操作。
 *
 * 命令执行策略：
 * 1. Shizuku UserService — 在独立 shell 进程中运行（优先）
 * 2. Runtime.exec() 回退 — 普通应用权限（UserService 未绑定时）
 *
 * 注意：Shizuku 13.x 中 newProcess() 已从公开 API 移除。
 * UserService 是官方推荐的替代方案。
 */
object ShizukuBridge {

    private const val TAG = "ShizukuBridge"

    private val _isAvailable = MutableStateFlow(false)
    val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    private val _isBinderAlive = MutableStateFlow(false)
    val isBinderAlive: StateFlow<Boolean> = _isBinderAlive.asStateFlow()

    private val _isUserServiceBound = MutableStateFlow(false)
    val isUserServiceBound: StateFlow<Boolean> = _isUserServiceBound.asStateFlow()

    // UserService 代理引用
    private var userService: IClipGuardInterface? = null
    private var userServiceArgs: Shizuku.UserServiceArgs? = null
    private val serviceLock = Any()

    /**
     * 初始化 Shizuku 监听
     */
    fun init() {
        try {
            refreshState()

            try {
                Shizuku.addBinderReceivedListener {
                    Log.i(TAG, "Shizuku binder received")
                    _isBinderAlive.value = true
                    refreshState()
                    if (hasPermission()) bindUserService()
                }
            } catch (_: Throwable) {}

            try {
                Shizuku.addBinderDeadListener {
                    Log.w(TAG, "Shizuku binder dead")
                    _isBinderAlive.value = false
                    _isUserServiceBound.value = false
                    synchronized(serviceLock) { userService = null }
                    refreshState()
                }
            } catch (_: Throwable) {}

            // 初始绑定
            if (Shizuku.pingBinder() && hasPermission()) {
                bindUserService()
            }

            Log.i(TAG, "ShizukuBridge initialized")
        } catch (e: Throwable) {
            Log.e(TAG, "ShizukuBridge init failed", e)
        }
    }

    // ── UserService 绑定 ──

    fun bindUserService() {
        if (!Shizuku.pingBinder() || !hasPermission()) {
            Log.w(TAG, "Cannot bind UserService: not available or not authorized")
            return
        }

        try {
            // Shizuku 13.x UserServiceArgs: 使用基本构造器
            val componentName = ComponentName(
                "com.clipguard.app",
                "com.clipguard.app.shizuku.ShizukuUserService"
            )
            val args = Shizuku.UserServiceArgs(componentName)
            userServiceArgs = args

            Shizuku.bindUserService(args, userServiceConnection)
            Log.i(TAG, "UserService binding requested")
        } catch (e: Exception) {
            Log.e(TAG, "bindUserService failed", e)
        }
    }

    fun unbindUserService() {
        try {
            val args = userServiceArgs
            if (args != null) {
                Shizuku.unbindUserService(args, userServiceConnection)
            }
        } catch (e: Exception) {
            Log.e(TAG, "unbindUserService failed", e)
        }
        synchronized(serviceLock) { userService = null }
        userServiceArgs = null
        _isUserServiceBound.value = false
    }

    private val userServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            Log.i(TAG, "UserService connected")
            if (binder != null) {
                try {
                    synchronized(serviceLock) {
                        @Suppress("UNCHECKED_CAST")
                        userService = binder as? IClipGuardInterface
                    }
                    _isUserServiceBound.value = userService != null
                    Log.i(TAG, "UserService proxy created: ${userService != null}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to create UserService proxy", e)
                    _isUserServiceBound.value = false
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.w(TAG, "UserService disconnected")
            synchronized(serviceLock) { userService = null }
            _isUserServiceBound.value = false
        }
    }

    // ── 状态检查 ──

    private fun refreshState() {
        _isAvailable.value = try {
            Shizuku.pingBinder()
        } catch (e: Throwable) {
            false
        }
    }

    fun hasPermission(): Boolean {
        return try {
            if (!Shizuku.pingBinder()) return false
            Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
        } catch (e: Throwable) {
            false
        }
    }

    fun requestPermission(activity: android.app.Activity, requestCode: Int = 0) {
        try {
            if (!hasPermission()) {
                Shizuku.requestPermission(requestCode)
            } else {
                bindUserService()
            }
        } catch (e: Throwable) {
            Log.w(TAG, "requestPermission failed", e)
        }
    }

    // ── Shell 命令执行 ──

    /**
     * 执行 shell 命令
     *
     * 优先使用 Shizuku UserService（shell 权限进程），
     * 不可用时回退到 Runtime.exec()（普通应用权限）。
     *
     * 注意：Shizuku 13.x 中 newProcess() 已移除。
     * 权限提升依赖 UserService 在独立 shell 进程中运行。
     */
    fun execShell(vararg commands: String): ShellResult {
        val command = commands.joinToString(" ")

        // 策略1: 使用已绑定的 UserService（shell 权限）
        synchronized(serviceLock) {
            userService?.let { service ->
                return try {
                    Log.d(TAG, "Executing via UserService: $command")
                    val raw = service.execShell(command)
                    parseShellResult(raw)
                } catch (e: Exception) {
                    Log.e(TAG, "UserService execShell failed", e)
                    ShellResult(-1, "", "UserService error: ${e.message}")
                }
            }
        }

        // 策略2: 回退到 Runtime.exec()（普通权限）
        // pm grant/revoke 需要 shell 权限，此回退方案对这些命令无效
        Log.w(TAG, "UserService not bound, falling back to Runtime.exec (no elevated privileges)")
        return executeViaRuntime(command)
    }

    /**
     * 通过 Runtime.exec() 执行命令（普通应用权限）
     */
    private fun executeViaRuntime(command: String): ShellResult {
        return try {
            Log.d(TAG, "Executing via Runtime: $command")
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val exitCode = process.waitFor()
            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            ShellResult(exitCode, stdout, stderr)
        } catch (e: Exception) {
            Log.e(TAG, "executeViaRuntime failed", e)
            ShellResult(-1, "", e.message ?: "Unknown error")
        }
    }

    // ── 剪贴板操作（通过 Shell） ──

    /**
     * 通过 shell 读取剪贴板（需要 UserService 绑定才能绕过 Android 10+ 限制）
     */
    fun readClipboardViaShell(): String {
        val result = execShell("dumpsys clipboard")
        if (!result.isSuccess || result.stdout.isBlank()) return ""

        val output = result.stdout

        // 解析 dumpsys 输出: Android 12+ "T:text内容"
        val tPattern = Regex("""T:([^}]+)""")
        tPattern.find(output)?.let { return it.groupValues[1].trim() }

        // 回退: text/plain 后的内容
        val plainPattern = Regex("""text/plain[^{]*\{([^}]*)\}""")
        plainPattern.find(output)?.let { return it.groupValues[1].trim() }

        Log.d(TAG, "Could not parse clipboard text from dumpsys")
        return ""
    }

    /**
     * 通过 shell 清除剪贴板（需要 UserService 绑定）
     */
    fun clearClipboardViaShell(): Boolean {
        // 方法1: service call clipboard
        val r1 = execShell("service call clipboard 2 i32 0 s16 ''")
        if (r1.isSuccess) {
            Log.i(TAG, "Clipboard cleared via service call")
            return true
        }

        // 方法2: cmd clipboard set
        val r2 = execShell("cmd clipboard set '' 2>/dev/null")
        if (r2.isSuccess) {
            Log.i(TAG, "Clipboard cleared via cmd clipboard")
            return true
        }

        Log.w(TAG, "All shell clipboard clear methods failed")
        return false
    }

    // ── 工具方法 ──

    private fun parseShellResult(raw: String): ShellResult {
        val parts = raw.split("|", limit = 3)
        return if (parts.size >= 3) {
            val exitCode = parts[0].toIntOrNull() ?: -1
            ShellResult(exitCode, parts[1], parts[2])
        } else {
            ShellResult(-1, raw, "")
        }
    }

    data class ShellResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String
    ) {
        val isSuccess: Boolean get() = exitCode == 0
    }
}
