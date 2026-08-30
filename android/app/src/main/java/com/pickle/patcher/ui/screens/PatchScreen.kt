package com.pickle.patcher.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.InstallDesktop
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pickle.patcher.lib.ApkPatcher
import com.pickle.patcher.patcher.BundleState
import com.pickle.patcher.patcher.PatchUiState
import com.pickle.patcher.patcher.PatcherViewModel
import com.pickle.patcher.ui.theme.Mint80
import com.pickle.patcher.ui.theme.OnDark
import com.pickle.patcher.ui.theme.OnDarkMuted
import java.text.DecimalFormat

private val mbFmt = DecimalFormat("0.0")

private fun Long.mb(): String = "${mbFmt.format(this / 1048576.0)} MB"

@Composable
fun PatchScreen(vm: PatcherViewModel) {
    val screenScroll = rememberScrollState()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(screenScroll)
            .padding(20.dp),
    ) {
        Text("Patch Wizard", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "1) Pick an APK → 2) Load a bundle → 3) Patch & sign. Then install!",
            style = MaterialTheme.typography.bodyLarge,
            color = OnDarkMuted,
        )

        Spacer(Modifier.height(18.dp))

        SectionTitle("1 · Source APK")
        Spacer(Modifier.height(10.dp))
        SourceApkCard(vm)

        Spacer(Modifier.height(18.dp))

        SectionTitle("2 · Mod Bundle")
        Spacer(Modifier.height(10.dp))
        BundleCard(vm)

        Spacer(Modifier.height(18.dp))

        SectionTitle("3 · Patch")
        Spacer(Modifier.height(10.dp))
        PatchRunCard(vm)
    }
}

@Composable
private fun SourceApkCard(vm: PatcherViewModel) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
            vm.pickSource(it)
        }
    }
    val source by vm.source.collectAsState()

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            if (source == null) {
                Text(
                    "No APK selected — choose the stock CS16Client installer APK.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnDarkMuted,
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { picker.launch(arrayOf("application/vnd.android.package-archive")) },
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Filled.FolderOpen, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Select APK")
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Icon(
                            Icons.Filled.InstallDesktop,
                            contentDescription = null,
                            modifier = Modifier.padding(10.dp).size(22.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            source!!.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${source!!.sizeBytes.mb()} · ${source!!.entryCount} girdi",
                            style = MaterialTheme.typography.bodyMedium,
                            color = OnDarkMuted,
                        )
                    }
                    TextButton(onClick = { picker.launch(arrayOf("application/vnd.android.package-archive")) }) {
                        Text("Change")
                    }
                }
            }
        }
    }
}

@Composable
private fun BundleCard(vm: PatcherViewModel) {
    val bundleState by vm.bundle.collectAsState()

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            when (val bs = bundleState) {
                is BundleState.None -> {
                    Text(
                        "A mod bundle is required. Download one or use the embedded build.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnDarkMuted,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { vm.fetchAndDownloadBundle() },
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Download from GitHub")
                        }
                        OutlinedButton(
                            onClick = { vm.useEmbeddedBundle() },
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Text("Use Embedded")
                        }
                    }
                }
                is BundleState.Downloading -> {
                    Column {
                        Text("Downloading bundle…", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        AnimatedProgressBar(bs.percent)
                    }
                }
                is BundleState.DownloadError -> {
                    Text(
                        bs.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row {
                        Button(
                            onClick = { vm.fetchAndDownloadBundle() },
                            shape = RoundedCornerShape(14.dp),
                        ) { Text("Retry") }
                        Spacer(Modifier.width(10.dp))
                        OutlinedButton(
                            onClick = { vm.useEmbeddedBundle() },
                            shape = RoundedCornerShape(14.dp),
                        ) { Text("Embedded") }
                    }
                }
                is BundleState.Ready -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Mint80.copy(alpha = 0.14f),
                        ) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.padding(10.dp).size(22.dp),
                                tint = Mint80,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(bs.bundleName, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${bs.entries} entries · version ${bs.version}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = OnDarkMuted,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PatchRunCard(vm: PatcherViewModel) {
    val state by vm.patch.collectAsState()
    val bundle = vm.bundle.collectAsState().value
    val context = LocalContext.current
    val canPatch = vm.source.collectAsState().value != null && bundle is BundleState.Ready

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            when (val s = state) {
                is PatchUiState.Idle -> {
                    Button(
                        onClick = { vm.startPatch() },
                        enabled = canPatch,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(Icons.Filled.RocketLaunch, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Patch & Sign APK", style = MaterialTheme.typography.titleMedium)
                    }
                    if (!canPatch) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Select an APK and load a bundle to continue.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                is PatchUiState.Running -> {
                    PatchSteps(s.step, s.progress)
                }

                is PatchUiState.Done -> {
                    PatchResult(s.report, onInstall = { context.startActivity(vm.installIntent()) })
                }

                is PatchUiState.Failed -> {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("Patch failed", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        s.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnDarkMuted,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { vm.reset() },
                        shape = RoundedCornerShape(14.dp),
                    ) { Text("Try Again") }
                }
            }
        }
    }
}

@Composable
private fun PatchSteps(step: ApkPatcher.Step, progress: Float) {
    val steps = listOf(
        ApkPatcher.Step.ANALYZE to "Analyze",
        ApkPatcher.Step.INJECT to "Inject",
        ApkPatcher.Step.ALIGN to "Align",
        ApkPatcher.Step.SIGN to "Sign",
        ApkPatcher.Step.VERIFY to "Verify",
    )
    val currentIndex = steps.indexOfFirst { it.first == step }

    Column {
        Spacer(Modifier.height(4.dp))
        steps.forEachIndexed { i, (s, label) ->
            val st = when {
                i < currentIndex -> StepState.DONE
                i == currentIndex -> StepState.ACTIVE
                else -> StepState.PENDING
            }
            StepListItem(
                title = label,
                detail = if (st == StepState.ACTIVE) "Working… (${(progress * 100).toInt()}%)" else "",
                state = st,
            )
            if (i != steps.lastIndex) Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(12.dp))
        AnimatedProgressBar(progress)
    }
}

@Composable
private fun PatchResult(report: ApkPatcher.PatchReport, onInstall: () -> Unit) {
    AnimatedContent(
        targetState = true,
        transitionSpec = { fadeIn(tween(300)).togetherWith(fadeOut(tween(200))) },
        label = "resultIn",
    ) {
        Column {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Mint80.copy(alpha = 0.12f),
            ) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Text(
                        "Patch Complete",
                        style = MaterialTheme.typography.titleLarge,
                        color = Mint80,
                    )
                    Spacer(Modifier.height(4.dp))
                    val v = report.verification
                    Text(
                        "Output: ${report.signedSizeBytes.mb()} · source ${report.sourceName}\n" +
                                "Added entries: ${report.addedEntries.size} · kept: ${report.keptCount} · aligned: ${report.alignedStored}\n" +
                                "resources.arsc: ${if (report.arscStored) "stored" else "compressed"} / ${if (report.arscAligned) "aligned" else "NOT ALIGNED"}\n" +
                                "Signature: ${if (v != null && v.verified) "verified (v1=${v.usedV1} v2=${v.usedV2})" else "NOT VERIFIED"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnDark,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onInstall,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Filled.InstallDesktop, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Install (system installer)")
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "This replaces the existing AMXX install — the APK is signed with the same debug key.",
                style = MaterialTheme.typography.bodyMedium,
                color = OnDarkMuted,
            )
        }
    }
}