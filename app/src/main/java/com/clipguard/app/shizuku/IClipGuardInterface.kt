package com.clipguard.app.shizuku

/**
 * ClipGuard Shizuku UserService 接口
 *
 * 定义通过 Shizuku IPC 调用的远程方法。
 * Shizuku 会在独立的 shell 权限进程中实例化实现类，
 * 从而获得执行 pm grant/revoke 等系统命令所需的权限。
 */
interface IClipGuardInterface {
    /**
     * 执行 shell 命令（在 Shizuku shell 权限进程中执行）
     * @param command 要执行的完整命令
     * @return 命令的输出结果 (stdout + stderr)，格式: "exitCode|stdout|stderr"
     */
    fun execShell(command: String): String

    /**
     * 读取剪贴板内容（通过 dumpsys clipboard 从 shell 层读取）
     * @return 剪贴板文本内容，读取失败返回空字符串
     */
    fun readClipboard(): String

    /**
     * 从 shell 层清除剪贴板
     * @return 是否成功清除
     */
    fun clearClipboard(): Boolean

    /**
     * 销毁服务
     */
    fun destroy()
}
