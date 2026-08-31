package com.pickle.patcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pickle.patcher.patcher.CompileState
import com.pickle.patcher.patcher.PatcherViewModel
import com.pickle.patcher.patcher.SmaSource
import com.pickle.patcher.ui.theme.OnDarkMuted
import com.pickle.patcher.ui.theme.Mint80
import java.io.File

@Composable
fun CompilerScreen(vm: PatcherViewModel) {
    val scroll = rememberScrollState()
    val scripts by vm.scripts.collectAsState()
    val compile by vm.compile.collectAsState()
    var selected by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { vm.refreshScripts() }

    val selectedSource = scripts.firstOrNull { it.path == selected }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(20.dp),
    ) {
        Text("Compiler", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Compiles local AMXX plugins against the include folder next to each script.",
            style = MaterialTheme.typography.bodyLarge,
            color = OnDarkMuted,
        )

        Spacer(Modifier.height(18.dp))

        SectionTitle("Plugins (.sma)")
        Spacer(Modifier.height(10.dp))
        ElevatedCard(shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(
                        "Scripts folder: ${
                            File("/storage/emulated/0/xash/cstrike/addons/amxmodx/scripting").absolutePath
                        }",
                        style = MaterialTheme.typography.labelMedium,
                        color = OnDarkMuted,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { vm.refreshScripts() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                }
                Spacer(Modifier.height(6.dp))
                if (scripts.isEmpty()) {
                    Text(
                        "No .sma files found yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnDarkMuted,
                    )
                } else {
                    scripts.forEach { s ->
                        val isSelected = s.path == selected
                        Surface(
                            onClick = { selected = if (isSelected) null else s.path },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            } else {
                                MaterialTheme.colorScheme.surfaceContainer
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        ) {
                            Row(
                                Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(s.name, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        if (s.hasInclude) "include/ available" else "no include/ folder",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (s.hasInclude) Mint80 else OnDarkMuted,
                                    )
                                }
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { selected = if (isSelected) null else s.path },
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        selectedSource?.let { src ->
            ElevatedCard(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(18.dp)) {
                    Text(
                        "Compile ${src.name}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    val includeDir = File(File(src.path).parentFile, "include")
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Using include: ${includeDir.absolutePath}",
                        style = MaterialTheme.typography.labelMedium,
                        color = OnDarkMuted,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { vm.compile(src) },
                        shape = RoundedCornerShape(14.dp),
                        enabled = compile !is CompileState.Compiling,
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Compile")
                    }
                }
            }
        }

        if (selectedSource == null) {
            Spacer(Modifier.height(10.dp))
            Text(
                "Select a plugin above to compile it.",
                style = MaterialTheme.typography.bodyMedium,
                color = OnDarkMuted,
            )
        }

        Spacer(Modifier.height(18.dp))

        when (val c = compile) {
            is CompileState.Idle -> Unit
            is CompileState.Compiling -> {
                SectionTitle("Compiling ${c.source}…")
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            is CompileState.Done -> {
                SectionTitle("Output")
                Spacer(Modifier.height(8.dp))
                LogBox(c.log)
            }
            is CompileState.Failed -> {
                SectionTitle("Output")
                Spacer(Modifier.height(8.dp))
                LogBox(c.message, error = true)
            }
        }
    }
}

@Composable
private fun LogBox(text: String, error: Boolean = false) {
    val mono = androidx.compose.ui.text.font.FontFamily.Monospace
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = mono, fontSize = 11.sp),
            color = if (error) MaterialTheme.colorScheme.error else OnDarkMuted,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .padding(12.dp),
        )
    }
}
