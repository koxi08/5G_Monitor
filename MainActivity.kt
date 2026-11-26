package com.kosigan.fiveguard

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifier.createChannels(this)
        AppState.loadEvents(this)
        setContent {
            MaterialTheme {
                Surface(Modifier.systemBarsPadding()) { App() }
            }
        }
    }
}

private fun hasPhonePermission(c: Context) =
    ContextCompat.checkSelfPermission(c, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED

private fun startMonitor(c: Context) =
    ContextCompat.startForegroundService(c, Intent(c, MonitorService::class.java))

private fun stopMonitor(c: Context) {
    c.startService(Intent(c, MonitorService::class.java).setAction(MonitorService.ACTION_STOP))
}

@Composable
private fun App() {
    val ctx = LocalContext.current
    val gen by AppState.gen.collectAsState()
    val running by AppState.running.collectAsState()
    val blocked by AppState.blocked.collectAsState()
    val events by AppState.events.collectAsState()

    var autoCut by remember { mutableStateOf(Prefs.autoCut(ctx)) }
    var autoRestore by remember { mutableStateOf(Prefs.autoRestore(ctx)) }
    var delay by remember { mutableStateOf(Prefs.delaySec(ctx).toFloat()) }

    val vpnLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode != Activity.RESULT_OK) {
            autoCut = false
            Prefs.setAutoCut(ctx, false)
        }
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (hasPhonePermission(ctx)) startMonitor(ctx)
    }

    fun requestVpnIfNeeded() {
        VpnService.prepare(ctx)?.let { vpnLauncher.launch(it) }
    }

    val genColor = when (gen) {
        Gen.G5 -> Color(0xFF2E7D32)
        Gen.G4 -> Color(0xFFEF6C00)
        Gen.G3, Gen.G2 -> Color(0xFFC62828)
        Gen.NONE -> Color.Gray
    }

    LazyColumn(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Current network", style = MaterialTheme.typography.labelLarge)
                    Text(gen.label, fontSize = 72.sp, color = genColor)
                    Text(
                        when {
                            blocked -> "Internet is OFF (cut by 5G Guard)"
                            running -> "Monitoring…"
                            else -> "Not monitoring"
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
        item {
            Button(
                onClick = {
                    if (running) {
                        stopMonitor(ctx)
                    } else {
                        val perms = buildList {
                            add(Manifest.permission.READ_PHONE_STATE)
                            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                        }.toTypedArray()
                        if (autoCut) requestVpnIfNeeded()
                        permLauncher.launch(perms)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (running) "Stop monitoring" else "Start monitoring") }
        }
        if (blocked) {
            item {
                OutlinedButton(
                    onClick = {
                        ctx.startService(
                            Intent(ctx, BlockVpnService::class.java).setAction(BlockVpnService.ACTION_STOP)
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Turn internet back on now") }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingRow("Turn off internet when 5G drops to 4G", autoCut) {
                        autoCut = it
                        Prefs.setAutoCut(ctx, it)
                        if (it) requestVpnIfNeeded()
                    }
                    SettingRow("Turn internet back on when 5G returns", autoRestore) {
                        autoRestore = it
                        Prefs.setAutoRestore(ctx, it)
                    }
                    Text("Wait ${delay.toInt()} s before reacting (avoids false alarms from brief 5G flicker)")
                    Slider(
                        value = delay,
                        onValueChange = { delay = it },
                        onValueChangeFinished = { Prefs.setDelaySec(ctx, delay.toInt()) },
                        valueRange = 0f..30f,
                        steps = 29
                    )
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    Notifier.alert(ctx, "Test: 5G → 4G", "This is how the alert looks.", offerRestore = false)
                }) { Text("Test alert") }
                OutlinedButton(onClick = {
                    if (VpnService.prepare(ctx) == null) {
                        ctx.startService(Intent(ctx, BlockVpnService::class.java))
                    } else requestVpnIfNeeded()
                }) { Text("Test internet cut") }
            }
        }
        item { Text("Event log", style = MaterialTheme.typography.titleMedium) }
        items(events) { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun SettingRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
