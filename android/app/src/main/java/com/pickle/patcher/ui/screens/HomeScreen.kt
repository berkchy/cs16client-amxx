package com.pickle.patcher.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.pickle.patcher.patcher.PatcherViewModel
import com.pickle.patcher.ui.theme.Mint80
import com.pickle.patcher.ui.theme.Violet80

@Composable
fun HomeScreen(vm: PatcherViewModel, onGoToPatch: () -> Unit) {
    val scroll = rememberScrollState()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(20.dp),
    ) {
        Spacer(Modifier.height(6.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .align(Alignment.CenterHorizontally),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(124.dp)
                    .shadow(28.dp, RoundedCornerShape(34.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = RoundedCornerShape(34.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Box(Modifier.size(124.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.Bolt,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = Mint80,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Text(
            "AMX Mod X is built on GitHub, not on this device. Pick an APK, patch it, install it.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 8.dp),
        )

        Spacer(Modifier.height(26.dp))

        SectionTitle("How it works")
        Spacer(Modifier.height(10.dp))

        StepListItem(
            "Download the bundle",
            "GitHub Actions compiles the core, modules, and plugins into one package.",
            StepState.DONE,
        )
        Spacer(Modifier.height(10.dp))
        StepListItem(
            "Select the original APK",
            "Pick the stock CS16Client installer on this device, e.g. CS16Client-AMXX13-unsigned.apk.",
            StepState.ACTIVE,
        )
        Spacer(Modifier.height(10.dp))
        StepListItem(
            "Patch, sign & install",
            "The core and modules are injected, files aligned, and the APK re-signed.",
            StepState.PENDING,
        )

        Spacer(Modifier.height(26.dp))

        AnimatedVisibility(
            visible = true,
            enter = fadeIn(tween(600, delayMillis = 120)),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatChip(
                    "Build",
                    value = "GitHub",
                    accent = Mint80,
                    modifier = Modifier.weight(1f),
                )
                StatChip(
                    "Modules",
                    value = "11",
                    accent = Violet80,
                    modifier = Modifier.weight(1f),
                )
                StatChip(
                    "Signing",
                    value = "debug",
                    accent = Mint80,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(Modifier.height(26.dp))

        Button(
            onClick = onGoToPatch,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Icon(Icons.Filled.RocketLaunch, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Go to Patch", style = MaterialTheme.typography.titleMedium)
        }

        Spacer(Modifier.height(20.dp))
    }
}