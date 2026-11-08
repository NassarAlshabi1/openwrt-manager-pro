package com.openwrt.managerpro.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.openwrt.managerpro.R
import com.openwrt.managerpro.domain.RouterRepository
import com.openwrt.managerpro.ui.dashboard.DashboardScreen
import com.openwrt.managerpro.ui.devices.DevicesScreen
import com.openwrt.managerpro.ui.diagnostics.DiagnosticsScreen
import com.openwrt.managerpro.ui.firmware.FirmwareScreen
import com.openwrt.managerpro.ui.onboarding.OnboardingScreen
import com.openwrt.managerpro.ui.settings.SettingsScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

object Routes {
    const val ONBOARDING = "onboarding"
    const val DASHBOARD = "dashboard"
    const val DEVICES = "devices"
    const val DIAGNOSTICS = "diagnostics"
    const val FIRMWARE = "firmware"
    const val SETTINGS = "settings"
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val routerRepository: RouterRepository,
) : ViewModel() {
    private val _hasProfile = MutableStateFlow<Boolean?>(null)
    val hasProfile = _hasProfile.asStateFlow()

    init {
        recheck()
    }

    fun recheck() {
        viewModelScope.launch {
            _hasProfile.value = runCatching { routerRepository.hasProfile() }.getOrDefault(false)
        }
    }
}

private data class Tab(val route: String, val labelRes: Int, val icon: @Composable () -> Unit)

@Composable
fun AppRoot(mainViewModel: MainViewModel = hiltViewModel()) {
    val hasProfile by mainViewModel.hasProfile.collectAsStateWithLifecycle()

    when (hasProfile) {
        null -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        false -> {
            OnboardingScreen(onConnected = { mainViewModel.recheck() })
        }
        else -> {
            MainTabs(onProfileCleared = { mainViewModel.recheck() })
        }
    }
}

@Composable
private fun MainTabs(onProfileCleared: () -> Unit) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val tabs = listOf(
        Tab(Routes.DASHBOARD, R.string.nav_dashboard) { Icon(Icons.Filled.Dashboard, contentDescription = null) },
        Tab(Routes.DEVICES, R.string.nav_devices) { Icon(Icons.Filled.Devices, contentDescription = null) },
        Tab(Routes.DIAGNOSTICS, R.string.nav_diagnostics) { Icon(Icons.Filled.BugReport, contentDescription = null) },
        Tab(Routes.FIRMWARE, R.string.nav_firmware) { Icon(Icons.Filled.SystemUpdate, contentDescription = null) },
        Tab(Routes.SETTINGS, R.string.nav_settings) { Icon(Icons.Filled.Settings, contentDescription = null) },
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                for (tab in tabs) {
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = {
                            if (currentRoute != tab.route) {
                                navController.navigate(tab.route) {
                                    popUpTo(Routes.DASHBOARD) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = tab.icon,
                        label = { Text(stringResource(tab.labelRes)) },
                    )
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            NavHost(navController = navController, startDestination = Routes.DASHBOARD) {
                composable(Routes.DASHBOARD) { DashboardScreen() }
                composable(Routes.DEVICES) { DevicesScreen() }
                composable(Routes.DIAGNOSTICS) { DiagnosticsScreen() }
                composable(Routes.FIRMWARE) { FirmwareScreen() }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        onProfileCleared = {
                            onProfileCleared()
                            navController.navigate(Routes.ONBOARDING)
                        },
                    )
                }
                composable(Routes.ONBOARDING) {
                    OnboardingScreen(onConnected = { navController.navigate(Routes.DASHBOARD) })
                }
            }
        }
    }
}
