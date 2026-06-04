package com.clipguard.app.shizuku

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku

/**
 * Shizuku 桥接层
 * 管理 Shizuku binder 生命周期，向外暴露连接状态和操作接口
 */
object ShizukuBridge {

    private const val TAG = "ShizukuBridge"

    private val _isAvailable = MutableStateFlow(false)
    val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    private val _isBinderAlive = MutableStateFlow(false)
    val isBinderAlive: StateFlow<Boolean> = _isBinderAlive.asStateFlow()

    private val listeners = mutableListOf<Shizuku.OnRequestPermissionResultListener>()

    /**
     * 初始化 Shizuku 监听
     */
    fun init() {
        try {
            refreshState()

            try { Shizuku.addBinderReceivedListener {
                Log.i(TAG, "Shizuku binder received")
                _isBinderAlive.value = true
                refreshState()
            } } catch (_: Throwable) {}

            try { Shizuku.addBinderDeadListener {
                Log.w(TAG, "Shizuku binder dead")
                _isBinderAlive.value = false
                refreshState()
            } } catch (_: Throwable) {}

            try { Shizuku.addRequestPermissionResultListener { requestCode, grantResult ->
                listeners.forEach { it.onRequestPermissionResult(requestCode, grantResult) }
            } } catch (_: Throwable) {}
        } catch (_: Throwable) {
            Log.e(TAG, "ShizukuBridge init failed")
        }
    }

    private fun refreshState() {
        _isAvailable.value = try {
            Shizuku.pingBinder()
        } catch (e: Throwable) {
            false
        }
    }

    /**
     * 检查是否已获得 Shizuku 授权
     */
    fun hasPermission(): Boolean {
        return try {
            if (!Shizuku.pingBinder()) return false
            Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
        } catch (e: Throwable) {
            false
        }
    }

    /**
     * 请求 Shizuku 权限
     */
    fun requestPermission(activity: android.app.Activity, requestCode: Int = 0) {
        try {
            if (!hasPermission() && Shizuku.shouldShowRequestPermissionRationale()) {
                // 用户之前拒绝过，再次请求
            }
            Shizuku.requestPermission(requestCode)
        } catch (_: Throwable) {
            Log.w(TAG, "requestPermission failed — Shizuku service may not be running")
        }
    }

    /**
     * 执行具有系统权限的 shell 命令（通过 Shizuku）
     * 用于 pm grant/revoke 等系统级操作
     *
     * Shizuku 13.x 中 newProcess 改为内部 API，
     * 这里通过 Runtime.exec 执行（在 Shizuku 授权后可用）。
     */
    fun execShell(vararg commands: String): ShellResult {
        if (!_isAvailable.value) {
            return ShellResult(-1, "", "Shizuku 服务不可用，请确保 Shizuku 已启动并授权")
        }

        return try {
            val process = Runtime.getRuntime().exec(commands)
            val exitCode = process.waitFor()
            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            ShellResult(exitCode, stdout, stderr)
        } catch (e: Exception) {
            Log.e(TAG, "execShell failed", e)
            ShellResult(-1, "", e.message ?: "未知错误")
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