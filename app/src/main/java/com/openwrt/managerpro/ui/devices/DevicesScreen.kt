package com.openwrt.managerpro.ui.devices

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.openwrt.managerpro.domain.DeviceRecord
import com.openwrt.managerpro.domain.Format
import com.openwrt.managerpro.domain.GetDevicesUseCase
import com.openwrt.managerpro.domain.ObserveDeviceEventsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class DeviceFilter { ALL, WIRELESS, WIRED }

data class DevicesUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val devices: List<DeviceRecord> = emptyList(),
)

@HiltViewModel
class DevicesViewModel @Inject constructor(
    private val getDevices: GetDevicesUseCase,
    private val observeEvents: ObserveDeviceEventsUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(DevicesUiState())
    val state = _state.asStateFlow()

    init {
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
                _state.value = DevicesUiState(loading = false, devices = getDevices())
            } catch (e: Exception) {
                _state.value = DevicesUiState(loading = false, error = e.message ?: "error")
            }
        }
    }

    fun startLiveUpdates(intervalMs: Long = 3000L) = observeEvents.start(intervalMs)

    fun stopLiveUpdates() = observeEvents.stop()
}

@Composable
fun DevicesScreen(viewModel: DevicesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf(DeviceFilter.ALL.name) }
    val filterEnum = try { DeviceFilter.valueOf(filter) } catch (e: Exception) { DeviceFilter.ALL }

    LaunchedEffect(Unit) { viewModel.startLiveUpdates() }
    DisposableEffect(Unit) {
        onDispose { viewModel.stopLiveUpdates() }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.nav_devices), style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(R.string.devices_count, state.devices.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { viewModel.load() }) {
                if (state.loading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.refresh))
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = filterEnum == DeviceFilter.ALL,
                onClick = { filter = DeviceFilter.ALL.name },
                label = { Text(stringResource(R.string.devices_all)) },
            )
            FilterChip(
                selected = filterEnum == DeviceFilter.WIRELESS,
                onClick = { filter = DeviceFilter.WIRELESS.name },
                label = { Text(stringResource(R.string.devices_wireless)) },
            )
            FilterChip(
                selected = filterEnum == DeviceFilter.WIRED,
                onClick = { filter = DeviceFilter.WIRED.name },
                label = { Text(stringResource(R.string.devices_wired)) },
            )
        }

        Spacer(Modifier.height(8.dp))

        if (state.error != null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    state.error ?: "",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp),
                )
            }
        } else {
            val shown = when (filterEnum) {
                DeviceFilter.ALL -> state.devices
                DeviceFilter.WIRELESS -> state.devices.filter { it.isWireless }
                DeviceFilter.WIRED -> state.devices.filter { !it.isWireless }
            }
            if (shown.isEmpty() && !state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.devices_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(shown, key = { it.mac }) { device ->
                        DeviceRow(device)
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(device: DeviceRecord) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (device.isWireless) Icons.Filled.Wifi else Icons.Filled.Computer,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    device.hostname ?: device.vendor ?: stringResource(R.string.devices_unknown_host),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    buildString {
                        append(device.mac)
                        device.ip?.let { append("  -  $it") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        device.vendor ?: stringResource(R.string.devices_unknown_vendor),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (device.isWireless && device.signalDbm != null) {
                        Spacer(Modifier.width(8.dp))
                        SignalBars(dbm = device.signalDbm)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "${device.signalDbm} dBm",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Icon(
                imageVector = Icons.Filled.Devices,
                contentDescription = null,
                tint = if (device.isWireless) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun SignalBars(dbm: Int) {
    val level = Format.signalQuality(dbm)
    val active = MaterialTheme.colorScheme.primary
    val inactive = MaterialTheme.colorScheme.outline
    Row(verticalAlignment = Alignment.Bottom) {
        for (i in 1..4) {
            val color = if (i <= level) active else inactive
            Box(
                modifier = Modifier
                    .padding(horizontal = 1.dp)
                    .size(width = 4.dp, height = (4 + i * 3).dp)
                    .background(color, CircleShape),
            )
        }
    }
}
