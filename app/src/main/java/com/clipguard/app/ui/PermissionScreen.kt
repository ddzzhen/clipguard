package com.clipguard.app.ui

import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipguard.app.ClipGuardApp
import com.clipguard.app.model.AppInfo
import com.clipguard.app.model.AppPermission
import com.clipguard.app.shizuku.PermissionController
import com.clipguard.app.shizuku.ShizukuBridge
import com.clipguard.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PermissionScreen(
    shizukuAvailable: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var appList by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var selectedApp by remember { mutableStateOf<AppPermission?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var resultMessage by remember { mutableStateOf<String?>(null) }

    // 加载应用列表（不需要 Shizuku 也能加载）
    LaunchedEffect(Unit) {
        isLoading = true
        appList = withContext(Dispatchers.IO) {
            loadInstalledApps()
        }
        isLoading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 顶栏
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "返回", tint = OnDarkText)
            }
            Text(
                "权限管理",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = OnDarkText
            )
        }

        if (!shizukuAvailable) {
            // Shizuku 未连接提示（不阻断浏览应用列表）
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = ErrorRed.copy(alpha = 0.12f)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, null, tint = ErrorRed, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Shizuku 未连接 — 仅可浏览，无法修改权限",
                        fontSize = 13.sp,
                        color = ErrorRed
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // 选中的应用详情
        if (selectedApp != null) {
            AppPermissionDetail(
                appPermission = selectedApp!!,
                onClose = { selectedApp = null },
                onGrant = { perm ->
                    scope.launch(Dispatchers.IO) {
                        val ok = PermissionController.grantPermission(selectedApp!!.packageName, perm)
                        resultMessage = if (ok) "已授予 $perm" else "授予失败"
                        // 刷新状态
                        selectedApp = PermissionController.getAppPermissions(selectedApp!!.packageName)
                    }
                },
                onRevoke = { perm ->
                    scope.launch(Dispatchers.IO) {
                        val ok = PermissionController.revokePermission(selectedApp!!.packageName, perm)
                        resultMessage = if (ok) "已撤销 $perm" else "撤销失败"
                        selectedApp = PermissionController.getAppPermissions(selectedApp!!.packageName)
                    }
                },
                onRevokeAll = {
                    scope.launch(Dispatchers.IO) {
                        val count = PermissionController.revokeAllDangerous(selectedApp!!.packageName)
                        resultMessage = "已撤销 $count 项权限"
                        selectedApp = PermissionController.getAppPermissions(selectedApp!!.packageName)
                    }
                }
            )
        } else {
            // 搜索栏
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("搜索应用...", color = OnDarkTextMuted) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = OnDarkTextMuted) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = OnDarkText,
                    unfocusedTextColor = OnDarkText,
                    focusedBorderColor = Primary,
                    unfocusedBorderColor = DarkSurface,
                    cursorColor = Primary
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 提示消息
            resultMessage?.let {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = SuccessGreen.copy(alpha = 0.15f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        it,
                        modifier = Modifier.padding(12.dp),
                        color = SuccessGreen
                    )
                }
                LaunchedEffect(it) {
                    kotlinx.coroutines.delay(2000)
                    resultMessage = null
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Primary)
                }
            } else {
                val filtered = if (searchQuery.isBlank()) appList
                else appList.filter { it.label.contains(searchQuery, ignoreCase = true) }

                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(filtered) { app ->
                        AppListItem(
                            appInfo = app,
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    selectedApp = PermissionController.getAppPermissions(app.packageName)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppListItem(appInfo: AppInfo, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 简易应用图标占位
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Primary.copy(alpha = 0.2f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        appInfo.label.take(1),
                        fontWeight = FontWeight.Bold,
                        color = Primary
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(appInfo.label, fontWeight = FontWeight.Medium, color = OnDarkText)
                Text(
                    appInfo.packageName,
                    fontSize = 12.sp,
                    color = OnDarkTextMuted
                )
            }
            if (appInfo.isSystemApp) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = WarningOrange.copy(alpha = 0.15f)
                ) {
                    Text(
                        "系统",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontSize = 10.sp,
                        color = WarningOrange
                    )
                }
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = OnDarkTextMuted
            )
        }
    }
}

@Composable
private fun AppPermissionDetail(
    appPermission: AppPermission,
    onClose: () -> Unit,
    onGrant: (String) -> Unit,
    onRevoke: (String) -> Unit,
    onRevokeAll: () -> Unit
) {
    val grantedCount = appPermission.permissions.count { it.granted }
    val totalCount = appPermission.permissions.size

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    appPermission.appLabel,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = OnDarkText
                )
                Text(
                    "已授权 $grantedCount / $totalCount 项",
                    fontSize = 13.sp,
                    color = if (grantedCount > totalCount / 2) WarningOrange else SuccessGreen
                )
            }
            TextButton(onClick = onRevokeAll) {
                Text("一键撤销所有", color = ErrorRed)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(appPermission.permissions) { permState ->
                PermissionRow(
                    permissionState = permState,
                    onToggle = {
                        if (permState.granted) onRevoke(permState.name)
                        else onGrant(permState.name)
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant)
        ) {
            Text("返回应用列表", color = OnDarkText)
        }
    }
}

@Composable
private fun PermissionRow(
    permissionState: AppPermission.PermissionState,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (permissionState.granted) Icons.Default.CheckCircle
                    else Icons.Default.RemoveCircle,
                    contentDescription = null,
                    tint = if (permissionState.granted) SuccessGreen else ErrorRed,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(permissionState.label, color = OnDarkText, fontSize = 14.sp)
                    Text(
                        permissionState.name.split(".").last(),
                        fontSize = 11.sp,
                        color = OnDarkTextMuted
                    )
                }
            }
            Switch(
                checked = permissionState.granted,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = DarkBackground,
                    checkedTrackColor = SuccessGreen
                ),
                modifier = Modifier.height(24.dp)
            )
        }
    }
}

/**
 * 获取已安装应用列表
 */
private fun loadInstalledApps(): List<AppInfo> {
    val pm = ClipGuardApp.instance.packageManager
    return try {
        pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .sortedBy { pm.getApplicationLabel(it).toString().lowercase() }
            .map { appInfo ->
                AppInfo(
                    packageName = appInfo.packageName,
                    label = pm.getApplicationLabel(appInfo).toString(),
                    isSystemApp = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                )
            }
    } catch (e: Exception) {
        emptyList()
    }
}