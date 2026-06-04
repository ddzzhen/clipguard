package com.clipguard.app.shizuku

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceConnector
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Shizuku UserService
 * 当 Shizuku 授权后，运行在系统进程上下文，可直接执行需要系统权限的操作。
 * 用于绕过 pm grant/revoke 的权限限制。
 */
class PermissionUserService : rikka.shizuku.ShizukuUserService() {

    companion object {
        private const val TAG = "PermissionUserService"
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "PermissionUserService created")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "PermissionUserService destroyed")
    }

    /**
     * 执行 shell 命令（通过 Shizuku 的 binder 运行的 shell）
     */
    suspend fun executeCommand(command: Array<String>): CommandResult = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec(command)
            val stdout = BufferedReader(InputStreamReader(process.inputStream)).readText()
            val stderr = BufferedReader(InputStreamReader(process.errorStream)).readText()
            val exitCode = process.waitFor()
            CommandResult(exitCode, stdout, stderr)
        } catch (e: Exception) {
            Log.e(TAG, "executeCommand failed", e)
            CommandResult(-1, "", e.message ?: "Unknown error")
        }
    }

    data class CommandResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String
    ) {
        val isSuccess: Boolean get() = exitCode == 0
    }
}