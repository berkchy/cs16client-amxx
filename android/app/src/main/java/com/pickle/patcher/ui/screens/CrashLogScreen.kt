package com.pickle.patcher.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import android.content.Context
import android.content.Intent
import com.pickle.patcher.patcher.PatcherViewModel
import com.pickle.patcher.ui.theme.Mint80
import com.pickle.patcher.ui.theme.OnDarkMuted

@Composable
fun CrashLogScreen(vm: PatcherViewModel) {
    val crash by vm.crashLog.collectAsState()
    val context = LocalContext.current
    val scroll = rememberScrollState()
    val textScroll = rememberScrollState()

    LaunchedEffect(Unit) { vm.refreshCrashLog() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(20.dp),
    ) {
        Text("Crash Log", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "If the patcher died, its stack trace lands in a file. Review it here before sharing.",
            style = MaterialTheme.typography.bodyLarge,
            color = OnDarkMuted,
        )

        Spacer(Modifier.height(18.dp))

        ElevatedCard(shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.BugReport,
                        contentDescription = null,
                        tint = Mint80,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Latest crash", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.weight(1f))
                    if (crash == null) {
                        Text(
                            "None",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = "found",
                            tint = Mint80,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                val c = crash
                if (c == null) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "No crash file found.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "The file is looked for in the app files dir and Downloads/" +
                            "patcher-crash.txt. If the app hasn't crashed yet, there is nothing to show.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnDarkMuted,
                    )
                } else {
                    Text(
                        "${c.fileName} · ${c.modified} · ${c.sizeBytes} bytes",
                        style = MaterialTheme.typography.labelMedium,
                        color = Mint80,
                    )
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Text(
                            c.content,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 160.dp, max = 420.dp)
                                .verticalScroll(textScroll)
                                .padding(14.dp),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { vm.refreshCrashLog() },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Refresh")
            }
            OutlinedButton(
                onClick = { crash?.content?.let { shareCrash(context, it) } },
                enabled = crash != null,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Share")
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "CrashLog.install() hooks the default uncaught-handler on app start; " +
                "only in-process Java/Kotlin crashes are captured (not engine SIGSEGVs).",
            style = MaterialTheme.typography.labelMedium,
            color = OnDarkMuted,
        )
    }
}

private fun shareCrash(context: Context, content: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "patcher-crash.txt\n\n$content")
    }
    context.startActivity(
        Intent.createChooser(intent, "Share crash log"),
    )
}