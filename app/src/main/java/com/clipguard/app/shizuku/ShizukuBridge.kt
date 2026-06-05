package com.clipguard.app.shizuku

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
 * 注意：Shizuku 13.x 中 newProcess() 标记为 @RestrictTo，但方法仍存在于字节码中。
 * 通过反射调用以绕过编译期限制。UserService 是官方推荐的长期方案（需要 AIDL）。
 */
object ShizukuBridge {

    private const val TAG = "ShizukuBridge"

    private val _isAvailable = MutableStateFlow(false)
    val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    private val _isBinderAlive = MutableStateFlow(false)
    val isBinderAlive: StateFlow<Boolean> = _isBinderAlive.asStateFlow()

    private val _hasPermission = MutableStateFlow(false)
    val hasPermissionFlow: StateFlow<Boolean> = _hasPermission.asStateFlow()

    // 缓存反射获取的 newProcess 方法
    private var newProcessMethod: java.lang.reflect.Method? = null

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
                    _hasPermission.value = false
                    refreshState()
                }
            } catch (_: Throwable) {}

            try {
                Shizuku.addRequestPermissionResultListener { requestCode, grantResult ->
                    Log.i(TAG, "Permission result: code=$requestCode, granted=$grantResult")
                    val granted = grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED
                    _hasPermission.value = granted
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
        _hasPermission.value = hasPermission()
    }

    fun hasPermission(): Boolean {
        return try {
            if (!Shizuku.pingBinder()) return false
            Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
        } catch (e: Throwable) {
            false
        }
    }

    /**
     * 请求 Shizuku 权限（遵循官方文档的推荐流程）
     */
    fun requestPermission(activity: android.app.Activity, requestCode: Int = 0) {
        try {
            // Shizuku pre-v11 不支持
            if (Shizuku.isPreV11()) {
                Log.w(TAG, "Shizuku pre-v11 is unsupported")
                return
            }

            if (Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                // 已授权
                _hasPermission.value = true
                return
            }

            if (Shizuku.shouldShowRequestPermissionRationale()) {
                // 用户选择了"拒绝且不再询问"
                Log.w(TAG, "User denied permission permanently")
                return
            }

            // 请求权限（Shizuku Manager 会弹出授权对话框）
            Shizuku.requestPermission(requestCode)
        } catch (e: Throwable) {
            Log.w(TAG, "requestPermission failed", e)
        }
    }

    // ── Shell 命令执行（通过反射调用 newProcess）──

    /**
     * 以 shell 权限执行命令
     *
     * 通过反射调用 Shizuku.newProcess() 绕过编译期 @RestrictTo 限制。
     * 如果反射失败，回退到 Runtime.exec()（普通权限）。
     */
    fun execShell(vararg commands: String): ShellResult {
        val command = commands.joinToString(" ")

        // 策略1: Shizuku 反射调用（shell/root 权限）
        if (hasPermission()) {
            try {
                return executeViaShizukuReflection(command)
            } catch (e: Exception) {
                Log.w(TAG, "Shizuku reflection exec failed: ${e.message}")
            }
        }

        // 策略2: Runtime.exec() 回退
        return executeViaRuntime(command)
    }

    /**
     * 通过反射调用 Shizuku.newProcess(String[], String[], String)
     */
    @Suppress("BanUncheckedReflection")
    private fun executeViaShizukuReflection(command: String): ShellResult {
        try {
            // 缓存反射方法
            if (newProcessMethod == null) {
                newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                    "newProcess",
                    Array<String>::class.java,
                    Array<String>::class.java,
                    String::class.java
                )
                newProcessMethod?.isAccessible = true
            }

            val method = newProcessMethod
                ?: throw NoSuchMethodException("Shizuku.newProcess not found via reflection")

            Log.d(TAG, "Executing via Shizuku (reflection): $command")
            val process = method.invoke(null, arrayOf("sh", "-c", command), null, null) as Process
            val exitCode = process.waitFor()
            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            ShellResult(exitCode, stdout, stderr)
        } catch (e: Exception) {
            Log.e(TAG, "executeViaShizukuReflection failed", e)
            throw e
        }
    }

    /**
     * Runtime.exec() 回退（普通应用权限）
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
     * 通过 shell 读取剪贴板（需要 Shizuku 授权才能绕过 Android 10+ 限制）
     */
    fun readClipboardViaShell(): String {
        val result = execShell("dumpsys clipboard")
        if (!result.isSuccess || result.stdout.isBlank()) return ""

        val output = result.stdout

        // Android 12+: "T:text内容"
        val tPattern = Regex("""T:([^}]+)""")
        tPattern.find(output)?.let { return it.groupValues[1].trim() }

        // 回退: text/plain 后的内容
        val plainPattern = Regex("""text/plain[^{]*\{([^}]*)\}""")
        plainPattern.find(output)?.let { return it.groupValues[1].trim() }

        return ""
    }

    /**
     * 通过 shell 清除剪贴板
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
