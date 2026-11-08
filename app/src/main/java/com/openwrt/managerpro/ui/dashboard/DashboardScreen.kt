package com.openwrt.managerpro.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.openwrt.managerpro.R
import com.openwrt.managerpro.domain.DashboardData
import com.openwrt.managerpro.domain.Format
import com.openwrt.managerpro.domain.GetDashboardUseCase
import com.openwrt.managerpro.domain.ObserveDeviceEventsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val data: DashboardData? = null,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val getDashboard: GetDashboardUseCase,
    private val observeEvents: ObserveDeviceEventsUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardUiState())
    val state = _state.asStateFlow()

    init {
        observeEvents.start(5000L)
        load()
        viewModelScope.launch {
            observeEvents.events.collect {
                load()
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val data = getDashboard()
                _state.value = DashboardUiState(loading = false, data = data)
            } catch (e: Exception) {
                _state.value = DashboardUiState(loading = false, error = e.message ?: "error")
            }
        }
    }

    override fun onCleared() {
        observeEvents.stop()
        super.onCleared()
    }
}

@Composable
fun DashboardScreen(viewModel: DashboardViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.load()
    }

    val data = state.data
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.nav_dashboard), style = MaterialTheme.typography.headlineSmall)
                        data?.let {
                            Text(
                                "${it.distribution} ${it.firmwareVersion} - ${it.model}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(onClick = { viewModel.load() }) {
                        if (state.loading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.refresh))
                        }
                    }
                }
            }

            if (state.error != null) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                stringResource(R.string.error_generic),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(state.error ?: "", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            if (data != null) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard(
                            modifier = Modifier.weight(1f),
                            icon = { Icon(Icons.Filled.Speed, contentDescription = null) },
                            label = stringResource(R.string.dash_uptime),
                            value = Format.uptime(data.uptimeSec),
                        )
                        StatCard(
                            modifier = Modifier.weight(1f),
                            icon = { Icon(Icons.Filled.Devices, contentDescription = null) },
                            label = stringResource(R.string.dash_devices_online),
                            value = data.devicesOnline.toString(),
                        )
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard(
                            modifier = Modifier.weight(1f),
                            icon = { Icon(Icons.Filled.Memory, contentDescription = null) },
                            label = stringResource(R.string.dash_memory),
                            value = "${data.memUsedKb / 1024}/${data.memTotalKb / 1024} MB",
                        )
                        StatCard(
                            modifier = Modifier.weight(1f),
                            icon = { Icon(Icons.Filled.Thermostat, contentDescription = null) },
                            label = stringResource(R.string.dash_temperature),
                            value = data.temperatureC?.let { "$it C" } ?: "-",
                        )
                    }
                }
                item {
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                stringResource(R.string.dash_load),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(8.dp))
                            val maxLoad = 1.0
                            LinearProgressIndicator(
                                progress = (data.load1 / maxLoad).toFloat().coerceIn(0f, 1f),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.dash_load_value, data.load1, data.load5, data.load15),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                item {
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                stringResource(R.string.dash_network),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            InfoRow(stringResource(R.string.dash_status), stringResource(if (data.wanUp) R.string.dash_online else R.string.dash_offline))
                            InfoRow(stringResource(R.string.dash_wan_ip), data.wanIp ?: "-")
                            InfoRow(stringResource(R.string.dash_gateway), data.wanGateway ?: "-")
                            if (data.dnsServers.isNotEmpty()) {
                                InfoRow(stringResource(R.string.dash_dns), data.dnsServers.take(2).joinToString(", "))
                            }
                            InfoRow(stringResource(R.string.dash_hostname), data.hostname)
                            InfoRow(stringResource(R.string.dash_kernel), data.kernel)
                        }
                    }
                }
            }
        }
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
    label: String,
    value: String,
) {
    ElevatedCard(modifier = modifier) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            icon()
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
