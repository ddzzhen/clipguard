package com.clipguard.app.shizuku

import android.content.pm.PackageManager
import com.clipguard.app.ClipGuardApp
import com.clipguard.app.model.AppPermission

/**
 * 权限控制器
 * 通过 Shizuku 执行系统级 pm grant/revoke 操作
 */
object PermissionController {

    // 危险权限组映射：权限名 → 中文标签
    private val DANGEROUS_PERMISSIONS = mapOf(
        "android.permission.CAMERA" to "相机",
        "android.permission.RECORD_AUDIO" to "麦克风",
        "android.permission.ACCESS_FINE_LOCATION" to "精确定位",
        "android.permission.ACCESS_COARSE_LOCATION" to "粗略定位",
        "android.permission.READ_CONTACTS" to "读取联系人",
        "android.permission.WRITE_CONTACTS" to "写入联系人",
        "android.permission.READ_SMS" to "读取短信",
        "android.permission.SEND_SMS" to "发送短信",
        "android.permission.RECEIVE_SMS" to "接收短信",
        "android.permission.READ_EXTERNAL_STORAGE" to "读取存储",
        "android.permission.WRITE_EXTERNAL_STORAGE" to "写入存储",
        "android.permission.READ_MEDIA_IMAGES" to "读取图片",
        "android.permission.READ_MEDIA_VIDEO" to "读取视频",
        "android.permission.READ_MEDIA_AUDIO" to "读取音频",
        "android.permission.READ_PHONE_STATE" to "读取手机状态",
        "android.permission.CALL_PHONE" to "拨打电话",
        "android.permission.READ_CALL_LOG" to "读取通话记录",
        "android.permission.BODY_SENSORS" to "身体传感器",
        "android.permission.ACTIVITY_RECOGNITION" to "活动识别",
        "android.permission.POST_NOTIFICATIONS" to "通知权限",
        "android.permission.ACCESS_BACKGROUND_LOCATION" to "后台定位",
        "android.permission.BLUETOOTH_CONNECT" to "蓝牙连接",
        "android.permission.BLUETOOTH_SCAN" to "蓝牙扫描",
    )

    /**
     * 获取某个应用所有权限的授予状态
     */
    fun getAppPermissions(packageName: String): AppPermission {
        val context = ClipGuardApp.instance
        val pm = context.packageManager
        val appLabel = try {
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }

        val permissionStates = DANGEROUS_PERMISSIONS.map { (permName, permLabel) ->
            val granted = pm.checkPermission(permName, packageName) == PackageManager.PERMISSION_GRANTED
            AppPermission.PermissionState(
                name = permName,
                label = permLabel,
                granted = granted,
                dangerous = true
            )
        }

        return AppPermission(
            packageName = packageName,
            appLabel = appLabel,
            permissions = permissionStates
        )
    }

    /**
     * 通过 Shizuku 授予权限
     */
    fun grantPermission(packageName: String, permission: String): Boolean {
        val result = ShizukuBridge.execShell("pm", "grant", packageName, permission)
        return result.isSuccess
    }

    /**
     * 通过 Shizuku 撤销权限
     */
    fun revokePermission(packageName: String, permission: String): Boolean {
        val result = ShizukuBridge.execShell("pm", "revoke", packageName, permission)
        return result.isSuccess
    }

    /**
     * 批量撤销某应用的全部危险权限（隐私一键锁死）
     */
    fun revokeAllDangerous(packageName: String): Int {
        var count = 0
        DANGEROUS_PERMISSIONS.keys.forEach { perm ->
            if (revokePermission(packageName, perm)) count++
        }
        return count
    }

    /**
     * 获取所有可管理的危险权限列表
     */
    fun getDangerousPermissionsList(): Map<String, String> = DANGEROUS_PERMISSIONS
}