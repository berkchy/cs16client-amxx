package com.pickle.patcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Architecture
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.outlined.Architecture
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pickle.patcher.patcher.PatcherViewModel
import com.pickle.patcher.ui.screens.CrashLogScreen
import com.pickle.patcher.ui.screens.CompilerScreen
import com.pickle.patcher.ui.screens.HomeScreen
import com.pickle.patcher.ui.screens.PatchScreen
import com.pickle.patcher.ui.screens.ReleasesScreen
import com.pickle.patcher.ui.theme.AmxxPatcherTheme
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        CrashLog.install(applicationContext)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AmxxPatcherTheme {
                val vm: PatcherViewModel = viewModel()
                PatcherApp(vm)
            }
        }
    }
}

private enum class Dest(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val icon: ImageVector,
) {
    Home("home", "Home", Icons.Filled.Home, Icons.Outlined.Home),
    Patch("patch", "Patch", Icons.Filled.RocketLaunch, Icons.Outlined.Architecture),
    Crash("crash", "Crash Log", Icons.Filled.BugReport, Icons.Outlined.BugReport),
    Compiler("compiler", "Compiler", Icons.Filled.Code, Icons.Outlined.Code),
    Releases("releases", "Downloads", Icons.Filled.Build, Icons.Outlined.Build),
}

@Composable
fun PatcherApp(vm: PatcherViewModel) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val currentRoute = entry?.destination?.route

    Scaffold(
        contentColor = MaterialTheme.colorScheme.onBackground,
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 4.dp,
            ) {
                Dest.entries.forEach { dest ->
                    val selected = currentRoute == dest.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (!selected) {
                                nav.navigate(dest.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = if (selected) dest.selectedIcon else dest.icon,
                                contentDescription = dest.label,
                            )
                        },
                        label = { Text(dest.label, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Dest.Home.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Dest.Home.route) {
                HomeScreen(vm, onGoToPatch = {
                    nav.navigate(Dest.Patch.route) {
                        popUpTo(Dest.Home.route) { saveState = true }
                        launchSingleTop = true
                    }
                })
            }
            composable(Dest.Patch.route) { PatchScreen(vm) }
            composable(Dest.Crash.route) { CrashLogScreen(vm) }
            composable(Dest.Compiler.route) { CompilerScreen(vm) }
            composable(Dest.Releases.route) { ReleasesScreen(vm) }
        }
    }
}