package com.guardian.sentinel

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { Screen() } }
    }
}

private fun colorFor(level: RiskLevel) = when (level) {
    RiskLevel.CRITICAL -> Color(0xFFE53935)
    RiskLevel.HIGH -> Color(0xFFFB8C00)
    RiskLevel.MEDIUM -> Color(0xFFFDD835)
    RiskLevel.LOW -> Color(0xFF43A047)
}

private fun fmtBytes(b: Long): String {
    if (b < 1024) return "$b B"
    val kb = b / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    return "%.2f GB".format(mb / 1024.0)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Screen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var results by remember { mutableStateOf<List<AppRisk>>(emptyList()) }
    var scanning by remember { mutableStateOf(false) }
    var sortMode by rememberSaveable { mutableStateOf(SortMode.RISK) }
    val displayed = remember(results, sortMode) { results.sortedFor(sortMode) }

    fun runScan() {
        scanning = true
        scope.launch {
            val r = withContext(Dispatchers.Default) { RiskEngine.scan(ctx) }
            results = r
            scanning = false
        }
    }

    LaunchedEffect(Unit) { runScan() }

    val critical = results.count { it.level == RiskLevel.CRITICAL }
    val high = results.count { it.level == RiskLevel.HIGH }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sentinel Guard", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { runScan() }) {
                        Icon(
                            painter = painterResource(id = R.drawable.refresh_24px),
                            contentDescription = "Pindai ulang"
                        )
                    }
                }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {

            UsageAccessBanner(
                onAccessGranted = { runScan() },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatBox("KRITIS", critical, Color(0xFFE53935))
                StatBox("TINGGI", high, Color(0xFFFB8C00))
                StatBox("TERPINDAI", results.size, Color(0xFF42A5F5))
            }

            SortToggle(
                mode = sortMode,
                onModeChange = { sortMode = it },
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
            )

            if (scanning) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                Text(
                    "Memindai aplikasi & aktivitas jaringan…",
                    Modifier.padding(16.dp), fontSize = 13.sp
                )
            }

            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                items(displayed, key = { it.packageName }) { app ->
                    AppCard(app = app, showUpload = sortMode == SortMode.UPLOAD)
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
fun StatBox(label: String, value: Int, c: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$value", color = c, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(label, fontSize = 11.sp, color = Color.Gray)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppCard(app: AppRisk, showUpload: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    val c = colorFor(app.level)

    Card(
        Modifier.fillMaxWidth().padding(vertical = 5.dp)
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp)) {
                    Canvas(Modifier.fillMaxSize()) { drawCircle(c) }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(app.label, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text(app.packageName, fontSize = 11.sp, color = Color.Gray)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${app.score}", color = c,
                        fontWeight = FontWeight.Bold, fontSize = 20.sp
                    )
                    if (showUpload) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(id = R.drawable.upload_24px),
                                contentDescription = "Upload",
                                tint = Color(0xFFEF5350),
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                " ${fmtBytes(app.txBytes)}",
                                fontSize = 11.sp, color = Color.LightGray
                            )
                        }
                    }
                }
            }

            if (app.flags.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    app.flags.forEach { f ->
                        Surface(
                            color = c.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                f, color = c, fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(
                                    horizontal = 7.dp, vertical = 3.dp
                                )
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(expanded) {
                Column(Modifier.padding(top = 12.dp)) {
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Izin sensitif / sinyal:", fontWeight = FontWeight.Medium,
                        fontSize = 13.sp
                    )
                    app.reasons.forEach {
                        Text("• $it", fontSize = 12.sp, color = Color.LightGray)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Data ${NetworkUsage.WINDOW_DAYS} hari terakhir (Wi-Fi + seluler):",
                        fontSize = 12.sp, color = Color.Gray
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = R.drawable.download_24px),
                            contentDescription = "Download",
                            tint = Color(0xFF42A5F5),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(" ${fmtBytes(app.rxBytes)}   ", fontSize = 12.sp)

                        Icon(
                            painter = painterResource(id = R.drawable.upload_24px),
                            contentDescription = "Upload",
                            tint = Color(0xFFEF5350),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(" ${fmtBytes(app.txBytes)}", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}