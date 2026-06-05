package com.clipguard.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipguard.app.model.ClipboardEvent
import com.clipguard.app.model.ClipboardEvent.ActionTaken
import com.clipguard.app.model.ClipboardEvent.DetectedType
import com.clipguard.app.service.ClipboardGuardService
import com.clipguard.app.shizuku.ShizukuBridge
import com.clipguard.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HomeScreen(
    shizukuAvailable: Boolean,
    serviceRunning: Boolean,
    accessibilityEnabled: Boolean,
    eventCount: Int,
    onToggleService: (Boolean) -> Unit,
    onNavigateToClipboard: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    onNavigateToAccessibilitySettings: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 标题
        item {
            Text(
                "ClipGuard",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = OnDarkText
            )
            Text(
                "剪贴板隐私守护",
                fontSize = 14.sp,
                color = OnDarkTextMuted
            )
        }

        // AccessibilityService 未开启时的醒目提示
        if (!accessibilityEnabled) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onNavigateToAccessibilitySettings),
                    colors = CardDefaults.cardColors(containerColor = WarningOrange.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = WarningOrange,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "需要开启无障碍服务",
                                fontWeight = FontWeight.SemiBold,
                                color = WarningOrange,
                                fontSize = 15.sp
                            )
                            Text(
                                "点击此处前往设置 → 无障碍 → ClipGuard → 开启",
                                fontSize = 13.sp,
                                color = OnDarkTextMuted
                            )
                        }
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = WarningOrange
                        )
                    }
                }
            }
        }

        // 状态卡片
        item {
            StatusCards(
                shizukuAvailable = shizukuAvailable,
                serviceRunning = serviceRunning,
                accessibilityEnabled = accessibilityEnabled,
                eventCount = eventCount
            )
        }

        // 守护开关
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            "剪贴板守护",
                            fontWeight = FontWeight.SemiBold,
                            color = OnDarkText
                        )
                        Text(
                            if (serviceRunning) "正在运行" else "已停止",
                            fontSize = 12.sp,
                            color = if (serviceRunning) SuccessGreen else ErrorRed
                        )
                    }
                    Switch(
                        checked = serviceRunning,
                        onCheckedChange = onToggleService,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = DarkBackground,
                            checkedTrackColor = Primary
                        )
                    )
                }
            }
        }

        // 快捷入口
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickEntryCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.ContentPaste,
                    title = "剪贴板日志",
                    subtitle = "$eventCount 条记录",
                    onClick = onNavigateToClipboard
                )
                QuickEntryCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Security,
                    title = "权限管理",
                    subtitle = "Shizuku",
                    onClick = onNavigateToPermissions
                )
            }
        }

        // 保护方式说明
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "保护方式",
                        fontWeight = FontWeight.SemiBold,
                        color = Primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    ProtectionMethodRow(
                        icon = Icons.Default.Accessibility,
                        label = "无障碍服务",
                        description = if (accessibilityEnabled) "已开启 - Android 10+ 完整保护" else "未开启 - 点击上方卡片开启",
                        isActive = accessibilityEnabled
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    ProtectionMethodRow(
                        icon = Icons.Default.Bolt,
                        label = "Shizuku 辅助",
                        description = if (shizukuAvailable) "已连接 - Shell 层级剪贴板操作" else "未连接 - 安装 Shizuku 并授权",
                        isActive = shizukuAvailable
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    ProtectionMethodRow(
                        icon = Icons.Default.Shield,
                        label = "前台服务守护",
                        description = if (serviceRunning) "运行中 - 基础监控 + 通知告警" else "未运行",
                        isActive = serviceRunning
                    )
                }
            }
        }

        // 防护策略说明
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "防护策略",
                        fontWeight = FontWeight.SemiBold,
                        color = Primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    StrategyRow("密码 / API密钥 / 银行卡", "检测到立即清除")
                    StrategyRow("身份证 / 手机号 / 邮箱", "告警通知但不自动清除")
                    StrategyRow("IP 地址", "记录日志便于审计")
                }
            }
        }
    }
}

@Composable
private fun StatusCards(
    shizukuAvailable: Boolean,
    serviceRunning: Boolean,
    accessibilityEnabled: Boolean,
    eventCount: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatusCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.Accessibility,
            label = "无障碍",
            value = if (accessibilityEnabled) "已开启" else "未开启",
            isGood = accessibilityEnabled
        )
        StatusCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.Bolt,
            label = "Shizuku",
            value = if (shizukuAvailable) "已连接" else "未连接",
            isGood = shizukuAvailable
        )
        StatusCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.Shield,
            label = "守护",
            value = if (serviceRunning) "保护中" else "已暂停",
            isGood = serviceRunning
        )
        StatusCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.History,
            label = "拦截",
            value = "$eventCount 次",
            isGood = true
        )
    }
}

@Composable
private fun StatusCard(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    isGood: Boolean
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isGood) SuccessGreen else ErrorRed,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(label, fontSize = 10.sp, color = OnDarkTextMuted)
            Text(
                value,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isGood) SuccessGreen else ErrorRed
            )
        }
    }
}

@Composable
private fun ProtectionMethodRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    description: String,
    isActive: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isActive) SuccessGreen else OnDarkTextMuted,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = if (isActive) SuccessGreen else OnDarkText
            )
            Text(
                description,
                fontSize = 11.sp,
                color = OnDarkTextMuted
            )
        }
    }
}

@Composable
private fun QuickEntryCard(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, fontWeight = FontWeight.SemiBold, color = OnDarkText)
            Text(subtitle, fontSize = 12.sp, color = OnDarkTextMuted)
        }
    }
}

@Composable
private fun StrategyRow(pattern: String, action: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(pattern, fontSize = 13.sp, color = OnDarkText)
        Text(action, fontSize = 13.sp, color = Primary)
    }
}
