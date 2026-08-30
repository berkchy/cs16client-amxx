package com.pickle.patcher.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(20.dp),
    ) {
        Text("Bundle Yönetimi", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "GitHub Actions her sürümü derler: çekirdek, 11 modül ve pluginler. Tek bundle'a paketler.",
            style = MaterialTheme.typography.bodyLarge,
            color = OnDarkMuted,
        )

        Spacer(Modifier.height(18.dp))

        SectionTitle("En son sürüm")
        Spacer(Modifier.height(10.dp))
        ElevatedCard(shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(18.dp)) {
                note?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CloudDownload, tint = Mint80)
                        Spacer(Modifier.width(8.dp))
                        Text("$it", style = MaterialTheme.typography.titleMedium, maxLines = 2)
                    }
                    Spacer(Modifier.height(6.dp))
                }
                when (val b = bundle) {
                    is BundleState.Downloading -> Text(
                        "İndiriliyor…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnDarkMuted,
                    )
                    is BundleState.DownloadError -> Text(
                        b.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    is BundleState.Ready -> Text(
                        "Hazır: ${b.bundleName} · versiyon ${b.version}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Mint80,
                    )
                    is BundleState.None -> Text(
                        "Henüz indirilmedi.",
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
                        Text("İndir / Güncelle")
                    }
                    OutlinedButton(
                        onClick = { vm.useEmbeddedBundle() },
                        shape = RoundedCornerShape(14.dp),
                    ) { Text("Gömülü") }
                }
                if (vm.hasCachedBundle) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { vm.useCachedBundle() },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Önbellekteki bundle'ı yükle") }
                } else {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Önbellekte bundle yok — ilk indirme sırasında oluşur.",
                        style = MaterialTheme.typography.labelMedium,
                        color = OnDarkMuted,
                    )
                }
            }
        }

        Spacer(Modifier.height(22.dp))

        SectionTitle("Oyun kutusu")
        Spacer(Modifier.height(10.dp))
        GameFolderCard()
    }
}

@Composable
private fun GameFolderCard() {
    val context = LocalContext.current
    val gamePaths = listOf(
        "Çekirdek dosyalar" to listOf(
            File("/storage/emulated/0/xash/cstrike/addons/amxmodx/bin/"),
            File("/storage/emulated/0/xash/cstrike/addons/amxmodx/modules/"),
        ),
        "Plugin klasörü" to listOf(
            File("/storage/emulated/0/xash/cstrike/addons/amxmodx/plugins/"),
        ),
        "Oyun verisi" to listOf(
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
                                if (d.exists()) "${d.path} ✓" else "${d.path} (yok)",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (d.exists()) Mint80 else OnDarkMuted,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Yamalanan APK kurulduktan sonra pluginleri/konfigleri bu klasörlere kopyalayabilirsin.",
                style = MaterialTheme.typography.bodyMedium,
                color = OnDarkMuted,
            )
        }
    }
}