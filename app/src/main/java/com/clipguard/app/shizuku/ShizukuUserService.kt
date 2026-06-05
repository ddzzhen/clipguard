package com.clipguard.app.shizuku

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Shizuku UserService 实现
 *
 * 由 Shizuku 在独立进程中实例化和管理，具有 shell/ADB 级系统权限。
 * 必须继承 android.app.Service 以符合 Android manifest 声明要求。
 *
 * Shizuku 通过 bindUserService() 连接此服务，并基于 IClipGuardInterface
 * 创建跨进程代理对象。所有方法调用都在 shell 权限进程中执行。
 */
class ShizukuUserService : Service(), IClipGuardInterface {

    companion object {
        private const val TAG = "ShizukuUserService"
    }

    override fun onBind(intent: Intent?): IBinder? {
        // Shizuku 会基于 IClipGuardInterface 自动生成 binder 代理
        // 这里的 onBind 由 Shizuku 框架接管
        Log.d(TAG, "onBind called")
        return null
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "ShizukuUserService created in process: ${android.os.Process.myPid()}")
    }

    override fun onDestroy() {
        Log.i(TAG, "ShizukuUserService destroyed")
        super.onDestroy()
    }

    /**
     * 执行 shell 命令（具有 shell 权限）
     *
     * Shizuku 管理此服务的进程具有 ADB shell 级别权限，
     * 因此 Runtime.exec() 在此上下文中可以执行 pm grant/revoke 等系统命令。
     */
    override fun execShell(command: String): String {
        return try {
            Log.d(TAG, "execShell: $command")
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val stdout = process.inputStream.bufferedReader().use(BufferedReader::readText)
            val stderr = process.errorStream.bufferedReader().use(BufferedReader::readText)
            val exitCode = process.waitFor()
            "$exitCode|$stdout|$stderr"
        } catch (e: Exception) {
            Log.e(TAG, "execShell failed", e)
            "-1||${e.message}"
        }
    }

    /**
     * 通过 dumpsys clipboard 从 shell 层读取剪贴板内容
     *
     * 此方法绕过 Android 10+ 的 ClipboardManager 后台限制，
     * 因为 dumpsys 命令以 shell 权限执行。
     */
    override fun readClipboard(): String {
        return try {
            val result = execShell("dumpsys clipboard")
            Log.d(TAG, "dumpsys clipboard output length: ${result.length}")

            val parts = result.split("|", limit = 3)
            if (parts.size < 2 || parts[0] != "0") return ""

            val output = parts[1]
            parseClipboardFromDumpsys(output)
        } catch (e: Exception) {
            Log.e(TAG, "readClipboard failed", e)
            ""
        }
    }

    /**
     * 解析 dumpsys clipboard 输出中的文本内容
     */
    private fun parseClipboardFromDumpsys(output: String): String {
        // 模式1: Android 12+ "ClipData { ... T:text content}"
        val textPattern1 = Regex("""T:([^}]+)""")
        textPattern1.find(output)?.let {
            return it.groupValues[1].trim()
        }

        // 模式2: "Primary clip:" 后面的内容行
        val lines = output.lines()
        for (i in lines.indices) {
            val line = lines[i].trim()
            if (line.startsWith("Primary clip:") || line.startsWith("Clip Data:")) {
                for (j in i + 1 until lines.size) {
                    val contentLine = lines[j].trim()
                    if (contentLine.isNotEmpty() &&
                        !contentLine.startsWith("android") &&
                        !contentLine.startsWith("ClipData") &&
                        !contentLine.startsWith("flags=") &&
                        !contentLine.startsWith("--")) {
                        return contentLine
                    }
                }
            }
        }

        // 模式3: MIME type text/plain 后的内容
        val textPattern2 = Regex("""text/plain[^{]*\{([^}]*)\}""")
        textPattern2.find(output)?.let {
            return it.groupValues[1].trim()
        }

        Log.w(TAG, "Could not parse clipboard text from dumpsys output")
        return ""
    }

    /**
     * 通过 shell 清除剪贴板
     */
    override fun clearClipboard(): Boolean {
        return try {
            // 方法1: 通过 service call clipboard
            val result1 = execShell("service call clipboard 2 i32 0 s16 ''")
            if (result1.startsWith("0|")) {
                Log.i(TAG, "Clipboard cleared via service call")
                return true
            }

            // 方法2: 通过 cmd clipboard set
            val result2 = execShell("cmd clipboard set '' 2>/dev/null")
            if (result2.startsWith("0|")) {
                Log.i(TAG, "Clipboard cleared via cmd clipboard")
                return true
            }

            Log.w(TAG, "All clipboard clear methods failed")
            false
        } catch (e: Exception) {
            Log.e(TAG, "clearClipboard failed", e)
            false
        }
    }

    /**
     * 销毁服务
     */
    override fun destroy() {
        Log.i(TAG, "destroy() called, killing process")
        stopSelf()
        android.os.Process.killProcess(android.os.Process.myPid())
    }
}
