package com.example.bandsdetector

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun BandsScreen(
    hasLocationPermission: Boolean,
    onRequestPermission: () -> Unit
) {
    val context = LocalContext.current
    var snapshot by remember {
        mutableStateOf(NetworkSnapshot("未知运营商", "", null, emptyList(), false))
    }

    fun refresh() {
        if (hasLocationPermission) snapshot = readNetworkSnapshot(context)
    }

    // 定时刷新（每 2 秒）
    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            while (true) {
                refresh()
                delay(2000)
            }
        }
    }

    // 回到前台立即刷新一次
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, hasLocationPermission) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            if (!hasLocationPermission) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "需要位置权限才能读取基站频段信息（Android 系统要求）",
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = onRequestPermission) {
                                Text("授予权限")
                            }
                        }
                    }
                }
            } else if (!snapshot.hasData) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "未检测到移动网络注册小区",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "请：\n1. 打开手机流量（移动数据）\n2. 关闭 WiFi\n3. 确认 SIM 卡已插入且有信号",
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            }

            if (hasLocationPermission) {
                // 当前网络类型卡片
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "当前网络",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (snapshot.hasData)
                                    "${snapshot.carrier} ${snapshot.tech}"
                                else snapshot.carrier,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            snapshot.primary?.let { p ->
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = String.format(
                                        Locale.US,
                                        "频点 %d  ·  %.1f MHz  ·  RSRP %d dBm",
                                        p.arfcn, p.freqMhz, p.rsrp
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // 当前连接频段（大字号高亮置顶）
                snapshot.primary?.let { p ->
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "当前连接频段",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = p.band,
                                    fontSize = 56.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                // 载波聚合/双连接：列出全部聚合频段
                if (snapshot.aggregated.size > 1) {
                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            if (snapshot.primary?.rat == "NR")
                                "载波聚合（${snapshot.aggregated.size}CC）"
                            else
                                "双连接（NSA / EN-DC）",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    items(snapshot.aggregated) { cell ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (cell.isPrimary)
                                    MaterialTheme.colorScheme.secondaryContainer
                                else
                                    MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = cell.band,
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (cell.isPrimary)
                                                MaterialTheme.colorScheme.onSecondaryContainer
                                            else
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                                        Surface(
                                            shape = MaterialTheme.shapes.small,
                                            color = if (cell.isPrimary)
                                                MaterialTheme.colorScheme.primary
                                            else
                                                MaterialTheme.colorScheme.outline
                                        ) {
                                            Text(
                                                text = if (cell.isPrimary) "主载波" else "辅载波",
                                                fontSize = 11.sp,
                                                color = if (cell.isPrimary)
                                                    MaterialTheme.colorScheme.onPrimary
                                                else
                                                    MaterialTheme.colorScheme.surface,
                                                modifier = Modifier.padding(
                                                    horizontal = 6.dp, vertical = 2.dp
                                                )
                                            )
                                        }
                                    }
                                    Text(
                                        text = String.format(
                                            Locale.US,
                                            "%s · 频点 %d · %.1f MHz · RSRP %d dBm",
                                            if (cell.rat == "NR") "5G NR" else "4G LTE",
                                            cell.arfcn, cell.freqMhz, cell.rsrp
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = (if (cell.isPrimary)
                                            MaterialTheme.colorScheme.onSecondaryContainer
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                                ).copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                }

                // 运营商全部频段列表（排除当前已连接的）
                val connectedBands = snapshot.aggregated.map { it.band }.toSet()
                val allBands = CarrierBands.bandsFor(snapshot.carrier)
                    .filter { it.band !in connectedBands }
                if (allBands.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "${snapshot.carrier} 在用的全部频段",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    items(allBands) { info ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = info.band,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = (if (info.is5G) "5G · " else "4G · ") + info.freqDesc,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            )
                        }
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                        )
                    }
                }
            }

            // 底部署名
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "By dy嗷.呜",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}
