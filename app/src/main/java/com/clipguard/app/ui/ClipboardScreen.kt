package com.clipguard.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipguard.app.model.ClipboardEvent
import com.clipguard.app.model.ClipboardEvent.ActionTaken
import com.clipguard.app.model.ClipboardEvent.DetectedType
import com.clipguard.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ClipboardScreen(
    events: List<ClipboardEvent>,
    onClearLog: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 顶栏
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, "返回", tint = OnDarkText)
                }
                Text(
                    "剪贴板日志",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnDarkText
                )
            }
            TextButton(onClick = onClearLog) {
                Text("清空", color = ErrorRed)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (events.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.ContentPaste,
                        contentDescription = null,
                        tint = OnDarkTextMuted,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("暂无剪贴板事件", color = OnDarkTextMuted)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(events) { event ->
                    EventCard(event)
                }
            }
        }
    }
}

@Composable
private fun EventCard(event: ClipboardEvent) {
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (event.actionTaken) {
                ActionTaken.CLEARED -> DarkSurfaceVariant.copy(alpha = 0.7f)
                ActionTaken.WARNED -> DarkSurface.copy(alpha = 0.8f)
                else -> DarkSurface
            }
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // 头部：时间 + 操作标签
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    dateFormat.format(Date(event.timestamp)),
                    fontSize = 12.sp,
                    color = OnDarkTextMuted,
                    fontFamily = FontFamily.Monospace
                )
                ActionBadge(event.actionTaken)
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 内容（脱敏后）
            Text(
                event.content,
                fontSize = 13.sp,
                color = OnDarkText,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                fontFamily = FontFamily.Monospace
            )

            // 检测类型标签
            if (event.detectedTypes.isNotEmpty() && !event.detectedTypes.contains(DetectedType.NONE)) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    event.detectedTypes.forEach { type ->
                        TypeChip(type)
                    }
                }
            }

            // 来源应用
            event.sourcePackage?.let { pkg ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "来源: $pkg",
                    fontSize = 11.sp,
                    color = OnDarkTextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ActionBadge(action: ActionTaken) {
    val (label, color) = when (action) {
        ActionTaken.CLEARED -> "已清除" to ErrorRed
        ActionTaken.WARNED -> "已告警" to WarningOrange
        ActionTaken.ALLOWED -> "已放行" to SuccessGreen
    }

    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}

@Composable
private fun TypeChip(type: DetectedType) {
    val (label, color) = when (type) {
        DetectedType.ID_CARD -> "身份证" to WarningOrange
        DetectedType.BANK_CARD -> "银行卡" to ErrorRed
        DetectedType.PHONE_NUMBER -> "手机号" to InfoBlue
        DetectedType.PASSWORD -> "密码" to ErrorRed
        DetectedType.EMAIL -> "邮箱" to Primary
        DetectedType.TOKEN -> "API密钥" to ErrorRed
        DetectedType.IP_ADDRESS -> "IP" to PrimaryVariant
        DetectedType.NONE -> "普通" to SuccessGreen
    }

    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
            fontSize = 10.sp,
            color = color
        )
    }
}