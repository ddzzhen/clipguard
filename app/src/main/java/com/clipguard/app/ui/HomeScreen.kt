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
    eventCount: Int,
    onToggleService: (Boolean) -> Unit,
    onNavigateToClipboard: () -> Unit,
    onNavigateToPermissions: () -> Unit
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

        // 状态卡片
        item {
            StatusCards(
                shizukuAvailable = shizukuAvailable,
                serviceRunning = serviceRunning,
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
    eventCount: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
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
            label = "守护状态",
            value = if (serviceRunning) "保护中" else "已暂停",
            isGood = serviceRunning
        )
        StatusCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.History,
            label = "今日拦截",
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
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isGood) SuccessGreen else ErrorRed,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(label, fontSize = 11.sp, color = OnDarkTextMuted)
            Text(
                value,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isGood) SuccessGreen else ErrorRed
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