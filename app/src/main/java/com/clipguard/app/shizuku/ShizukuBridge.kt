package com.clipguard.app.shizuku

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku

/**
 * Shizuku 桥接层
 *
 * 管理 Shizuku binder 生命周期，提供权限状态查询和 shell 命令执行。
 *
 * 注意：Shizuku 13.x 中 newProcess() 已从公开 API 移除，
 * bindUserService/unbindUserService 的具体签名因版本而异。
 * 当前 shell 命令通过 Runtime.exec() 执行（普通应用权限），
 * 完整的 shell 权限执行需要后续确认 Shizuku 版本后进行适配。
 */
object ShizukuBridge {

    private const val TAG = "ShizukuBridge"

    private val _isAvailable = MutableStateFlow(false)
    val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    private val _isBinderAlive = MutableStateFlow(false)
    val isBinderAlive: StateFlow<Boolean> = _isBinderAlive.asStateFlow()

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
                }
            } catch (_: Throwable) {}

            try {
                Shizuku.addBinderDeadListener {
                    Log.w(TAG, "Shizuku binder dead")
                    _isBinderAlive.value = false
                    refreshState()
                }
            } catch (_: Throwable) {}

            Log.i(TAG, "ShizukuBridge initialized")
        } catch (e: Throwable) {
            Log.e(TAG, "ShizukuBridge init failed", e)
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
            }
        } catch (e: Throwable) {
            Log.w(TAG, "requestPermission failed", e)
        }
    }

    // ── Shell 命令执行 ──

    /**
     * 执行 shell 命令
     *
     * 当前通过 Runtime.exec() 执行（普通应用权限）。
     *
     * TODO: 当确认 Shizuku 版本的 UserService API 签名后，可改为通过
     * Shizuku UserService 在 shell 权限进程中执行，以支持 pm grant/revoke 等操作。
     * 参见 ShizukuUserService.kt 和 IClipGuardInterface.kt 的预留实现。
     */
    fun execShell(vararg commands: String): ShellResult {
        val command = commands.joinToString(" ")

        return try {
            Log.d(TAG, "Executing: $command")
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val exitCode = process.waitFor()
            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            ShellResult(exitCode, stdout, stderr)
        } catch (e: Exception) {
            Log.e(TAG, "execShell failed", e)
            ShellResult(-1, "", e.message ?: "Unknown error")
        }
    }

    /**
     * 通过 shell 读取剪贴板（仅当 Shizuku UserService 可用时有效）
     */
    fun readClipboardViaShell(): String {
        val result = execShell("dumpsys clipboard")
        if (!result.isSuccess || result.stdout.isBlank()) return ""

        val output = result.stdout

        val tPattern = Regex("""T:([^}]+)""")
        tPattern.find(output)?.let { return it.groupValues[1].trim() }

        val plainPattern = Regex("""text/plain[^{]*\{([^}]*)\}""")
        plainPattern.find(output)?.let { return it.groupValues[1].trim() }

        return ""
    }

    /**
     * 通过 shell 清除剪贴板（仅当 Shizuku UserService 可用时有效）
     */
    fun clearClipboardViaShell(): Boolean {
        val r1 = execShell("service call clipboard 2 i32 0 s16 ''")
        if (r1.isSuccess) return true

        val r2 = execShell("cmd clipboard set '' 2>/dev/null")
        return r2.isSuccess
    }

    // ── 工具方法 ──

    data class ShellResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String
    ) {
        val isSuccess: Boolean get() = exitCode == 0
    }
}
