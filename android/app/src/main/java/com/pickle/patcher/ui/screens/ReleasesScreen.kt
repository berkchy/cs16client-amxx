package com.pickle.patcher.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pickle.patcher.patcher.AddonsState
import com.pickle.patcher.patcher.BundleState
import com.pickle.patcher.patcher.PatcherViewModel
import com.pickle.patcher.ui.theme.Mint80
import com.pickle.patcher.ui.theme.OnDarkMuted
import java.io.File

@Composable
fun ReleasesScreen(vm: PatcherViewModel) {
    val scroll = rememberScrollState()
    val note by vm.releaseNote.collectAsState()
    val bundle by vm.bundle.collectAsState()
    val addons by vm.addons.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(20.dp),
    ) {
        Text("Downloads", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Install the latest addons (plugins, modules, configs) into your cstrike " +
                "folder, or manage the mod bundle used while patching.",
            style = MaterialTheme.typography.bodyLarge,
            color = OnDarkMuted,
        )

        Spacer(Modifier.height(18.dp))

        SectionTitle("Addons")
        Spacer(Modifier.height(10.dp))
        ElevatedCard(shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(18.dp)) {
                Text(
                    "Downloads only the addons package — smaller and quicker than the " +
                        "full bundle. Extracted into /storage/emulated/0/xash/cstrike.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnDarkMuted,
                )
                Spacer(Modifier.height(12.dp))
                when (val a = addons) {
                    is AddonsState.Downloading -> {
                        LinearProgressIndicator(
                            progress = { a.percent },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            a.step,
                            style = MaterialTheme.typography.bodyMedium,
                            color = OnDarkMuted,
                        )
                    }
                    is AddonsState.Done -> Text(
                        a.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Mint80,
                    )
                    is AddonsState.Error -> Text(
                        a.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    is AddonsState.None -> Text(
                        "Not downloaded yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnDarkMuted,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { vm.fetchAndInstallAddons() },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.CloudDownload, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Download & Install Addons")
                }
            }
        }

        Spacer(Modifier.height(22.dp))

        SectionTitle("Latest release (mod bundle)")
        Spacer(Modifier.height(10.dp))
        ElevatedCard(shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(18.dp)) {
                note?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CloudDownload, contentDescription = null, tint = Mint80)
                        Spacer(Modifier.width(8.dp))
                        Text("$it", style = MaterialTheme.typography.titleMedium, maxLines = 2)
                    }
                    Spacer(Modifier.height(6.dp))
                }
                when (val b = bundle) {
                    is BundleState.Downloading -> Text(
                        "Downloading…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnDarkMuted,
                    )
                    is BundleState.DownloadError -> Text(
                        b.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    is BundleState.Ready -> Text(
                        "Ready: ${b.bundleName} · version ${b.version}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Mint80,
                    )
                    is BundleState.None -> Text(
                        "Not downloaded yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnDarkMuted,
                    )
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { vm.fetchAndDownloadBundle() },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Download / Update")
                    }
                    OutlinedButton(
                        onClick = { vm.useEmbeddedBundle() },
                        shape = RoundedCornerShape(14.dp),
                    ) { Text("Embedded") }
                }
                if (vm.hasCachedBundle) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { vm.useCachedBundle() },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Load cached bundle") }
                } else {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "No cached bundle — one is created on first download.",
                        style = MaterialTheme.typography.labelMedium,
                        color = OnDarkMuted,
                    )
                }
            }
        }

        Spacer(Modifier.height(22.dp))

        SectionTitle("Game folders")
        Spacer(Modifier.height(10.dp))
        GameFolderCard()
    }
}

@Composable
private fun GameFolderCard() {
    val context = LocalContext.current
    val gamePaths = listOf(
        "Core files" to listOf(
            File("/storage/emulated/0/xash/cstrike/addons/amxmodx/bin/"),
            File("/storage/emulated/0/xash/cstrike/addons/amxmodx/modules/"),
        ),
        "Plugins folder" to listOf(
            File("/storage/emulated/0/xash/cstrike/addons/amxmodx/plugins/"),
        ),
        "Game data" to listOf(
            File("/storage/emulated/0/xash/cstrike/"),
        ),
    )

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            gamePaths.forEach { (label, dirs) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.FolderSpecial,
                        contentDescription = null,
                        tint = Mint80,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(label, style = MaterialTheme.typography.titleSmall)
                        dirs.forEach { d ->
                            Text(
                                if (d.exists()) "${d.path} ✓" else "${d.path} (missing)",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (d.exists()) Mint80 else OnDarkMuted,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "After the patched APK is installed, copy plugins and configs into these folders.",
                style = MaterialTheme.typography.bodyMedium,
                color = OnDarkMuted,
            )
        }
    }
}