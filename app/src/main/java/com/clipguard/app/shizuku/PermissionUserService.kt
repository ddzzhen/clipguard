package com.clipguard.app.shizuku

import android.os.IBinder
import android.util.Log
import rikka.shizuku.ShizukuUserService

/**
 * Shizuku UserService — 让 Shizuku Manager 能识别并授权本应用。
 *
 * Shizuku 13.x 中，只需继承 ShizukuUserService 并暴露一个空 binder 即可。
 * 实际 shell 操作通过 ShizukuBridge 完成。
 */
class PermissionUserService : ShizukuUserService() {

    companion object {
        private const val TAG = "PermissionUserService"
    }

    override fun onBind(intent: android.content.Intent?): IBinder {
        Log.i(TAG, "PermissionUserService bound")
        // 返回一个空 binder，Shizuku 只需要服务能被绑定即可
        return object : IBinder {
            override fun queryLocalInterface(descriptor: String?): android.os.IInterface? = null
            override fun isBinderAlive(): Boolean = true
            override fun getInterfaceDescriptor(): String = "clipguard"
            override fun dump(fd: java.io.FileDescriptor?, args: Array<out String>?) {}
            override fun dumpAsync(fd: java.io.FileDescriptor?, args: Array<out String>?) {}
            override fun pingBinder(): Boolean = true
            override fun linkToDeath(recipient: IBinder.DeathRecipient?, flags: Int) {}
            override fun unlinkToDeath(recipient: IBinder.DeathRecipient?, flags: Int): Boolean = true
        }
    }
}